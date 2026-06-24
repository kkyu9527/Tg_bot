package com.kixyu.tgbot.service.message;

import com.kixyu.tgbot.domain.entity.Message;
import com.kixyu.tgbot.domain.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

    /**
     * 保存一条消息实体到数据库。
     *
     * @param message 消息实体
     * @return 持久化后的消息实体
     */
    @Override
    public Message saveMessage(Message message) {
        return messageRepository.save(message);
    }

    /**
     * 根据话题 ID 查询该话题下的所有消息。
     *
     * @param topicId 话题 ID
     * @return 消息列表
     */
    @Override
    public List<Message> getMessagesByTopicId(Long topicId) {
        return messageRepository.findByTopicId(topicId);
    }

    /**
     * 根据话题 ID 和内容类型查询消息。
     *
     * @param topicId     话题 ID
     * @param contentType 内容类型
     * @return 消息列表
     */
    @Override
    public List<Message> getMessagesByTopicIdAndContentType(Long topicId, Message.ContentType contentType) {
        return messageRepository.findByTopicIdAndContentType(topicId, contentType);
    }

    /**
     * 根据话题 ID 和消息类型查询消息。
     *
     * @param topicId     话题 ID
     * @param messageType 消息类型
     * @return 消息列表
     */
    @Override
    public List<Message> getMessagesByTopicIdAndMessageType(Long topicId, Message.MessageType messageType) {
        return messageRepository.findByTopicIdAndMessageType(topicId, messageType);
    }

    /**
     * 根据原始消息 ID 查询消息。
     *
     * @param originalMessageId 原始消息 ID
     * @return 匹配的消息，可为空
     */
    @Override
    public Optional<Message> getMessageByOriginalMessageId(Long originalMessageId) {
        return messageRepository.findByOriginalMessageId(originalMessageId);
    }

    /**
     * 根据机器人转发消息 ID 查询消息。
     *
     * @param forwardedMessageId 机器人转发后的消息 ID
     * @return 匹配的消息，可为空
     */
    @Override
    public Optional<Message> getMessageByForwardedMessageId(Long forwardedMessageId) {
        return messageRepository.findByForwardedMessageId(forwardedMessageId);
    }

    /**
     * 根据话题 ID 和发送者 ID 查询消息。
     *
     * @param topicId  话题 ID
     * @param senderId 发送者 ID
     * @return 消息列表
     */
    @Override
    public List<Message> getMessagesByTopicIdAndSenderId(Long topicId, Long senderId) {
        return messageRepository.findByTopicIdAndSenderId(topicId, senderId);
    }

    /**
     * 统计指定话题中某个发送者的指定类型消息数量。
     *
     * @param topicId     话题 ID
     * @param senderId    发送者 ID
     * @param messageType 消息类型
     * @return            消息数量
     */
    @Override
    public long countMessagesByTopicIdAndSenderIdAndMessageType(Long topicId, Long senderId, Message.MessageType messageType) {
        return messageRepository.countByTopicIdAndSenderIdAndMessageType(topicId, senderId, messageType);
    }

    /**
     * 根据媒体组 ID 查询消息。
     *
     * @param mediaGroupId 媒体组 ID
     * @return 消息列表
     */
    @Override
    public List<Message> getMessagesByMediaGroupId(String mediaGroupId) {
        return messageRepository.findByMediaGroupId(mediaGroupId);
    }

    /**
     * 根据内容类型查询消息。
     *
     * @param contentType 内容类型
     * @return 消息列表
     */
    @Override
    public List<Message> getMessagesByContentType(Message.ContentType contentType) {
        return messageRepository.findByContentType(contentType);
    }

    /**
     * 删除指定话题下的所有消息。
     *
     * @param topicId 话题 ID
     */
    @Override
    public void deleteMessagesByTopicId(Long topicId) {
        messageRepository.deleteByTopicId(topicId);
    }

    /**
     * 删除指定话题中某个发送者的所有消息。
     *
     * @param topicId  话题 ID
     * @param senderId 发送者 ID
     */
    @Override
    public void deleteMessagesByTopicIdAndSenderId(Long topicId, Long senderId) {
        messageRepository.deleteByTopicIdAndSenderId(topicId, senderId);
    }

    /**
     * 删除指定媒体组的所有消息。
     *
     * @param mediaGroupId 媒体组 ID
     */
    @Override
    public void deleteMessagesByMediaGroupId(String mediaGroupId) {
        messageRepository.deleteByMediaGroupId(mediaGroupId);
    }

    /**
     * 创建一条普通消息实体并保存。
     * 该实体记录了话题、发送者、原始消息 ID 与机器人转发消息 ID 的映射关系。
     *
     * @param topicId            话题 ID
     * @param messageType        消息类型
     * @param contentType        内容类型
     * @param senderId           发送者 ID
     * @param senderUsername     发送者用户名
     * @param senderFirstName    发送者名
     * @param senderLastName     发送者姓
     * @param originalMessageId  原始消息 ID
     * @param forwardedMessageId 机器人转发后的消息 ID
     * @return 持久化后的消息实体
     */
    @Override
    public Message createMessage(Long topicId, Message.MessageType messageType, Message.ContentType contentType,
                                 Long senderId, String senderUsername, String senderFirstName, String senderLastName,
                                 Long originalMessageId, Long forwardedMessageId) {

        Message message = Message.builder()
                .topicId(topicId)
                .messageType(messageType)
                .contentType(contentType)
                .senderId(senderId)
                .senderUsername(senderUsername)
                .senderFirstName(senderFirstName)
                .senderLastName(senderLastName)
                .originalMessageId(originalMessageId)
                .forwardedMessageId(forwardedMessageId)
                .build();

        return saveMessage(message);
    }

    /**
     * 创建一条媒体组消息实体并保存。
     * 该实体记录了话题、发送者、媒体组 ID 以及消息 ID 映射关系。
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
     * @param forwardedMessageId 机器人转发后的消息 ID
     * @return 持久化后的消息实体
     */
    @Override
    public Message createMediaGroupMessage(Long topicId, Message.MessageType messageType, Message.ContentType contentType,
                                           String mediaGroupId,
                                           Long senderId, String senderUsername, String senderFirstName, String senderLastName,
                                           Long originalMessageId, Long forwardedMessageId) {

        Message message = Message.builder()
                .topicId(topicId)
                .messageType(messageType)
                .contentType(contentType)
                .mediaGroupId(mediaGroupId)
                .senderId(senderId)
                .senderUsername(senderUsername)
                .senderFirstName(senderFirstName)
                .senderLastName(senderLastName)
                .originalMessageId(originalMessageId)
                .forwardedMessageId(forwardedMessageId)
                .build();

        return saveMessage(message);
    }
}
