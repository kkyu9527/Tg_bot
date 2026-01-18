package com.kixyu.tgbot.service;

import com.kixyu.tgbot.domain.entity.User;

import java.util.List;

public interface UserService {

    boolean isBlocked(Long userId);

    void saveOrUpdateUserInfo(Long userId, String username, String firstName, String lastName);

    User block(Long userId);

    User unblock(Long userId);

    List<User> listBlocked();
}
