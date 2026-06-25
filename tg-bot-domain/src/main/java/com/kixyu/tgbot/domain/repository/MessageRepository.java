package com.kixyu.tgbot.domain.repository;

import com.kixyu.tgbot.domain.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Telegram 消息映射数据仓储。
 */
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
     * 统计指定话题中某个发送者的指定类型消息数量。
     *
     * @param topicId       话题 ID
     * @param senderId      发送者 ID
     * @param messageType   消息类型
     * @return              消息数量
     */
    long countByTopicIdAndSenderIdAndMessageType(Long topicId, Long senderId, Message.MessageType messageType);

    /**
     * 查询指定话题中某个发送者最近一条指定类型消息。
     *
     * @param topicId       话题 ID
     * @param senderId      发送者 ID
     * @param messageType   消息类型
     * @return              最近一条消息
     */
    Optional<Message> findFirstByTopicIdAndSenderIdAndMessageTypeOrderByCreateTimeDesc(Long topicId, Long senderId, Message.MessageType messageType);

    /**
     * 删除指定话题下的所有消息。
     *
     * @param topicId 话题 ID
     */
    void deleteByTopicId(Long topicId);

}
