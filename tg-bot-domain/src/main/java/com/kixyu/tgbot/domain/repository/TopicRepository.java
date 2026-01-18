package com.kixyu.tgbot.domain.repository;

import com.kixyu.tgbot.domain.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {
    
    // 根据用户ID查找话题
    List<Topic> findByUserId(Long userId);
    
    // 根据话题ID查找记录
    Optional<Topic> findByTopicId(Long topicId);
    
    // 根据用户ID和聊天ID查找话题
    Optional<Topic> findByUserIdAndChatId(Long userId, String chatId);
    
    // 根据聊天ID查找所有话题
    List<Topic> findByChatId(String chatId);
    
    // 删除指定用户的所有话题记录
    void deleteByUserId(Long userId);
    
    // 删除指定聊天中的特定话题
    void deleteByTopicIdAndChatId(Long topicId, String chatId);
}