package com.kixyu.tgbot.service.blacklist;

import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.domain.entity.User;
import com.kixyu.tgbot.service.topic.TopicService;
import com.kixyu.tgbot.service.user.UserService;
import com.kixyu.tgbot.support.OnboardingSupport;
import com.kixyu.tgbot.support.UserConfigKeyboardFactory;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.EditMessageReplyMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
class BlacklistCommandServiceImpl implements BlacklistCommandService {

    private static final long BLOCKED_LIST_DELETE_DELAY_MILLIS = 30_000L;
    private static final long ACTION_DEBOUNCE_MILLIS = 5_000L;

    private final TelegramBotProperties telegramBotProperties;
    private final TelegramApiClient telegramApiClient;
    private final UserService userService;
    private final TopicService topicService;
    private final OnboardingSupport onboardingSupport;
    private final UserConfigKeyboardFactory userConfigKeyboardFactory;
    private final Map<String, Integer> lastListMessageIds = new ConcurrentHashMap<>();
    private final Map<String, Long> recentActionTimes = new ConcurrentHashMap<>();

    @Override
    public boolean handleIfBlacklistMessage(Message message, Chat chat) {
        if (message == null || message.from() == null || chat == null || chat.id() == null || message.text() == null) {
            return false;
        }
        Long groupId = telegramBotProperties.getGroupId();
        Long ownerId = telegramBotProperties.getOwnerId();
        if (groupId == null || !groupId.equals(chat.id()) || ownerId == null || !ownerId.equals(message.from().id())) {
            return false;
        }

        ParsedBlacklistCommand command = parse(message.text());
        if (command == null) {
            return false;
        }

        switch (command.action()) {
            case BLOCK -> block(message, chat, command.userId(), true);
            case UNBLOCK -> unblock(message, chat, command.userId(), true);
            case LIST -> sendBlockedList(message, chat, true);
            case EXIT_LIST -> exitBlockedList(message, chat);
        }
        return true;
    }

    @Override
    public boolean handleIfBlacklistCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.data() == null || !callbackQuery.data().startsWith("bl:")) {
            return false;
        }
        if (isNotOwnerCallback(callbackQuery)) {
            return true;
        }

        CallbackAction callbackAction = parseCallbackAction(callbackQuery.data());
        if (callbackAction == null) {
            answer(callbackQuery, "⚠️ 回调数据格式不对，操作失败了～");
            return true;
        }

        Object rawMessage = callbackQuery.maybeInaccessibleMessage();
        Message message = rawMessage instanceof Message m ? m : null;
        Chat chat = message == null ? null : message.chat();
        if (chat == null || chat.id() == null) {
            answer(callbackQuery, null);
            return true;
        }
        Long groupId = telegramBotProperties.getGroupId();
        if (groupId != null && !groupId.equals(chat.id())) {
            answer(callbackQuery, "🏠 只能在指定的配置群组里操作这条按钮哦～");
            return true;
        }

        switch (callbackAction.action()) {
            case "block" -> {
                block(message, chat, callbackAction.userId(), false);
                answer(callbackQuery, "已拉黑该用户");
            }
            case "unblock" -> {
                unblock(message, chat, callbackAction.userId(), false);
                answer(callbackQuery, "已取消拉黑");
            }
            case "list" -> {
                sendBlockedList(message, chat, false);
                answer(callbackQuery, null);
            }
            default -> answer(callbackQuery, "未知操作");
        }
        return true;
    }

    private void block(Message message, Chat chat, Long explicitUserId, boolean deleteSourceMessage) {
        Topic topic = null;
        Long targetUserId = explicitUserId;
        if (targetUserId == null) {
            topic = findCurrentTopic(message);
            targetUserId = topic == null ? null : topic.getUserId();
        }
        if (targetUserId == null) {
            sendHint(chat.id(), message.messageThreadId(), "⚠️ 小提示\n\n在用户话题里发送 /block，或使用 /block 用户ID。");
            deleteCommandMessage(chat.id(), message);
            return;
        }
        if (isDuplicateAction("block", chat.id(), message.messageThreadId(), targetUserId)) {
            deleteSourceMessage(chat.id(), message, deleteSourceMessage);
            return;
        }

        userService.block(targetUserId);
        sendBlockedNotice(targetUserId);
        Topic refreshedTopic = topic == null ? findTopicByUserId(targetUserId) : topic;
        onboardingSupport.syncBlockedTopicName(refreshedTopic, true);
        refreshTopicKeyboard(chat.id(), refreshedTopic);
        refreshSourceKeyboard(chat.id(), message, refreshedTopic, deleteSourceMessage);
        sendHint(chat.id(), message.messageThreadId(), "已拉黑用户：" + targetUserId);
        deleteSourceMessage(chat.id(), message, deleteSourceMessage);
        log.info("已通过黑名单入口拉黑用户，userId={}, sourceMessageId={}", targetUserId, message.messageId());
    }

    private void unblock(Message message, Chat chat, Long explicitUserId, boolean deleteSourceMessage) {
        Topic topic = null;
        Long targetUserId = explicitUserId;
        if (targetUserId == null) {
            topic = findCurrentTopic(message);
            targetUserId = topic == null ? null : topic.getUserId();
        }
        if (targetUserId == null) {
            sendHint(chat.id(), message.messageThreadId(), "⚠️ 小提示\n\n在用户话题里发送 /unblock，或使用 /unblock 用户ID。");
            deleteCommandMessage(chat.id(), message);
            return;
        }
        if (isDuplicateAction("unblock", chat.id(), message.messageThreadId(), targetUserId)) {
            deleteSourceMessage(chat.id(), message, deleteSourceMessage);
            return;
        }

        userService.unblock(targetUserId);
        sendUnblockedNotice(targetUserId);
        Topic refreshedTopic = topic == null ? findTopicByUserId(targetUserId) : topic;
        onboardingSupport.syncBlockedTopicName(refreshedTopic, false);
        refreshTopicKeyboard(chat.id(), refreshedTopic);
        refreshSourceKeyboard(chat.id(), message, refreshedTopic, deleteSourceMessage);
        sendHint(chat.id(), message.messageThreadId(), "已取消拉黑用户：" + targetUserId);
        deleteSourceMessage(chat.id(), message, deleteSourceMessage);
        log.info("已通过黑名单入口取消拉黑用户，userId={}, sourceMessageId={}", targetUserId, message.messageId());
    }

    private void sendBlockedList(Message message, Chat chat, boolean deleteSourceMessage) {
        Long threadId = message.messageThreadId();
        if (isDuplicateAction("list", chat.id(), threadId, null)) {
            deleteSourceMessage(chat.id(), message, deleteSourceMessage);
            return;
        }
        List<User> blockedUsers = userService.listBlocked();
        String text = buildBlockedListText(blockedUsers);
        SendMessage req = telegramApiClient.createSendMessage(chat.id(), text);
        if (threadId != null) {
            req.messageThreadId(threadId);
        }
        try {
            SendResponse response = telegramApiClient.execute(req);
            if (response != null && response.message() != null && response.message().messageId() != null) {
                lastListMessageIds.put(listKey(chat.id(), threadId), response.message().messageId());
                telegramApiClient.scheduleDelete(chat.id(), response.message().messageId(), BLOCKED_LIST_DELETE_DELAY_MILLIS);
            }
        } catch (RuntimeException e) {
            log.warn("发送黑名单文本列表失败，chatId={}, threadId={}", chat.id(), threadId, e);
        }
        deleteSourceMessage(chat.id(), message, deleteSourceMessage);
    }

    private String buildBlockedListText(List<User> blockedUsers) {
        if (blockedUsers == null || blockedUsers.isEmpty()) {
            return "✅ 当前没有已拉黑的用户。\n\n" + buildCommandHelpText() + buildBlockedListAutoDeleteHint();
        }

        StringBuilder text = new StringBuilder("🧾 黑名单成员\n\n");
        int index = 1;
        for (User user : blockedUsers) {
            if (user == null || user.getUserId() == null) {
                continue;
            }
            text.append(index++)
                    .append(". ")
                    .append(displayName(user))
                    .append(" (")
                    .append(user.getUserId())
                    .append(")\n")
                    .append("   取消拉黑：/unblock_")
                    .append(user.getUserId())
                    .append("\n");
        }
        text.append("\n").append(buildCommandHelpText()).append(buildBlockedListAutoDeleteHint());
        return text.toString();
    }

    private String buildBlockedListAutoDeleteHint() {
        long seconds = Math.max(1L, Duration.ofMillis(BLOCKED_LIST_DELETE_DELAY_MILLIS).toSeconds());
        String durationText;
        if (seconds < 60L || seconds % 60L != 0L) {
            durationText = seconds + " 秒";
        } else {
            durationText = (seconds / 60L) + " 分钟";
        }
        return "\n\n提示：这条黑名单列表消息将在 " + durationText + " 后自动删除。";
    }

    private String buildCommandHelpText() {
        return """
                可用命令：
                .拉黑：在当前用户话题拉黑该用户
                .取消拉黑：在当前用户话题取消拉黑
                .拉黑 用户ID：按用户 ID 拉黑
                .取消拉黑 用户ID：按用户 ID 取消拉黑
                .黑名单：查看黑名单成员
                .退出黑名单：退出查看并删除列表
                /exit_blacklist：点一下发送即可退出查看
                """.stripTrailing();
    }

    private String displayName(User user) {
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return "@" + user.getUsername();
        }
        return Topic.generateTopicName(user.getFirstName(), user.getLastName(), null, user.getUserId());
    }

    private void exitBlockedList(Message message, Chat chat) {
        Long threadId = message.messageThreadId();
        if (isDuplicateAction("exit_list", chat.id(), threadId, null)) {
            deleteCommandMessage(chat.id(), message);
            return;
        }
        Integer listMessageId = lastListMessageIds.remove(listKey(chat.id(), threadId));
        if (listMessageId == null && message.replyToMessage() != null && message.replyToMessage().messageId() != null) {
            String repliedText = message.replyToMessage().text();
            if (repliedText != null && repliedText.contains("黑名单成员")) {
                listMessageId = message.replyToMessage().messageId();
            }
        }
        if (listMessageId != null) {
            try {
                telegramApiClient.execute(new DeleteMessage(chat.id(), listMessageId));
            } catch (RuntimeException e) {
                log.warn("删除黑名单文本列表失败，chatId={}, messageId={}", chat.id(), listMessageId, e);
            }
        }
        deleteCommandMessage(chat.id(), message);
    }

    private ParsedBlacklistCommand parse(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            return null;
        }

        String normalized = normalizeCommandText(text);
        ParsedBlacklistCommand directCommand = switch (normalized) {
            case "拉黑", "block", "ban" -> new ParsedBlacklistCommand(BlacklistAction.BLOCK, null);
            case "取消拉黑", "解除拉黑", "unblock", "unban" -> new ParsedBlacklistCommand(BlacklistAction.UNBLOCK, null);
            case "黑名单", "查看黑名单", "查看黑名单成员", "blacklist", "blocked" ->
                    new ParsedBlacklistCommand(BlacklistAction.LIST, null);
            case "退出黑名单", "退出查看黑名单", "退出查看黑名单成员", "exit", "exit_blacklist", "close_blacklist" ->
                    new ParsedBlacklistCommand(BlacklistAction.EXIT_LIST, null);
            default -> null;
        };
        if (directCommand != null) {
            return directCommand;
        }

        Long embeddedUnblockUserId = parseEmbeddedUnblockUserId(normalized);
        if (embeddedUnblockUserId != null) {
            return new ParsedBlacklistCommand(BlacklistAction.UNBLOCK, embeddedUnblockUserId);
        }

        String[] parts = normalized.split("\\s+");
        if (parts.length >= 2) {
            Long userId = parseUserId(parts[1]);
            if (userId == null) {
                return null;
            }
            return switch (parts[0]) {
                case "拉黑", "block", "ban" -> new ParsedBlacklistCommand(BlacklistAction.BLOCK, userId);
                case "取消拉黑", "解除拉黑", "unblock", "unban" -> new ParsedBlacklistCommand(BlacklistAction.UNBLOCK, userId);
                default -> null;
            };
        }
        return null;
    }

    private String normalizeCommandText(String text) {
        String normalized = text.trim();
        if (normalized.startsWith("/") || normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        int atIndex = normalized.indexOf('@');
        int spaceIndex = normalized.indexOf(' ');
        if (atIndex > 0 && (spaceIndex < 0 || atIndex < spaceIndex)) {
            String commandPart = normalized.substring(0, atIndex);
            String argsPart = spaceIndex > 0 ? normalized.substring(spaceIndex) : "";
            normalized = commandPart + argsPart;
        }
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private Long parseEmbeddedUnblockUserId(String text) {
        String prefix = "unblock_";
        if (!text.startsWith(prefix)) {
            return null;
        }
        return parseUserId(text.substring(prefix.length()));
    }

    private Long parseUserId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Topic findCurrentTopic(Message message) {
        Long groupId = telegramBotProperties.getGroupId();
        Long threadId = message.messageThreadId();
        if (groupId == null || threadId == null) {
            return null;
        }
        String groupChatId = String.valueOf(groupId);
        return topicService.getTopicByTopicId(threadId)
                .filter(topic -> groupChatId.equals(topic.getChatId()))
                .orElse(null);
    }

    private Topic findTopicByUserId(Long userId) {
        Long groupId = telegramBotProperties.getGroupId();
        if (groupId == null || userId == null) {
            return null;
        }
        return topicService.getTopicByUserIdAndChatId(userId, String.valueOf(groupId)).orElse(null);
    }

    private void refreshTopicKeyboard(Long chatId, Topic topic) {
        if (chatId == null || topic == null || topic.getWelcomeMessageId() == null || topic.getWelcomeMessageId() > Integer.MAX_VALUE) {
            return;
        }
        try {
            InlineKeyboardMarkup markup = userConfigKeyboardFactory.buildForTopic(topic);
            telegramApiClient.execute(new EditMessageReplyMarkup(chatId, topic.getWelcomeMessageId().intValue()).replyMarkup(markup));
        } catch (RuntimeException e) {
            log.warn("刷新黑名单按钮状态失败，chatId={}, topicId={}, messageId={}", chatId, topic.getTopicId(), topic.getWelcomeMessageId(), e);
        }
    }

    private void refreshSourceKeyboard(Long chatId, Message message, Topic topic, boolean deleteSourceMessage) {
        if (deleteSourceMessage || chatId == null || message == null || message.messageId() == null || topic == null) {
            return;
        }
        Long welcomeMessageId = topic.getWelcomeMessageId();
        if (welcomeMessageId != null && welcomeMessageId.equals(message.messageId().longValue())) {
            return;
        }
        try {
            InlineKeyboardMarkup markup = userConfigKeyboardFactory.buildForTopic(topic);
            telegramApiClient.execute(new EditMessageReplyMarkup(chatId, message.messageId()).replyMarkup(markup));
        } catch (RuntimeException e) {
            log.warn("刷新当前黑名单按钮状态失败，chatId={}, topicId={}, messageId={}", chatId, topic.getTopicId(), message.messageId(), e);
        }
    }

    private void sendBlockedNotice(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            SendResponse response = telegramApiClient.execute(telegramApiClient.createSendMessage(userId, "🚫 转发状态\n\n当前状态：你的消息「不会再被转发给主人」。"));
            telegramApiClient.scheduleDeleteIfOk(userId, response);
        } catch (RuntimeException e) {
            log.warn("发送拉黑提示给用户失败，userId={}", userId, e);
        }
    }

    private void sendUnblockedNotice(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            SendResponse response = telegramApiClient.execute(telegramApiClient.createSendMessage(userId, "✅ 转发状态\n\n当前状态：你的消息「会再次被转发给主人啦～」。"));
            telegramApiClient.scheduleDeleteIfOk(userId, response);
        } catch (RuntimeException e) {
            log.warn("发送取消拉黑提示给用户失败，userId={}", userId, e);
        }
    }

    private void sendHint(Long chatId, Long threadId, String text) {
        if (chatId == null || text == null || text.isBlank()) {
            return;
        }
        try {
            SendMessage req = telegramApiClient.createSendMessage(chatId, text);
            if (threadId != null) {
                req.messageThreadId(threadId);
            }
            SendResponse response = telegramApiClient.execute(req);
            telegramApiClient.scheduleDeleteIfOk(chatId, response);
        } catch (RuntimeException e) {
            log.warn("发送黑名单命令提示失败，chatId={}, threadId={}", chatId, threadId, e);
        }
    }

    private void deleteCommandMessage(Long chatId, Message message) {
        deleteSourceMessage(chatId, message, true);
    }

    private void deleteSourceMessage(Long chatId, Message message, boolean delete) {
        if (chatId != null && message != null && message.messageId() != null) {
            if (delete) {
                telegramApiClient.scheduleDelete(chatId, message.messageId(), 0L);
            }
        }
    }

    private String listKey(Long chatId, Long threadId) {
        return chatId + ":" + (threadId == null ? 0L : threadId);
    }

    private boolean isDuplicateAction(String action, Long chatId, Long threadId, Long userId) {
        if (action == null || chatId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = action + ":" + listKey(chatId, threadId) + ":" + (userId == null ? 0L : userId);
        Long previous = recentActionTimes.put(key, now);
        if (previous == null) {
            return false;
        }
        return now - previous < ACTION_DEBOUNCE_MILLIS;
    }

    private enum BlacklistAction {
        BLOCK,
        UNBLOCK,
        LIST,
        EXIT_LIST
    }

    private record ParsedBlacklistCommand(BlacklistAction action, Long userId) {
    }

    private record CallbackAction(String action, long userId) {
    }

    private CallbackAction parseCallbackAction(String data) {
        String[] parts = data.split(":");
        if (parts.length != 3) {
            return null;
        }
        long userId;
        try {
            userId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        return new CallbackAction(parts[1], userId);
    }

    private boolean isNotOwnerCallback(CallbackQuery callbackQuery) {
        Long ownerId = telegramBotProperties.getOwnerId();
        if (ownerId != null && (callbackQuery.from() == null || !ownerId.equals(callbackQuery.from().id()))) {
            answer(callbackQuery, "🛡️ 只有主人可以操作这个按钮～");
            return true;
        }
        return false;
    }

    private void answer(CallbackQuery callbackQuery, String text) {
        telegramApiClient.answerCallback(callbackQuery, text, true);
    }
}
