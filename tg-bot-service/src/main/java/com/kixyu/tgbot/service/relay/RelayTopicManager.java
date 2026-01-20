package com.kixyu.tgbot.service.relay;

import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.TopicService;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.kixyu.tgbot.support.OnboardingSupport;
import com.kixyu.tgbot.telegram.TelegramApiErrorUtil;
import com.pengrad.telegrambot.model.ForumTopic;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.CreateForumTopic;
import com.pengrad.telegrambot.request.EditForumTopic;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.CreateForumTopicResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelayTopicManager {

    private final TelegramApiClient telegramApiClient;
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
    public Topic ensureTopic(User user, String groupChatId) {
        Optional<Topic> existing = topicService.getTopicByUserIdAndChatId(user.id(), groupChatId);
        if (existing.isPresent()) {
            Topic topic = existing.get();
            if (!isForumTopicAlive(groupChatId, topic)) {
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
    public Topic recreateTopic(User user, String groupChatId) {
        topicService.handleTopicDeletion(user.id(), groupChatId);
        return createAndPersistTopic(user, groupChatId);
    }

    private Topic createAndPersistTopic(User user, String groupChatId) {
        String topicName = Topic.generateTopicName(user.firstName(), user.lastName(), user.username(), user.id());
        Long threadId = createForumTopic(groupChatId, topicName);
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
                onboardingSupport.pinMessage(groupChatId, sentMessage.messageId());
            }
        } catch (RuntimeException e) {
            log.warn("发送新用户提示消息失败，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, threadId, e);
        }

        return topic;
    }

    /**
     * 校验话题是否仍然存在且可编辑。
     *
     * @param groupChatId 群 chatId（字符串形式）
     * @param topic 话题实体
     * @return 话题可用则返回 true，否则返回 false
     */
    private boolean isForumTopicAlive(String groupChatId, Topic topic) {
        if (topic == null || topic.getTopicId() == null || topic.getTopicId() > Integer.MAX_VALUE) {
            return false;
        }

        long groupChatIdLong;
        try {
            groupChatIdLong = Long.parseLong(groupChatId);
        } catch (NumberFormatException e) {
            return false;
        }

        String topicName = topic.getTopicName();
        if (topicName == null || topicName.isBlank()) {
            topicName = "Topic " + topic.getTopicId();
        }

        try {
            BaseResponse response = telegramApiClient.execute(new EditForumTopic(groupChatIdLong, topic.getTopicId()).name(topicName));
            return response != null && (response.isOk() || TelegramApiErrorUtil.looksLikeNotModified(response));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 在目标群组中创建一个新的论坛话题。
     *
     * @param groupChatId 群 chatId（字符串形式）
     * @param topicName 话题名称
     * @return 新话题的 messageThreadId；创建失败则返回 null
     */
    private Long createForumTopic(String groupChatId, String topicName) {
        try {
            long groupChatIdLong = Long.parseLong(groupChatId);
            CreateForumTopicResponse response = telegramApiClient.execute(new CreateForumTopic(groupChatIdLong, topicName));
            if (response == null || !response.isOk()) {
                return null;
            }
            ForumTopic forumTopic = response.forumTopic();
            return forumTopic == null ? null : forumTopic.messageThreadId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
