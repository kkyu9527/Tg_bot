package com.kixyu.tgbot.domain.repository;

import com.kixyu.tgbot.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 根据 Telegram 用户ID查找用户
    Optional<User> findByUserId(Long userId);

    // 检查用户是否被拉黑
    boolean existsByUserIdAndBlockedTrue(Long userId);

    // 查找所有被拉黑的用户
    List<User> findByBlockedTrue();
}
