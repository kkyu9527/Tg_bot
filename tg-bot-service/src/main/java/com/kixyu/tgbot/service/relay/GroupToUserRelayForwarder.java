package com.kixyu.tgbot.service.relay;

import com.kixyu.tgbot.domain.entity.Message.ContentType;
import com.kixyu.tgbot.service.bot.BotService;
import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InputMedia;
import com.pengrad.telegrambot.model.request.ReplyParameters;
import com.pengrad.telegrambot.request.CopyMessage;
import com.pengrad.telegrambot.request.SendMediaGroup;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.MessageIdResponse;
import com.pengrad.telegrambot.response.MessagesResponse;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
class GroupToUserRelayForwarder {

    private final TelegramApiClient telegramApiClient;
    private final TelegramBotProperties telegramBotProperties;
    private final RelayReplyResolver relayReplyResolver;
    private final TelegramMessageMediaMapper telegramMessageMediaMapper;
    private final BotService botService;

    private final ScheduledExecutorService mediaGroupScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, MediaGroupRelaySupport.MessageBuffer<OwnerMediaGroupContext>> ownerMediaGroupBuffers = new ConcurrentHashMap<>();

    private record OwnerMediaGroupContext(String groupChatId, Long threadId, Long userId, String mediaGroupId) {
    }

    /**
     * 将群话题中由主人发送的消息回流给对应用户。
     *
     * @param groupMessage 群内消息
     */
    void relay(Message groupMessage) {
        relayInternal(groupMessage, true);
    }

    /**
     * 回流核心逻辑。
     *
     * @param groupMessage 群内消息
     * @param allowMediaGroup 是否允许将媒体组聚合后回流
     */
    private void relayInternal(Message groupMessage, boolean allowMediaGroup) {
        if (groupMessage == null || groupMessage.from() == null || groupMessage.chat() == null || groupMessage.chat().id() == null) {
            return;
        }

        Long groupId = telegramBotProperties.getGroupId();
        if (groupId == null || groupId == 0L) {
            log.debug("未配置群组 groupId，跳过回流");
            return;
        }
        if (!groupId.equals(groupMessage.chat().id())) {
            log.debug("非目标群组消息，跳过回流，chatId={}", groupMessage.chat().id());
            return;
        }

        Long ownerId = telegramBotProperties.getOwnerId();
        if (ownerId != null && !ownerId.equals(groupMessage.from().id())) {
            log.debug("非主人消息，跳过回流，fromId={}, ownerId={}", groupMessage.from().id(), ownerId);
            return;
        }

        Long threadId = groupMessage.messageThreadId();
        if (threadId == null) {
            log.debug("非话题消息，跳过回流，messageId={}", groupMessage.messageId());
            return;
        }

        Long userId = relayReplyResolver.resolveTargetUserId(groupMessage, threadId, String.valueOf(groupId));
        if (userId == null) {
            log.warn("无法解析目标用户，跳过回流，threadId={}, messageId={}", threadId, groupMessage.messageId());
            return;
        }

        String groupChatId = String.valueOf(groupId);
        if (allowMediaGroup && groupMessage.mediaGroupId() != null && !groupMessage.mediaGroupId().isBlank()) {
            enqueueOwnerMediaGroupMessage(groupMessage, groupChatId, threadId, userId);
            return;
        }

        log.info("回流群话题消息到用户开始，threadId={}, groupMessageId={}, userId={}",
                threadId, groupMessage.messageId(), userId);

        if (groupMessage.messageId() == null) {
            return;
        }
        CopyMessage copyMessage = new CopyMessage(userId, groupId, groupMessage.messageId());

        Integer replyTo = relayReplyResolver.resolveUserReplyToMessageId(groupMessage);
        if (replyTo != null) {
            copyMessage.replyParameters(new ReplyParameters(replyTo).allowSendingWithoutReply(true));
        }

        MessageIdResponse copied = telegramApiClient.execute(copyMessage);
        if (copied == null || !copied.isOk() || copied.messageId() == null) {
            log.warn("回流群话题消息失败，threadId={}, userId={}, error={}", threadId, userId, copied == null ? null : copied.description());
            if (looksLikeBlocked(copied)) {
                notifyOwnerUserBlocked(groupId, threadId, userId);
            }
            return;
        }

        ContentType contentType = telegramMessageMediaMapper.inferContentType(groupMessage);
        Long originalMessageId = groupMessage.messageId() == null ? null : groupMessage.messageId().longValue();
        Long forwardedMessageId = copied.messageId() == null ? null : copied.messageId().longValue();
        if (originalMessageId == null || forwardedMessageId == null) {
            return;
        }

        botService.handleOwnerReplyInTopic(groupMessage.from(), contentType, threadId, originalMessageId, forwardedMessageId);
        log.info("回流群话题消息成功，threadId={}, groupMessageId={}, userMessageId={}, userId={}",
                threadId, originalMessageId, forwardedMessageId, userId);
    }

    /**
     * 将主人在群话题中发送的媒体组消息缓冲起来，待稳定后批量回流给用户。
     *
     * @param groupMessage 群内媒体组消息
     * @param groupChatId 群 chatId（字符串形式）
     * @param threadId 话题 threadId
     * @param userId 目标用户 ID
     */
    private void enqueueOwnerMediaGroupMessage(Message groupMessage, String groupChatId, Long threadId, Long userId) {
        String key = groupChatId + ":" + threadId + ":" + userId + ":" + groupMessage.mediaGroupId();
        MediaGroupRelaySupport.MessageBuffer<OwnerMediaGroupContext> buffer = ownerMediaGroupBuffers.computeIfAbsent(
                key,
                k -> new MediaGroupRelaySupport.MessageBuffer<>(new OwnerMediaGroupContext(groupChatId, threadId, userId, groupMessage.mediaGroupId()))
        );
        buffer.add(groupMessage);
        synchronized (buffer) {
            if (buffer.getScheduledCheck() == null || buffer.getScheduledCheck().isCancelled() || buffer.getScheduledCheck().isDone()) {
                buffer.setScheduledCheck(mediaGroupScheduler.scheduleAtFixedRate(
                        () -> MediaGroupRelaySupport.checkAndFlush(buffer, () -> flushOwnerMediaGroup(buffer)),
                        500,
                        500,
                        TimeUnit.MILLISECONDS
                ));
            }
        }
    }

    /**
     * 将已收集完成的媒体组批量回流给用户，并记录映射关系。
     *
     * @param buffer 媒体组缓冲
     */
    private void flushOwnerMediaGroup(MediaGroupRelaySupport.MessageBuffer<OwnerMediaGroupContext> buffer) {
        OwnerMediaGroupContext context = buffer.context();
        if (context == null) {
            return;
        }
        String key = context.groupChatId() + ":" + context.threadId() + ":" + context.userId() + ":" + context.mediaGroupId();
        ownerMediaGroupBuffers.remove(key, buffer);

        List<Message> bufferedMessages = buffer.messages();
        if (bufferedMessages.size() < 2) {
            for (Message message : bufferedMessages) {
                relayInternal(message, false);
            }
            return;
        }

        User owner = bufferedMessages.getFirst().from();
        if (owner == null) {
            return;
        }

        MediaGroupRelaySupport.CollectedMediaGroup collected = MediaGroupRelaySupport.collectMedias(
                bufferedMessages,
                telegramMessageMediaMapper
        );
        List<Message> originals = collected.originals();
        ArrayList<InputMedia<?>> medias = collected.medias();

        if (medias.size() < 2) {
            for (Message message : bufferedMessages) {
                relayInternal(message, false);
            }
            return;
        }

        MessagesResponse response = telegramApiClient.execute(new SendMediaGroup(context.userId(), medias.toArray(new InputMedia[0])));
        Message[] sent = response == null ? null : response.messages();
        if (response == null || !response.isOk() || sent == null || sent.length != medias.size()) {
            log.warn("回流媒体组失败，threadId={}, userId={}, mediaGroupId={}, expected={}, actual={}, error={}",
                    context.threadId(), context.userId(), context.mediaGroupId(), medias.size(), sent == null ? null : sent.length, response == null ? null : response.description());
            if (looksLikeBlocked(response)) {
                Long groupId = telegramBotProperties.getGroupId();
                Long threadId = context.threadId();
                if (groupId != null && threadId != null) {
                    notifyOwnerUserBlocked(groupId, threadId, context.userId());
                }
                return;
            }
            for (Message message : bufferedMessages) {
                relayInternal(message, false);
            }
            return;
        }

        for (int i = 0; i < sent.length; i++) {
            Message original = originals.get(i);
            Message forwarded = sent[i];
            if (original.messageId() == null || forwarded.messageId() == null) {
                continue;
            }
            Long originalMessageId = original.messageId().longValue();
            Long forwardedMessageId = forwarded.messageId().longValue();
            botService.handleOwnerMediaGroupReplyInTopic(
                    owner,
                    context.mediaGroupId(),
                    ContentType.MEDIA_GROUP,
                    context.threadId(),
                    originalMessageId,
                    forwardedMessageId
            );
        }

        log.info("回流群话题媒体组相册成功，threadId={}, userId={}, mediaGroupId={}, count={}",
                context.threadId(), context.userId(), context.mediaGroupId(), sent.length);
    }

    /**
     * 判断 Telegram API 响应是否提示用户已被阻塞或账号已停用。
     *
     * @param response Telegram API 响应
     * @return         如果响应提示用户被阻塞或账号停用，则返回 true；否则返回 false
     */
    private boolean looksLikeBlocked(BaseResponse response) {
        if (response == null || response.description() == null) {
            return false;
        }
        String msg = response.description().toLowerCase();
        return msg.contains("blocked by the user")
                || msg.contains("bot was blocked")
                || msg.contains("forbidden")
                || msg.contains("user is deactivated")
                || msg.contains("chat not found");
    }

    /**
     * 通知群话题主人用户已被阻塞或账号已停用。
     *
     * @param groupId  群 chatId
     * @param threadId 话题 ID
     * @param userId   被阻塞或停用的用户 ID
     */
    private void notifyOwnerUserBlocked(Long groupId, Long threadId, Long userId) {
        if (groupId == null || threadId == null || userId == null) {
            return;
        }
        if (threadId > Integer.MAX_VALUE) {
            return;
        }
        try {
            String text = "🚫 提示\n\n无法给该用户发送消息，可能已拉黑机器人或账号不可用。\nuserId = " + userId + "。";
            SendResponse response = telegramApiClient.execute(
                    telegramApiClient.createSendMessage(groupId, text).messageThreadId(threadId.intValue())
            );
            telegramApiClient.scheduleDeleteIfOk(groupId, response);
        } catch (RuntimeException e) {
            log.warn("发送“用户可能拉黑机器人”提示失败，groupId={}, threadId={}, userId={}", groupId, threadId, userId, e);
        }
    }
}
