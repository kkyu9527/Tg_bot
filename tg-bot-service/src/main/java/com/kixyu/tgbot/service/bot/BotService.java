package com.kixyu.tgbot.service.bot;

import com.kixyu.tgbot.domain.entity.Message;
import com.pengrad.telegrambot.model.User;

/**
 * 机器人消息映射业务服务。
 */
public interface BotService {

    /**
     * 处理用户发送的普通消息，并记录私聊消息与群话题消息之间的映射。
     *
     * @param user                  发送消息的用户
     * @param contentType           消息内容类型
     * @param chatId                群聊 ID
     * @param userMessageId         用户私聊中的原始消息 ID
     * @param botForwardedMessageId 机器人转发到群话题后的消息 ID
     */
    void handleUserMessage(User user, Message.ContentType contentType, String chatId, Long userMessageId, Long botForwardedMessageId);

    /**
     * 处理用户发送的媒体组消息，并记录媒体组内每条消息的映射。
     *
     * @param user                  发送消息的用户
     * @param mediaGroupId          媒体组 ID
     * @param contentType           消息内容类型
     * @param chatId                群聊 ID
     * @param userMessageId         用户私聊中的原始消息 ID
     * @param botForwardedMessageId 机器人转发到群话题后的消息 ID
     */
    void handleUserMediaGroupMessage(User user, String mediaGroupId, Message.ContentType contentType, String chatId, Long userMessageId, Long botForwardedMessageId);

    /**
     * 处理主人在群话题中的回复，并记录群话题消息与用户私聊消息之间的映射。
     *
     * @param owner              主人用户
     * @param contentType        消息内容类型
     * @param topicId            话题 ID
     * @param originalMessageId  群话题中的原始消息 ID
     * @param forwardedMessageId 回流到用户私聊后的消息 ID
     */
    void handleOwnerReplyInTopic(User owner, Message.ContentType contentType, Long topicId, Long originalMessageId, Long forwardedMessageId);

    /**
     * 处理主人在群话题中的媒体组回复，并记录媒体组内每条消息的映射。
     *
     * @param owner              主人用户
     * @param mediaGroupId       媒体组 ID
     * @param contentType        消息内容类型
     * @param topicId            话题 ID
     * @param originalMessageId  群话题中的原始消息 ID
     * @param forwardedMessageId 回流到用户私聊后的消息 ID
     */
    void handleOwnerMediaGroupReplyInTopic(User owner, String mediaGroupId, Message.ContentType contentType, Long topicId, Long originalMessageId, Long forwardedMessageId);
}
