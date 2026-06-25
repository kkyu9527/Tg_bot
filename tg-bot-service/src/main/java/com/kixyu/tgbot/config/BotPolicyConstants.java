package com.kixyu.tgbot.config;

import java.time.Duration;

/**
 * 机器人全局业务策略常量。
 *
 * <p>只放会影响用户可见行为的策略值；局部算法参数应保留在对应实现类中。</p>
 */
public final class BotPolicyConstants {

    /**
     * 普通提示消息默认自动删除延迟。
     */
    public static final Duration DEFAULT_HINT_AUTO_DELETE_DELAY = Duration.ofSeconds(30);

    /**
     * 黑名单列表消息自动删除延迟。列表比普通提示保留更久，方便主人查看和操作。
     */
    public static final Duration BLOCKED_LIST_AUTO_DELETE_DELAY = Duration.ofMinutes(2);

    /**
     * 黑名单命令/按钮操作防抖窗口，避免重复点击或重复命令造成连续执行。
     */
    public static final Duration BLACKLIST_ACTION_DEBOUNCE_WINDOW = Duration.ofSeconds(5);

    /**
     * 新用户在达到该转发消息数量前处于低信任期。
     */
    public static final int LOW_TRUST_MESSAGE_LIMIT = 10;

    /**
     * 低信任期内，两条文本消息之间要求的最小间隔。
     */
    public static final Duration LOW_TRUST_MESSAGE_INTERVAL = Duration.ofSeconds(10);

    /**
     * 工具类，禁止实例化。
     */
    private BotPolicyConstants() {
    }

    /**
     * 将 Duration 转为毫秒。
     *
     * @param duration 时长
     * @return         毫秒数；duration 为空时返回 0
     */
    public static long millis(Duration duration) {
        return duration == null ? 0L : duration.toMillis();
    }

    /**
     * 将时长格式化为中文展示文本。
     *
     * @param duration 时长
     * @return         中文时长文本
     */
    public static String formatDuration(Duration duration) {
        long seconds = Math.max(1L, duration == null ? 0L : duration.toSeconds());
        if (seconds < 60L || seconds % 60L != 0L) {
            return seconds + " 秒";
        }
        return (seconds / 60L) + " 分钟";
    }
}
