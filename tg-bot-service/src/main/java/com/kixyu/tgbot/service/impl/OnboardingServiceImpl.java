package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.OnboardingService;
import com.kixyu.tgbot.service.TopicService;
import com.kixyu.tgbot.service.UserService;
import com.kixyu.tgbot.service.common.OnboardingCommonService;
import com.kixyu.tgbot.support.OnboardingSupport;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingServiceImpl implements OnboardingService {

    private final OnboardingSupport onboardingSupport;
    private final TelegramBotProperties telegramBotProperties;
    private final TelegramApiClient telegramApiClient;
    private final TopicService topicService;
    private final UserService userService;
    private final OnboardingCommonService onboardingCommonService;

    /**
     * 统一的命令处理入口，根据命令名称分发到具体处理方法。
     *
     * @param command  命令字符串（不含斜杠）
     * @param updateId 更新 ID
     * @param message  消息实体
     * @param chat     聊天实体
     */
    @Override
    public void handleCommand(String command, Integer updateId, Message message, Chat chat) {
        if (command == null) {
            return;
        }
        switch (command) {
            case "start" -> {
                if (message == null || message.from() == null) {
                    return;
                }
                Long privateChatId;
                if (chat != null && chat.id() != null && Chat.Type.Private.equals(chat.type())) {
                    privateChatId = chat.id();
                } else {
                    privateChatId = message.from().id();
                }
                if (privateChatId != null) {
                    handleStart(message.from(), privateChatId);
                }
            }
            case "info" -> handleInfoCommand(updateId, message, chat);
            case "chatid" -> handleChatIdCommand(updateId, message, chat);
            case "close_topic" -> handleCloseTopicCommand(updateId, message, chat);
            case "delete" -> handleDeleteCommand(updateId, message, chat);
            case "unblock" -> handleUnblockCommand(updateId, message, chat);
            case "full_mode" -> handleFullModeCommand(updateId, message, chat);
            default -> {
            }
        }
    }

    /**
     * 处理用户在私聊中发送的 /start 命令。
     * 负责发送欢迎消息、在群组中创建或恢复用户话题并发送提示信息。
     *
     * @param user          触发命令的用户
     * @param privateChatId 用户私聊窗口的聊天 ID
     */
    private void handleStart(User user, Long privateChatId) {
        try {
            log.info("处理 /start，userId={}, privateChatId={}, username={}", user.id(), privateChatId, user.username());
            try {
                onboardingSupport.sendWelcomeToUser(user, privateChatId);
                log.info("已发送欢迎消息，userId={}, privateChatId={}", user.id(), privateChatId);
            } catch (RuntimeException e) {
                log.warn("发送欢迎消息失败，userId={}, privateChatId={}", user.id(), privateChatId, e);
            }

            Long groupId = onboardingSupport.getGroupId();
            if (groupId == null || groupId == 0L) {
                log.warn("未配置群组 groupId，跳过创建话题，userId={}", user.id());
                return;
            }

            String groupChatId = String.valueOf(groupId);
            Optional<Topic> existing = onboardingSupport.getTopicByUserIdAndChatId(user.id(), groupChatId);
            if (existing.isPresent()) {
                Topic existingTopic = existing.get();
                if (onboardingSupport.isPlaceholderTopicId(existingTopic.getTopicId())) {
                    log.warn("检测到占位 topicId，准备重建话题并修复映射，userId={}, groupChatId={}, topicId={}",
                            user.id(), groupChatId, existingTopic.getTopicId());
                    onboardingSupport.recreateAndUpdateTopic(user, groupChatId);
                    return;
                }

                if (!onboardingSupport.isForumTopicAlive(groupChatId, existingTopic)) {
                    log.warn("检测到群话题不存在，准备重建话题并清理遗留数据，userId={}, groupChatId={}, topicId={}",
                            user.id(), groupChatId, existingTopic.getTopicId());
                    onboardingSupport.recreateAndUpdateTopic(user, groupChatId);
                    return;
                }

                log.info("已存在有效用户话题映射，跳过创建，userId={}, groupChatId={}, topicId={}",
                        user.id(), groupChatId, existingTopic.getTopicId());
                return;
            }

            String topicName = Topic.generateTopicName(user.firstName(), user.lastName(), user.username(), user.id());
            log.info("准备创建群组话题，userId={}, groupChatId={}, topicName={}", user.id(), groupChatId, topicName);
            Long threadId = onboardingSupport.createForumTopic(groupChatId, topicName);
            if (threadId == null) {
                log.warn("创建群组话题失败，userId={}, groupChatId={}", user.id(), groupChatId);
                return;
            }
            log.info("创建群组话题成功，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, threadId);

            onboardingSupport.createTopic(
                    user.id(),
                    user.username(),
                    user.firstName(),
                    user.lastName(),
                    threadId,
                    groupChatId
            );
            log.info("保存用户话题映射成功，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, threadId);

            String caption = onboardingSupport.buildNewUserCaption(user);

            Message sentMessage = onboardingSupport.sendNewUserMessageToTopic(groupChatId, threadId, user, caption);
            if (sentMessage == null || sentMessage.messageId() == null) {
                log.warn("发送新用户提示消息失败，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, threadId);
                return;
            }
            log.info("已发送新用户提示消息，userId={}, groupChatId={}, threadId={}, messageId={}",
                    user.id(), groupChatId, threadId, sentMessage.messageId());
            onboardingSupport.pinMessage(groupChatId, sentMessage.messageId());
            log.info("已尝试置顶新用户提示消息，userId={}, groupChatId={}, threadId={}, messageId={}",
                    user.id(), groupChatId, threadId, sentMessage.messageId());
        } catch (RuntimeException e) {
            log.error("处理 /start 失败，userId={}, privateChatId={}", user == null ? null : user.id(), privateChatId, e);
        }
    }

    /**
     * 处理 /unblock 命令，列出或取消拉黑用户。
     *
     * @param updateId 更新 ID
     * @param message  消息实体
     * @param chat     聊天实体
     */
    private void handleUnblockCommand(Integer updateId, Message message, Chat chat) {
        if (onboardingCommonService.isInvalidGroupOwnerCommand(message, chat)) {
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
                    onboardingCommonService.sendText(chatId, "当前没有已拉黑的用户。");
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
                onboardingCommonService.sendText(chatId, "无效的 userId：" + parts[1]);
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
                onboardingCommonService.sendText(chatId, reply);
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
                onboardingCommonService.sendText(chatId, "取消拉黑失败：" + e.getMessage());
            } catch (RuntimeException ex) {
                log.warn("发送 /unblock 失败提示消息失败，updateId={}, chatId={}", updateId, chatId, ex);
            }
        }
    }

    /**
     * 处理 /info 命令，显示当前用户账号信息。
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
            onboardingCommonService.sendText(privateChatId, text.toString());
            log.info("处理 /info 完成，updateId={}, userId={}", updateId, user.id());
        } catch (RuntimeException e) {
            log.warn("处理 /info 发送消息失败，updateId={}, userId={}", updateId, user.id(), e);
        }
    }

    /**
     * 处理 /chatid 命令，输出当前群组 ID。
     *
     * @param updateId 更新 ID
     * @param message  消息实体
     * @param chat     聊天实体
     */
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
            onboardingCommonService.sendText(chatId, text);
            log.info("处理 /chatid 完成，updateId={}, chatId={}", updateId, chatId);
        } catch (RuntimeException e) {
            log.warn("处理 /chatid 发送消息失败，updateId={}, chatId={}", updateId, chatId, e);
        }
    }

    private void handleCloseTopicCommand(Integer updateId, Message message, Chat chat) {
        if (onboardingCommonService.isInvalidGroupOwnerCommand(message, chat)) {
            return;
        }

        Long groupId = telegramBotProperties.getGroupId();
        Long threadId = message.messageThreadId();
        if (threadId == null) {
            return;
        }

        try {
            onboardingCommonService.deleteForumTopic(groupId, threadId);
            log.info("已请求删除话题，updateId={}, groupId={}, threadId={}", updateId, groupId, threadId);
        } catch (RuntimeException e) {
            log.warn("删除话题调用 Telegram API 失败，updateId={}, groupId={}, threadId={}", updateId, groupId, threadId, e);
        }

        if (groupId == null) {
            return;
        }
        String groupChatId = String.valueOf(groupId);
        topicService.getTopicByTopicId(threadId)
                .filter(t -> groupChatId.equals(t.getChatId()))
                .ifPresent(topic -> onboardingCommonService.deleteTopicMessagesAndMapping(updateId, topic));
    }

    /**
     * 处理 /fullmode 命令，为当前话题开启全消息转发模式。
     *
     * @param updateId 更新 ID
     * @param message  命令消息
     * @param chat     命令所在聊天
     */
    private void handleFullModeCommand(Integer updateId, Message message, Chat chat) {
        if (onboardingCommonService.isInvalidGroupOwnerCommand(message, chat)) {
            return;
        }
        Long groupId = telegramBotProperties.getGroupId();
        Long threadId = message.messageThreadId();
        if (groupId == null || threadId == null) {
            return;
        }
        String groupChatId = String.valueOf(groupId);
        Topic topic = topicService.getTopicByTopicId(threadId).orElse(null);
        if (topic == null || !groupChatId.equals(topic.getChatId())) {
            return;
        }
        Long chatId = chat.id();
        if (chatId == null) {
            return;
        }
        boolean fullMode = Boolean.TRUE.equals(topic.getFullMode());
        String text = "请选择该用户的转发模式：";
        String textOnlyLabel = fullMode ? "文字模式" : "✅ 文字模式";
        String fullModeLabel = fullMode ? "✅ 全消息模式" : "全消息模式";
        InlineKeyboardButton textOnlyButton = new InlineKeyboardButton(textOnlyLabel)
                .callbackData("md:text:" + topic.getTopicId());
        InlineKeyboardButton fullModeButton = new InlineKeyboardButton(fullModeLabel)
                .callbackData("md:full:" + topic.getTopicId());
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(textOnlyButton, fullModeButton);
        try {
            telegramApiClient.execute(
                    new SendMessage(chatId.longValue(), text)
                            .messageThreadId(threadId)
                            .replyMarkup(keyboard)
            );
            log.info("已发送转发模式选择消息，updateId={}, groupChatId={}, threadId={}, userId={}",
                    updateId, groupChatId, threadId, topic.getUserId());
        } catch (RuntimeException e) {
            log.warn("发送转发模式选择消息失败，updateId={}, groupChatId={}, threadId={}", updateId, groupChatId, threadId, e);
        }
    }

    /**
     * 处理 /delete 命令，撤回一对私聊与群话题消息。
     *
     * @param updateId 更新 ID
     * @param message  命令消息
     * @param chat     命令所在聊天
     */
    private void handleDeleteCommand(Integer updateId, Message message, Chat chat) {
        if (message == null || message.from() == null || chat == null || chat.id() == null) {
            return;
        }
        if (message.replyToMessage() == null || message.replyToMessage().messageId() == null) {
            Long chatId = chat.id();
            try {
                SendResponse response = telegramApiClient.execute(
                        new SendMessage(chatId.longValue(), "请先回复要撤回的那条消息，然后再发送 /delete (5s后删除本消息)")
                );
                Integer hintMessageId = response == null || response.message() == null ? null : response.message().messageId();
                if (hintMessageId != null) {
                    new Thread(() -> {
                        try {
                            Thread.sleep(5_000L);
                            telegramApiClient.execute(new DeleteMessage(chatId, hintMessageId));
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        } catch (RuntimeException ex) {
                            log.warn("删除 /delete 提示消息失败，updateId={}, chatId={}, messageId={}", updateId, chatId, hintMessageId, ex);
                        }
                    }).start();
                }
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

        int repliedDate = message.replyToMessage().date();
        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (nowSeconds - repliedDate > 48L * 3600L) {
            Integer commandMessageId = message.messageId();
            if (commandMessageId != null) {
                try {
                    telegramApiClient.execute(new DeleteMessage(chatId, commandMessageId));
                } catch (RuntimeException e) {
                    log.warn("删除超过 48 小时的 /delete 命令消息失败，updateId={}, chatId={}, messageId={}", updateId, chatId, commandMessageId, e);
                }
            }
            new Thread(() -> {
                Integer hintId = null;
                try {
                    SendResponse resp = telegramApiClient.execute(
                            new SendMessage(chatId.longValue(), "撤回失败：消息发送已超过 48 小时，受 Telegram 限制无法删除（5s后删除本消息）")
                    );
                    hintId = resp == null || resp.message() == null ? null : resp.message().messageId();
                } catch (RuntimeException e) {
                    log.warn("发送超过 48 小时删除失败提示消息失败，updateId={}, chatId={}", updateId, chatId, e);
                }
                try {
                    Thread.sleep(5_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (hintId != null) {
                    try {
                        telegramApiClient.execute(new DeleteMessage(chatId, hintId));
                    } catch (RuntimeException e) {
                        log.warn("删除超过 48 小时删除失败提示消息失败，updateId={}, chatId={}, messageId={}", updateId, chatId, hintId, e);
                    }
                }
            }).start();
            return;
        }

        Long groupId = telegramBotProperties.getGroupId();

        if (Chat.Type.Private.equals(chat.type())) {
            Long userId = message.from().id();
            com.kixyu.tgbot.domain.entity.Message mapping = onboardingCommonService.findMessageMapping(repliedMessageId);
            if (mapping != null) {
                Topic topic = onboardingCommonService.findValidTopic(mapping);
                if (topic != null && topic.getUserId() != null && topic.getUserId().equals(userId)) {
                    Long senderId = mapping.getSenderId();
                    if (senderId == null
                            || !senderId.equals(userId)
                            || mapping.getMessageType() != com.kixyu.tgbot.domain.entity.Message.MessageType.USER_MESSAGE) {
                        Integer commandMessageId = message.messageId();
                        if (commandMessageId != null) {
                            try {
                                telegramApiClient.execute(new DeleteMessage(chatId, commandMessageId));
                            } catch (RuntimeException e) {
                                log.warn("删除 /delete 命令消息失败，updateId={}, chatId={}, messageId={}", updateId, chatId, commandMessageId, e);
                            }
                        }
                        new Thread(() -> {
                            Integer hintId = null;
                            try {
                                SendResponse resp = telegramApiClient.execute(
                                        new SendMessage(chatId.longValue(), "不能删除非自己发送的消息（5s后删除本消息）")
                                );
                                hintId = resp == null || resp.message() == null ? null : resp.message().messageId();
                            } catch (RuntimeException e) {
                                log.warn("发送删除他人消息提示失败，updateId={}, chatId={}", updateId, chatId, e);
                            }
                            try {
                                Thread.sleep(5_000L);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            if (hintId != null) {
                                try {
                                    telegramApiClient.execute(new DeleteMessage(chatId, hintId));
                                } catch (RuntimeException e) {
                                    log.warn("删除删除他人消息提示失败，updateId={}, chatId={}, messageId={}", updateId, chatId, hintId, e);
                                }
                            }
                        }).start();
                        return;
                    }
                }
            }
            onboardingCommonService.deletePairedMessagesFromPrivate(updateId, userId, chatId, repliedMessageId);
        } else if (groupId != null && groupId.equals(chatId)) {
            Long ownerId = telegramBotProperties.getOwnerId();
            if (ownerId != null && !ownerId.equals(message.from().id())) {
                return;
            }
            onboardingCommonService.deletePairedMessagesFromGroup(updateId, chatId, repliedMessageId);
        }

        try {
            telegramApiClient.execute(new DeleteMessage(chatId, message.messageId()));
        } catch (RuntimeException e) {
            log.warn("删除 /delete 命令消息失败，updateId={}, chatId={}, messageId={}", updateId, chatId, message.messageId(), e);
        }
    }

}
