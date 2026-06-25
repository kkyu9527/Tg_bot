package com.kixyu.tgbot.service.relay;

import com.pengrad.telegrambot.model.Message;

/**
 * Telegram 私聊与群话题之间的消息中继服务。
 */
public interface MessageRelayService {

    /**
     * 转发用户私聊消息到群话题。
     *
     * @param privateMessage 用户私聊消息
     */
    void forwardPrivateMessageToGroupTopic(Message privateMessage);

    /**
     * 将群话题中的主人消息回流给用户。
     *
     * @param groupMessage 群话题中的消息
     */
    void relayGroupTopicMessageToUser(Message groupMessage);
}
