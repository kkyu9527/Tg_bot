package com.kixyu.tgbot.service;

import com.pengrad.telegrambot.model.User;

public interface OnboardingService {

    /**
     * 处理用户在私聊中发送的 /start 命令。
     *
     * @param user          触发命令的用户
     * @param privateChatId 用户私聊窗口的聊天 ID
     */
    void handleStart(User user, Long privateChatId);
}
