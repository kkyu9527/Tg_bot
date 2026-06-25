package com.kixyu.tgbot.service.callback;

import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.blacklist.BlacklistCommandService;
import com.kixyu.tgbot.service.onboarding.OnboardingService;
import com.kixyu.tgbot.service.topic.TopicService;
import com.kixyu.tgbot.service.verification.VerificationService;
import com.kixyu.tgbot.support.UserConfigKeyboardFactory;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.EditMessageReplyMarkup;
import com.pengrad.telegrambot.response.SendResponse;

@Service
@RequiredArgsConstructor
@Slf4j
class CallbackQueryServiceImpl implements CallbackQueryService {

    private static final String BLOCK_CALLBACK_PREFIX = "bl:";
    private static final String MODE_CALLBACK_PREFIX = "md:";

    private final TelegramApiClient telegramApiClient;
    private final TelegramBotProperties telegramBotProperties;
    private final TopicService topicService;
    private final UserConfigKeyboardFactory userConfigKeyboardFactory;
    private final BlacklistCommandService blacklistCommandService;
    private final VerificationService verificationService;
    private final OnboardingService onboardingService;

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
        if (data.startsWith(BLOCK_CALLBACK_PREFIX) && blacklistCommandService.handleIfBlacklistCallback(callbackQuery)) {
            return;
        }
        if (data.startsWith(VerificationService.CALLBACK_PREFIX)) {
            if (verificationService.handleVerificationCallback(callbackQuery) && callbackQuery.from() != null) {
                onboardingService.handleVerifiedStart(callbackQuery.from(), resolvePrivateChatId(callbackQuery));
            }
            return;
        }
        if (data.startsWith(MODE_CALLBACK_PREFIX)) {
            handleModeCallback(callbackQuery, data);
            return;
        }
        answer(callbackQuery, null);
    }

    private Long resolvePrivateChatId(CallbackQuery callbackQuery) {
        Object rawMessage = callbackQuery.maybeInaccessibleMessage();
        Message message = rawMessage instanceof Message m ? m : null;
        if (message != null && message.chat() != null && message.chat().id() != null) {
            return message.chat().id();
        }
        return callbackQuery.from() == null ? null : callbackQuery.from().id();
    }

    /**
     * 给 Telegram 回调查询发送响应，可选附带提示文本。
     *
     * @param callbackQuery 回调查询对象
     * @param text          可选提示文本，为 null 则不下发文字
     */
    private void answer(CallbackQuery callbackQuery, String text) {
        telegramApiClient.answerCallback(callbackQuery, text, true);
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
     * @return 解析后的 CallbackAction 对象，如果解析失败则返回 null
     */
    private CallbackAction parseCallbackAction(CallbackQuery callbackQuery, String data) {
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
            answer(callbackQuery, "无效的话题ID");
            return null;
        }
        return new CallbackAction(action, id);
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
        CallbackAction callbackAction = parseCallbackAction(callbackQuery, data);
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
                SendResponse response = telegramApiClient.execute(telegramApiClient.createSendMessage(targetUserId, notifyText));
                telegramApiClient.scheduleDeleteIfOk(targetUserId, response);
            } catch (RuntimeException e) {
                log.warn("发送转发模式变更提示给用户失败，topicId={}, userId={}", topic.getTopicId(), targetUserId, e);
            }
        }
        Long chatId = message.chat().id();
        Integer messageId = message.messageId();

        InlineKeyboardMarkup markup = userConfigKeyboardFactory.buildForTopic(topic);
        Long welcomeMessageId = topic.getWelcomeMessageId();
        if (welcomeMessageId != null && welcomeMessageId <= Integer.MAX_VALUE) {
            try {
                EditMessageReplyMarkup editWelcome =
                        new EditMessageReplyMarkup(chatId, welcomeMessageId.intValue()).replyMarkup(markup);
                telegramApiClient.execute(editWelcome);
            } catch (RuntimeException e) {
                log.warn("更新新用户卡片消息模式按钮状态失败，chatId={}, messageId={}", chatId, welcomeMessageId, e);
            }
        }
        EditMessageReplyMarkup edit = new EditMessageReplyMarkup(chatId, messageId).replyMarkup(markup);
        telegramApiClient.execute(edit);
    }

}
