package com.kixyu.tgbot.service.callback;

import com.pengrad.telegrambot.model.CallbackQuery;

/**
 * Telegram 按钮回调分发服务。
 */
public interface CallbackQueryService {

    /**
     * 处理 Telegram 的回调查询事件。
     *
     * @param callbackQuery 回调查询对象
     */
    void handleCallbackQuery(CallbackQuery callbackQuery);
}
