package com.kixyu.tgbot.service.message;

import com.kixyu.tgbot.domain.entity.Message;
import java.util.List;
import java.util.Optional;

public interface MessageService {

    /**
     * 根据话题ID获取消息列表
     */
    List<Message> getMessagesByTopicId(Long topicId);

    /**
     * 根据原始消息ID获取消息
     */
    Optional<Message> getMessageByOriginalMessageId(Long originalMessageId);

    /**
     * 根据机器人转发消息ID获取消息
     */
    Optional<Message> getMessageByForwardedMessageId(Long forwardedMessageId);

    /**
     * 统计指定话题中某个发送者的指定类型消息数量
     */
    long countMessagesByTopicIdAndSenderIdAndMessageType(Long topicId, Long senderId, Message.MessageType messageType);

    /**
     * 查询指定话题中某个发送者最近一条指定类型消息
     */
    Optional<Message> getLatestMessageByTopicIdAndSenderIdAndMessageType(Long topicId, Long senderId, Message.MessageType messageType);

    /**
     * 删除指定话题的所有消息
     */
    void deleteMessagesByTopicId(Long topicId);

    /**
     * 创建消息（通用）
     */
    void createMessage(Long topicId, Message.MessageType messageType, Message.ContentType contentType,
                       Long senderId, String senderUsername, String senderFirstName, String senderLastName,
                       Long originalMessageId, Long forwardedMessageId);

    /**
     * 创建媒体组消息
     */
    void createMediaGroupMessage(Long topicId, Message.MessageType messageType, Message.ContentType contentType,
                                 String mediaGroupId,
                                 Long senderId, String senderUsername, String senderFirstName, String senderLastName,
                                 Long originalMessageId, Long forwardedMessageId);
}
