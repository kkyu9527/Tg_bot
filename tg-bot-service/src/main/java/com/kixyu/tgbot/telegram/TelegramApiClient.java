package com.kixyu.tgbot.telegram;

import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.config.BotPolicyConstants;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.DeleteForumTopic;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Telegram Bot API 客户端封装。
 */
@Component
@Slf4j
public class TelegramApiClient {

    private static final ScheduledExecutorService DELETE_SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "telegram-delete-scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final TelegramBot bot;
    private final String token;

    /**
     * 根据配置的机器人 Token 初始化 TelegramBot 客户端。
     *
     * @param telegramBotProperties Telegram 机器人配置
     */
    public TelegramApiClient(TelegramBotProperties telegramBotProperties) {
        this.token = telegramBotProperties.getToken();
        this.bot = new TelegramBot(token);
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
     * 创建发送文本消息请求。
     *
     * @param chatId 聊天 ID
     * @param text   消息文本
     * @return       发送消息请求
     */
    public SendMessage createSendMessage(Long chatId, String text) {
        Objects.requireNonNull(chatId, "chatId");
        return new SendMessage(chatId.longValue(), text);
    }

    /**
     * 创建发送图片消息请求。
     *
     * @param chatId 聊天 ID
     * @param photo  图片字节内容
     * @return       发送图片请求
     */
    public SendPhoto createSendPhoto(Long chatId, byte[] photo) {
        Objects.requireNonNull(chatId, "chatId");
        return new SendPhoto(chatId.longValue(), photo);
    }

    /**
     * 回复 Telegram 回调查询。
     *
     * @param callbackQuery 回调查询对象
     * @param text          提示文本
     * @param showAlert     是否弹窗展示
     */
    public void answerCallback(CallbackQuery callbackQuery, String text, boolean showAlert) {
        if (callbackQuery == null || callbackQuery.id() == null) {
            return;
        }
        AnswerCallbackQuery request = new AnswerCallbackQuery(callbackQuery.id());
        if (text != null && !text.isBlank()) {
            request.text(text);
            request.showAlert(showAlert);
        }
        execute(request);
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
     * 按默认延迟删除发送成功的消息。
     *
     * @param chatId   聊天 ID
     * @param response 发送响应对象
     */
    public void scheduleDeleteIfOk(Long chatId, SendResponse response) {
        scheduleDeleteIfOk(chatId, response, BotPolicyConstants.millis(BotPolicyConstants.DEFAULT_HINT_AUTO_DELETE_DELAY));
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
        DELETE_SCHEDULER.schedule(() -> {
            try {
                bot.execute(new DeleteMessage(chatId, messageId));
            } catch (RuntimeException e) {
                log.debug("删除 Telegram 消息失败，chatId={}, messageId={}", chatId, messageId, e);
            }
        }, Math.max(delayMillis, 0L), TimeUnit.MILLISECONDS);
    }

    /**
     * 按默认延迟删除指定聊天中的消息。
     *
     * @param chatId    聊天 ID
     * @param messageId 消息 ID
     */
    public void scheduleDelete(Long chatId, Integer messageId) {
        scheduleDelete(chatId, messageId, BotPolicyConstants.millis(BotPolicyConstants.DEFAULT_HINT_AUTO_DELETE_DELAY));
    }

    /**
     * 删除指定群聊中的论坛话题。
     *
     * @param chatId          群聊 ID
     * @param messageThreadId 话题线程 ID
     * @return Telegram API 响应
     */
    public BaseResponse deleteForumTopic(Long chatId, Long messageThreadId) {
        if (chatId == null || messageThreadId == null) {
            return null;
        }
        return execute(new DeleteForumTopic(chatId, messageThreadId));
    }

    /**
     * 根据 Telegram filePath 下载文件内容。
     *
     * @param filePath Telegram File API 返回的文件路径
     * @return 文件内容；下载失败时返回 null
     */
    public byte[] downloadFileBytes(String filePath) {
        if (token == null || token.isBlank() || filePath == null || filePath.isBlank()) {
            return null;
        }

        String url = "https://api.telegram.org/file/bot" + token + "/" + filePath;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            log.warn("下载 Telegram 文件失败，filePath={}, status={}", filePath, response.statusCode());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("下载 Telegram 文件被中断，filePath={}", filePath, e);
            return null;
        } catch (IOException e) {
            log.warn("下载 Telegram 文件异常，filePath={}", filePath, e);
            return null;
        }
    }
}
