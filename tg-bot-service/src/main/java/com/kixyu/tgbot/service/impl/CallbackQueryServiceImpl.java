package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.domain.entity.User;
import com.kixyu.tgbot.service.CallbackQueryService;
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

    private final TelegramApiClient telegramApiClient;
    private final TelegramBotProperties telegramBotProperties;
    private final UserService userService;

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
     * 处理拉黑或取消拉黑用户的回调查询。
     *
     * @param callbackQuery 回调查询对象
     * @param data          包含操作类型和目标用户ID的回调数据
     */
    private void handleBlockCallback(CallbackQuery callbackQuery, String data) {
        Long ownerId = telegramBotProperties.getOwnerId();
        if (ownerId != null && (callbackQuery.from() == null || !ownerId.equals(callbackQuery.from().id()))) {
            answer(callbackQuery, "只有主人可以操作");
            return;
        }
        String[] parts = data.split(":");
        if (parts.length != 3) {
            answer(callbackQuery, "无效的回调数据");
            return;
        }
        String action = parts[1];
        Long targetUserId;
        try {
            targetUserId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            answer(callbackQuery, "无效的用户ID");
            return;
        }
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
