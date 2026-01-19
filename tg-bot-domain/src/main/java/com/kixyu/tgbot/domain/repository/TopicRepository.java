package com.kixyu.tgbot.domain.repository;

import com.kixyu.tgbot.domain.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {
    
    /**
     * 根据用户 ID 查询话题列表。
     *
     * @param userId    用户 ID
     * @return          话题列表
     */
    List<Topic> findByUserId(Long userId);
    
    /**
     * 根据话题 ID 查询话题。
     *
     * @param topicId   话题 ID
     * @return          匹配的话题（如果存在）
     */
    Optional<Topic> findByTopicId(Long topicId);
    
    /**
     * 根据用户 ID 和聊天 ID 查询话题。
     *
     * @param userId    用户 ID
     * @param chatId    聊天 ID
     * @return          匹配的话题（如果存在）
     */
    Optional<Topic> findByUserIdAndChatId(Long userId, String chatId);
    
    /**
     * 根据聊天 ID 查询所有话题。
     *
     * @param chatId    聊天 ID
     * @return          话题列表
     */
    List<Topic> findByChatId(String chatId);
    
    /**
     * 删除指定用户的所有话题记录。
     *
     * @param userId 用户 ID
     */
    void deleteByUserId(Long userId);
    
    /**
     * 删除指定聊天中的特定话题。
     *
     * @param topicId 话题 ID
     * @param chatId  聊天 ID
     */
    void deleteByTopicIdAndChatId(Long topicId, String chatId);
}