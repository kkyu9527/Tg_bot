package com.kixyu.tgbot.service.relay;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InputMedia;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * 媒体组消息聚合与 flush 判定辅助类。
 */
final class MediaGroupRelaySupport {

    /**
     * 媒体组消息到达后，首次检查缓冲是否稳定的延迟。
     */
    static final long FLUSH_CHECK_INITIAL_DELAY_MILLIS = 500L;

    /**
     * 媒体组缓冲稳定性检查间隔。
     */
    static final long FLUSH_CHECK_INTERVAL_MILLIS = 500L;

    /**
     * 消息数量连续稳定达到该次数后，认为媒体组已接收完成。
     */
    private static final int STABLE_CHECK_THRESHOLD = 3;

    /**
     * 即使消息数量未连续稳定，媒体组缓冲最长也只等待该时间。
     */
    private static final long MAX_BUFFER_WAIT_MILLIS = 8_000L;

    /**
     * 媒体组聚合与批量发送的辅助逻辑。
     */
    private MediaGroupRelaySupport() {
    }

    /**
     * 媒体组 flush 缓冲状态接口。
     */
    interface FlushBuffer {
        /**
         * 获取用于保护缓冲状态的锁对象。
         *
         * @return 缓冲锁
         */
        Object lock();

        /**
         * 获取当前已缓冲的消息数量。
         *
         * @return 当前缓冲数量
         */
        int messageCount();

        /**
         * 获取上一次检查时的消息数量。
         *
         * @return 上一次数量
         */
        int getLastCount();

        /**
         * 更新上一次检查时的消息数量。
         *
         * @param lastCount 上一次数量
         */
        void setLastCount(int lastCount);

        /**
         * 获取消息数量稳定计数（连续多次不变的次数）。
         *
         * @return 稳定计数
         */
        int getStableCount();

        /**
         * 更新消息数量稳定计数。
         *
         * @param stableCount 稳定计数
         */
        void setStableCount(int stableCount);

        /**
         * 获取缓冲创建时间（毫秒）。
         *
         * @return 创建时间戳（毫秒）
         */
        long getCreatedAtMillis();

        /**
         * 获取定时检查任务。
         *
         * @return 定时任务
         */
        ScheduledFuture<?> getScheduledCheck();

        /**
         * 设置定时检查任务。
         *
         * @param scheduledCheck 定时任务
         */
        void setScheduledCheck(ScheduledFuture<?> scheduledCheck);
    }

    /**
     * 消息缓冲区，用于聚合待发送的消息。
     *
     * @param <C> 上下文类型
     */
    static final class MessageBuffer<C> implements FlushBuffer {

        private final C context;
        private final List<Message> messages = new ArrayList<>();
        private final Object lock = new Object();
        private final long createdAtMillis = System.currentTimeMillis();
        private ScheduledFuture<?> scheduledCheck;
        private int lastCount = 0;
        private int stableCount = 0;

        /**
         * 创建消息缓冲区。
         *
         * @param context 缓冲上下文
         */
        MessageBuffer(C context) {
            this.context = context;
        }

        /**
         * 获取缓冲上下文。
         *
         * @return 缓冲上下文
         */
        C context() {
            return context;
        }

        /**
         * 获取已缓冲的消息列表。
         *
         * @return 已缓冲消息列表
         */
        List<Message> messages() {
            return messages;
        }

        /**
         * 添加消息到缓冲区并按消息 ID 排序。
         *
         * @param message Telegram 消息
         */
        void add(Message message) {
            synchronized (lock) {
                messages.add(message);
                messages.sort(Comparator.comparingInt(m -> m.messageId() == null ? 0 : m.messageId()));
                if (scheduledCheck == null || scheduledCheck.isCancelled() || scheduledCheck.isDone()) {
                    lastCount = messages.size();
                    stableCount = 0;
                }
            }
        }

        /**
         * 获取用于保护缓冲状态的锁对象。
         *
         * @return 缓冲锁
         */
        @Override
        public Object lock() {
            return lock;
        }

        /**
         * 获取缓冲中的消息数量。
         *
         * @return 消息数量
         */
        @Override
        public int messageCount() {
            return messages.size();
        }

        /**
         * 获取上最后一次检查时的消息数量。
         *
         * @return 上一次数量
         */
        @Override
        public int getLastCount() {
            return lastCount;
        }

        /**
         * 更新上最后一次检查时的消息数量。
         *
         * @param lastCount 上一次数量
         */
        @Override
        public void setLastCount(int lastCount) {
            this.lastCount = lastCount;
        }

        /**
         * 获取消息数量稳定计数（连续多次不变的次数）。
         *
         * @return 稳定计数
         */
        @Override
        public int getStableCount() {
            return stableCount;
        }

        /**
         * 更新消息数量稳定计数。
         *
         * @param stableCount 稳定计数
         */
        @Override
        public void setStableCount(int stableCount) {
            this.stableCount = stableCount;
        }

        /**
         * 获取缓冲创建时间（毫秒）。
         *
         * @return 创建时间戳（毫秒）
         */
        @Override
        public long getCreatedAtMillis() {
            return createdAtMillis;
        }

        /**
         * 获取定时检查任务。
         *
         * @return 定时任务
         */
        @Override
        public ScheduledFuture<?> getScheduledCheck() {
            return scheduledCheck;
        }

        /**
         * 设置定时检查任务。
         *
         * @param scheduledCheck 定时任务
         */
        @Override
        public void setScheduledCheck(ScheduledFuture<?> scheduledCheck) {
            this.scheduledCheck = scheduledCheck;
        }
    }

    /**
     * 收集到的媒体组元素，包含原始消息和对应的输入媒体。
     *
     * @param originals 原始消息列表
     * @param medias    输入媒体列表
     */
    record CollectedMediaGroup(List<Message> originals, ArrayList<InputMedia<?>> medias) {
    }

    /**
     * 从消息列表中收集可发送的媒体组元素，并返回与之对应的原始消息列表。
     *
     * @param messages 消息列表
     * @param telegramMessageMediaMapper 媒体映射器
     * @return 收集结果
     */
    static CollectedMediaGroup collectMedias(List<Message> messages, TelegramMessageMediaMapper telegramMessageMediaMapper) {
        List<Message> originals = new ArrayList<>();
        ArrayList<InputMedia<?>> medias = new ArrayList<>();
        for (Message message : messages) {
            InputMedia<?> media = telegramMessageMediaMapper.toInputMedia(message);
            if (media != null) {
                medias.add(media);
                originals.add(message);
            }
        }
        return new CollectedMediaGroup(originals, medias);
    }

    /**
     * 检查缓冲是否满足“可 flush”的条件，满足则取消定时任务并执行 flush。
     *
     * <p>当消息数量连续多次稳定，或累计等待超过阈值时触发 flush。</p>
     *
     * @param buffer 缓冲区
     * @param flush flush 执行器
     */
    static void checkAndFlush(FlushBuffer buffer, Runnable flush) {
        boolean shouldFlush;
        Object lock = buffer.lock();
        synchronized (lock) {
            int currentCount = buffer.messageCount();
            if (currentCount == buffer.getLastCount()) {
                buffer.setStableCount(buffer.getStableCount() + 1);
            } else {
                buffer.setStableCount(0);
                buffer.setLastCount(currentCount);
            }
            long elapsed = System.currentTimeMillis() - buffer.getCreatedAtMillis();
            shouldFlush = buffer.getStableCount() >= STABLE_CHECK_THRESHOLD || elapsed >= MAX_BUFFER_WAIT_MILLIS;
        }

        if (!shouldFlush) {
            return;
        }

        ScheduledFuture<?> futureToCancel;
        synchronized (lock) {
            futureToCancel = buffer.getScheduledCheck();
            buffer.setScheduledCheck(null);
        }
        if (futureToCancel != null) {
            futureToCancel.cancel(false);
        }
        flush.run();
    }
}
