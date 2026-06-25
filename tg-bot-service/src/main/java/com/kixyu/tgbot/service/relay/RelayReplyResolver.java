package com.kixyu.tgbot.service.relay;

import com.kixyu.tgbot.domain.entity.Message.MessageType;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.message.MessageService;
import com.kixyu.tgbot.service.topic.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.pengrad.telegrambot.model.Message;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RelayReplyResolver {

    private final TopicService topicService;
    private final MessageService messageService;

    /**
     * 根据群话题消息推断目标用户 ID。
     *
     * <p>优先从“回复的消息”里查询映射到的原始发送者；如果无法解析，则退化为按 threadId 查话题映射。</p>
     *
     * @param groupMessage      群内话题消息
     * @param threadId          话题 threadId
     * @param groupChatId       群 chatId（字符串形式）
     * @return                  目标用户 ID；无法解析则返回 null
     */
    public Long resolveTargetUserId(Message groupMessage, Long threadId, String groupChatId) {
        if (groupMessage.replyToMessage() != null && groupMessage.replyToMessage().messageId() != null) {
            Long repliedMessageId = groupMessage.replyToMessage().messageId().longValue();
            var mapped = messageService.getMessageByForwardedMessageId(repliedMessageId);
            if (mapped.isPresent()) {
                return mapped.get().getSenderId();
            }
        }

        Optional<Topic> topic = topicService.getTopicByTopicId(threadId);
        if (topic.isPresent() && groupChatId.equals(topic.get().getChatId())) {
            return topic.get().getUserId();
        }

        return null;
    }

    /**
     * 将群内“主人回复”的 replyTo 关系映射为用户侧的 replyToMessageId。
     *
     * @param groupMessage 群内话题消息
     * @return 用户侧 replyToMessageId；无法映射则返回 null
     */
    public Integer resolveUserReplyToMessageId(Message groupMessage) {
        if (groupMessage.replyToMessage() == null || groupMessage.replyToMessage().messageId() == null) {
            return null;
        }

        Long repliedMessageId = groupMessage.replyToMessage().messageId().longValue();
        var mapped = messageService.getMessageByForwardedMessageId(repliedMessageId);
        if (mapped.isEmpty()) {
            return null;
        }

        Long originalMessageId = mapped.get().getOriginalMessageId();
        if (originalMessageId == null) {
            return null;
        }

        if (originalMessageId > Integer.MAX_VALUE) {
            return null;
        }
        return originalMessageId.intValue();
    }

    /**
     * 将用户侧“回复”的 replyTo 映射为群内“主人回复”的 replyToMessageId。
     *
     * @param privateMessage    用户侧消息
     * @return                  群内“主人回复”的 replyToMessageId；无法映射则返回 null
     */
    public Integer resolveGroupReplyToMessageId(Message privateMessage) {
        if (privateMessage.replyToMessage() == null || privateMessage.replyToMessage().messageId() == null) {
            return null;
        }

        Long repliedMessageId = privateMessage.replyToMessage().messageId().longValue();

        var mapped = messageService.getMessageByOriginalMessageId(repliedMessageId);
        if (mapped.isEmpty()) {
            mapped = messageService.getMessageByForwardedMessageId(repliedMessageId);
            if (mapped.isEmpty()) {
                return null;
            }
        }

        var entity = mapped.get();
        Long targetMessageId;
        if (entity.getMessageType() == MessageType.USER_MESSAGE) {
            targetMessageId = entity.getForwardedMessageId();
        } else if (entity.getMessageType() == MessageType.OWNER_MESSAGE) {
            targetMessageId = entity.getOriginalMessageId();
        } else {
            targetMessageId = null;
        }

        if (targetMessageId == null || targetMessageId > Integer.MAX_VALUE) {
            return null;
        }
        return targetMessageId.intValue();
    }
}
