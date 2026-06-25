package com.kixyu.tgbot.domain.repository;

import com.kixyu.tgbot.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Telegram 用户数据仓储。
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据 Telegram 用户 ID 查询用户。
     *
     * @param userId    Telegram 用户 ID
     * @return          匹配的用户（如果存在）
     */
    Optional<User> findByUserId(Long userId);

    /**
     * 检查指定用户是否已被拉黑。
     *
     * @param userId    Telegram 用户 ID
     * @return          是否存在被拉黑的用户
     */
    boolean existsByUserIdAndBlockedTrue(Long userId);

    /**
     * 查询所有被拉黑的用户。
     *
     * @return 被拉黑的用户列表
     */
    List<User> findByBlockedTrue();
}
