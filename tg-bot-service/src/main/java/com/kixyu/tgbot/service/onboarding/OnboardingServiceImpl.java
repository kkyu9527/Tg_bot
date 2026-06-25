package com.kixyu.tgbot.service.onboarding;

import com.kixyu.tgbot.config.BotPolicyConstants;
import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.domain.entity.Message.MessageType;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.topic.TopicService;
import com.kixyu.tgbot.service.common.CommandMessageCleanupService;
import com.kixyu.tgbot.service.user.UserService;
import com.kixyu.tgbot.service.verification.VerificationService;
import com.kixyu.tgbot.support.OnboardingSupport;
import com.kixyu.tgbot.support.UserConfigKeyboardFactory;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户引导与命令处理服务实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
class OnboardingServiceImpl implements OnboardingService {

    private static final String DELETE_USAGE_HINT = appendDefaultAutoDeleteHint("⚠️ 小提示\n\n请先「回复」要撤回的那条消息，然后再发送 /delete。");
    private static final String DELETE_EXPIRED_HINT = appendDefaultAutoDeleteHint("⛔ 撤回失败\n\n原因：消息发送已超过 48 小时，受 Telegram 限制无法删除。");
    private static final String DELETE_OTHERS_HINT = appendDefaultAutoDeleteHint("🙅‍♀️ 不能删除「非本人发送」的消息哦～");

    private final OnboardingSupport onboardingSupport;
    private final TelegramBotProperties telegramBotProperties;
    private final TelegramApiClient telegramApiClient;
    private final TopicService topicService;
    private final CommandMessageCleanupService commandMessageCleanupService;
    private final UserService userService;
    private final VerificationService verificationService;
    private final UserConfigKeyboardFactory userConfigKeyboardFactory;

    /**
     * 为提示文本追加默认自动删除说明。
     *
     * @param text 原始提示文本
     * @return     追加自动删除说明后的提示文本
     */
    private static String appendDefaultAutoDeleteHint(String text) {
        return text + "\n（⏱️ " + BotPolicyConstants.formatDuration(BotPolicyConstants.DEFAULT_HINT_AUTO_DELETE_DELAY) + "后会自动删除本条提示～）";
    }

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
        if (!"start".equals(command)
                && message != null
                && message.from() != null
                && chat != null
                && Chat.Type.Private.equals(chat.type())
                && userService.isUnverified(message.from().id())) {
            verificationService.remindVerificationRequired(message.from(), chat.id());
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
                    handleStartCommand(message.from(), privateChatId);
                }
            }
            case "info" -> handleInfoCommand(updateId, message, chat);
            case "chatid" -> handleChatIdCommand(updateId, message, chat);
            case "close_topic" -> handleCloseTopicCommand(updateId, message, chat);
            case "delete" -> handleDeleteCommand(updateId, message, chat);
            case "user_config" -> handleUserConfigCommand(updateId, message, chat);
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
    private void handleStartCommand(User user, Long privateChatId) {
        if (user == null || user.id() == null || privateChatId == null) {
            return;
        }
        if (userService.isUnverified(user.id())) {
            verificationService.sendChallenge(user, privateChatId);
            return;
        }
        handleVerifiedStart(user, privateChatId);
    }

    /**
     * 处理已验证用户的启动流程。
     *
     * @param user          触发启动流程的用户
     * @param privateChatId 用户私聊窗口的聊天 ID
     */
    @Override
    public void handleVerifiedStart(User user, Long privateChatId) {
        try {
            log.info("处理 /start，userId={}, privateChatId={}, username={}", user.id(), privateChatId, user.username());
            try {
                onboardingSupport.sendWelcomeToUser(user, privateChatId);
                log.info("已发送欢迎消息，userId={}, privateChatId={}", user.id(), privateChatId);
            } catch (RuntimeException e) {
                log.warn("发送欢迎消息失败，userId={}, privateChatId={}", user.id(), privateChatId, e);
            }

            Long groupId = telegramBotProperties.getGroupId();
            if (groupId == null || groupId == 0L) {
                log.warn("未配置群组 groupId，跳过创建话题，userId={}", user.id());
                return;
            }

            String groupChatId = String.valueOf(groupId);
            Optional<Topic> existing = topicService.getTopicByUserIdAndChatId(user.id(), groupChatId);
            if (existing.isPresent()) {
                Topic existingTopic = existing.get();
                if (onboardingSupport.isPlaceholderTopicId(existingTopic.getTopicId())) {
                    log.warn("检测到占位 topicId，准备重建话题并修复映射，userId={}, groupChatId={}, topicId={}",
                            user.id(), groupChatId, existingTopic.getTopicId());
                    onboardingSupport.recreateAndUpdateTopic(user, groupChatId);
                    return;
                }

                if (onboardingSupport.isForumTopicMissing(groupChatId, existingTopic)) {
                    log.warn("检测到群话题不存在，准备重建话题并清理遗留数据，userId={}, groupChatId={}, topicId={}",
                            user.id(), groupChatId, existingTopic.getTopicId());
                    onboardingSupport.recreateAndUpdateTopic(user, groupChatId);
                    return;
                }

                log.info("已存在有效用户话题映射，跳过创建，userId={}, groupChatId={}, topicId={}",
                        user.id(), groupChatId, existingTopic.getTopicId());
                onboardingSupport.ensureWelcomeMessagePinned(groupChatId, existingTopic, user);
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

            Topic createdTopic = topicService.createTopic(
                    user.id(),
                    user.username(),
                    user.firstName(),
                    user.lastName(),
                    threadId,
                    groupChatId
            );
            log.info("保存用户话题映射成功，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, threadId);

            String caption = onboardingSupport.buildTopicCaption(createdTopic);

            Message sentMessage = onboardingSupport.sendNewUserMessageToTopic(groupChatId, threadId, user, caption);
            if (sentMessage == null || sentMessage.messageId() == null) {
                log.warn("发送新用户提示消息失败，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, threadId);
                return;
            }
            topicService.getTopicByUserIdAndChatId(user.id(), groupChatId)
                    .ifPresent(topic -> {
                        topic.setWelcomeMessageId(sentMessage.messageId().longValue());
                        topicService.saveTopic(topic);
                    });
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
        text.append("📇 账号信息\n\n");
        text.append("👤 名字：").append(displayName).append("\n");
        text.append("🆔 用户 ID：").append(user.id());
        if (user.username() != null && !user.username().isBlank()) {
            text.append("\n📛 用户名：@").append(user.username());
        }
        if (user.languageCode() != null && !user.languageCode().isBlank()) {
            text.append("\n🌐 语言：").append(user.languageCode());
        }
        if (Boolean.TRUE.equals(user.isPremium())) {
            text.append("\n💎 账号类型：Telegram Premium");
        }
        text.append("\n\n💡 小技巧\n");
        text.append("如果想撤回一条已经发送的消息，可以在私聊或话题中回复那条消息，然后发送 /delete。\n");
        text.append("我会尽量同时删除两端的对应消息（受 Telegram 限制，部分旧消息可能无法撤回）。");

        try {
            SendResponse response = telegramApiClient.execute(
                    telegramApiClient.createSendMessage(privateChatId, text.toString())
            );
            telegramApiClient.scheduleDeleteIfOk(privateChatId, response);
            if (message.messageId() != null) {
                telegramApiClient.scheduleDelete(privateChatId, message.messageId());
            }
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
        String text = "🆔 当前群组 ID：" + chatId;

        try {
            SendResponse response = telegramApiClient.execute(
                    telegramApiClient.createSendMessage(chatId, text)
            );
            telegramApiClient.scheduleDeleteIfOk(chatId, response);
            if (message.messageId() != null) {
                telegramApiClient.scheduleDelete(chatId, message.messageId());
            }
            log.info("处理 /chatid 完成，updateId={}, chatId={}", updateId, chatId);
        } catch (RuntimeException e) {
            log.warn("处理 /chatid 发送消息失败，updateId={}, chatId={}", updateId, chatId, e);
        }
    }

    /**
     * 处理 /close_topic 命令，删除当前话题并清理数据。
     *
     * @param updateId 更新 ID
     * @param message  消息实体
     * @param chat     聊天实体
     */
    private void handleCloseTopicCommand(Integer updateId, Message message, Chat chat) {
        if (commandMessageCleanupService.isInvalidGroupOwnerCommand(message, chat)) {
            return;
        }

        Long groupId = telegramBotProperties.getGroupId();
        Long threadId = message.messageThreadId();
        if (threadId == null) {
            return;
        }

        try {
            commandMessageCleanupService.deleteForumTopic(groupId, threadId);
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
                .ifPresent(topic -> commandMessageCleanupService.deleteTopicMessagesAndMapping(updateId, topic));
    }

    /**
     * 处理 /user_config 命令，显示当前用户的配置。
     *
     * @param updateId 更新 ID
     * @param message  消息实体
     * @param chat     聊天实体
     */
    private void handleUserConfigCommand(Integer updateId, Message message, Chat chat) {
        if (commandMessageCleanupService.isInvalidGroupOwnerCommand(message, chat)) {
            return;
        }
        Long groupId = telegramBotProperties.getGroupId();
        Long threadId = message.messageThreadId();
        if (groupId == null || threadId == null || chat == null || chat.id() == null) {
            return;
        }
        String groupChatId = String.valueOf(groupId);
        Topic topic = topicService.getTopicByTopicId(threadId).orElse(null);
        if (topic == null || !groupChatId.equals(topic.getChatId())) {
            return;
        }
        Long targetUserId = topic.getUserId();
        if (targetUserId == null) {
            return;
        }
        Long chatId = chat.id();
        StringBuilder text = new StringBuilder();
        text.append("⚙️ 用户配置\n\n");
        text.append("请在下方选择该用户的配置：\n");
        text.append("userId = ").append(targetUserId);

        InlineKeyboardMarkup keyboard = userConfigKeyboardFactory.buildForTopic(topic);

        SendResponse configResponse = null;
        try {
            configResponse = telegramApiClient.execute(
                    telegramApiClient.createSendMessage(chatId, text.toString())
                            .messageThreadId(threadId)
                            .replyMarkup(keyboard)
            );
            log.info("已发送用户配置选择消息，updateId={}, groupChatId={}, threadId={}, userId={}",
                    updateId, groupChatId, threadId, targetUserId);
        } catch (RuntimeException e) {
            log.warn("发送用户配置选择消息失败，updateId={}, groupChatId={}, threadId={}, userId={}",
                    updateId, groupChatId, threadId, targetUserId, e);
        }

        if (configResponse != null && configResponse.message() != null && configResponse.message().messageId() != null) {
            telegramApiClient.scheduleDelete(chatId, configResponse.message().messageId());
        }
        if (message.messageId() != null) {
            telegramApiClient.scheduleDelete(chat.id(), message.messageId());
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
            Integer commandMessageId = message.messageId();
            if (chatId != null) {
                sendTemporaryHint(updateId, chatId, message.messageThreadId(), DELETE_USAGE_HINT, "提示 /delete 使用方式失败");
                if (commandMessageId != null) {
                    telegramApiClient.scheduleDelete(chatId, commandMessageId, 0L);
                }
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
                telegramApiClient.scheduleDelete(chatId, commandMessageId, 0L);
            }
            sendTemporaryHint(updateId, chatId, message.messageThreadId(), DELETE_EXPIRED_HINT, "发送超过 48 小时删除失败提示消息失败");
            return;
        }

        Long groupId = telegramBotProperties.getGroupId();

        if (Chat.Type.Private.equals(chat.type())) {
            Long userId = message.from().id();
            var mapping = commandMessageCleanupService.findMessageMapping(repliedMessageId);
            if (mapping != null) {
                Topic topic = commandMessageCleanupService.findValidTopic(mapping);
                if (topic != null && topic.getUserId() != null && topic.getUserId().equals(userId)) {
                    Long senderId = mapping.getSenderId();
                    if (senderId == null
                            || !senderId.equals(userId)
                            || mapping.getMessageType() != MessageType.USER_MESSAGE) {
                        Integer commandMessageId = message.messageId();
                        if (commandMessageId != null) {
                            telegramApiClient.scheduleDelete(chatId, commandMessageId, 0L);
                        }
                        sendTemporaryHint(updateId, chatId, message.messageThreadId(), DELETE_OTHERS_HINT, "发送删除他人消息提示失败");
                        return;
                    }
                }
            }
            commandMessageCleanupService.deletePairedMessagesFromPrivate(updateId, userId, chatId, repliedMessageId);
        } else if (groupId != null && groupId.equals(chatId)) {
            Long ownerId = telegramBotProperties.getOwnerId();
            if (ownerId != null && !ownerId.equals(message.from().id())) {
                return;
            }
            var mapping = commandMessageCleanupService.findMessageMapping(repliedMessageId);
            Topic topic = commandMessageCleanupService.findValidTopic(mapping);
            String groupChatId = String.valueOf(groupId);
            if (mapping == null || topic == null || !groupChatId.equals(topic.getChatId())) {
                Long threadId = message.messageThreadId();
                sendTemporaryHint(updateId, chatId, threadId, DELETE_USAGE_HINT, "提示 /delete 使用方式失败");
                Integer commandMessageId = message.messageId();
                if (commandMessageId != null) {
                    telegramApiClient.scheduleDelete(chatId, commandMessageId, 0L);
                }
                return;
            }
            commandMessageCleanupService.deletePairedMessagesFromGroup(updateId, chatId, repliedMessageId);
        }

        if (message.messageId() != null) {
            telegramApiClient.scheduleDelete(chatId, message.messageId(), 0L);
        }
    }

    /**
     * 发送临时提示消息，并按默认延迟自动删除。
     *
     * @param updateId       更新 ID
     * @param chatId         聊天 ID
     * @param threadId       话题线程 ID
     * @param text           提示文本
     * @param failureMessage 发送失败时的日志前缀
     */
    private void sendTemporaryHint(Integer updateId, Long chatId, Long threadId, String text, String failureMessage) {
        if (chatId == null || text == null || text.isBlank()) {
            return;
        }
        try {
            SendMessage request = telegramApiClient.createSendMessage(chatId, text);
            if (threadId != null) {
                request.messageThreadId(threadId);
            }
            SendResponse response = telegramApiClient.execute(request);
            telegramApiClient.scheduleDeleteIfOk(chatId, response);
        } catch (RuntimeException e) {
            log.warn("{}，updateId={}, chatId={}", failureMessage, updateId, chatId, e);
        }
    }
}
