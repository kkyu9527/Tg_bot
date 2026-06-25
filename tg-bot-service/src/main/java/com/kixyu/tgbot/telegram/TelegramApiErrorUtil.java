package com.kixyu.tgbot.telegram;

import com.pengrad.telegrambot.response.BaseResponse;

/**
 * Telegram API 错误响应判断工具。
 */
public final class TelegramApiErrorUtil {

    /**
     * 工具类，禁止实例化。
     */
    private TelegramApiErrorUtil() {
    }

    /**
     * 判断返回错误是否“疑似话题(thread) 不存在/无效”。
     *
     * @param response Telegram API 返回
     * @return 疑似 thread 无效则返回 true，否则返回 false
     */
    public static boolean looksLikeInvalidThread(BaseResponse response) {
        String message = extractMessage(response);
        if (message == null) {
            return false;
        }
        String msg = message.toLowerCase();
        return msg.contains("message thread") || msg.contains("topic") || msg.contains("thread") || msg.contains("not found");
    }

    /**
     * 判断返回错误是否表示“未修改”(not modified)。
     *
     * @param response Telegram API 返回
     * @return 疑似未修改则返回 true，否则返回 false
     */
    public static boolean looksLikeNotModified(BaseResponse response) {
        String message = extractMessage(response);
        if (message == null) {
            return false;
        }
        String msg = message.toLowerCase();
        return msg.contains("not modified") || msg.contains("topic_not_modified");
    }

    /**
     * 提取 Telegram API 的错误描述信息。
     *
     * @param response Telegram API 返回
     * @return 描述字符串；返回为空则为 null
     */
    private static String extractMessage(BaseResponse response) {
        if (response == null) {
            return null;
        }
        return response.description();
    }
}
