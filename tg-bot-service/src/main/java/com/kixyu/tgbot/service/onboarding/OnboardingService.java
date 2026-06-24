package com.kixyu.tgbot.service.onboarding;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;

public interface OnboardingService {

    /**
     * 处理从 webhook 解析出的命令入口。
     *
     * @param command  命令字符串（不含斜杠）
     * @param updateId 更新 ID
     * @param message  消息实体
     * @param chat     聊天实体
     */
    void handleCommand(String command, Integer updateId, Message message, Chat chat);

    /**
     * 处理已验证用户的启动流程。
     *
     * @param user          触发启动流程的用户
     * @param privateChatId 用户私聊窗口的聊天 ID
     */
    void handleVerifiedStart(User user, Long privateChatId);
}
