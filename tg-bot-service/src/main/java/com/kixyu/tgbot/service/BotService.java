package com.kixyu.tgbot.service;

import com.kixyu.tgbot.domain.entity.Message;
import com.kixyu.tgbot.domain.entity.Topic;
import com.pengrad.telegrambot.model.User;

public interface BotService {

    /**
     * 处理用户发送的普通消息，并记录私聊消息与群话题消息之间的映射。
     *
     * @param user                  发送消息的用户
     * @param contentType           消息内容类型
     * @param chatId                群聊 ID
     * @param userMessageId         用户私聊中的原始消息 ID
     * @param botForwardedMessageId 机器人转发到群话题后的消息 ID
     * @param topicId               话题 ID（可选）
     * @return 持久化后的消息实体
     */
    Message handleUserMessage(User user, Message.ContentType contentType, String chatId, Long userMessageId, Long botForwardedMessageId, Long topicId);

    /**
     * 处理用户发送的媒体组消息，并记录媒体组内每条消息的映射。
     *
     * @param user                  发送消息的用户
     * @param mediaGroupId          媒体组 ID
     * @param contentType           消息内容类型
     * @param chatId                群聊 ID
     * @param userMessageId         用户私聊中的原始消息 ID
     * @param botForwardedMessageId 机器人转发到群话题后的消息 ID
     * @param topicId               话题 ID（可选）
     * @return 持久化后的消息实体
     */
    Message handleUserMediaGroupMessage(User user, String mediaGroupId, Message.ContentType contentType, String chatId, Long userMessageId, Long botForwardedMessageId, Long topicId);

    /**
     * 处理主人在群话题中的回复，并记录群话题消息与用户私聊消息之间的映射。
     *
     * @param owner              主人用户
     * @param contentType        消息内容类型
     * @param topicId            话题 ID
     * @param chatId             群聊 ID
     * @param originalMessageId  群话题中的原始消息 ID
     * @param forwardedMessageId 回流到用户私聊后的消息 ID
     * @return 持久化后的消息实体
     */
    Message handleOwnerReplyInTopic(User owner, Message.ContentType contentType, Long topicId, String chatId, Long originalMessageId, Long forwardedMessageId);

    /**
     * 处理主人在群话题中的媒体组回复，并记录媒体组内每条消息的映射。
     *
     * @param owner              主人用户
     * @param mediaGroupId       媒体组 ID
     * @param contentType        消息内容类型
     * @param topicId            话题 ID
     * @param chatId             群聊 ID
     * @param originalMessageId  群话题中的原始消息 ID
     * @param forwardedMessageId 回流到用户私聊后的消息 ID
     * @return 持久化后的消息实体
     */
    Message handleOwnerMediaGroupReplyInTopic(User owner, String mediaGroupId, Message.ContentType contentType, Long topicId, String chatId, Long originalMessageId, Long forwardedMessageId);

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
    Message handleBotForwardedMessage(Long topicId, Message.ContentType contentType, User sender, Long originalMessageId, Long forwardedMessageId, String chatId);

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
    Message handleBotForwardedMediaGroupMessage(Long topicId, String mediaGroupId, Message.ContentType contentType, User sender, Long originalMessageId, Long forwardedMessageId, String chatId);

    /**
     * 更新话题名称。
     *
     * @param topicId      话题 ID
     * @param newTopicName 新的话题名称
     * @return 更新后的话题实体
     */
    Topic updateTopicName(Long topicId, String newTopicName);

    /**
     * 根据原始消息 ID 查找消息映射。
     *
     * @param originalMessageId 原始消息 ID
     * @return 匹配的消息实体
     */
    Message findMessageByOriginalMessageId(Long originalMessageId);

    /**
     * 根据机器人转发消息 ID 查找消息映射。
     *
     * @param forwardedMessageId 机器人转发后的消息 ID
     * @return 匹配的消息实体
     */
    Message findMessageByForwardedMessageId(Long forwardedMessageId);

    /**
     * 根据话题 ID 查找话题。
     *
     * @param topicId 话题 ID
     * @return 匹配的话题实体
     */
    Topic findTopicByTopicId(Long topicId);

    /**
     * 处理用户在指定聊天中的话题删除。
     *
     * @param userId 用户 ID
     * @param chatId 聊天 ID
     */
    void handleTopicDeletion(Long userId, String chatId);

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
    Topic recreateTopicForUser(Long userId, String chatId, String username, String firstName, String lastName, Long newTopicId);
}
