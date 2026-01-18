package com.kixyu.tgbot.service.common;

import com.kixyu.tgbot.domain.entity.Message;
import com.kixyu.tgbot.domain.entity.Topic;
import com.pengrad.telegrambot.model.User;

public interface BotCommonService {

    /**
     * 获取或创建指定用户在指定聊天中的话题。
     *
     * @param user   Telegram 用户
     * @param chatId 聊天 ID（通常为群组 ID）
     * @return 已存在或新创建的话题
     */
    Topic getOrCreateUserTopic(User user, String chatId);

    /**
     * 创建一条用户普通消息的映射记录。
     *
     * @param user                  发送消息的用户
     * @param contentType           消息内容类型
     * @param topicId               所属话题 ID
     * @param userMessageId         用户原始消息 ID
     * @param botForwardedMessageId 机器人转发到群话题后的消息 ID
     * @return 持久化后的消息实体
     */
    Message createUserMessage(User user, Message.ContentType contentType, Long topicId, Long userMessageId, Long botForwardedMessageId);

    /**
     * 创建一条用户媒体组消息的映射记录。
     *
     * @param user                  发送消息的用户
     * @param mediaGroupId          媒体组 ID
     * @param contentType           消息内容类型
     * @param topicId               所属话题 ID
     * @param userMessageId         用户原始消息 ID
     * @param botForwardedMessageId 机器人转发到群话题后的消息 ID
     * @return 持久化后的消息实体
     */
    Message createUserMediaGroupMessage(User user, String mediaGroupId, Message.ContentType contentType, Long topicId, Long userMessageId, Long botForwardedMessageId);

    /**
     * 创建一条主人回复消息的映射记录。
     *
     * @param owner              主人用户
     * @param contentType        消息内容类型
     * @param topicId            所属话题 ID
     * @param originalMessageId  原始消息 ID（主人发送的消息）
     * @param forwardedMessageId 机器人回流给用户后的消息 ID
     * @return 持久化后的消息实体
     */
    Message createOwnerReplyMessage(User owner, Message.ContentType contentType, Long topicId, Long originalMessageId, Long forwardedMessageId);

    /**
     * 创建一条主人媒体组回复消息的映射记录。
     *
     * @param owner              主人用户
     * @param mediaGroupId       媒体组 ID
     * @param contentType        消息内容类型
     * @param topicId            所属话题 ID
     * @param originalMessageId  原始消息 ID（主人发送的消息）
     * @param forwardedMessageId 机器人回流给用户后的消息 ID
     * @return 持久化后的消息实体
     */
    Message createOwnerMediaGroupReplyMessage(User owner, String mediaGroupId, Message.ContentType contentType, Long topicId, Long originalMessageId, Long forwardedMessageId);

    /**
     * 创建一条机器人转发消息的映射记录。
     *
     * @param contentType        消息内容类型
     * @param sender             原始发送者
     * @param topicId            所属话题 ID
     * @param originalMessageId  原始消息 ID
     * @param forwardedMessageId 机器人转发后的消息 ID
     * @return 持久化后的消息实体
     */
    Message createBotForwardedMessage(Message.ContentType contentType, User sender, Long topicId, Long originalMessageId, Long forwardedMessageId);

    /**
     * 创建一条机器人转发媒体组消息的映射记录。
     *
     * @param mediaGroupId       媒体组 ID
     * @param contentType        消息内容类型
     * @param sender             原始发送者
     * @param topicId            所属话题 ID
     * @param originalMessageId  原始消息 ID
     * @param forwardedMessageId 机器人转发后的消息 ID
     * @return 持久化后的消息实体
     */
    Message createBotForwardedMediaGroupMessage(String mediaGroupId, Message.ContentType contentType, User sender, Long topicId, Long originalMessageId, Long forwardedMessageId);
}
