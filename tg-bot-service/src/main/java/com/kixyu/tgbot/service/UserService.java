package com.kixyu.tgbot.service;

import com.kixyu.tgbot.domain.entity.User;

import java.util.List;

public interface UserService {

    /**
     * 检查用户是否已被阻塞。
     *
     * @param userId    用户 ID
     * @return          如果用户已被阻塞，则返回 true；否则返回 false
     */
    boolean isBlocked(Long userId);

    /**
     * 保存或更新用户信息。
     *
     * @param userId        用户 ID
     * @param username      用户名
     * @param firstName     firstName
     * @param lastName      lastName
     */
    void saveOrUpdateUserInfo(Long userId, String username, String firstName, String lastName);

    /**
     * 阻塞用户，防止其与机器人进行交互。
     *
     * @param userId    用户 ID
     * @return          被阻塞的用户实体
     */
    User block(Long userId);

    /**
     * 解除用户阻塞，允许其与机器人进行交互。
     *
     * @param userId    用户 ID
     * @return          解除阻塞的用户实体
     */
    User unblock(Long userId);

    /**
     * 列出所有已被阻塞的用户。
     *
     * @return 已被阻塞的用户实体列表
     */
    List<User> listBlocked();
}
