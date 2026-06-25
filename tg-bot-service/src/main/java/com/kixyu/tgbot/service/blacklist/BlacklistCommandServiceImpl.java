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
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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

        BlacklistCommandParser.Command command = BlacklistCommandParser.parse(message.text());
        if (command == null) {
            return false;
        }

        switch (command.action()) {
            case BLOCK -> block(message, chat, command.userId(), true);
            case UNBLOCK -> unblock(message, chat, command.userId(), true);
            case LIST -> sendBlockedList(message, chat);
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

        BlacklistCallbackAction callbackAction = BlacklistCallbackAction.parse(callbackQuery.data());
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
            case "list_open" -> {
                sendBlockedListPanel(message, chat, callbackAction.page());
                answer(callbackQuery, null);
            }
            case "list_page" -> updateBlockedListPanel(callbackQuery, message, chat, callbackAction.page());
            case "list_unblock" -> unblockFromList(callbackQuery, message, chat, callbackAction.userId(), callbackAction.page());
            case "list_close" -> closeBlockedListPanel(callbackQuery, message, chat);
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
        onboardingSupport.refreshWelcomeMessage(refreshedTopic);
        refreshOperationPanelKeyboard(chat.id(), message, refreshedTopic, deleteSourceMessage);
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
        onboardingSupport.refreshWelcomeMessage(refreshedTopic);
        refreshOperationPanelKeyboard(chat.id(), message, refreshedTopic, deleteSourceMessage);
        sendHint(chat.id(), message.messageThreadId(), "已取消拉黑用户：" + targetUserId);
        deleteSourceMessage(chat.id(), message, deleteSourceMessage);
        log.info("已通过黑名单入口取消拉黑用户，userId={}, sourceMessageId={}", targetUserId, message.messageId());
    }

    /**
     * 发送已拉黑用户分页管理面板。
     *
     * @param message 触发消息
     * @param chat    消息所在聊天
     */
    private void sendBlockedList(Message message, Chat chat) {
        Long threadId = message.messageThreadId();
        if (isDuplicateAction("list", chat.id(), threadId, null)) {
            deleteCommandMessage(chat.id(), message);
            return;
        }
        sendBlockedListPanel(message, chat, 0);
        deleteCommandMessage(chat.id(), message);
    }

    /**
     * 发送已拉黑用户分页管理面板。
     *
     * @param message       触发消息
     * @param chat          消息所在聊天
     * @param requestedPage 请求页码
     */
    private void sendBlockedListPanel(Message message, Chat chat, int requestedPage) {
        Long threadId = message.messageThreadId();
        List<User> blockedUsers = userService.listBlocked();
        int page = BlacklistPanelFactory.normalizePage(requestedPage, blockedUsers == null ? 0 : blockedUsers.size());
        String text = BlacklistPanelFactory.buildText(blockedUsers, page);
        SendMessage req = telegramApiClient.createSendMessage(chat.id(), text);
        req.replyMarkup(BlacklistPanelFactory.buildKeyboard(blockedUsers, page));
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
    }

    /**
     * 更新黑名单分页管理面板。
     *
     * @param callbackQuery 回调查询对象
     * @param message       面板消息
     * @param chat          消息所在聊天
     * @param requestedPage 请求页码
     */
    private void updateBlockedListPanel(CallbackQuery callbackQuery, Message message, Chat chat, int requestedPage) {
        List<User> blockedUsers = userService.listBlocked();
        int page = BlacklistPanelFactory.normalizePage(requestedPage, blockedUsers == null ? 0 : blockedUsers.size());
        if (editBlockedListPanel(callbackQuery, chat.id(), message.messageId(), blockedUsers, page)) {
            answer(callbackQuery, null);
        }
    }

    /**
     * 从分页管理面板取消拉黑用户。
     *
     * @param callbackQuery 回调查询对象
     * @param message       面板消息
     * @param chat          消息所在聊天
     * @param userId        目标用户 ID
     * @param requestedPage 请求页码
     */
    private void unblockFromList(CallbackQuery callbackQuery, Message message, Chat chat, long userId, int requestedPage) {
        if (userId <= 0L) {
            answer(callbackQuery, "用户 ID 无效");
            return;
        }
        if (isDuplicateAction("list_unblock", chat.id(), message.messageThreadId(), userId)) {
            answer(callbackQuery, null);
            return;
        }
        userService.unblock(userId);
        sendUnblockedNotice(userId);
        Topic refreshedTopic = findTopicByUserId(userId);
        onboardingSupport.syncBlockedTopicName(refreshedTopic, false);
        onboardingSupport.refreshWelcomeMessage(refreshedTopic);
        List<User> blockedUsers = userService.listBlocked();
        int page = BlacklistPanelFactory.normalizePage(requestedPage, blockedUsers == null ? 0 : blockedUsers.size());
        if (editBlockedListPanel(callbackQuery, chat.id(), message.messageId(), blockedUsers, page)) {
            answer(callbackQuery, "已取消拉黑");
        }
        log.info("已通过黑名单分页面板取消拉黑用户，userId={}, panelMessageId={}", userId, message.messageId());
    }

    /**
     * 关闭黑名单分页管理面板。
     *
     * @param callbackQuery 回调查询对象
     * @param message       面板消息
     * @param chat          消息所在聊天
     */
    private void closeBlockedListPanel(CallbackQuery callbackQuery, Message message, Chat chat) {
        try {
            telegramApiClient.execute(new DeleteMessage(chat.id(), message.messageId()));
            lastListMessageIds.remove(listKey(chat.id(), message.messageThreadId()));
            answer(callbackQuery, null);
        } catch (RuntimeException e) {
            log.warn("关闭黑名单分页面板失败，chatId={}, messageId={}", chat.id(), message.messageId(), e);
            answer(callbackQuery, "关闭失败");
        }
    }

    /**
     * 编辑黑名单分页管理面板。
     *
     * @param callbackQuery 回调查询对象
     * @param chatId        聊天 ID
     * @param messageId     面板消息 ID
     * @param blockedUsers  已拉黑用户列表
     * @param page          页码
     * @return              更新成功时返回 true，否则返回 false
     */
    private boolean editBlockedListPanel(CallbackQuery callbackQuery, Long chatId, Integer messageId, List<User> blockedUsers, int page) {
        try {
            telegramApiClient.execute(
                    new EditMessageText(chatId, messageId, BlacklistPanelFactory.buildText(blockedUsers, page))
                            .replyMarkup(BlacklistPanelFactory.buildKeyboard(blockedUsers, page))
            );
            return true;
        } catch (RuntimeException e) {
            log.warn("更新黑名单分页面板失败，chatId={}, messageId={}, page={}", chatId, messageId, page, e);
            answer(callbackQuery, "刷新失败");
            return false;
        }
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
     * 刷新临时用户配置面板上的按钮状态。
     *
     * @param chatId              聊天 ID
     * @param message             当前消息
     * @param topic               话题实体
     * @param deleteSourceMessage 是否删除触发消息
     */
    private void refreshOperationPanelKeyboard(Long chatId, Message message, Topic topic, boolean deleteSourceMessage) {
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
            log.warn("刷新用户配置面板按钮状态失败，chatId={}, topicId={}, messageId={}", chatId, topic.getTopicId(), message.messageId(), e);
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
