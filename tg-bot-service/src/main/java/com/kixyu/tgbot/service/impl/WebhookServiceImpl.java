package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.service.OnboardingService;
import com.kixyu.tgbot.service.WebhookService;
import com.kixyu.tgbot.service.CallbackQueryService;
import com.kixyu.tgbot.service.MessageRelayService;
import com.kixyu.tgbot.service.MessageService;
import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.support.TelegramCommandExtractor;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.domain.repository.MessageRepository;
import com.kixyu.tgbot.service.TopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.EditMessageCaption;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookServiceImpl implements WebhookService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final TelegramBotProperties telegramBotProperties;
    private final TelegramCommandExtractor telegramCommandExtractor;
    private final OnboardingService onboardingService;
    private final MessageRelayService messageRelayService;
    private final CallbackQueryService callbackQueryService;
    private final TelegramApiClient telegramApiClient;
    private final MessageRepository messageRepository;
    private final TopicService topicService;
    private final MessageService messageService;

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

            String command = telegramCommandExtractor.extractCommand(message);
            if (command != null) {
                switch (command) {
                    case "start" -> {
                        Long privateChatId;
                        if (chat != null && chat.id() != null && Chat.Type.Private.equals(chat.type())) {
                            privateChatId = chat.id();
                        } else {
                            privateChatId = message.from().id();
                        }
                        log.info("处理 /start，updateId={}, fromId={}, privateChatId={}", updateId, message.from().id(), privateChatId);
                        if (privateChatId != null) {
                            onboardingService.handleStart(message.from(), privateChatId);
                        }
                        return;
                    }
                    case "info" -> {
                        handleInfoCommand(updateId, message, chat);
                        return;
                    }
                    case "chatid" -> {
                        handleChatIdCommand(updateId, message, chat);
                        return;
                    }
                    case "close_topic" -> {
                        handleCloseTopicCommand(updateId, message, chat);
                        return;
                    }
                }

            }

            if (chat != null && Chat.Type.Private.equals(chat.type())) {
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

    private void handleInfoCommand(Integer updateId, Message message, Chat chat) {
        if (message == null || message.from() == null) {
            return;
        }
        if (chat == null || chat.id() == null || !Chat.Type.Private.equals(chat.type())) {
            return;
        }
        User user = message.from();
        Long privateChatId = chat.id();

        String displayName = Topic.generateTopicName(user.firstName(), user.lastName(), user.username(), user.id());
        StringBuilder text = new StringBuilder();
        text.append("账号信息\n");
        text.append("名字：").append(displayName).append("\n");
        text.append("用户ID：").append(user.id());
        if (user.username() != null && !user.username().isBlank()) {
            text.append("\n用户名：@").append(user.username());
        }
        if (user.languageCode() != null && !user.languageCode().isBlank()) {
            text.append("\n语言：").append(user.languageCode());
        }
        if (Boolean.TRUE.equals(user.isPremium())) {
            text.append("\n账号类型：Telegram Premium");
        }

        try {
            sendText(privateChatId, text.toString());
            log.info("处理 /info 完成，updateId={}, userId={}", updateId, user.id());
        } catch (RuntimeException e) {
            log.warn("处理 /info 发送消息失败，updateId={}, userId={}", updateId, user.id(), e);
        }
    }

    private void handleChatIdCommand(Integer updateId, Message message, Chat chat) {
        if (message == null || message.from() == null || chat == null || chat.id() == null) {
            return;
        }
        if (Chat.Type.Private.equals(chat.type())) {
            return;
        }

        Long ownerId = telegramBotProperties.getOwnerId();
        if (ownerId != null && !ownerId.equals(message.from().id())) {
            return;
        }

        Long chatId = chat.id();
        String text = "当前群组 ID 为：" + chatId;

        try {
            sendText(chatId, text);
            log.info("处理 /chatid 完成，updateId={}, chatId={}", updateId, chatId);
        } catch (RuntimeException e) {
            log.warn("处理 /chatid 发送消息失败，updateId={}, chatId={}", updateId, chatId, e);
        }
    }

    private void handleCloseTopicCommand(Integer updateId, Message message, Chat chat) {
        if (message == null || message.from() == null || chat == null || chat.id() == null) {
            return;
        }

        Long groupId = telegramBotProperties.getGroupId();
        if (groupId == null || groupId == 0L || !groupId.equals(chat.id())) {
            return;
        }

        Long threadId = message.messageThreadId();
        if (threadId == null) {
            return;
        }

        Long ownerId = telegramBotProperties.getOwnerId();
        if (ownerId != null && !ownerId.equals(message.from().id())) {
            return;
        }

        try {
            callDeleteForumTopic(groupId, threadId);
            log.info("已请求删除话题，updateId={}, groupId={}, threadId={}", updateId, groupId, threadId);
        } catch (RuntimeException e) {
            log.warn("删除话题调用 Telegram API 失败，updateId={}, groupId={}, threadId={}", updateId, groupId, threadId, e);
        }

        String groupChatId = String.valueOf(groupId);
        Topic topic = topicService.getTopicByTopicId(threadId)
                .filter(t -> groupChatId.equals(t.getChatId()))
                .orElse(null);
        if (topic != null && topic.getTopicId() != null) {
            Long topicId = topic.getTopicId();
            try {
                messageService.deleteMessagesByTopicId(topicId);
                topicService.deleteTopicByTopicIdAndChatId(topicId, groupChatId);
                log.info("已删除本地话题及消息数据，updateId={}, topicId={}, groupChatId={}", updateId, topicId, groupChatId);
            } catch (RuntimeException e) {
                log.warn("删除本地话题或消息数据失败，updateId={}, topicId={}, groupChatId={}", updateId, topicId, groupChatId, e);
            }
        }

    }

    private void callDeleteForumTopic(Long groupId, Long threadId) {
        String token = telegramBotProperties.getToken();
        if (token == null || token.isBlank()) {
            return;
        }
        if (groupId == null || threadId == null) {
            return;
        }

        String url = "https://api.telegram.org/bot" + token + "/deleteForumTopic"
                + "?chat_id=" + groupId
                + "&message_thread_id=" + threadId;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("deleteForumTopic http status " + response.statusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
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
            com.kixyu.tgbot.domain.entity.Message mapping = messageRepository
                    .findByOriginalMessageIdAndSenderIdAndMessageType(
                            editedMessage.messageId().longValue(),
                            editedMessage.from().id(),
                            com.kixyu.tgbot.domain.entity.Message.MessageType.USER_MESSAGE
                    )
                    .orElse(null);
            if (mapping == null || mapping.getForwardedMessageId() == null || groupId == null) {
                return;
            }
            editMessageInChat(groupId, mapping.getForwardedMessageId(), editedMessage);
            return;
        }

        if (groupId != null && groupId.equals(chatId) && ownerId != null && ownerId.equals(editedMessage.from().id())) {
            com.kixyu.tgbot.domain.entity.Message mapping = messageRepository
                    .findByOriginalMessageIdAndSenderIdAndMessageType(
                            editedMessage.messageId().longValue(),
                            editedMessage.from().id(),
                            com.kixyu.tgbot.domain.entity.Message.MessageType.OWNER_MESSAGE
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

    @SuppressWarnings("deprecation")
    private void sendText(Object chatId, String text) {
        telegramApiClient.execute(new SendMessage(chatId, text));
    }
}
