package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.domain.entity.User;
import com.kixyu.tgbot.service.CallbackQueryService;
import com.kixyu.tgbot.service.TopicService;
import com.kixyu.tgbot.service.UserService;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.EditMessageReplyMarkup;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackQueryServiceImpl implements CallbackQueryService {

    private static final String CALLBACK_PREFIX = "m:";
    private static final String BLOCK_CALLBACK_PREFIX = "bl:";
    private static final String MODE_CALLBACK_PREFIX = "md:";

    private final TelegramApiClient telegramApiClient;
    private final TelegramBotProperties telegramBotProperties;
    private final UserService userService;
    private final TopicService topicService;

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
        if (data.startsWith(CALLBACK_PREFIX)) {
            answer(callbackQuery, "功能已停用");
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
            answer(callbackQuery, "只有主人可以操作");
            return true;
        }
        return false;
    }

    private record CallbackAction(String action, long id) {
    }

    /**
     * 解析回调查询数据。
     *
     * @param callbackQuery 回调查询对象
     * @param data          回调查询数据
     * @param invalidIdMessage 如果无效的用户ID，则返回的提示文本
     * @return 解析后的 CallbackAction 对象，如果解析失败则返回 null
     */
    private CallbackAction parseCallbackAction(CallbackQuery callbackQuery, String data, String invalidIdMessage) {
        String[] parts = data.split(":");
        if (parts.length != 3) {
            answer(callbackQuery, "无效的回调数据");
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
        CallbackAction callbackAction = parseCallbackAction(callbackQuery, data, "无效的用户ID");
        if (callbackAction == null) {
            return;
        }
        String action = callbackAction.action();
        Long targetUserId = callbackAction.id();
        boolean blocked;
        if ("block".equals(action)) {
            User user = userService.block(targetUserId);
            blocked = user != null && Boolean.TRUE.equals(user.getBlocked());
            notifyTargetUserBlocked(targetUserId);
            answer(callbackQuery, "已拉黑该用户");
        } else if ("unblock".equals(action)) {
            User user = userService.unblock(targetUserId);
            blocked = user != null && Boolean.TRUE.equals(user.getBlocked());
            notifyTargetUserUnblocked(targetUserId);
            answer(callbackQuery, "已取消拉黑");
        } else {
            answer(callbackQuery, "未知操作");
            return;
        }
        Object rawMessage = callbackQuery.maybeInaccessibleMessage();
        Message message = rawMessage instanceof Message m ? m : null;
        if (message == null || message.chat() == null || message.messageId() == null) {
            return;
        }
        Long chatId = message.chat().id();
        Integer messageId = message.messageId();

        String originalText = message.text();
        if (originalText != null
                && originalText.startsWith("选择要取消拉黑的用户")
                && "unblock".equals(action)) {
            EditMessageText editText = new EditMessageText(chatId, messageId, "已移除黑名单")
                    .replyMarkup(new InlineKeyboardMarkup());
            telegramApiClient.execute(editText);
            return;
        }

        InlineKeyboardMarkup markup = buildBlockInlineKeyboard(targetUserId, blocked);
        EditMessageReplyMarkup edit = new EditMessageReplyMarkup(chatId, messageId).replyMarkup(markup);
        telegramApiClient.execute(edit);
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
            answer(callbackQuery, "话题不存在或已被删除");
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
            answer(callbackQuery, "只能在配置的群组中操作");
            return;
        }
        boolean fullMode;
        if ("full".equals(action)) {
            topic.setFullMode(true);
            topicService.saveTopic(topic);
            fullMode = true;
            answer(callbackQuery, "已切换为全消息转发模式");
        } else if ("text".equals(action)) {
            topic.setFullMode(false);
            topicService.saveTopic(topic);
            fullMode = false;
            answer(callbackQuery, "已切换为仅文本模式");
        } else {
            answer(callbackQuery, "未知操作");
            return;
        }

        Long targetUserId = topic.getUserId();
        if (targetUserId != null) {
            String notifyText = fullMode
                    ? "提示：主人已将你的消息转发模式设置为“全消息模式”，你发送的图片、视频等也会被转发给主人。"
                    : "提示：主人已将你的消息转发模式设置为“文字模式”，只有纯文本消息会被转发给主人，图片、视频等将不会被转发。";
            try {
                SendMessage request = new SendMessage(targetUserId.longValue(), notifyText);
                telegramApiClient.execute(request);
            } catch (RuntimeException e) {
                log.warn("发送转发模式变更提示给用户失败，topicId={}, userId={}", topic.getTopicId(), targetUserId, e);
            }
        }
        Long chatId = message.chat().id();
        Integer messageId = message.messageId();
        InlineKeyboardMarkup markup = buildBlockAndModeInlineKeyboard(topic, fullMode);
        EditMessageReplyMarkup edit = new EditMessageReplyMarkup(chatId, messageId).replyMarkup(markup);
        telegramApiClient.execute(edit);
    }

    /**
     * 构建拉黑或取消拉黑用户的内联键盘。
     *
     * @param userId    目标用户ID
     * @param blocked   是否已拉黑
     * @return          内联键盘标记up
     */
    private InlineKeyboardMarkup buildBlockInlineKeyboard(Long userId, boolean blocked) {
        String text = blocked ? "取消拉黑" : "拉黑此用户";
        String action = blocked ? "unblock" : "block";
        String callbackData = BLOCK_CALLBACK_PREFIX + action + ":" + userId;
        InlineKeyboardButton button = new InlineKeyboardButton(text).callbackData(callbackData);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.addRow(button);
        return markup;
    }

    /**
     * 构建拉黑或取消拉黑用户的内联键盘，同时包含消息转发模式选择。
     *
     * @param topic    话题实体
     * @param fullMode 当前是否为全消息模式
     * @return         内联键盘标记up
     */
    private InlineKeyboardMarkup buildBlockAndModeInlineKeyboard(Topic topic, boolean fullMode) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        Long userId = topic.getUserId();
        if (userId != null) {
            boolean blocked = userService.isBlocked(userId);
            String blockText = blocked ? "取消拉黑" : "拉黑此用户";
            String blockAction = blocked ? "unblock" : "block";
            String blockCallback = BLOCK_CALLBACK_PREFIX + blockAction + ":" + userId;
            InlineKeyboardButton blockButton = new InlineKeyboardButton(blockText).callbackData(blockCallback);
            markup.addRow(blockButton);
        }

        Long topicId = topic.getTopicId();
        if (topicId != null) {
            String textOnlyLabel = fullMode ? "文字模式" : "✅ 文字模式";
            String fullModeLabel = fullMode ? "✅ 全消息模式" : "全消息模式";
            InlineKeyboardButton textOnlyButton = new InlineKeyboardButton(textOnlyLabel)
                    .callbackData(MODE_CALLBACK_PREFIX + "text:" + topicId);
            InlineKeyboardButton fullModeButton = new InlineKeyboardButton(fullModeLabel)
                    .callbackData(MODE_CALLBACK_PREFIX + "full:" + topicId);
            markup.addRow(textOnlyButton, fullModeButton);
        }

        return markup;
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
            String text = "提示：你的消息已被主人设置为不再转发。";
            SendMessage request = new SendMessage(userId.longValue(), text);
            telegramApiClient.execute(request);
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
            String text = "提示：主人已取消对你的拉黑，你的消息将再次被转发。";
            SendMessage request = new SendMessage(userId.longValue(), text);
            telegramApiClient.execute(request);
        } catch (RuntimeException e) {
            log.warn("发送取消拉黑提示给用户失败，userId={}", userId, e);
        }
    }
}
