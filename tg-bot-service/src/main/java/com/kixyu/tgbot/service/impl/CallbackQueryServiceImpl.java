package com.kixyu.tgbot.service.impl;

import com.kixyu.tgbot.service.CallbackQueryService;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackQueryServiceImpl implements CallbackQueryService {

    private static final String CALLBACK_PREFIX = "m:";

    private final TelegramApiClient telegramApiClient;

    /**
     * 处理 Telegram 的回调查询事件。
     *
     * @param callbackQuery 回调查询对象
     */
    @Override
    public void handleCallbackQuery(CallbackQuery callbackQuery) {
        if (callbackQuery == null) {
            return;
        }
        String data = callbackQuery.data();
        if (data == null || !data.startsWith(CALLBACK_PREFIX)) {
            answer(callbackQuery, null);
            return;
        }
        answer(callbackQuery, "功能已停用");
    }

    /**
     * 给 Telegram 回调查询发送响应，可选附带提示文本。
     *
     * @param callbackQuery 回调查询对象
     * @param text          可选提示文本，为 null 则不下发文字
     */
    private void answer(CallbackQuery callbackQuery, String text) {
        if (callbackQuery == null || callbackQuery.id() == null) {
            return;
        }
        AnswerCallbackQuery req = new AnswerCallbackQuery(callbackQuery.id());
        if (text != null && !text.isBlank()) {
            req.text(text);
        }
        telegramApiClient.execute(req);
    }
}
