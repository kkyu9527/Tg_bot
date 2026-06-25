package com.kixyu.tgbot.service.relay;

import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.topic.TopicService;
import com.kixyu.tgbot.support.OnboardingSupport;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 转发链路中的用户话题获取与重建管理器。
 */
@Service
@RequiredArgsConstructor
@Slf4j
class RelayTopicManager {

    private final TopicService topicService;
    private final OnboardingSupport onboardingSupport;

    /**
     * 获取或创建用户在目标群组中的话题映射。
     *
     * <p>若已存在映射，会周期性校验话题是否仍然存在；若话题已失效则会重建。</p>
     *
     * @param user 用户
     * @param groupChatId 群 chatId（字符串形式）
     * @return 话题实体；创建失败则返回 null
     */
    Topic ensureTopic(User user, String groupChatId) {
        Optional<Topic> existing = topicService.getTopicByUserIdAndChatId(user.id(), groupChatId);
        if (existing.isPresent()) {
            Topic topic = existing.get();
            if (onboardingSupport.isForumTopicMissing(groupChatId, topic)) {
                log.warn("检测到群话题不存在，准备重建话题并清理遗留数据，userId={}, groupChatId={}, topicId={}", user.id(), groupChatId, topic.getTopicId());
                return recreateTopic(user, groupChatId);
            }
            return topic;
        }

        return createAndPersistTopic(user, groupChatId);
    }

    /**
     * 清理旧映射并为用户重建群话题。
     *
     * @param user 用户
     * @param groupChatId 群 chatId（字符串形式）
     * @return 新的话题实体；创建失败则返回 null
     */
    Topic recreateTopic(User user, String groupChatId) {
        topicService.handleTopicDeletion(user.id(), groupChatId);
        return createAndPersistTopic(user, groupChatId);
    }

    /**
     * 创建群话题并保存本地映射。
     *
     * @param user        用户
     * @param groupChatId 群 chatId（字符串形式）
     * @return            新建的话题实体；创建失败则返回 null
     */
    private Topic createAndPersistTopic(User user, String groupChatId) {
        String topicName = Topic.generateTopicName(user.firstName(), user.lastName(), user.username(), user.id());
        Long threadId = onboardingSupport.createForumTopic(groupChatId, topicName);
        if (threadId == null) {
            return null;
        }
        Topic topic = topicService.createTopic(
                user.id(),
                user.username(),
                user.firstName(),
                user.lastName(),
                threadId,
                groupChatId
        );

        try {
            String caption = onboardingSupport.buildNewUserCaption(user);
            Message sentMessage = onboardingSupport.sendNewUserMessageToTopic(groupChatId, threadId, user, caption);
            if (sentMessage != null && sentMessage.messageId() != null) {
                topic.setWelcomeMessageId(sentMessage.messageId().longValue());
                topicService.saveTopic(topic);
                onboardingSupport.pinMessage(groupChatId, sentMessage.messageId());
            }
        } catch (RuntimeException e) {
            log.warn("发送新用户提示消息失败，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, threadId, e);
        }

        return topic;
    }
}
