package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.domain.entity.Message;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.BotService;
import com.kixyu.tgbot.service.MessageService;
import com.kixyu.tgbot.service.TopicService;
import com.kixyu.tgbot.service.common.BotCommonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.pengrad.telegrambot.model.User;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotServiceImpl implements BotService {

    private final TopicService topicService;
    private final MessageService messageService;
    private final BotCommonService botCommonService;

    /**
     * 处理用户发送的媒体组消息，并记录媒体组内每条消息的映射。
     *
     * @param user                 发送消息的用户
     * @param mediaGroupId         媒体组 ID
     * @param contentType          消息内容类型
     * @param chatId               群聊 ID
     * @param userMessageId        用户私聊中的原始消息 ID
     * @param botForwardedMessageId 机器人转发到群话题后的消息 ID
     * @param topicId              话题 ID（可选）
     * @return 持久化后的消息实体
     */
    @Override
    public Message handleUserMediaGroupMessage(User user, String mediaGroupId, Message.ContentType contentType, String chatId, Long userMessageId, Long botForwardedMessageId, Long topicId) {
        log.info("处理用户 {} 的媒体组消息: {}", user.username(), mediaGroupId);

        Topic topic = botCommonService.getOrCreateUserTopic(user, chatId);

        Message message = botCommonService.createUserMediaGroupMessage(user, mediaGroupId, contentType, topic.getTopicId(), userMessageId, botForwardedMessageId);

        log.info("成功处理用户媒体组消息，话题ID: {}", topic.getTopicId());
        return message;
    }

    /**
     * 处理用户发送的普通消息，并记录私聊消息与群话题消息之间的映射。
     *
     * @param user                 发送消息的用户
     * @param contentType          消息内容类型
     * @param chatId               群聊 ID
     * @param userMessageId        用户私聊中的原始消息 ID
     * @param botForwardedMessageId 机器人转发到群话题后的消息 ID
     * @param topicId              话题 ID（可选，最终以映射中的 topicId 为准）
     * @return 持久化后的消息实体
     */
    @Override
    public Message handleUserMessage(User user, Message.ContentType contentType, String chatId, Long userMessageId, Long botForwardedMessageId, Long topicId) {
        log.info("处理用户 {} 的消息，类型: {}", user.username(), contentType);

        Topic topic = botCommonService.getOrCreateUserTopic(user, chatId);

        Message message = botCommonService.createUserMessage(user, contentType, topic.getTopicId(), userMessageId, botForwardedMessageId);

        log.info("成功处理用户消息，话题ID: {}", topic.getTopicId());
        return message;
    }

    /**
     * 处理主人在群话题中的回复，并记录群话题消息与用户私聊消息之间的映射。
     *
     * @param owner             主人用户
     * @param contentType       消息内容类型
     * @param topicId           话题 ID
     * @param chatId            群聊 ID
     * @param originalMessageId 群话题中的原始消息 ID
     * @param forwardedMessageId 回流到用户私聊后的消息 ID
     * @return 持久化后的消息实体
     */
    @Override
    public Message handleOwnerReplyInTopic(User owner, Message.ContentType contentType, Long topicId, String chatId, Long originalMessageId, Long forwardedMessageId) {
        log.info("处理主人 {} 在话题 {} 中的回复，类型: {}", owner.username(), topicId, contentType);

        Message message = botCommonService.createOwnerReplyMessage(owner, contentType, topicId, originalMessageId, forwardedMessageId);

        log.info("成功创建主人回复消息，话题ID: {}", topicId);
        return message;
    }

    /**
     * 处理主人在群话题中的媒体组回复，并记录媒体组内每条消息的映射。
     *
     * @param owner             主人用户
     * @param mediaGroupId      媒体组 ID
     * @param contentType       消息内容类型
     * @param topicId           话题 ID
     * @param chatId            群聊 ID
     * @param originalMessageId 群话题中的原始消息 ID
     * @param forwardedMessageId 回流到用户私聊后的消息 ID
     * @return 持久化后的消息实体
     */
    @Override
    public Message handleOwnerMediaGroupReplyInTopic(User owner, String mediaGroupId, Message.ContentType contentType, Long topicId, String chatId, Long originalMessageId, Long forwardedMessageId) {
        log.info("处理主人 {} 在话题 {} 中的媒体组回复: {}", owner.username(), topicId, mediaGroupId);

        Message message = botCommonService.createOwnerMediaGroupReplyMessage(owner, mediaGroupId, contentType, topicId, originalMessageId, forwardedMessageId);

        log.info("成功创建主人媒体组回复消息，话题ID: {}", topicId);
        return message;
    }

    /**
     * 处理机器人以「转发」形式发送的普通消息，并记录映射。
     *
     * @param topicId            话题 ID
     * @param contentType        消息内容类型
     * @param sender             原始发送者
     * @param originalMessageId  原始消息 ID
     * @param forwardedMessageId 机器人转发后的消息 ID
     * @param chatId             聊天 ID
     * @return 持久化后的消息实体
     */
    @Override
    public Message handleBotForwardedMessage(Long topicId, Message.ContentType contentType, User sender, Long originalMessageId, Long forwardedMessageId, String chatId) {
        log.info("处理机器人转发消息，话题ID: {}, 原始消息ID: {}, 类型: {}", topicId, originalMessageId, contentType);

        Message message = botCommonService.createBotForwardedMessage(contentType, sender, topicId, originalMessageId, forwardedMessageId);

        log.info("成功创建机器人转发消息，话题ID: {}", topicId);
        return message;
    }

    /**
     * 处理机器人以「转发」形式发送的媒体组消息，并记录映射。
     *
     * @param topicId            话题 ID
     * @param mediaGroupId       媒体组 ID
     * @param contentType        消息内容类型
     * @param sender             原始发送者
     * @param originalMessageId  原始消息 ID
     * @param forwardedMessageId 机器人转发后的消息 ID
     * @param chatId             聊天 ID
     * @return 持久化后的消息实体
     */
    @Override
    public Message handleBotForwardedMediaGroupMessage(Long topicId, String mediaGroupId, Message.ContentType contentType, User sender, Long originalMessageId, Long forwardedMessageId, String chatId) {
        log.info("处理机器人转发媒体组消息，话题ID: {}, 原始消息ID: {}, 媒体组ID: {}", topicId, originalMessageId, mediaGroupId);

        Message message = botCommonService.createBotForwardedMediaGroupMessage(mediaGroupId, contentType, sender, topicId, originalMessageId, forwardedMessageId);

        log.info("成功创建机器人转发媒体组消息，话题ID: {}", topicId);
        return message;
    }

    /**
     * 更新话题名称。
     *
     * @param topicId      话题 ID
     * @param newTopicName 新的话题名称
     * @return 更新后的话题实体
     */
    @Override
    public Topic updateTopicName(Long topicId, String newTopicName) {
        // 获取现有话题
        Topic topic = topicService.getTopicByTopicId(topicId)
                .orElseThrow(() -> new RuntimeException("话题不存在: " + topicId));

        // 更新话题名称
        topic.setTopicName(newTopicName);

        return topicService.saveTopic(topic);
    }

    /**
     * 根据原始消息 ID 查找消息映射。
     *
     * @param originalMessageId 原始消息 ID
     * @return 匹配的消息实体
     */
    @Override
    public Message findMessageByOriginalMessageId(Long originalMessageId) {
        return messageService.getMessageByOriginalMessageId(originalMessageId)
                .orElseThrow(() -> new RuntimeException("未找到对应消息: " + originalMessageId));
    }

    /**
     * 根据机器人转发消息 ID 查找消息映射。
     *
     * @param forwardedMessageId 机器人转发后的消息 ID
     * @return 匹配的消息实体
     */
    @Override
    public Message findMessageByForwardedMessageId(Long forwardedMessageId) {
        return messageService.getMessageByForwardedMessageId(forwardedMessageId)
                .orElseThrow(() -> new RuntimeException("未找到对应消息: " + forwardedMessageId));
    }

    /**
     * 根据话题 ID 查找话题。
     *
     * @param topicId 话题 ID
     * @return 匹配的话题实体
     */
    @Override
    public Topic findTopicByTopicId(Long topicId) {
        return topicService.getTopicByTopicId(topicId)
                .orElseThrow(() -> new RuntimeException("未找到对应话题: " + topicId));
    }

    /**
     * 处理用户在指定聊天中的话题删除。
     *
     * @param userId 用户 ID
     * @param chatId 聊天 ID
     */
    @Override
    public void handleTopicDeletion(Long userId, String chatId) {
        log.info("处理用户 {} 在聊天 {} 中话题的删除", userId, chatId);
        topicService.handleTopicDeletion(userId, chatId);
    }

    /**
     * 为用户在指定聊天中重新创建话题。
     *
     * @param userId    用户 ID
     * @param chatId    聊天 ID
     * @param username  用户名
     * @param firstName 名
     * @param lastName  姓
     * @param newTopicId 新的话题 ID
     * @return 新创建的话题实体
     */
    @Override
    public Topic recreateTopicForUser(Long userId, String chatId, String username, String firstName, String lastName, Long newTopicId) {
        log.info("为用户 {} 在聊天 {} 中重新创建话题，新话题ID: {}", userId, chatId, newTopicId);
        return topicService.recreateTopic(userId, chatId, username, firstName, lastName, newTopicId);
    }
}
