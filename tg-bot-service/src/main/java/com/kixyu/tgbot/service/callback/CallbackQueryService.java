package com.kixyu.tgbot.service.callback;

import com.pengrad.telegrambot.model.CallbackQuery;

public interface CallbackQueryService {

    /**
     * 处理 Telegram 的回调查询事件。
     */
    void handleCallbackQuery(CallbackQuery callbackQuery);
}
