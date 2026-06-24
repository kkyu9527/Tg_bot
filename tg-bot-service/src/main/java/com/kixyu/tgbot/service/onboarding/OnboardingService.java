package com.kixyu.tgbot.service.onboarding;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;

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
}
