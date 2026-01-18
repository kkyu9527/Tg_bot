package com.kixyu.tgbot.service.relay.internal;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InputMedia;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

final class MediaGroupRelaySupport {

    /**
     * 媒体组聚合与批量发送的辅助逻辑。
     */
    private MediaGroupRelaySupport() {
    }

    interface FlushBuffer {
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

    static final class MessageBuffer<C> implements FlushBuffer {

        private final C context;
        private final List<Message> messages = new ArrayList<>();
        private final long createdAtMillis = System.currentTimeMillis();
        private ScheduledFuture<?> scheduledCheck;
        private int lastCount = 0;
        private int stableCount = 0;

        MessageBuffer(C context) {
            this.context = context;
        }

        C context() {
            return context;
        }

        List<Message> messages() {
            return messages;
        }

        synchronized void add(Message message) {
            messages.add(message);
            messages.sort(Comparator.comparingInt(m -> m.messageId() == null ? 0 : m.messageId()));
            if (scheduledCheck == null || scheduledCheck.isCancelled() || scheduledCheck.isDone()) {
                lastCount = messages.size();
                stableCount = 0;
            }
        }

        @Override
        public int messageCount() {
            return messages.size();
        }

        @Override
        public int getLastCount() {
            return lastCount;
        }

        @Override
        public void setLastCount(int lastCount) {
            this.lastCount = lastCount;
        }

        @Override
        public int getStableCount() {
            return stableCount;
        }

        @Override
        public void setStableCount(int stableCount) {
            this.stableCount = stableCount;
        }

        @Override
        public long getCreatedAtMillis() {
            return createdAtMillis;
        }

        @Override
        public ScheduledFuture<?> getScheduledCheck() {
            return scheduledCheck;
        }

        @Override
        public void setScheduledCheck(ScheduledFuture<?> scheduledCheck) {
            this.scheduledCheck = scheduledCheck;
        }
    }

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
        synchronized (buffer) {
            int currentCount = buffer.messageCount();
            if (currentCount == buffer.getLastCount()) {
                buffer.setStableCount(buffer.getStableCount() + 1);
            } else {
                buffer.setStableCount(0);
                buffer.setLastCount(currentCount);
            }
            long elapsed = System.currentTimeMillis() - buffer.getCreatedAtMillis();
            shouldFlush = buffer.getStableCount() >= 3 || elapsed >= 8000;
        }

        if (!shouldFlush) {
            return;
        }

        ScheduledFuture<?> futureToCancel;
        synchronized (buffer) {
            futureToCancel = buffer.getScheduledCheck();
            buffer.setScheduledCheck(null);
        }
        if (futureToCancel != null) {
            futureToCancel.cancel(false);
        }
        flush.run();
    }
}
