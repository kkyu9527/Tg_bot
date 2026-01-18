package com.kixyu.tgbot.domain.repository;

import com.kixyu.tgbot.domain.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // 根据话题ID查找消息
    List<Message> findByTopicId(Long topicId);

    // 根据话题ID和消息类型查找消息
    List<Message> findByTopicIdAndMessageType(Long topicId, Message.MessageType messageType);

    // 根据话题ID和内容类型查找消息
    List<Message> findByTopicIdAndContentType(Long topicId, Message.ContentType contentType);

    // 根据原始消息ID查找消息
    Optional<Message> findByOriginalMessageId(Long originalMessageId);

    // 根据机器人转发消息ID查找消息
    Optional<Message> findByForwardedMessageId(Long forwardedMessageId);

    // 根据原始消息ID、发送者ID和消息类型查找消息
    Optional<Message> findByOriginalMessageIdAndSenderIdAndMessageType(Long originalMessageId, Long senderId, Message.MessageType messageType);

    // 根据话题ID和发送者ID查找消息
    List<Message> findByTopicIdAndSenderId(Long topicId, Long senderId);

    // 根据媒体组ID查找消息
    List<Message> findByMediaGroupId(String mediaGroupId);

    // 根据内容类型查找消息
    List<Message> findByContentType(Message.ContentType contentType);

    // 删除指定话题的所有消息
    void deleteByTopicId(Long topicId);

    // 删除指定话题和发送者的特定消息
    void deleteByTopicIdAndSenderId(Long topicId, Long senderId);

    // 删除指定媒体组的所有消息
    void deleteByMediaGroupId(String mediaGroupId);
}
