package com.kixyu.tgbot.service.topic;

import com.kixyu.tgbot.domain.entity.Topic;
import java.util.Optional;

public interface TopicService {
    
    /**
     * 保存话题
     */
    Topic saveTopic(Topic topic);
    
    /**
     * 根据话题ID获取话题信息
     */
    Optional<Topic> getTopicByTopicId(Long topicId);
    
    /**
     * 根据用户ID和聊天ID获取话题信息
     */
    Optional<Topic> getTopicByUserIdAndChatId(Long userId, String chatId);
    
    /**
     * 删除指定聊天中的特定话题
     */
    void deleteTopicByTopicIdAndChatId(Long topicId, String chatId);
    
    /**
     * 创建新的话题
     */
    Topic createTopic(Long userId, String username, String firstName, String lastName, 
                     Long topicId, String chatId);
    
    /**
     * 获取或创建用户话题
     */
    Topic getOrCreateTopicByUserAndChat(Long userId, String chatId, String username, String firstName, String lastName);
    
    /**
     * 处理话题删除后的清理和重新创建
     */
    void handleTopicDeletion(Long userId, String chatId);
    
}
