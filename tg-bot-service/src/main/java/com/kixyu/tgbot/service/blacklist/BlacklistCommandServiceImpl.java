package com.kixyu.tgbot.service.blacklist;

import com.kixyu.tgbot.config.BotPolicyConstants;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 黑名单命令与按钮回调处理服务实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
class BlacklistCommandServiceImpl implements BlacklistCommandService {

    private final TelegramBotProperties telegramBotProperties;
    private final TelegramApiClient telegramApiClient;
    private final UserService userService;
    private final TopicService topicService;
    private final OnboardingSupport onboardingSupport;
    private final UserConfigKeyboardFactory userConfigKeyboardFactory;
    private final Map<String, Integer> lastListMessageIds = new ConcurrentHashMap<>();
    private final Map<String, Long> recentActionTimes = new ConcurrentHashMap<>();

    /**
     * 尝试处理群内黑名单管理消息。
     *
     * @param message Telegram 消息
     * @param chat    消息所在聊天
     * @return        如果消息已被黑名单功能消费则返回 true，否则返回 false
     */
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

    /**
     * 尝试处理黑名单相关按钮回调。
     *
     * @param callbackQuery Telegram 回调查询
     * @return              如果回调已被黑名单功能消费则返回 true，否则返回 false
     */
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

    /**
     * 执行拉黑操作并刷新关联话题状态。
     *
     * @param message             触发消息
     * @param chat                消息所在聊天
     * @param explicitUserId      显式指定的用户 ID
     * @param deleteSourceMessage 是否删除触发消息
     */
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

    /**
     * 执行取消拉黑操作并刷新关联话题状态。
     *
     * @param message             触发消息
     * @param chat                消息所在聊天
     * @param explicitUserId      显式指定的用户 ID
     * @param deleteSourceMessage 是否删除触发消息
     */
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

    /**
     * 发送已拉黑用户列表。
     *
     * @param message             触发消息
     * @param chat                消息所在聊天
     * @param deleteSourceMessage 是否删除触发消息
     */
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
                telegramApiClient.scheduleDelete(chat.id(), response.message().messageId(),
                        BotPolicyConstants.millis(BotPolicyConstants.BLOCKED_LIST_AUTO_DELETE_DELAY));
            }
        } catch (RuntimeException e) {
            log.warn("发送黑名单文本列表失败，chatId={}, threadId={}", chat.id(), threadId, e);
        }
        deleteSourceMessage(chat.id(), message, deleteSourceMessage);
    }

    /**
     * 构建黑名单列表文本。
     *
     * @param blockedUsers 已拉黑用户列表
     * @return             黑名单列表文本
     */
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

    /**
     * 构建黑名单列表自动删除提示。
     *
     * @return 自动删除提示文本
     */
    private String buildBlockedListAutoDeleteHint() {
        String durationText = BotPolicyConstants.formatDuration(BotPolicyConstants.BLOCKED_LIST_AUTO_DELETE_DELAY);
        return "\n\n提示：这条黑名单列表消息将在 " + durationText + " 后自动删除。";
    }

    /**
     * 构建黑名单命令帮助文本。
     *
     * @return 命令帮助文本
     */
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

    /**
     * 获取用户展示名称。
     *
     * @param user 用户实体
     * @return     展示名称
     */
    private String displayName(User user) {
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return "@" + user.getUsername();
        }
        return Topic.generateTopicName(user.getFirstName(), user.getLastName(), null, user.getUserId());
    }

    /**
     * 退出黑名单列表查看并删除列表消息。
     *
     * @param message 触发消息
     * @param chat    消息所在聊天
     */
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

    /**
     * 解析黑名单文本命令。
     *
     * @param rawText 原始消息文本
     * @return        解析后的命令；无法解析时返回 null
     */
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

    /**
     * 规范化命令文本。
     *
     * @param text 原始命令文本
     * @return     规范化后的命令文本
     */
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

    /**
     * 从内嵌取消拉黑命令中解析用户 ID。
     *
     * @param text 规范化后的命令文本
     * @return     用户 ID；无法解析时返回 null
     */
    private Long parseEmbeddedUnblockUserId(String text) {
        String prefix = "unblock_";
        if (!text.startsWith(prefix)) {
            return null;
        }
        return parseUserId(text.substring(prefix.length()));
    }

    /**
     * 解析用户 ID。
     *
     * @param text 用户 ID 文本
     * @return     用户 ID；无法解析时返回 null
     */
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

    /**
     * 根据当前群话题消息查找对应话题。
     *
     * @param message 群话题消息
     * @return        当前话题；无法匹配时返回 null
     */
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

    /**
     * 根据用户 ID 查找当前配置群中的话题。
     *
     * @param userId 用户 ID
     * @return       用户话题；无法匹配时返回 null
     */
    private Topic findTopicByUserId(Long userId) {
        Long groupId = telegramBotProperties.getGroupId();
        if (groupId == null || userId == null) {
            return null;
        }
        return topicService.getTopicByUserIdAndChatId(userId, String.valueOf(groupId)).orElse(null);
    }

    /**
     * 刷新话题欢迎消息上的配置按钮。
     *
     * @param chatId 聊天 ID
     * @param topic  话题实体
     */
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

    /**
     * 刷新当前消息上的配置按钮。
     *
     * @param chatId              聊天 ID
     * @param message             当前消息
     * @param topic               话题实体
     * @param deleteSourceMessage 是否删除触发消息
     */
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

    /**
     * 向用户发送已拉黑通知。
     *
     * @param userId 用户 ID
     */
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

    /**
     * 向用户发送已取消拉黑通知。
     *
     * @param userId 用户 ID
     */
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

    /**
     * 在指定聊天或话题中发送临时提示。
     *
     * @param chatId   聊天 ID
     * @param threadId 话题线程 ID
     * @param text     提示文本
     */
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

    /**
     * 删除命令消息。
     *
     * @param chatId  聊天 ID
     * @param message 命令消息
     */
    private void deleteCommandMessage(Long chatId, Message message) {
        deleteSourceMessage(chatId, message, true);
    }

    /**
     * 按需删除来源消息。
     *
     * @param chatId  聊天 ID
     * @param message 来源消息
     * @param delete  是否删除
     */
    private void deleteSourceMessage(Long chatId, Message message, boolean delete) {
        if (chatId != null && message != null && message.messageId() != null) {
            if (delete) {
                telegramApiClient.scheduleDelete(chatId, message.messageId(), 0L);
            }
        }
    }

    /**
     * 构建黑名单列表消息缓存键。
     *
     * @param chatId   聊天 ID
     * @param threadId 话题线程 ID
     * @return         缓存键
     */
    private String listKey(Long chatId, Long threadId) {
        return chatId + ":" + (threadId == null ? 0L : threadId);
    }

    /**
     * 判断当前操作是否为短时间内重复操作。
     *
     * @param action   操作名称
     * @param chatId   聊天 ID
     * @param threadId 话题线程 ID
     * @param userId   目标用户 ID
     * @return         重复操作时返回 true，否则返回 false
     */
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
        return now - previous < BotPolicyConstants.millis(BotPolicyConstants.BLACKLIST_ACTION_DEBOUNCE_WINDOW);
    }

    /**
     * 黑名单文本命令动作。
     */
    private enum BlacklistAction {
        BLOCK,
        UNBLOCK,
        LIST,
        EXIT_LIST
    }

    /**
     * 解析后的黑名单文本命令。
     *
     * @param action 黑名单动作
     * @param userId 目标用户 ID
     */
    private record ParsedBlacklistCommand(BlacklistAction action, Long userId) {
    }

    /**
     * 解析后的黑名单按钮回调动作。
     *
     * @param action 动作名称
     * @param userId 目标用户 ID
     */
    private record CallbackAction(String action, long userId) {
    }

    /**
     * 解析黑名单按钮回调数据。
     *
     * @param data 回调数据
     * @return     解析后的回调动作；无法解析时返回 null
     */
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

    /**
     * 判断回调是否来自非主人用户。
     *
     * @param callbackQuery 回调查询对象
     * @return              非主人用户时返回 true，否则返回 false
     */
    private boolean isNotOwnerCallback(CallbackQuery callbackQuery) {
        Long ownerId = telegramBotProperties.getOwnerId();
        if (ownerId != null && (callbackQuery.from() == null || !ownerId.equals(callbackQuery.from().id()))) {
            answer(callbackQuery, "🛡️ 只有主人可以操作这个按钮～");
            return true;
        }
        return false;
    }

    /**
     * 回复黑名单相关按钮回调。
     *
     * @param callbackQuery 回调查询对象
     * @param text          提示文本
     */
    private void answer(CallbackQuery callbackQuery, String text) {
        telegramApiClient.answerCallback(callbackQuery, text, true);
    }
}
