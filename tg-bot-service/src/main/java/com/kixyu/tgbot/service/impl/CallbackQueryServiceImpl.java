package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.domain.entity.User;
import com.kixyu.tgbot.service.CallbackQueryService;
import com.kixyu.tgbot.service.TopicService;
import com.kixyu.tgbot.service.UserService;
import com.kixyu.tgbot.support.OnboardingSupport;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.EditMessageReplyMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackQueryServiceImpl implements CallbackQueryService {

    private static final String BLOCK_CALLBACK_PREFIX = "bl:";
    private static final String MODE_CALLBACK_PREFIX = "md:";

    private final TelegramApiClient telegramApiClient;
    private final TelegramBotProperties telegramBotProperties;
    private final UserService userService;
    private final TopicService topicService;
    private final OnboardingSupport onboardingSupport;

    /**
     * 处理 Telegram 的回调查询事件。
     *
     * @param callbackQuery 回调查询对象
     */
    @Override
    public void handleCallbackQuery(CallbackQuery callbackQuery) {
        if (callbackQuery == null) {
            return;
        }
        String data = callbackQuery.data();
        if (data == null || data.isBlank()) {
            answer(callbackQuery, null);
            return;
        }
        if (data.startsWith(BLOCK_CALLBACK_PREFIX)) {
            handleBlockCallback(callbackQuery, data);
            return;
        }
        if (data.startsWith(MODE_CALLBACK_PREFIX)) {
            handleModeCallback(callbackQuery, data);
            return;
        }
        answer(callbackQuery, null);
    }

    /**
     * 给 Telegram 回调查询发送响应，可选附带提示文本。
     *
     * @param callbackQuery 回调查询对象
     * @param text          可选提示文本，为 null 则不下发文字
     */
    private void answer(CallbackQuery callbackQuery, String text) {
        if (callbackQuery == null || callbackQuery.id() == null) {
            return;
        }
        AnswerCallbackQuery req = new AnswerCallbackQuery(callbackQuery.id());
        if (text != null && !text.isBlank()) {
            req.text(text);
        }
        telegramApiClient.execute(req);
    }

    /**
     * 检查回调查询是否来自非主人用户。
     * 如果是，会发送提示并返回 true；否则返回 false。
     *
     * @param callbackQuery 回调查询对象
     * @return 如果不是主人用户则返回 true，否则返回 false
     */
    private boolean isNotOwnerOperator(CallbackQuery callbackQuery) {
        Long ownerId = telegramBotProperties.getOwnerId();
        if (ownerId != null && (callbackQuery.from() == null || !ownerId.equals(callbackQuery.from().id()))) {
            answer(callbackQuery, "🛡️ 只有主人可以操作这个按钮～");
            return true;
        }
        return false;
    }

    private record CallbackAction(String action, long id) {
    }

    /**
     * 解析回调查询数据。
     *
     * @param callbackQuery     回调查询对象
     * @param data              回调查询数据
     * @param invalidIdMessage  如果无效的用户ID，则返回的提示文本
     * @return 解析后的 CallbackAction 对象，如果解析失败则返回 null
     */
    private CallbackAction parseCallbackAction(CallbackQuery callbackQuery, String data, String invalidIdMessage) {
        String[] parts = data.split(":");
        if (parts.length != 3) {
            answer(callbackQuery, "⚠️ 回调数据格式不对，操作失败了～");
            return null;
        }
        String action = parts[1];
        long id;
        try {
            id = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            answer(callbackQuery, invalidIdMessage);
            return null;
        }
        return new CallbackAction(action, id);
    }

    /**
     * 处理拉黑回调。
     *
     * @param callbackQuery 回调查询对象
     * @param data          回调查询数据
     */
    private void handleBlockCallback(CallbackQuery callbackQuery, String data) {
        if (isNotOwnerOperator(callbackQuery)) {
            return;
        }
        CallbackAction callbackAction = parseCallbackAction(callbackQuery, data, "⚠️ 用户 ID 看起来不太对呢～");
        if (callbackAction == null) {
            return;
        }
        String action = callbackAction.action();

        Object rawMessage = callbackQuery.maybeInaccessibleMessage();
        Message message = rawMessage instanceof Message m ? m : null;
        if ("list".equals(action)) {
            handleBlockedList(callbackQuery, message);
            return;
        }

        Long targetUserId = callbackAction.id();
        if ("block".equals(action)) {
            userService.block(targetUserId);
            notifyTargetUserBlocked(targetUserId);
            answer(callbackQuery, "已拉黑该用户");
        } else if ("unblock".equals(action)) {
            userService.unblock(targetUserId);
            notifyTargetUserUnblocked(targetUserId);
            answer(callbackQuery, "已取消拉黑");
        } else {
            answer(callbackQuery, "未知操作");
            return;
        }
        if (message == null || message.chat() == null || message.messageId() == null) {
            return;
        }
        Long chatId = message.chat().id();
        Integer messageId = message.messageId();

        String text = message.text();
        if ("unblock".equals(action)
                && text != null
                && text.contains("🧾 请选择要取消拉黑的用户")) {
            try {
                telegramApiClient.execute(new DeleteMessage(chatId, messageId));
            } catch (RuntimeException e) {
                log.warn("删除已拉黑用户列表消息失败，chatId={}, messageId={}", chatId, messageId, e);
            }
            return;
        }

        Long groupId = telegramBotProperties.getGroupId();
        Long threadId = message.messageThreadId();
        if (groupId == null || threadId == null || !groupId.equals(chatId)) {
            return;
        }
        Topic topic = topicService.getTopicByTopicId(threadId).orElse(null);
        if (topic == null || !String.valueOf(groupId).equals(topic.getChatId())) {
            return;
        }
        InlineKeyboardMarkup markup = onboardingSupport.buildUserConfigKeyboard(topic);
        EditMessageReplyMarkup edit = new EditMessageReplyMarkup(chatId, messageId).replyMarkup(markup);
        telegramApiClient.execute(edit);
    }

    /**
     * 处理已拉黑用户列表回调。
     *
     * @param callbackQuery 回调查询对象
     * @param message       消息实体
     */
    private void handleBlockedList(CallbackQuery callbackQuery, Message message) {
        if (message == null || message.chat() == null || message.chat().id() == null) {
            answer(callbackQuery, null);
            return;
        }
        Long chatId = message.chat().id();
        Long configuredGroupId = telegramBotProperties.getGroupId();
        if (configuredGroupId != null && !configuredGroupId.equals(chatId)) {
            answer(callbackQuery, "🏠 只能在指定的配置群组里操作这条按钮哦～");
            return;
        }

        java.util.List<User> blockedUsers = userService.listBlocked();
        if (blockedUsers == null || blockedUsers.isEmpty()) {
            try {
                SendMessage req = new SendMessage(chatId.longValue(), "✅ 当前没有已拉黑的用户，列表是空的～");
                Long threadId = message.messageThreadId();
                if (threadId != null) {
                    req.messageThreadId(threadId);
                }
                SendResponse response = telegramApiClient.execute(req);
                telegramApiClient.scheduleDeleteIfOk(chatId, response, 30_000L);
            } catch (RuntimeException e) {
                log.warn("发送“当前没有已拉黑的用户”提示失败，chatId={}", chatId, e);
            }
            return;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        for (User blockedUser : blockedUsers) {
            Long targetUserId = blockedUser.getUserId();
            if (targetUserId == null) {
                continue;
            }
            StringBuilder label = new StringBuilder();
            if (blockedUser.getUsername() != null && !blockedUser.getUsername().isBlank()) {
                label.append("@").append(blockedUser.getUsername());
            } else {
                String displayName = Topic.generateTopicName(
                        blockedUser.getFirstName(),
                        blockedUser.getLastName(),
                        null,
                        targetUserId
                );
                label.append(displayName);
            }
            label.append(" (").append(targetUserId).append(")");
            String callbackData = BLOCK_CALLBACK_PREFIX + "unblock:" + targetUserId;
            InlineKeyboardButton button = new InlineKeyboardButton(label.toString()).callbackData(callbackData);
            keyboard.addRow(button);
        }

        try {
            SendMessage req = new SendMessage(chatId.longValue(), "🧾 请选择要取消拉黑的用户：").replyMarkup(keyboard);
            Long threadId = message.messageThreadId();
            if (threadId != null) {
                req.messageThreadId(threadId);
            }
            telegramApiClient.execute(req);
        } catch (RuntimeException e) {
            log.warn("发送已拉黑用户列表失败，chatId={}", chatId, e);
        }
    }
    
    /**
     * 处理消息转发模式回调。
     *
     * @param callbackQuery 回调查询对象
     * @param data          回调查询数据
     */
    private void handleModeCallback(CallbackQuery callbackQuery, String data) {
        if (isNotOwnerOperator(callbackQuery)) {
            return;
        }
        CallbackAction callbackAction = parseCallbackAction(callbackQuery, data, "无效的话题ID");
        if (callbackAction == null) {
            return;
        }
        String action = callbackAction.action();
        Long topicId = callbackAction.id();
        Topic topic = topicService.getTopicByTopicId(topicId).orElse(null);
        if (topic == null) {
            answer(callbackQuery, "🧵 当前话题不存在或已被删除啦～");
            return;
        }
        Object rawMessage = callbackQuery.maybeInaccessibleMessage();
        Message message = rawMessage instanceof Message m ? m : null;
        if (message == null || message.chat() == null || message.messageId() == null) {
            answer(callbackQuery, null);
            return;
        }
        Long groupId = telegramBotProperties.getGroupId();
        if (groupId != null && !groupId.equals(message.chat().id())) {
            answer(callbackQuery, "🏠 只能在指定的配置群组里操作这条按钮哦～");
            return;
        }
        boolean fullMode;
        if ("full".equals(action)) {
            topic.setFullMode(true);
            topicService.saveTopic(topic);
            fullMode = true;
            answer(callbackQuery, "✅ 已切换为「全消息模式」\n\n📸 图片、视频等都会被转发给主人～");
        } else if ("text".equals(action)) {
            topic.setFullMode(false);
            topicService.saveTopic(topic);
            fullMode = false;
            answer(callbackQuery, "✅ 已切换为「文字模式」\n\n✉️ 只有纯文本消息会被转发给主人～");
        } else {
            answer(callbackQuery, "❓ 未知操作，请重新试试～");
            return;
        }

        Long targetUserId = topic.getUserId();
        if (targetUserId != null) {
            String notifyText = fullMode
                    ? "🔁 转发模式已更新\n\n当前模式：📸 全消息模式\n说明：你发送的图片、视频等也会被转发给主人～"
                    : "🔁 转发模式已更新\n\n当前模式：✉️ 文字模式\n说明：只有纯文本消息会被转发给主人，图片、视频等将不会被转发～";
            try {
                SendResponse response = telegramApiClient.execute(new SendMessage(targetUserId.longValue(), notifyText));
                telegramApiClient.scheduleDeleteIfOk(targetUserId, response, 30_000L);
            } catch (RuntimeException e) {
                log.warn("发送转发模式变更提示给用户失败，topicId={}, userId={}", topic.getTopicId(), targetUserId, e);
            }
        }
        Long chatId = message.chat().id();
        Integer messageId = message.messageId();

        InlineKeyboardMarkup markup = onboardingSupport.buildUserConfigKeyboard(topic);
        EditMessageReplyMarkup edit = new EditMessageReplyMarkup(chatId, messageId).replyMarkup(markup);
        telegramApiClient.execute(edit);
    }

    /**
     * 通知目标用户已被拉黑。
     *
     * @param userId 目标用户ID
     */
    private void notifyTargetUserBlocked(Long userId) {
        if (userId == null) {
            return;
        }
            try {
                String text = "🚫 转发状态\n\n当前状态：你的消息「不会再被转发给主人」。";
                SendResponse response = telegramApiClient.execute(new SendMessage(userId.longValue(), text));
                telegramApiClient.scheduleDeleteIfOk(userId, response, 30_000L);
            } catch (RuntimeException e) {
            log.warn("发送拉黑提示给用户失败，userId={}", userId, e);
        }
    }

    /**
     * 通知目标用户已被取消拉黑。
     *
     * @param userId 目标用户ID
     */
    private void notifyTargetUserUnblocked(Long userId) {
        if (userId == null) {
            return;
        }
            try {
                String text = "✅ 转发状态\n\n当前状态：你的消息「会再次被转发给主人啦～」。";
                SendResponse response = telegramApiClient.execute(new SendMessage(userId.longValue(), text));
                telegramApiClient.scheduleDeleteIfOk(userId, response, 30_000L);
            } catch (RuntimeException e) {
            log.warn("发送取消拉黑提示给用户失败，userId={}", userId, e);
        }
    }
}
