package com.kixyu.tgbot.telegram;

import com.kixyu.tgbot.config.TelegramBotProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
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

    /**
     * 如果发送响应成功，异步计划删除指定聊天中的消息。
     *
     * @param chatId        聊天 ID
     * @param response      发送响应对象
     * @param delayMillis   延迟删除时间（毫秒）
     */
    public void scheduleDeleteIfOk(Long chatId, SendResponse response, long delayMillis) {
        if (chatId == null || response == null || response.message() == null) {
            return;
        }
        Integer messageId = response.message().messageId();
        if (messageId == null) {
            return;
        }
        scheduleDelete(chatId, messageId, delayMillis);
    }

    /**
     * 异步计划删除指定聊天中的消息。
     *
     * @param chatId        聊天 ID
     * @param messageId     消息 ID
     * @param delayMillis   延迟删除时间（毫秒）
     */
    public void scheduleDelete(Long chatId, Integer messageId, long delayMillis) {
        if (chatId == null || messageId == null) {
            return;
        }
        new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
                bot.execute(new DeleteMessage(chatId, messageId));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ignored) {
            }
        }).start();
    }
}
