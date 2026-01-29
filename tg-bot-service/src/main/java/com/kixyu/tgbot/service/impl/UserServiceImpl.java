package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.domain.entity.User;
import com.kixyu.tgbot.domain.repository.UserRepository;
import com.kixyu.tgbot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    /**
     * 检查用户是否被拉黑
     *
     * @param userId 用户 ID
     * @return       如果用户被拉黑则返回 true，否则返回 false
     */
    @Override
    public boolean isBlocked(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.existsByUserIdAndBlockedTrue(userId);
    }

    /**
     * 保存或更新用户信息
     *
     * @param userId    用户 ID
     * @param username  用户名
     * @param firstName 名
     * @param lastName  姓
     */
    @Override
    public void saveOrUpdateUserInfo(Long userId, String username, String firstName, String lastName) {
        if (userId == null) {
            return;
        }
        User existing = userRepository.findByUserId(userId).orElse(null);
        if (existing == null) {
            User created = User.builder()
                    .userId(userId)
                    .username(username)
                    .firstName(firstName)
                    .lastName(lastName)
                    .blocked(false)
                    .build();
            userRepository.save(created);
            return;
        }
        boolean changed = false;
        if (username != null && !username.equals(existing.getUsername())) {
            existing.setUsername(username);
            changed = true;
        }
        if (firstName != null && !firstName.equals(existing.getFirstName())) {
            existing.setFirstName(firstName);
            changed = true;
        }
        if (lastName != null && !lastName.equals(existing.getLastName())) {
            existing.setLastName(lastName);
            changed = true;
        }
        if (changed) {
            userRepository.save(existing);
        }
    }

    /**
     * 拉黑用户
     *
     * @param userId 用户 ID
     * @return       如果用户已被拉黑则返回已存在的用户实体，否则返回新创建的用户实体
     */
    @Override
    public User block(Long userId) {
        if (userId == null) {
            return null;
        }
        User existing = userRepository.findByUserId(userId).orElse(null);
        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getBlocked())) {
                return existing;
            }
            existing.setBlocked(true);
            return userRepository.save(existing);
        }
        User created = User.builder()
                .userId(userId)
                .blocked(true)
                .build();
        return userRepository.save(created);
    }

    /**
     * 取消拉黑用户
     *
     * @param userId 用户 ID
     * @return       如果用户存在则返回用户实体；若用户不存在则返回 null
     */
    @Override
    public User unblock(Long userId) {
        if (userId == null) {
            return null;
        }
        User existing = userRepository.findByUserId(userId).orElse(null);
        if (existing == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(existing.getBlocked())) {
            return existing;
        }
        existing.setBlocked(false);
        return userRepository.save(existing);
    }

    /**
     * 列出所有被拉黑的用户
     *
     * @return 被拉黑的用户实体列表
     */
    @Override
    public List<User> listBlocked() {
        return userRepository.findByBlockedTrue();
    }
}
