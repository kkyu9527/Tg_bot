package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.service.OnboardingService;
import com.kixyu.tgbot.service.WebhookService;
import com.kixyu.tgbot.service.CallbackQueryService;
import com.kixyu.tgbot.service.MessageRelayService;
import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.support.TelegramCommandExtractor;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.domain.repository.MessageRepository;
import com.kixyu.tgbot.service.TopicService;
import com.kixyu.tgbot.service.UserService;
import com.kixyu.tgbot.service.MessageService;
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
import com.pengrad.telegrambot.request.DeleteMessage;
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
    private final UserService userService;

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
                    case "delete" -> {
                        handleDeleteCommand(updateId, message, chat);
                        return;
                    }
                    case "unblock" -> {
                        handleUnblockCommand(updateId, message, chat);
                        return;
                    }
                }

            }

            if (chat != null && Chat.Type.Private.equals(chat.type())) {
                log.info("私聊消息转发到群话题，updateId={}, fromId={}, privateChatId={}", updateId, message.from().id(), chat.id());
                messageRelayService.forwardPrivateMessageToGroupTopic(message);
                return;
            }

            /**
             * 检查消息是否来自配置的群聊。
             * 如果是，则将群聊消息回流到用户。
             *
             * @param updateId 更新 ID
             * @param message  消息实体
             * @param chat     聊天实体
             */
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
     * 处理 /unblock 命令，列出所有已拉黑的用户。
     *
     * @param updateId 更新 ID
     * @param message  消息实体
     * @param chat     聊天实体
     */
    private void handleUnblockCommand(Integer updateId, Message message, Chat chat) {
        if (isInvalidGroupOwnerCommand(message, chat)) {
            return;
        }
        if (message.text() == null || message.text().isBlank()) {
            return;
        }
        String text = message.text().trim();
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            Long chatId = chat.id();
            java.util.List<com.kixyu.tgbot.domain.entity.User> blockedUsers = userService.listBlocked();
            if (blockedUsers == null || blockedUsers.isEmpty()) {
                try {
                    sendText(chatId, "当前没有已拉黑的用户。");
                } catch (RuntimeException e) {
                    log.warn("发送“当前没有已拉黑的用户”提示失败，updateId={}, chatId={}", updateId, chatId, e);
                }
                return;
            }
            com.pengrad.telegrambot.model.request.InlineKeyboardMarkup keyboard = new com.pengrad.telegrambot.model.request.InlineKeyboardMarkup();
            for (com.kixyu.tgbot.domain.entity.User user : blockedUsers) {
                Long targetUserId = user.getUserId();
                if (targetUserId == null) {
                    continue;
                }
                StringBuilder label = new StringBuilder();
                if (user.getUsername() != null && !user.getUsername().isBlank()) {
                    label.append("@").append(user.getUsername());
                } else {
                    String displayName = Topic.generateTopicName(
                            user.getFirstName(),
                            user.getLastName(),
                            null,
                            targetUserId
                    );
                    label.append(displayName);
                }
                label.append(" (").append(targetUserId).append(")");
                String callbackData = "bl:unblock:" + targetUserId;
                com.pengrad.telegrambot.model.request.InlineKeyboardButton button =
                        new com.pengrad.telegrambot.model.request.InlineKeyboardButton(label.toString()).callbackData(callbackData);
                keyboard.addRow(button);
            }
            try {
                telegramApiClient.execute(
                        new SendMessage(chatId.longValue(), "选择要取消拉黑的用户：").replyMarkup(keyboard)
                );
            } catch (RuntimeException e) {
                log.warn("发送已拉黑用户列表失败，updateId={}, chatId={}", updateId, chatId, e);
            }
            return;
        }
        Long targetUserId;
        try {
            targetUserId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            Long chatId = chat.id();
            try {
                sendText(chatId, "无效的 userId：" + parts[1]);
            } catch (RuntimeException ex) {
                log.warn("发送 /unblock 参数错误提示失败，updateId={}, chatId={}", updateId, chatId, ex);
            }
            return;
        }
        try {
            com.kixyu.tgbot.domain.entity.User user = userService.unblock(targetUserId);
            Long chatId = chat.id();
            if (chatId != null) {
                String reply = user != null && !Boolean.TRUE.equals(user.getBlocked())
                        ? "已取消拉黑用户：" + targetUserId
                        : "该用户当前未被拉黑：" + targetUserId;
                sendText(chatId, reply);
            }
            try {
                String notify = "提示：主人已通过命令取消对你的拉黑，你的消息将再次被转发。";
                telegramApiClient.execute(new SendMessage(targetUserId.longValue(), notify));
            } catch (RuntimeException e) {
                log.warn("通过命令取消拉黑后通知用户失败，updateId={}, userId={}", updateId, targetUserId, e);
            }
        } catch (RuntimeException e) {
            Long chatId = chat.id();
            log.warn("处理 /unblock 失败，updateId={}, userId={}", updateId, targetUserId, e);
            try {
                sendText(chatId, "取消拉黑失败：" + e.getMessage());
            } catch (RuntimeException ex) {
                log.warn("发送 /unblock 失败提示消息失败，updateId={}, chatId={}", updateId, chatId, ex);
            }
        }
    }

    /**
     * 处理 /info 命令，显示用户账号信息。
     *
     * @param updateId 更新 ID
     * @param message  消息实体
     * @param chat     聊天实体
     */
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
        text.append("\n\n小技巧：如果想撤回一条已经发送的消息，可以在私聊或话题中回复那条消息，然后发送 /delete，机器人会尽量同时删除两端的对应消息（受 Telegram 限制，部分旧消息可能无法撤回）。");

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

    /**
     * 处理 /close 命令，关闭当前话题。
     *
     * @param updateId 更新 ID
     * @param message  消息实体
     * @param chat     聊天实体
     */
    private void handleCloseTopicCommand(Integer updateId, Message message, Chat chat) {
        if (isInvalidGroupOwnerCommand(message, chat)) {
            return;
        }

        Long groupId = telegramBotProperties.getGroupId();
        Long threadId = message.messageThreadId();
        if (threadId == null) {
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

    /**
     * 检查是否为无效的群组所有者命令。
     *
     * @param message   消息实体
     * @param chat      聊天实体
     * @return          如果是无效命令则返回 true，否则返回 false
     */
    private boolean isInvalidGroupOwnerCommand(Message message, Chat chat) {
        if (message == null || message.from() == null || chat == null || chat.id() == null) {
            return true;
        }
        Long groupId = telegramBotProperties.getGroupId();
        if (groupId == null || groupId == 0L || !groupId.equals(chat.id())) {
            return true;
        }
        Long ownerId = telegramBotProperties.getOwnerId();
        return ownerId != null && !ownerId.equals(message.from().id());
    }

    private void handleDeleteCommand(Integer updateId, Message message, Chat chat) {
        if (message == null || message.from() == null || chat == null || chat.id() == null) {
            return;
        }
        if (message.replyToMessage() == null || message.replyToMessage().messageId() == null) {
            Long chatId = chat.id();
            try {
                sendText(chatId, "请先回复要撤回的那条消息，然后再发送 /delete");
            } catch (RuntimeException e) {
                log.warn("提示 /delete 使用方式失败，updateId={}, chatId={}", updateId, chatId, e);
            }
            try {
                telegramApiClient.execute(new DeleteMessage(chatId, message.messageId()));
            } catch (RuntimeException e) {
                log.warn("删除无效 /delete 命令消息失败，updateId={}, chatId={}, messageId={}", updateId, chatId, message.messageId(), e);
            }
            return;
        }

        Long repliedMessageId = message.replyToMessage().messageId().longValue();
        Long chatId = chat.id();
        Long groupId = telegramBotProperties.getGroupId();

        if (Chat.Type.Private.equals(chat.type())) {
            Long userId = message.from().id();
            deletePairedMessagesFromPrivate(updateId, userId, chatId, repliedMessageId);
        } else if (groupId != null && groupId.equals(chatId)) {
            Long ownerId = telegramBotProperties.getOwnerId();
            if (ownerId != null && !ownerId.equals(message.from().id())) {
                return;
            }
            deletePairedMessagesFromGroup(updateId, chatId, repliedMessageId);
        }

        try {
            telegramApiClient.execute(new DeleteMessage(chatId, message.messageId()));
        } catch (RuntimeException e) {
            log.warn("删除 /delete 命令消息失败，updateId={}, chatId={}, messageId={}", updateId, chatId, message.messageId(), e);
        }
    }

    /**
     * 删除私聊中的成对消息。
     *
     * @param updateId          更新 ID
     * @param userId            用户 ID
     * @param privateChatId     私聊 chatId
     * @param repliedMessageId  被回复消息 ID
     */
    private void deletePairedMessagesFromPrivate(Integer updateId, Long userId, Long privateChatId, Long repliedMessageId) {
        com.kixyu.tgbot.domain.entity.Message mapping = findMessageMapping(repliedMessageId);
        if (mapping == null) {
            return;
        }

        Topic topic = findValidTopic(mapping);
        if (topic == null) {
            return;
        }
        Long mappedUserId = topic.getUserId();
        if (!mappedUserId.equals(userId)) {
            return;
        }
        Long groupId = parseChatIdLong(topic.getChatId());
        if (groupId == null) {
            return;
        }

        PairedMessageIds ids = resolvePairedMessageIds(mapping);
        if (ids == null) {
            return;
        }
        Long userMessageId = ids.userMessageId();
        Long groupMessageId = ids.groupMessageId();

        if (userMessageId != null && userMessageId <= Integer.MAX_VALUE) {
            try {
                telegramApiClient.execute(new DeleteMessage(privateChatId, userMessageId.intValue()));
            } catch (RuntimeException e) {
                log.warn("删除私聊消息失败，updateId={}, userId={}, messageId={}", updateId, userId, userMessageId, e);
            }
        }
        if (groupMessageId != null && groupMessageId <= Integer.MAX_VALUE) {
            try {
                telegramApiClient.execute(new DeleteMessage(groupId, groupMessageId.intValue()));
            } catch (RuntimeException e) {
                log.warn("删除群话题消息失败，updateId={}, groupId={}, messageId={}", updateId, groupId, groupMessageId, e);
            }
        }
    }

    /**
     * 删除群聊中的成对消息。
     *
     * @param updateId          更新 ID
     * @param groupId           群 chatId
     * @param repliedMessageId  被回复消息 ID
     */
    private void deletePairedMessagesFromGroup(Integer updateId, Long groupId, Long repliedMessageId) {
        com.kixyu.tgbot.domain.entity.Message mapping = findMessageMapping(repliedMessageId);
        if (mapping == null) {
            return;
        }

        Topic topic = findValidTopic(mapping);
        if (topic == null) {
            return;
        }
        Long mappedGroupId = parseChatIdLong(topic.getChatId());
        if (mappedGroupId == null || !mappedGroupId.equals(groupId)) {
            return;
        }

        Long privateChatId = topic.getUserId();

        PairedMessageIds ids = resolvePairedMessageIds(mapping);
        if (ids == null) {
            return;
        }
        Long userMessageId = ids.userMessageId();
        Long groupMessageId = ids.groupMessageId();

        if (groupMessageId != null && groupMessageId <= Integer.MAX_VALUE) {
            try {
                telegramApiClient.execute(new DeleteMessage(groupId, groupMessageId.intValue()));
            } catch (RuntimeException e) {
                log.warn("删除群话题消息失败，updateId={}, groupId={}, messageId={}", updateId, groupId, groupMessageId, e);
            }
        }
        if (userMessageId != null && userMessageId <= Integer.MAX_VALUE) {
            try {
                telegramApiClient.execute(new DeleteMessage(privateChatId, userMessageId.intValue()));
            } catch (RuntimeException e) {
                log.warn("删除私聊消息失败，updateId={}, userId={}, messageId={}", updateId, privateChatId, userMessageId, e);
            }
        }
    }

    /**
     * 根据消息 ID 查找消息映射。
     *
     * @param messageId 消息 ID
     * @return          消息实体；未找到则返回 null
     */
    private com.kixyu.tgbot.domain.entity.Message findMessageMapping(Long messageId) {
        return messageService.getMessageByOriginalMessageId(messageId)
                .orElseGet(() -> messageService.getMessageByForwardedMessageId(messageId).orElse(null));
    }

    /**
     * 查找有效话题。
     *
     * @param mapping 消息映射实体
     * @return        有效话题实体；未找到则返回 null
     */
    private Topic findValidTopic(com.kixyu.tgbot.domain.entity.Message mapping) {
        if (mapping == null || mapping.getTopicId() == null) {
            return null;
        }
        Long topicId = mapping.getTopicId();
        Topic topic = topicService.getTopicByTopicId(topicId).orElse(null);
        if (topic == null || topic.getUserId() == null || topic.getTopicId() == null) {
            return null;
        }
        return topic;
    }

    /**
     * 解析成对消息 ID。
     *
     * @param mapping 消息映射实体
     * @return        成对消息 ID 实体；未解析到则返回 null
     */
    private PairedMessageIds resolvePairedMessageIds(com.kixyu.tgbot.domain.entity.Message mapping) {
        if (mapping == null) {
            return null;
        }
        Long userMessageId;
        Long groupMessageId;
        if (mapping.getMessageType() == com.kixyu.tgbot.domain.entity.Message.MessageType.USER_MESSAGE) {
            userMessageId = mapping.getOriginalMessageId();
            groupMessageId = mapping.getForwardedMessageId();
        } else if (mapping.getMessageType() == com.kixyu.tgbot.domain.entity.Message.MessageType.OWNER_MESSAGE) {
            userMessageId = mapping.getForwardedMessageId();
            groupMessageId = mapping.getOriginalMessageId();
        } else {
            return null;
        }
        return new PairedMessageIds(userMessageId, groupMessageId);
    }

    private record PairedMessageIds(Long userMessageId, Long groupMessageId) {
    }

    /**
     * 解析字符串为长整型 chatId。
     *
     * @param chatId 聊天 ID 字符串
     * @return       解析后的长整型 chatId；解析失败则返回 null
     */
    private Long parseChatIdLong(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(chatId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 调用 Telegram API 删除群话题。
     *
     * @param groupId  群 chatId
     * @param threadId 话题 ID
     */
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

    private void sendText(Long chatId, String text) {
        if (chatId == null) {
            return;
        }
        telegramApiClient.execute(new SendMessage(chatId.longValue(), text));
    }
}
