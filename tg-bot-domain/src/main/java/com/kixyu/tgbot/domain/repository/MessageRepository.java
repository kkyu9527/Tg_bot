package com.kixyu.tgbot.domain.repository;

import com.kixyu.tgbot.domain.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * 根据话题 ID 查询消息列表。
     *
     * @param topicId   话题 ID
     * @return          消息列表
     */
    List<Message> findByTopicId(Long topicId);

    /**
     * 根据话题 ID 和消息类型查询消息列表。
     *
     * @param topicId       话题 ID
     * @param messageType   消息类型
     * @return              消息列表
     */
    List<Message> findByTopicIdAndMessageType(Long topicId, Message.MessageType messageType);

    /**
     * 根据话题 ID 和内容类型查询消息列表。
     *
     * @param topicId       话题 ID
     * @param contentType   内容类型
     * @return              消息列表
     */
    List<Message> findByTopicIdAndContentType(Long topicId, Message.ContentType contentType);

    /**
     * 根据原始消息 ID 查询消息。
     *
     * @param originalMessageId 原始消息 ID
     * @return                  匹配的消息（如果存在）
     */
    Optional<Message> findByOriginalMessageId(Long originalMessageId);

    /**
     * 根据机器人转发消息 ID 查询消息。
     *
     * @param forwardedMessageId    机器人转发后的消息 ID
     * @return                      匹配的消息（如果存在）
     */
    Optional<Message> findByForwardedMessageId(Long forwardedMessageId);

    /**
     * 根据原始消息 ID、发送者 ID 和消息类型查询消息。
     *
     * @param originalMessageId 原始消息 ID
     * @param senderId          发送者 ID
     * @param messageType       消息类型
     * @return                  匹配的消息（如果存在）
     */
    Optional<Message> findByOriginalMessageIdAndSenderIdAndMessageType(Long originalMessageId, Long senderId, Message.MessageType messageType);

    /**
     * 根据话题 ID 和发送者 ID查询消息列表。
     *
     * @param topicId   话题 ID
     * @param senderId  发送者 ID
     * @return          消息列表
     */
    List<Message> findByTopicIdAndSenderId(Long topicId, Long senderId);

    /**
     * 统计指定话题中某个发送者的指定类型消息数量。
     *
     * @param topicId       话题 ID
     * @param senderId      发送者 ID
     * @param messageType   消息类型
     * @return              消息数量
     */
    long countByTopicIdAndSenderIdAndMessageType(Long topicId, Long senderId, Message.MessageType messageType);

    /**
     * 根据媒体组 ID 查询消息列表。
     *
     * @param mediaGroupId  媒体组 ID
     * @return              消息列表
     */
    List<Message> findByMediaGroupId(String mediaGroupId);

    /**
     * 根据内容类型查询消息列表。
     *
     * @param contentType   内容类型
     * @return              消息列表
     */
    List<Message> findByContentType(Message.ContentType contentType);

    /**
     * 删除指定话题下的所有消息。
     *
     * @param topicId 话题 ID
     */
    void deleteByTopicId(Long topicId);

    /**
     * 删除指定话题中某个发送者的所有消息。
     *
     * @param topicId  话题 ID
     * @param senderId 发送者 ID
     */
    void deleteByTopicIdAndSenderId(Long topicId, Long senderId);

    /**
     * 删除指定媒体组下的所有消息。
     *
     * @param mediaGroupId 媒体组 ID
     */
    void deleteByMediaGroupId(String mediaGroupId);
}
