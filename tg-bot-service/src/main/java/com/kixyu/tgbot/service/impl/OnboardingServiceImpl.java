package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.OnboardingService;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.kixyu.tgbot.support.OnboardingSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingServiceImpl implements OnboardingService {

    private final OnboardingSupport onboardingSupport;

    /**
     * 处理用户在私聊中发送的 /start 命令。
     * 负责发送欢迎消息、在群组中创建或恢复用户话题并发送提示信息。
     *
     * @param user          触发命令的用户
     * @param privateChatId 用户私聊窗口的聊天 ID
     */
    @Override
    public void handleStart(User user, Long privateChatId) {
        try {
            log.info("处理 /start，userId={}, privateChatId={}, username={}", user.id(), privateChatId, user.username());
            try {
                onboardingSupport.sendWelcomeToUser(user, privateChatId);
                log.info("已发送欢迎消息，userId={}, privateChatId={}", user.id(), privateChatId);
            } catch (RuntimeException e) {
                log.warn("发送欢迎消息失败，userId={}, privateChatId={}", user.id(), privateChatId, e);
            }

            Long groupId = onboardingSupport.getGroupId();
            if (groupId == null || groupId == 0L) {
                log.warn("未配置群组 groupId，跳过创建话题，userId={}", user.id());
                return;
            }

            String groupChatId = String.valueOf(groupId);
            Optional<Topic> existing = onboardingSupport.getTopicByUserIdAndChatId(user.id(), groupChatId);
            if (existing.isPresent()) {
                Topic existingTopic = existing.get();
                if (onboardingSupport.isPlaceholderTopicId(existingTopic.getTopicId())) {
                    log.warn("检测到占位 topicId，准备重建话题并修复映射，userId={}, groupChatId={}, topicId={}",
                            user.id(), groupChatId, existingTopic.getTopicId());
                    onboardingSupport.recreateAndUpdateTopic(user, groupChatId);
                    return;
                }

                if (!onboardingSupport.isForumTopicAlive(groupChatId, existingTopic)) {
                    log.warn("检测到群话题不存在，准备重建话题并清理遗留数据，userId={}, groupChatId={}, topicId={}",
                            user.id(), groupChatId, existingTopic.getTopicId());
                    onboardingSupport.recreateAndUpdateTopic(user, groupChatId);
                    return;
                }

                log.info("已存在有效用户话题映射，跳过创建，userId={}, groupChatId={}, topicId={}",
                        user.id(), groupChatId, existingTopic.getTopicId());
                return;
            }

            String topicName = Topic.generateTopicName(user.firstName(), user.lastName(), user.username(), user.id());
            log.info("准备创建群组话题，userId={}, groupChatId={}, topicName={}", user.id(), groupChatId, topicName);
            Long threadId = onboardingSupport.createForumTopic(groupChatId, topicName);
            if (threadId == null) {
                log.warn("创建群组话题失败，userId={}, groupChatId={}", user.id(), groupChatId);
                return;
            }
            log.info("创建群组话题成功，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, threadId);

            onboardingSupport.createTopic(
                    user.id(),
                    user.username(),
                    user.firstName(),
                    user.lastName(),
                    threadId,
                    groupChatId
            );
            log.info("保存用户话题映射成功，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, threadId);

            String caption = onboardingSupport.buildNewUserCaption(user);

            Message sentMessage = onboardingSupport.sendNewUserMessageToTopic(groupChatId, threadId, user, caption);
            if (sentMessage == null || sentMessage.messageId() == null) {
                log.warn("发送新用户提示消息失败，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, threadId);
                return;
            }
            log.info("已发送新用户提示消息，userId={}, groupChatId={}, threadId={}, messageId={}",
                    user.id(), groupChatId, threadId, sentMessage.messageId());
            onboardingSupport.pinMessage(groupChatId, sentMessage.messageId());
            log.info("已尝试置顶新用户提示消息，userId={}, groupChatId={}, threadId={}, messageId={}",
                    user.id(), groupChatId, threadId, sentMessage.messageId());
        } catch (RuntimeException e) {
            log.error("处理 /start 失败，userId={}, privateChatId={}", user == null ? null : user.id(), privateChatId, e);
        }
    }
}
