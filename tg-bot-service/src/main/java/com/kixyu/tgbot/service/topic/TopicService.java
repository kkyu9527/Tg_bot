package com.kixyu.tgbot.service.topic;

import com.kixyu.tgbot.domain.entity.Topic;
import java.util.Optional;

/**
 * Telegram 群话题数据服务。
 */
public interface TopicService {
    
    /**
     * 保存话题。
     *
     * @param topic 话题实体
     * @return      保存后的话题实体
     */
    Topic saveTopic(Topic topic);
    
    /**
     * 根据话题 ID 获取话题信息。
     *
     * @param topicId 话题 ID
     * @return        匹配的话题（如果存在）
     */
    Optional<Topic> getTopicByTopicId(Long topicId);
    
    /**
     * 根据用户 ID 和聊天 ID 获取话题信息。
     *
     * @param userId 用户 ID
     * @param chatId 聊天 ID
     * @return       匹配的话题（如果存在）
     */
    Optional<Topic> getTopicByUserIdAndChatId(Long userId, String chatId);
    
    /**
     * 删除指定聊天中的特定话题。
     *
     * @param topicId 话题 ID
     * @param chatId  聊天 ID
     */
    void deleteTopicByTopicIdAndChatId(Long topicId, String chatId);
    
    /**
     * 创建新的话题。
     *
     * @param userId    用户 ID
     * @param username  用户名
     * @param firstName 名
     * @param lastName  姓
     * @param topicId   话题 ID
     * @param chatId    聊天 ID
     * @return          创建后的话题实体
     */
    Topic createTopic(Long userId, String username, String firstName, String lastName, 
                     Long topicId, String chatId);
    
    /**
     * 获取或创建用户话题。
     *
     * @param userId    用户 ID
     * @param chatId    聊天 ID
     * @param username  用户名
     * @param firstName 名
     * @param lastName  姓
     * @return          已存在或创建后的话题实体
     */
    Topic getOrCreateTopicByUserAndChat(Long userId, String chatId, String username, String firstName, String lastName);
    
    /**
     * 处理话题删除后的清理和重新创建。
     *
     * @param userId 用户 ID
     * @param chatId 聊天 ID
     */
    void handleTopicDeletion(Long userId, String chatId);
    
}
