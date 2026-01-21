package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.domain.repository.TopicRepository;
import com.kixyu.tgbot.service.MessageService;
import com.kixyu.tgbot.service.TopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;
    private final MessageService messageService;

    /**
     * 保存话题实体到数据库。
     *
     * @param topic 话题实体
     * @return 持久化后的话题实体
     */
    @Override
    public Topic saveTopic(Topic topic) {
        return topicRepository.save(topic);
    }

    /**
     * 根据用户 ID 查询其所有话题。
     *
     * @param userId 用户 ID
     * @return 话题列表
     */
    @Override
    public List<Topic> getTopicsByUserId(Long userId) {
        return topicRepository.findByUserId(userId);
    }

    /**
     * 根据话题 ID 查询话题。
     *
     * @param topicId 话题 ID
     * @return 话题实体，可为空
     */
    @Override
    public Optional<Topic> getTopicByTopicId(Long topicId) {
        return topicRepository.findByTopicId(topicId);
    }

    /**
     * 根据用户 ID 和聊天 ID 查询话题。
     *
     * @param userId 用户 ID
     * @param chatId 聊天 ID
     * @return 话题实体，可为空
     */
    @Override
    public Optional<Topic> getTopicByUserIdAndChatId(Long userId, String chatId) {
        return topicRepository.findByUserIdAndChatId(userId, chatId);
    }

    /**
     * 根据聊天 ID 查询该聊天下所有话题。
     *
     * @param chatId 聊天 ID
     * @return 话题列表
     */
    @Override
    public List<Topic> getTopicsByChatId(String chatId) {
        return topicRepository.findByChatId(chatId);
    }

    /**
     * 删除指定用户在所有聊天中的话题。
     *
     * @param userId 用户 ID
     */
    @Override
    public void deleteTopicsByUserId(Long userId) {
        topicRepository.deleteByUserId(userId);
    }

    /**
     * 删除指定聊天中的某个话题。
     *
     * @param topicId 话题 ID
     * @param chatId  聊天 ID
     */
    @Override
    public void deleteTopicByTopicIdAndChatId(Long topicId, String chatId) {
        topicRepository.deleteByTopicIdAndChatId(topicId, chatId);
    }

    /**
     * 创建一个新的话题并保存。
     *
     * @param userId    用户 ID
     * @param username  用户名
     * @param firstName 名
     * @param lastName  姓
     * @param topicId   Telegram 话题 ID
     * @param chatId    聊天 ID
     * @return          持久化后的话题实体
     */
    @Override
    public Topic createTopic(Long userId, String username, String firstName, String lastName,
                            Long topicId, String chatId) {
        
        // 生成话题名称
        String topicName = Topic.generateTopicName(firstName, lastName, username, userId);
        
        Topic topic = Topic.builder()
                .userId(userId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .topicId(topicId)
                .topicName(topicName)
                .chatId(chatId)
                .fullMode(false)
                .build();
                
        return saveTopic(topic);
    }

    /**
     * 获取或更新用户在指定聊天中的话题。
     * 如果话题存在，会根据最新的用户信息更新名称；否则抛出异常。
     *
     * @param userId    用户 ID
     * @param chatId    聊天 ID
     * @param username  用户名
     * @param firstName 名
     * @param lastName  姓
     * @return          已存在或更新后的话题实体
     */
    @Override
    public Topic getOrCreateTopicByUserAndChat(Long userId, String chatId, String username, String firstName, String lastName) {
        // 先尝试获取已存在的话题
        Optional<Topic> existingTopicOpt = getTopicByUserIdAndChatId(userId, chatId);
        
        if (existingTopicOpt.isPresent()) {
            Topic existingTopic = existingTopicOpt.get();
            // 更新用户信息（可能发生变化）
            boolean updated = false;
            if (!Objects.equals(existingTopic.getUsername(), username)) {
                existingTopic.setUsername(username);
                updated = true;
            }
            if (!Objects.equals(existingTopic.getFirstName(), firstName)) {
                existingTopic.setFirstName(firstName);
                updated = true;
            }
            if (!Objects.equals(existingTopic.getLastName(), lastName)) {
                existingTopic.setLastName(lastName);
                updated = true;
            }
            
            // 如果用户信息有变化，则更新话题名称
            if (updated) {
                String newTopicName = Topic.generateTopicName(firstName, lastName, username, userId);
                existingTopic.setTopicName(newTopicName);
                return saveTopic(existingTopic);
            }
            
            return existingTopic;
        }

        throw new IllegalStateException("话题不存在，必须先通过 Telegram 创建话题后再保存映射");
    }

    /**
     * 处理话题删除逻辑：先删除该话题下所有消息，再删除话题本身。
     *
     * @param userId 用户 ID
     * @param chatId 聊天 ID
     */
    @Override
    public void handleTopicDeletion(Long userId, String chatId) {
        // 查找该用户在此聊天中的话题
        Optional<Topic> topicOpt = getTopicByUserIdAndChatId(userId, chatId);
        
        if (topicOpt.isPresent()) {
            Topic topic = topicOpt.get();
            log.info("删除旧话题数据，userId={}, chatId={}, topicId={}", userId, chatId, topic.getTopicId());
            
            // 删除该话题的所有相关消息
            messageService.deleteMessagesByTopicId(topic.getTopicId());
            
            // 删除话题本身
            deleteTopicByTopicIdAndChatId(topic.getTopicId(), chatId);
            log.info("删除旧话题数据完成，userId={}, chatId={}, topicId={}", userId, chatId, topic.getTopicId());
        }
    }

    /**
     * 重新创建话题：删除旧话题及其消息后，创建新话题。
     *
     * @param userId        用户 ID
     * @param chatId        聊天 ID
     * @param username      用户名
     * @param firstName     名
     * @param lastName      姓
     * @param newTopicId    新的话题 ID
     * @return              新创建的话题实体
     */
    @Override
    public Topic recreateTopic(Long userId, String chatId, String username, String firstName, String lastName, Long newTopicId) {
        // 先处理旧话题的删除
        handleTopicDeletion(userId, chatId);
        
        // 创建新话题
        return createTopic(userId, username, firstName, lastName, newTopicId, chatId);
    }
}
