package com.kixyu.tgbot.service.common;

import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.domain.entity.Message;
import com.kixyu.tgbot.domain.entity.Message.MessageType;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.message.MessageService;
import com.kixyu.tgbot.service.topic.TopicService;
import com.kixyu.tgbot.support.OnboardingSupport;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
class CommandMessageCleanupServiceImpl implements CommandMessageCleanupService {

    private final OnboardingSupport onboardingSupport;
    private final TelegramBotProperties telegramBotProperties;
    private final TelegramApiClient telegramApiClient;
    private final MessageService messageService;
    private final TopicService topicService;

    /**
     * 删除私聊与群聊中成对的消息（从私聊入口触发）。
     *
     * @param updateId         更新 ID，用于日志追踪
     * @param userId           私聊用户 ID
     * @param privateChatId    私聊会话 ID
     * @param repliedMessageId 被回复的私聊消息 ID
     */
    @Override
    public void deletePairedMessagesFromPrivate(Integer updateId, Long userId, Long privateChatId, Long repliedMessageId) {
        Message mapping = findMessageMapping(repliedMessageId);
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
        Long groupId = onboardingSupport.parseChatIdLong(topic.getChatId());
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
     * 删除群聊与私聊中成对的消息（从群聊入口触发）。
     *
     * @param updateId         更新 ID，用于日志追踪
     * @param groupId          群聊 ID
     * @param repliedMessageId 被回复的群聊消息 ID
     */
    @Override
    public void deletePairedMessagesFromGroup(Integer updateId, Long groupId, Long repliedMessageId) {
        Message mapping = findMessageMapping(repliedMessageId);
        if (mapping == null) {
            return;
        }

        Topic topic = findValidTopic(mapping);
        if (topic == null) {
            return;
        }
        Long mappedGroupId = onboardingSupport.parseChatIdLong(topic.getChatId());
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
     * 删除与话题关联的所有 Telegram 消息及本地消息映射数据。
     *
     * @param updateId 更新 ID，用于日志追踪
     * @param topic    需要清理的本地话题实体
     */
    @Override
    public void deleteTopicMessagesAndMapping(Integer updateId, Topic topic) {
        if (topic == null || topic.getTopicId() == null) {
            return;
        }
        Long topicId = topic.getTopicId();
        Long privateChatId = topic.getUserId();
        Long groupId = onboardingSupport.parseChatIdLong(topic.getChatId());

        List<Message> messages = messageService.getMessagesByTopicId(topicId);
        if (messages != null) {
            for (Message mapping : messages) {
                deleteMessageSafely(updateId, privateChatId, mapping.getOriginalMessageId());
                deleteMessageSafely(updateId, privateChatId, mapping.getForwardedMessageId());
                if (groupId != null) {
                    deleteMessageSafely(updateId, groupId, mapping.getOriginalMessageId());
                    deleteMessageSafely(updateId, groupId, mapping.getForwardedMessageId());
                }
            }
        }
        try {
            messageService.deleteMessagesByTopicId(topicId);
            topicService.deleteTopicByTopicIdAndChatId(topicId, topic.getChatId());
            log.info("已删除本地话题及消息数据，updateId={}, topicId={}, groupChatId={}", updateId, topicId, topic.getChatId());
        } catch (RuntimeException e) {
            log.warn("删除本地话题或消息数据失败，updateId={}, topicId={}, groupChatId={}", updateId, topicId, topic.getChatId(), e);
        }
    }

    /**
     * 调用 Telegram HTTP 接口删除指定群聊的话题。
     *
     * @param groupId  群聊 ID
     * @param threadId 话题线程 ID
    */
    @Override
    public void deleteForumTopic(Long groupId, Long threadId) {
        if (groupId == null || threadId == null) {
            return;
        }

        BaseResponse response = telegramApiClient.deleteForumTopic(groupId, threadId);
        if (response == null || !response.isOk()) {
            throw new IllegalStateException("deleteForumTopic failed: " + (response == null ? null : response.description()));
        }
    }

    /**
     * 根据原始消息 ID 或转发消息 ID 查询消息映射。
     *
     * @param messageId 原始或转发消息 ID
     * @return          消息映射实体，找不到时返回 {@code null}
     */
    @Override
    public Message findMessageMapping(Long messageId) {
        return messageService.getMessageByOriginalMessageId(messageId)
                .orElseGet(() -> messageService.getMessageByForwardedMessageId(messageId).orElse(null));
    }

    /**
     * 根据消息映射查询有效的话题。
     *
     * @param mapping   消息映射实体
     * @return          关联的话题，找不到或无效时返回 {@code null}
     */
    @Override
    public Topic findValidTopic(Message mapping) {
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
     * 判断当前命令是否为无效的群主命令。
     * <p>
     * 包含以下校验：
     * <ul>
     *     <li>消息、发送人或聊天信息为空</li>
     *     <li>聊天 ID 与配置的群聊 ID 不一致</li>
     *     <li>发送人不是配置的群主</li>
     * </ul>
     *
     * @param message       Telegram 消息
     * @param chat          Telegram 聊天信息
     * @return {@code true} 表示命令无效，应直接返回；{@code false} 表示命令校验通过
     */
    @Override
    public boolean isInvalidGroupOwnerCommand(com.pengrad.telegrambot.model.Message message, Chat chat) {
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

    /**
     * 根据消息映射解析出私聊和群聊中的成对消息 ID。
     *
     * @param mapping   消息映射实体
     * @return          成对消息 ID，无法解析时返回 {@code null}
     */
    private PairedMessageIds resolvePairedMessageIds(Message mapping) {
        if (mapping == null) {
            return null;
        }
        Long userMessageId;
        Long groupMessageId;
        if (mapping.getMessageType() == MessageType.USER_MESSAGE) {
            userMessageId = mapping.getOriginalMessageId();
            groupMessageId = mapping.getForwardedMessageId();
        } else if (mapping.getMessageType() == MessageType.OWNER_MESSAGE) {
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
     * 安全删除指定消息，异常时仅记录日志而不中断流程。
     *
     * @param updateId  更新 ID，用于日志追踪
     * @param chatId    会话 ID
     * @param messageId 消息 ID
     */
    private void deleteMessageSafely(Integer updateId, Long chatId, Long messageId) {
        if (chatId == null || messageId == null || messageId > Integer.MAX_VALUE) {
            return;
        }
        try {
            telegramApiClient.execute(new DeleteMessage(chatId, messageId.intValue()));
        } catch (RuntimeException e) {
            log.warn("删除消息失败，updateId={}, chatId={}, messageId={}", updateId, chatId, messageId, e);
        }
    }
}
