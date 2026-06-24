package com.kixyu.tgbot.service.message;

import com.kixyu.tgbot.domain.entity.Message;
import java.util.List;
import java.util.Optional;

public interface MessageService {

    /**
     * 保存消息
     */
    Message saveMessage(Message message);

    /**
     * 根据话题ID获取消息列表
     */
    List<Message> getMessagesByTopicId(Long topicId);

    /**
     * 根据话题ID和消息类型获取消息列表
     */
    List<Message> getMessagesByTopicIdAndMessageType(Long topicId, Message.MessageType messageType);

    /**
     * 根据话题ID和内容类型获取消息列表
     */
    List<Message> getMessagesByTopicIdAndContentType(Long topicId, Message.ContentType contentType);

    /**
     * 根据原始消息ID获取消息
     */
    Optional<Message> getMessageByOriginalMessageId(Long originalMessageId);

    /**
     * 根据机器人转发消息ID获取消息
     */
    Optional<Message> getMessageByForwardedMessageId(Long forwardedMessageId);

    /**
     * 根据话题ID和发送者ID获取消息列表
     */
    List<Message> getMessagesByTopicIdAndSenderId(Long topicId, Long senderId);

    /**
     * 根据媒体组ID获取消息列表
     */
    List<Message> getMessagesByMediaGroupId(String mediaGroupId);

    /**
     * 根据内容类型获取消息列表
     */
    List<Message> getMessagesByContentType(Message.ContentType contentType);

    /**
     * 删除指定话题的所有消息
     */
    void deleteMessagesByTopicId(Long topicId);

    /**
     * 删除指定话题和发送者的特定消息
     */
    void deleteMessagesByTopicIdAndSenderId(Long topicId, Long senderId);

    /**
     * 删除指定媒体组的所有消息
     */
    void deleteMessagesByMediaGroupId(String mediaGroupId);

    /**
     * 创建消息（通用）
     */
    Message createMessage(Long topicId, Message.MessageType messageType, Message.ContentType contentType,
                          Long senderId, String senderUsername, String senderFirstName, String senderLastName,
                          Long originalMessageId, Long forwardedMessageId);

    /**
     * 创建媒体组消息
     */
    Message createMediaGroupMessage(Long topicId, Message.MessageType messageType, Message.ContentType contentType,
                                    String mediaGroupId,
                                    Long senderId, String senderUsername, String senderFirstName, String senderLastName,
                                    Long originalMessageId, Long forwardedMessageId);
}