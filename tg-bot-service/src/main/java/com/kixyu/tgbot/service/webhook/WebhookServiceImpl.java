package com.kixyu.tgbot.service.webhook;

import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.service.blacklist.BlacklistCommandService;
import com.kixyu.tgbot.service.callback.CallbackQueryService;
import com.kixyu.tgbot.service.onboarding.OnboardingService;
import com.kixyu.tgbot.service.relay.MessageRelayService;
import com.kixyu.tgbot.service.topic.TopicService;
import com.kixyu.tgbot.service.user.UserService;
import com.kixyu.tgbot.service.verification.VerificationService;
import com.kixyu.tgbot.support.TelegramCommandExtractor;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.kixyu.tgbot.domain.entity.Message.MessageType;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.domain.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.EditMessageCaption;
import com.pengrad.telegrambot.request.EditMessageText;

@Service
@RequiredArgsConstructor
@Slf4j
class WebhookServiceImpl implements WebhookService {

    private final TelegramBotProperties telegramBotProperties;
    private final TelegramCommandExtractor telegramCommandExtractor;
    private final OnboardingService onboardingService;
    private final MessageRelayService messageRelayService;
    private final CallbackQueryService callbackQueryService;
    private final TelegramApiClient telegramApiClient;
    private final MessageRepository messageRepository;
    private final TopicService topicService;
    private final BlacklistCommandService blacklistCommandService;
    private final UserService userService;
    private final VerificationService verificationService;

    /**
     * 处理来自 Telegram 的 Webhook 更新。
     * 包含回调查询、消息编辑、普通消息的转发与回流等逻辑入口。
     *
     * @param update Telegram 更新对象
     */
    @Override
    public void handleWebhook(Update update) {
        Integer updateId = update == null ? null : update.updateId();
        try {
            if (update == null) {
                log.debug("Webhook update 为空，跳过处理");
                return;
            }

            if (update.callbackQuery() != null) {
                log.info("收到 callbackQuery，updateId={}", updateId);
                callbackQueryService.handleCallbackQuery(update.callbackQuery());
                return;
            }

            if (update.editedMessage() != null) {
                handleEditedMessage(updateId, update.editedMessage());
                return;
            }

            Message message = update.message();
            if (message == null || message.from() == null) {
                log.debug("update 无 message/from，跳过处理，updateId={}", updateId);
                return;
            }
            if (Boolean.TRUE.equals(message.from().isBot())) {
                log.debug("忽略 bot 消息，updateId={}, fromId={}", updateId, message.from().id());
                return;
            }

            Chat chat = message.chat();
            log.info("收到消息，updateId={}, messageId={}, fromId={}, chatId={}, chatType={}, threadId={}, mediaGroupId={}",
                    updateId,
                    message.messageId(),
                    message.from().id(),
                    chat == null ? null : chat.id(),
                    chat == null ? null : chat.type(),
                    message.messageThreadId(),
                    message.mediaGroupId()
            );

            if (blacklistCommandService.handleIfBlacklistMessage(message, chat)) {
                log.info("黑名单管理消息已处理，updateId={}, messageId={}", updateId, message.messageId());
                return;
            }

            String command = telegramCommandExtractor.extractCommand(message);
            if (command != null) {
                onboardingService.handleCommand(command, updateId, message, chat);
                return;
            }

            if (chat != null && Chat.Type.Private.equals(chat.type())) {
                if (userService.isUnverified(message.from().id())) {
                    log.info("未验证私聊消息已拦截，updateId={}, fromId={}, privateChatId={}", updateId, message.from().id(), chat.id());
                    verificationService.remindVerificationRequired(message.from(), chat.id());
                    return;
                }
                log.info("私聊消息转发到群话题，updateId={}, fromId={}, privateChatId={}", updateId, message.from().id(), chat.id());
                messageRelayService.forwardPrivateMessageToGroupTopic(message);
                return;
            }

            Long groupId = telegramBotProperties.getGroupId();
            if (groupId != null && chat != null && groupId.equals(chat.id())) {
                log.info("群话题消息回流到用户，updateId={}, groupChatId={}, messageId={}", updateId, groupId, message.messageId());
                messageRelayService.relayGroupTopicMessageToUser(message);
            }
        } catch (RuntimeException e) {
            log.error("Webhook 处理异常，updateId={}", updateId, e);
        }
    }


    /**
     * 处理已编辑消息的同步更新。
     * 会根据消息映射关系找到对应聊天中的消息并进行文本或标题更新。
     *
     * @param updateId      更新 ID，用于日志
     * @param editedMessage 被编辑后的消息
     */
    private void handleEditedMessage(Integer updateId, Message editedMessage) {
        if (editedMessage == null || editedMessage.from() == null || editedMessage.chat() == null || editedMessage.messageId() == null) {
            return;
        }
        if (Boolean.TRUE.equals(editedMessage.from().isBot())) {
            return;
        }

        Chat chat = editedMessage.chat();
        Long chatId = chat.id();
        Long groupId = telegramBotProperties.getGroupId();
        Long ownerId = telegramBotProperties.getOwnerId();

        log.info("收到编辑消息，updateId={}, messageId={}, fromId={}, chatId={}, chatType={}, threadId={}",
                updateId,
                editedMessage.messageId(),
                editedMessage.from().id(),
                chatId,
                chat.type(),
                editedMessage.messageThreadId()
        );

        if (chatId == null) {
            return;
        }

        if (Chat.Type.Private.equals(chat.type())) {
            var mapping = messageRepository
                    .findByOriginalMessageIdAndSenderIdAndMessageType(
                            editedMessage.messageId().longValue(),
                            editedMessage.from().id(),
                            MessageType.USER_MESSAGE
                    )
                    .orElse(null);
            if (mapping == null || mapping.getForwardedMessageId() == null || groupId == null) {
                return;
            }
            editMessageInChat(groupId, mapping.getForwardedMessageId(), editedMessage);
            return;
        }

        if (groupId != null && groupId.equals(chatId) && ownerId != null && ownerId.equals(editedMessage.from().id())) {
            var mapping = messageRepository
                    .findByOriginalMessageIdAndSenderIdAndMessageType(
                            editedMessage.messageId().longValue(),
                            editedMessage.from().id(),
                            MessageType.OWNER_MESSAGE
                    )
                    .orElse(null);
            if (mapping == null || mapping.getForwardedMessageId() == null) {
                return;
            }
            Topic topic = topicService.getTopicByTopicId(mapping.getTopicId()).orElse(null);
            if (topic == null || topic.getUserId() == null) {
                return;
            }
            editMessageInChat(topic.getUserId(), mapping.getForwardedMessageId(), editedMessage);
        }
    }

    /**
     * 在指定聊天中编辑一条消息的文本或标题。
     *
     * @param chatId        目标聊天 ID（用户 ID 或群 ID）
     * @param messageId     目标消息 ID
     * @param editedMessage 含有最新内容的消息对象
     */
    private void editMessageInChat(Object chatId, Long messageId, Message editedMessage) {
        if (chatId == null || messageId == null || editedMessage == null) {
            return;
        }
        if (messageId > Integer.MAX_VALUE) {
            return;
        }
        int targetMessageId = messageId.intValue();

        String text = editedMessage.text();
        if (text != null) {
            telegramApiClient.execute(new EditMessageText(chatId, targetMessageId, text));
            return;
        }
        String caption = editedMessage.caption();
        if (caption != null) {
            telegramApiClient.execute(new EditMessageCaption(chatId, targetMessageId).caption(caption));
        }
    }

}
