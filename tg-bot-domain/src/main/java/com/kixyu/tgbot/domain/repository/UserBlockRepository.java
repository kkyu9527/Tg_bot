package com.kixyu.tgbot.domain.repository;

import com.kixyu.tgbot.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserId(Long userId);

    boolean existsByUserIdAndBlockedTrue(Long userId);

    List<User> findByBlockedTrue();
}
