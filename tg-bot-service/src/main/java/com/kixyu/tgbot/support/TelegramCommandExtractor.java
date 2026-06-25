package com.kixyu.tgbot.support;

import org.springframework.stereotype.Component;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.MessageEntity;

/**
 * Telegram 命令消息提取器。
 */
@Component
public class TelegramCommandExtractor {

    /**
     * 从 Telegram 消息中提取命令名称（不含斜杠和 @botname）。
     *
     * @param message Telegram 消息
     * @return 规范化后的命令名，例如 "start"；如果不存在命令则返回 null
     */
    public String extractCommand(Message message) {
        if (message == null || message.text() == null) {
            return null;
        }

        MessageEntity[] entities = message.entities();
        if (entities != null) {
            for (MessageEntity entity : entities) {
                if (MessageEntity.Type.bot_command.equals(entity.type()) && entity.offset() == 0) {
                    int length = entity.length();
                    if (length > 1 && length <= message.text().length()) {
                        String raw = message.text().substring(0, length);
                        return normalize(raw);
                    }
                }
            }
        }

        String text = message.text().trim();
        if (!text.startsWith("/")) {
            return null;
        }
        int spaceIndex = text.indexOf(' ');
        String raw = spaceIndex > 0 ? text.substring(0, spaceIndex) : text;
        return normalize(raw);
    }

    /**
     * 规范化命令字符串：去掉前导斜杠与 @botname，并转为小写。
     *
     * @param raw 原始命令字符串
     * @return 规范化后的命令名
     */
    private static String normalize(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int atIndex = trimmed.indexOf('@');
        if (atIndex > 0) {
            trimmed = trimmed.substring(0, atIndex);
        }
        return trimmed.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
