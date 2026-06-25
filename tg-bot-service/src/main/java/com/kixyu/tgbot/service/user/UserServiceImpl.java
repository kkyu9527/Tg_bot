package com.kixyu.tgbot.service.user;

import com.kixyu.tgbot.domain.entity.User;
import com.kixyu.tgbot.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Telegram 用户数据服务实现。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
class UserServiceImpl implements UserService {

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
     * 检查用户是否未通过人机验证
     *
     * @param userId 用户 ID
     * @return       如果用户未验证则返回 true，否则返回 false
     */
    @Override
    public boolean isUnverified(Long userId) {
        if (userId == null) {
            return true;
        }
        return userRepository.findByUserId(userId)
                .map(user -> !Boolean.TRUE.equals(user.getVerified()))
                .orElse(true);
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
    @Transactional
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
                    .verified(false)
                    .build();
            userRepository.save(created);
            return;
        }
        if (updateProfile(existing, username, firstName, lastName)) {
            userRepository.save(existing);
        }
    }

    /**
     * 标记用户已通过人机验证
     *
     * @param userId    用户 ID
     * @param username  用户名
     * @param firstName 名
     * @param lastName  姓
    */
    @Override
    @Transactional
    public void markVerified(Long userId, String username, String firstName, String lastName) {
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
                    .verified(true)
                    .build();
            userRepository.save(created);
            return;
        }
        boolean changed = updateProfile(existing, username, firstName, lastName);
        if (!Boolean.TRUE.equals(existing.getVerified())) {
            existing.setVerified(true);
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
    */
    @Override
    @Transactional
    public void block(Long userId) {
        if (userId == null) {
            return;
        }
        User existing = userRepository.findByUserId(userId).orElse(null);
        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getBlocked())) {
                return;
            }
            existing.setBlocked(true);
            userRepository.save(existing);
            return;
        }
        User created = User.builder()
                .userId(userId)
                .blocked(true)
                .verified(false)
                .build();
        userRepository.save(created);
    }

    /**
     * 取消拉黑用户
     *
     * @param userId 用户 ID
    */
    @Override
    @Transactional
    public void unblock(Long userId) {
        if (userId == null) {
            return;
        }
        User existing = userRepository.findByUserId(userId).orElse(null);
        if (existing == null) {
            return;
        }
        if (!Boolean.TRUE.equals(existing.getBlocked())) {
            return;
        }
        existing.setBlocked(false);
        userRepository.save(existing);
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

    /**
     * 更新用户资料字段。
     *
     * @param user      用户实体
     * @param username  用户名
     * @param firstName 名
     * @param lastName  姓
     * @return          如果资料发生变化则返回 true，否则返回 false
     */
    private boolean updateProfile(User user, String username, String firstName, String lastName) {
        boolean changed = false;
        if (username != null && !username.equals(user.getUsername())) {
            user.setUsername(username);
            changed = true;
        }
        if (firstName != null && !firstName.equals(user.getFirstName())) {
            user.setFirstName(firstName);
            changed = true;
        }
        if (lastName != null && !lastName.equals(user.getLastName())) {
            user.setLastName(lastName);
            changed = true;
        }
        return changed;
    }
}
