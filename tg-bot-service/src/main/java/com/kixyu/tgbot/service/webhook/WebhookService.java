package com.kixyu.tgbot.service.webhook;

import com.pengrad.telegrambot.model.Update;

/**
 * Telegram Webhook 更新处理服务。
 */
public interface WebhookService {

    /**
     * 处理来自 Telegram 的 Webhook 更新。
     *
     * @param update Telegram 更新对象
     */
    void handleWebhook(Update update);
}
