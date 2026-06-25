package com.kixyu.tgbot.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * 用户在群组中对应的 Telegram 话题实体。
 */
@Entity
@Table(name = "topics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 用户相关信息
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username")
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    // 话题相关信息
    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    // 群组ID
    @Column(name = "chat_id", nullable = false)
    private String chatId;

    @Column(name = "full_mode", nullable = false)
    private Boolean fullMode;

    @Column(name = "welcome_message_id")
    private Long welcomeMessageId;

    // 创建时间
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    // 更新时间
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 话题实体首次持久化前初始化时间字段。
     */
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    /**
     * 话题实体更新前刷新更新时间。
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    /**
     * 根据用户信息生成话题名称。
     *
     * 规则：优先使用firstName+lastName，若缺少某个则使用另一个，
     * 如果都没有则使用username，如果还是没有则使用id
     *
     * @param firstName 用户名
     * @param lastName  用户姓
     * @param username  Telegram 用户名
     * @param userId    用户 ID
     * @return          生成后的话题名称
     */
    public static String generateTopicName(String firstName, String lastName, String username, Long userId) {
        StringBuilder topicName = new StringBuilder();

        if (firstName != null && !firstName.trim().isEmpty()) {
            topicName.append(firstName.trim());
        }

        if (lastName != null && !lastName.trim().isEmpty()) {
            if (!topicName.isEmpty()) {
                topicName.append(" ");
            }
            topicName.append(lastName.trim());
        }

        if (topicName.isEmpty() && username != null && !username.trim().isEmpty()) {
            topicName.append(username.trim());
        }

        if (topicName.isEmpty() && userId != null) {
            topicName.append("User ").append(userId);
        }

        return topicName.toString();
    }
}
