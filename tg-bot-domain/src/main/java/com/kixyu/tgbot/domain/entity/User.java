package com.kixyu.tgbot.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Telegram 用户ID，唯一标识每个用户
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // Telegram 用户名，用户在 Telegram 中设置的名称
    @Column(name = "username")
    private String username;

    // 用户在 Telegram 中设置的第一个名称
    @Column(name = "first_name")
    private String firstName;

    // 用户在 Telegram 中设置的最后一个名称
    @Column(name = "last_name")
    private String lastName;

    // 用户是否被拉黑，默认值为 false
    @Column(name = "blocked", nullable = false)
    private Boolean blocked;

    // 用户创建时间，记录用户在系统中的注册时间
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    // 用户更新时间，记录用户信息最后一次更新的时间
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    // 当用户实体被持久化到数据库时调用，设置创建时间和更新时间为当前时间
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    // 当用户实体被更新到数据库时调用，设置更新时间为当前时间
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}

