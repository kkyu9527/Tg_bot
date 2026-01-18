package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.domain.entity.User;
import com.kixyu.tgbot.domain.repository.UserBlockRepository;
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

    private final UserBlockRepository userBlockRepository;

    @Override
    public boolean isBlocked(Long userId) {
        if (userId == null) {
            return false;
        }
        return userBlockRepository.existsByUserIdAndBlockedTrue(userId);
    }

    @Override
    public void saveOrUpdateUserInfo(Long userId, String username, String firstName, String lastName) {
        if (userId == null) {
            return;
        }
        User existing = userBlockRepository.findByUserId(userId).orElse(null);
        if (existing == null) {
            User created = User.builder()
                    .userId(userId)
                    .username(username)
                    .firstName(firstName)
                    .lastName(lastName)
                    .blocked(false)
                    .build();
            userBlockRepository.save(created);
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
            userBlockRepository.save(existing);
        }
    }

    @Override
    public User block(Long userId) {
        if (userId == null) {
            return null;
        }
        User existing = userBlockRepository.findByUserId(userId).orElse(null);
        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getBlocked())) {
                return existing;
            }
            existing.setBlocked(true);
            return userBlockRepository.save(existing);
        }
        User created = User.builder()
                .userId(userId)
                .blocked(true)
                .build();
        return userBlockRepository.save(created);
    }

    @Override
    public User unblock(Long userId) {
        if (userId == null) {
            return null;
        }
        User existing = userBlockRepository.findByUserId(userId).orElse(null);
        if (existing == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(existing.getBlocked())) {
            return existing;
        }
        existing.setBlocked(false);
        return userBlockRepository.save(existing);
    }

    @Override
    public List<User> listBlocked() {
        return userBlockRepository.findByBlockedTrue();
    }
}
