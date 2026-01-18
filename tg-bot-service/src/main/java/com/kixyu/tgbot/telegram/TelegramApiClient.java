package com.kixyu.tgbot.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.response.BaseResponse;
import com.kixyu.tgbot.config.TelegramBotProperties;
import org.springframework.stereotype.Component;

@Component
public class TelegramApiClient {

    private final TelegramBot bot;

    /**
     * 根据配置的机器人 Token 初始化 TelegramBot 客户端。
     *
     * @param telegramBotProperties Telegram 机器人配置
     */
    public TelegramApiClient(TelegramBotProperties telegramBotProperties) {
        this.bot = new TelegramBot(telegramBotProperties.getToken());
    }

    /**
     * 执行一次 Telegram API 请求。
     *
     * @param request Telegram 请求对象
     * @param <T>     请求类型
     * @param <R>     响应类型
     * @return Telegram 返回的响应
     */
    public <T extends BaseRequest<T, R>, R extends BaseResponse> R execute(BaseRequest<T, R> request) {
        return bot.execute(request);
    }
}
