package com.kixyu.tgbot.service.message;

import com.kixyu.tgbot.domain.entity.Message;
import com.kixyu.tgbot.domain.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 消息映射数据服务实现。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

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
     * 查询指定话题中某个发送者最近一条指定类型消息。
     *
     * @param topicId     话题 ID
     * @param senderId    发送者 ID
     * @param messageType 消息类型
     * @return            最近一条消息，可为空
     */
    @Override
    public Optional<Message> getLatestMessageByTopicIdAndSenderIdAndMessageType(Long topicId, Long senderId, Message.MessageType messageType) {
        return messageRepository.findFirstByTopicIdAndSenderIdAndMessageTypeOrderByCreateTimeDesc(topicId, senderId, messageType);
    }

    /**
     * 删除指定话题下的所有消息。
     *
     * @param topicId 话题 ID
    */
    @Override
    @Transactional
    public void deleteMessagesByTopicId(Long topicId) {
        messageRepository.deleteByTopicId(topicId);
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
    */
    @Override
    @Transactional
    public void createMessage(Long topicId, Message.MessageType messageType, Message.ContentType contentType,
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

        messageRepository.save(message);
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
    */
    @Override
    @Transactional
    public void createMediaGroupMessage(Long topicId, Message.MessageType messageType, Message.ContentType contentType,
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

        messageRepository.save(message);
    }
}
