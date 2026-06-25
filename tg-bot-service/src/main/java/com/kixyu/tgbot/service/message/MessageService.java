package com.kixyu.tgbot.service.message;

import com.kixyu.tgbot.domain.entity.Message;
import java.util.List;
import java.util.Optional;

/**
 * 消息映射数据服务。
 */
public interface MessageService {

    /**
     * 根据话题 ID 获取消息列表。
     *
     * @param topicId 话题 ID
     * @return        消息列表
     */
    List<Message> getMessagesByTopicId(Long topicId);

    /**
     * 根据原始消息 ID 获取消息。
     *
     * @param originalMessageId 原始消息 ID
     * @return                  匹配的消息（如果存在）
     */
    Optional<Message> getMessageByOriginalMessageId(Long originalMessageId);

    /**
     * 根据机器人转发消息 ID 获取消息。
     *
     * @param forwardedMessageId 机器人转发后的消息 ID
     * @return                   匹配的消息（如果存在）
     */
    Optional<Message> getMessageByForwardedMessageId(Long forwardedMessageId);

    /**
     * 统计指定话题中某个发送者的指定类型消息数量。
     *
     * @param topicId     话题 ID
     * @param senderId    发送者 ID
     * @param messageType 消息类型
     * @return            消息数量
     */
    long countMessagesByTopicIdAndSenderIdAndMessageType(Long topicId, Long senderId, Message.MessageType messageType);

    /**
     * 查询指定话题中某个发送者最近一条指定类型消息。
     *
     * @param topicId     话题 ID
     * @param senderId    发送者 ID
     * @param messageType 消息类型
     * @return            最近一条消息（如果存在）
     */
    Optional<Message> getLatestMessageByTopicIdAndSenderIdAndMessageType(Long topicId, Long senderId, Message.MessageType messageType);

    /**
     * 删除指定话题的所有消息。
     *
     * @param topicId 话题 ID
     */
    void deleteMessagesByTopicId(Long topicId);

    /**
     * 创建普通消息映射。
     *
     * @param topicId            话题 ID
     * @param messageType        消息类型
     * @param contentType        内容类型
     * @param senderId           发送者 ID
     * @param senderUsername     发送者用户名
     * @param senderFirstName    发送者名
     * @param senderLastName     发送者姓
     * @param originalMessageId  原始消息 ID
     * @param forwardedMessageId 转发后的消息 ID
     */
    void createMessage(Long topicId, Message.MessageType messageType, Message.ContentType contentType,
                       Long senderId, String senderUsername, String senderFirstName, String senderLastName,
                       Long originalMessageId, Long forwardedMessageId);

    /**
     * 创建媒体组消息映射。
     *
     * @param topicId            话题 ID
     * @param messageType        消息类型
     * @param contentType        内容类型
     * @param mediaGroupId       媒体组 ID
     * @param senderId           发送者 ID
     * @param senderUsername     发送者用户名
     * @param senderFirstName    发送者名
     * @param senderLastName     发送者姓
     * @param originalMessageId  原始消息 ID
     * @param forwardedMessageId 转发后的消息 ID
     */
    void createMediaGroupMessage(Long topicId, Message.MessageType messageType, Message.ContentType contentType,
                                 String mediaGroupId,
                                 Long senderId, String senderUsername, String senderFirstName, String senderLastName,
                                 Long originalMessageId, Long forwardedMessageId);
}
