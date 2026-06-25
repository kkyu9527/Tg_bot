package com.kixyu.tgbot.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * Telegram 消息映射实体。
 */
@Entity
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联话题ID
    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    // 消息类型（用户消息/机器人转发消息/主人回复消息）
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "message_type", nullable = false, length = 32)
    private MessageType messageType;

    // 消息内容类型
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "content_type", nullable = false, length = 32)
    private ContentType contentType;

    // 媒体组ID（如果是媒体组的一部分）
    @Column(name = "media_group_id")
    private String mediaGroupId;

    // 发送者ID
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    // 发送者用户名
    @Column(name = "sender_username")
    private String senderUsername;

    // 发送者姓名
    @Column(name = "sender_first_name")
    private String senderFirstName;

    // 发送者姓氏
    @Column(name = "sender_last_name")
    private String senderLastName;

    // 原始消息ID
    @Column(name = "original_message_id", nullable = false)
    private Long originalMessageId;

    // 机器人转发后的消息ID
    @Column(name = "forwarded_message_id", nullable = false)
    private Long forwardedMessageId;

    // 创建时间
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    // 更新时间
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 消息实体首次持久化前初始化时间字段。
     */
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    /**
     * 消息实体更新前刷新更新时间。
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    /**
     * 消息在转发链路中的来源类型。
     */
    public enum MessageType {
        USER_MESSAGE,           // 用户原始消息
        BOT_FORWARDED_MESSAGE,  // 机器人转发的消息
        OWNER_MESSAGE           // 主人在话题中的回复
    }

    /**
     * Telegram 消息内容类型。
     */
    public enum ContentType {
        TEXT,               // 纯文本
        PHOTO,              // 图片
        VIDEO,              // 视频
        DOCUMENT,           // 文档
        AUDIO,              // 音频
        VOICE,              // 语音
        STICKER,            // 贴纸
        LOCATION,           // 位置
        VENUE,              // 地点
        CONTACT,            // 联系人
        ANIMATION,          // 动图(GIF)
        MEDIA_GROUP,        // 媒体组
        POLL,               // 投票
        DICE,               // 骰子
        GAME                // 游戏
    }
}
