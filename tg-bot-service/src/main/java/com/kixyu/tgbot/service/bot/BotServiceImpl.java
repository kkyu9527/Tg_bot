package com.kixyu.tgbot.service.bot;

import com.kixyu.tgbot.domain.entity.Message;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.common.BotCommonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.pengrad.telegrambot.model.User;

@Service
@RequiredArgsConstructor
@Slf4j
class BotServiceImpl implements BotService {

    private final BotCommonService botCommonService;

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
    @Override
    public void handleUserMediaGroupMessage(User user, String mediaGroupId, Message.ContentType contentType, String chatId, Long userMessageId, Long botForwardedMessageId) {
        log.info("处理用户 {} 的媒体组消息: {}", user.username(), mediaGroupId);

        Topic topic = botCommonService.getOrCreateUserTopic(user, chatId);

        botCommonService.createUserMediaGroupMessage(user, mediaGroupId, contentType, topic.getTopicId(), userMessageId, botForwardedMessageId);

        log.info("成功处理用户媒体组消息，话题ID: {}", topic.getTopicId());
    }

    /**
     * 处理用户发送的普通消息，并记录私聊消息与群话题消息之间的映射。
     *
     * @param user                  发送消息的用户
     * @param contentType           消息内容类型
     * @param chatId                群聊 ID
     * @param userMessageId         用户私聊中的原始消息 ID
     * @param botForwardedMessageId 机器人转发到群话题后的消息 ID
     */
    @Override
    public void handleUserMessage(User user, Message.ContentType contentType, String chatId, Long userMessageId, Long botForwardedMessageId) {
        log.info("处理用户 {} 的消息，类型: {}", user.username(), contentType);

        Topic topic = botCommonService.getOrCreateUserTopic(user, chatId);

        botCommonService.createUserMessage(user, contentType, topic.getTopicId(), userMessageId, botForwardedMessageId);

        log.info("成功处理用户消息，话题ID: {}", topic.getTopicId());
    }

    /**
     * 处理主人在群话题中的回复，并记录群话题消息与用户私聊消息之间的映射。
     *
     * @param owner                 主人用户
     * @param contentType           消息内容类型
     * @param topicId               话题 ID
     * @param originalMessageId     群话题中的原始消息 ID
     * @param forwardedMessageId    回流到用户私聊后的消息 ID
     */
    @Override
    public void handleOwnerReplyInTopic(User owner, Message.ContentType contentType, Long topicId, Long originalMessageId, Long forwardedMessageId) {
        log.info("处理主人 {} 在话题 {} 中的回复，类型: {}", owner.username(), topicId, contentType);

        botCommonService.createOwnerReplyMessage(owner, contentType, topicId, originalMessageId, forwardedMessageId);

        log.info("成功创建主人回复消息，话题ID: {}", topicId);
    }

    /**
     * 处理主人在群话题中的媒体组回复，并记录媒体组内每条消息的映射。
     *
     * @param owner                 主人用户
     * @param mediaGroupId          媒体组 ID
     * @param contentType           消息内容类型
     * @param topicId               话题 ID
     * @param originalMessageId     群话题中的原始消息 ID
     * @param forwardedMessageId    回流到用户私聊后的消息 ID
     */
    @Override
    public void handleOwnerMediaGroupReplyInTopic(User owner, String mediaGroupId, Message.ContentType contentType, Long topicId, Long originalMessageId, Long forwardedMessageId) {
        log.info("处理主人 {} 在话题 {} 中的媒体组回复: {}", owner.username(), topicId, mediaGroupId);

        botCommonService.createOwnerMediaGroupReplyMessage(owner, mediaGroupId, contentType, topicId, originalMessageId, forwardedMessageId);

        log.info("成功创建主人媒体组回复消息，话题ID: {}", topicId);
    }

}
