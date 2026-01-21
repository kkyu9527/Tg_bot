package com.kixyu.tgbot.service.relay;

import com.kixyu.tgbot.domain.entity.Message.ContentType;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.BotService;
import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.service.UserService;
import com.kixyu.tgbot.service.relay.mapper.RelayReplyMapper;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.kixyu.tgbot.telegram.TelegramApiErrorUtil;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InputMedia;
import com.pengrad.telegrambot.model.request.ReplyParameters;
import com.pengrad.telegrambot.request.CopyMessage;
import com.pengrad.telegrambot.request.SendMediaGroup;
import com.pengrad.telegrambot.request.SendMessage;
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
public class UserToGroupRelayForwarder {

    private final TelegramApiClient telegramApiClient;
    private final TelegramBotProperties telegramBotProperties;
    private final RelayTopicManager relayTopicManager;
    private final TelegramMessageMediaMapper telegramMessageMediaMapper;
    private final BotService botService;
    private final RelayReplyMapper relayReplyMapper;
    private final UserService userService;

    private final ScheduledExecutorService mediaGroupScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, MediaGroupRelaySupport.MessageBuffer<MediaGroupContext>> mediaGroupBuffers = new ConcurrentHashMap<>();

    private record MediaGroupContext(Long privateChatId, String groupChatId, String mediaGroupId) {
    }

    /**
     * 将用户私聊消息转发到群话题。
     *
     * @param privateMessage 用户私聊消息
     */
    public void forward(Message privateMessage) {
        forwardInternal(privateMessage, true);
    }

    /**
     * 转发核心逻辑。
     *
     * @param privateMessage 用户私聊消息
     * @param allowMediaGroup 是否允许将媒体组聚合后转发
     */
    private void forwardInternal(Message privateMessage, boolean allowMediaGroup) {
        if (privateMessage == null || privateMessage.from() == null || privateMessage.chat() == null || privateMessage.chat().id() == null) {
            return;
        }

        Long groupId = telegramBotProperties.getGroupId();
        if (groupId == null || groupId == 0L) {
            log.warn("未配置群组 groupId，无法转发消息，userId={}", privateMessage.from().id());
            return;
        }

        User user = privateMessage.from();
        if (userService.isBlocked(user.id())) {
            log.info("检测到用户已被拉黑，跳过转发，userId={}", user.id());
            return;
        }
        String groupChatId = String.valueOf(groupId);

        log.info("转发私聊消息到群话题开始，userId={}, privateChatId={}, messageId={}",
                user.id(), privateMessage.chat().id(), privateMessage.messageId());

        if (allowMediaGroup && privateMessage.mediaGroupId() != null && !privateMessage.mediaGroupId().isBlank()) {
            enqueueMediaGroupMessage(privateMessage, groupChatId);
            return;
        }

        Topic topic = relayTopicManager.ensureTopic(user, groupChatId);
        if (topic == null) {
            log.warn("无法获取或创建话题，放弃转发，userId={}, groupChatId={}", user.id(), groupChatId);
            return;
        }

        if (topic.getTopicId() == null || topic.getTopicId() > Integer.MAX_VALUE) {
            log.warn("检测到无效 topicId，准备重建话题并转发，userId={}, groupChatId={}, topicId={}",
                    user.id(), groupChatId, topic.getTopicId());
            topic = relayTopicManager.recreateTopic(user, groupChatId);
            if (topic == null || topic.getTopicId() == null || topic.getTopicId() > Integer.MAX_VALUE) {
                return;
            }
        }

        ContentType contentType = telegramMessageMediaMapper.inferContentType(privateMessage);
        if (!Boolean.TRUE.equals(topic.getFullMode()) && contentType != ContentType.TEXT) {
            log.info("检测到话题未开启全模式，丢弃非文本消息，userId={}, topicId={}, contentType={}",
                    user.id(), topic.getTopicId(), contentType);
            Long privateChatId = privateMessage.chat().id();
            if (privateChatId != null) {
                String text = "💬 小提示\n\n当前仅支持转发「纯文本消息」。\n这条非文本消息不会被转发给主人。\n\n如需转发图片、视频等，请联系主人开启「全消息模式」～";
                try {
                    SendResponse response = telegramApiClient.execute(new SendMessage(privateChatId.longValue(), text));
                    telegramApiClient.scheduleDeleteIfOk(privateChatId, response, 30_000L);
                } catch (RuntimeException e) {
                    log.warn("发送非文本消息未转发提示失败，userId={}, chatId={}", user.id(), privateChatId, e);
                }
            }
            return;
        }

        CopyMessage copyMessage = new CopyMessage(groupId, privateMessage.chat().id(), privateMessage.messageId())
                .messageThreadId(topic.getTopicId());

        Integer replyTo = relayReplyMapper.resolveGroupReplyToMessageId(privateMessage);
        if (replyTo != null) {
            copyMessage.replyParameters(new ReplyParameters(replyTo).allowSendingWithoutReply(true));
        }

        MessageIdResponse copied = telegramApiClient.execute(copyMessage);
        if (copied == null || !copied.isOk() || copied.messageId() == null) {
            if (copied != null && TelegramApiErrorUtil.looksLikeInvalidThread(copied)) {
                log.warn("疑似话题不存在，尝试重建后重试转发，userId={}, topicId={}", user.id(), topic.getTopicId());
                Topic recreated = relayTopicManager.recreateTopic(user, groupChatId);
                if (recreated == null || recreated.getTopicId() == null || recreated.getTopicId() > Integer.MAX_VALUE) {
                    log.warn("重建话题失败，放弃重试转发，userId={}, groupChatId={}", user.id(), groupChatId);
                    return;
                }
                MessageIdResponse retried = telegramApiClient.execute(
                        new CopyMessage(groupId, privateMessage.chat().id(), privateMessage.messageId()).messageThreadId(recreated.getTopicId())
                );
                if (retried == null || !retried.isOk() || retried.messageId() == null) {
                    log.warn("重建后重试转发仍失败，userId={}", user.id());
                    return;
                }
                copied = retried;
                topic = recreated;
            } else {
                log.warn("转发私聊消息失败，userId={}, error={}", user.id(), copied == null ? null : copied.description());
                return;
            }
        }

        Long originalMessageId = privateMessage.messageId() == null ? null : privateMessage.messageId().longValue();
        Long forwardedMessageId = copied.messageId() == null ? null : copied.messageId().longValue();
        if (originalMessageId == null || forwardedMessageId == null) {
            log.warn("转发私聊消息失败：messageId 缺失，userId={}", user.id());
            return;
        }

        botService.handleUserMessage(user, contentType, groupChatId, originalMessageId, forwardedMessageId, topic.getTopicId());
        log.info("转发私聊消息成功，userId={}, topicId={}, originalMessageId={}, copiedMessageId={}",
                user.id(), topic.getTopicId(), originalMessageId, forwardedMessageId);
    }

    /**
     * 将媒体组消息缓冲起来，待稳定后批量转发到群话题。
     *
     * @param message 私聊消息
     * @param groupChatId 群 chatId（字符串形式）
     */
    private void enqueueMediaGroupMessage(Message message, String groupChatId) {
        String key = message.chat().id() + ":" + message.mediaGroupId();
        MediaGroupRelaySupport.MessageBuffer<MediaGroupContext> buffer = mediaGroupBuffers.computeIfAbsent(
                key,
                k -> new MediaGroupRelaySupport.MessageBuffer<>(new MediaGroupContext(message.chat().id(), groupChatId, message.mediaGroupId()))
        );
        buffer.add(message);
        synchronized (buffer) {
            if (buffer.getScheduledCheck() == null || buffer.getScheduledCheck().isCancelled() || buffer.getScheduledCheck().isDone()) {
                buffer.setScheduledCheck(mediaGroupScheduler.scheduleAtFixedRate(
                        () -> MediaGroupRelaySupport.checkAndFlush(buffer, () -> flushMediaGroup(buffer)),
                        500,
                        500,
                        TimeUnit.MILLISECONDS
                ));
            }
        }
    }

    /**
     * 将已收集完成的媒体组批量转发到群话题，并记录映射关系。
     *
     * @param buffer 媒体组缓冲
     */
    private void flushMediaGroup(MediaGroupRelaySupport.MessageBuffer<MediaGroupContext> buffer) {
        MediaGroupContext context = buffer.context();
        if (context == null) {
            return;
        }
        String key = context.privateChatId() + ":" + context.mediaGroupId();
        mediaGroupBuffers.remove(key, buffer);

        List<Message> bufferedMessages = buffer.messages();
        if (bufferedMessages.size() < 2) {
            for (Message message : bufferedMessages) {
                forwardInternal(message, false);
            }
            return;
        }

        Long groupId = telegramBotProperties.getGroupId();
        if (groupId == null || groupId == 0L) {
            return;
        }

        Message first = bufferedMessages.getFirst();
        if (first.from() == null) {
            return;
        }

        User user = first.from();
        if (userService.isBlocked(user.id())) {
            log.info("检测到用户已被拉黑，跳过媒体组转发，userId={}, mediaGroupId={}", user.id(), context.mediaGroupId());
            return;
        }
        String groupChatId = context.groupChatId();

        Topic topic = relayTopicManager.ensureTopic(user, groupChatId);
        if (topic == null) {
            log.warn("无法获取或创建话题，放弃转发媒体组，userId={}, groupChatId={}, mediaGroupId={}",
                    user.id(), groupChatId, context.mediaGroupId());
            return;
        }

        if (topic.getTopicId() == null || topic.getTopicId() > Integer.MAX_VALUE) {
            log.warn("检测到无效 topicId，准备重建话题并转发媒体组，userId={}, groupChatId={}, topicId={}",
                    user.id(), groupChatId, topic.getTopicId());
            topic = relayTopicManager.recreateTopic(user, groupChatId);
            if (topic == null || topic.getTopicId() == null || topic.getTopicId() > Integer.MAX_VALUE) {
                return;
            }
        }

        if (!Boolean.TRUE.equals(topic.getFullMode())) {
            log.info("检测到话题未开启全模式，丢弃媒体组消息，userId={}, topicId={}, mediaGroupId={}",
                    user.id(), topic.getTopicId(), context.mediaGroupId());
            Long privateChatId = context.privateChatId();
            if (privateChatId != null) {
                String text = "💬 小提示\n\n当前仅支持转发「纯文本消息」。\n该媒体组中的消息不会被转发给主人。\n\n如需转发图片、视频等，请联系主人开启「全消息模式」～";
                try {
                    SendResponse response = telegramApiClient.execute(new SendMessage(privateChatId.longValue(), text));
                    telegramApiClient.scheduleDeleteIfOk(privateChatId, response, 30_000L);
                } catch (RuntimeException e) {
                    log.warn("发送媒体组未转发提示失败，userId={}, chatId={}, mediaGroupId={}", user.id(), privateChatId, context.mediaGroupId(), e);
                }
            }
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
                forwardInternal(message, false);
            }
            return;
        }

        MessagesResponse response = telegramApiClient.execute(
                new SendMediaGroup(groupId, medias.toArray(new InputMedia[0])).messageThreadId(topic.getTopicId())
        );
        if (response == null || !response.isOk() || response.messages() == null) {
            if (response != null && TelegramApiErrorUtil.looksLikeInvalidThread(response)) {
                Topic recreated = relayTopicManager.recreateTopic(user, groupChatId);
                if (recreated == null || recreated.getTopicId() == null || recreated.getTopicId() > Integer.MAX_VALUE) {
                    return;
                }
                response = telegramApiClient.execute(
                        new SendMediaGroup(groupId, medias.toArray(new InputMedia[0])).messageThreadId(recreated.getTopicId())
                );
                topic = recreated;
            }
        }

        Message[] sent = response == null ? null : response.messages();
        if (response == null || !response.isOk() || sent == null || sent.length != medias.size()) {
            log.warn("发送媒体组失败：返回数量异常，userId={}, topicId={}, mediaGroupId={}, expected={}, actual={}, error={}",
                    user.id(), topic.getTopicId(), context.mediaGroupId(), medias.size(), sent == null ? null : sent.length, response == null ? null : response.description());
            return;
        }

        for (int i = 0; i < sent.length; i++) {
            Message original = originals.get(i);
            Message forwarded = sent[i];
            if (original.messageId() == null || forwarded.messageId() == null) {
                continue;
            }
            botService.handleUserMediaGroupMessage(
                    user,
                    context.mediaGroupId(),
                    ContentType.MEDIA_GROUP,
                    groupChatId,
                    original.messageId().longValue(),
                    forwarded.messageId().longValue(),
                    topic.getTopicId()
            );
        }

        log.info("转发私聊媒体组相册成功，userId={}, topicId={}, mediaGroupId={}, count={}",
                user.id(), topic.getTopicId(), context.mediaGroupId(), sent.length);
    }
}
