package com.kixyu.tgbot.service.common;

import com.kixyu.tgbot.domain.entity.Message;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.message.MessageService;
import com.kixyu.tgbot.service.topic.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.pengrad.telegrambot.model.User;

/**
 * 机器人通用消息与话题操作服务实现。
 */
@Service
@RequiredArgsConstructor
class BotCommonServiceImpl implements BotCommonService {

    private final TopicService topicService;
    private final MessageService messageService;

    /**
     * 获取或创建用户在指定聊天中的话题。
     *
     * @param user   Telegram 用户
     * @param chatId 聊天 ID（通常为群组 ID）
     * @return 已存在或新创建的话题
     */
    @Override
    public Topic getOrCreateUserTopic(User user, String chatId) {
        return topicService.getOrCreateTopicByUserAndChat(
                user.id(),
                chatId,
                user.username(),
                user.firstName(),
                user.lastName()
        );
    }

    /**
     * 创建并持久化一条用户普通消息的映射记录。
     *
     * @param user                  发送消息的用户
     * @param contentType           消息内容类型
     * @param topicId               所属话题 ID
     * @param userMessageId         用户原始消息 ID
     * @param botForwardedMessageId 机器人转发到群话题后的消息 ID
     */
    @Override
    public void createUserMessage(User user, Message.ContentType contentType, Long topicId, Long userMessageId, Long botForwardedMessageId) {
        messageService.createMessage(
                topicId,
                Message.MessageType.USER_MESSAGE,
                contentType,
                user.id(),
                user.username(),
                user.firstName(),
                user.lastName(),
                userMessageId,
                botForwardedMessageId
        );
    }

    /**
     * 创建并持久化一条用户媒体组消息的映射记录。
     *
     * @param user                  发送消息的用户
     * @param mediaGroupId          媒体组 ID
     * @param contentType           消息内容类型
     * @param topicId               所属话题 ID
     * @param userMessageId         用户原始消息 ID
     * @param botForwardedMessageId 机器人转发到群话题后的消息 ID
     */
    @Override
    public void createUserMediaGroupMessage(User user, String mediaGroupId, Message.ContentType contentType, Long topicId, Long userMessageId, Long botForwardedMessageId) {
        messageService.createMediaGroupMessage(
                topicId,
                Message.MessageType.USER_MESSAGE,
                contentType,
                mediaGroupId,
                user.id(),
                user.username(),
                user.firstName(),
                user.lastName(),
                userMessageId,
                botForwardedMessageId
        );
    }

    /**
     * 创建并持久化一条主人回复消息的映射记录。
     *
     * @param owner              主人用户
     * @param contentType        消息内容类型
     * @param topicId            所属话题 ID
     * @param originalMessageId  原始消息 ID（主人发送的消息）
     * @param forwardedMessageId 机器人回流给用户后的消息 ID
     */
    @Override
    public void createOwnerReplyMessage(User owner, Message.ContentType contentType, Long topicId, Long originalMessageId, Long forwardedMessageId) {
        messageService.createMessage(
                topicId,
                Message.MessageType.OWNER_MESSAGE,
                contentType,
                owner.id(),
                owner.username(),
                owner.firstName(),
                owner.lastName(),
                originalMessageId,
                forwardedMessageId
        );
    }

    /**
     * 创建并持久化一条主人媒体组回复消息的映射记录。
     *
     * @param owner              主人用户
     * @param mediaGroupId       媒体组 ID
     * @param contentType        消息内容类型
     * @param topicId            所属话题 ID
     * @param originalMessageId  原始消息 ID（主人发送的消息）
     * @param forwardedMessageId 机器人回流给用户后的消息 ID
     */
    @Override
    public void createOwnerMediaGroupReplyMessage(User owner, String mediaGroupId, Message.ContentType contentType, Long topicId, Long originalMessageId, Long forwardedMessageId) {
        messageService.createMediaGroupMessage(
                topicId,
                Message.MessageType.OWNER_MESSAGE,
                contentType,
                mediaGroupId,
                owner.id(),
                owner.username(),
                owner.firstName(),
                owner.lastName(),
                originalMessageId,
                forwardedMessageId
        );
    }

}
