package com.kixyu.tgbot.web.config;

import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.botcommandscope.BotCommandScopeAllGroupChats;
import com.pengrad.telegrambot.model.botcommandscope.BotCommandScopeAllPrivateChats;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.request.SetWebhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class TelegramBotStartupConfig {

    /**
     * 初始化 Telegram 机器人的 Webhook。
     *
     * @param telegramApiClient     Telegram API 客户端
     * @param telegramBotProperties 机器人配置属性
     * @return                      ApplicationRunner 实例，用于在应用启动时执行 Webhook 初始化
     */
    @Bean
    public ApplicationRunner telegramWebhookInitializer(TelegramApiClient telegramApiClient, TelegramBotProperties telegramBotProperties) {
        return args -> {
            String rawWebhookUrl = telegramBotProperties.getWebhookUrl();
            String webhookUrl = sanitizeWebhookUrl(rawWebhookUrl);
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.warn("未配置 webhookUrl，跳过 setWebhook");
                return;
            }

            try {
                telegramApiClient.execute(new SetWebhook().url(webhookUrl));
            } catch (RuntimeException e) {
                log.warn("setWebhook 异常，webhookUrl={}", webhookUrl, e);
            }

            initBotCommands(telegramApiClient);
        };
    }

    /**
     * 初始化 Telegram 机器人的命令菜单。
     *
     * @param telegramApiClient Telegram API 客户端
     */
    private static void initBotCommands(TelegramApiClient telegramApiClient) {
        try {
            BotCommand start = new BotCommand("start", "开始与机器人对话");
            BotCommand info = new BotCommand("info", "查看当前账号信息");
            BotCommand delete = new BotCommand("delete", "回复一条消息并发送以撤回该消息");
            BotCommand chatId = new BotCommand("chatid", "获取当前群组 ID");
            BotCommand closeTopic = new BotCommand("close_topic", "删除当前话题并清理数据");
            BotCommand block = new BotCommand("block", "拉黑或取消拉黑指定用户");
            BotCommand fullMode = new BotCommand("full_mode", "选择当前话题的消息转发模式");

            BotCommand[] defaultCommands = new BotCommand[]{start, info};
            BotCommand[] privateCommands = new BotCommand[]{start, info, delete};
            BotCommand[] groupCommands = new BotCommand[]{delete, chatId, closeTopic, block, fullMode};

            telegramApiClient.execute(new SetMyCommands(defaultCommands));
            telegramApiClient.execute(new SetMyCommands(privateCommands).scope(new BotCommandScopeAllPrivateChats()));
            telegramApiClient.execute(new SetMyCommands(groupCommands).scope(new BotCommandScopeAllGroupChats()));
        } catch (RuntimeException e) {
            log.warn("设置命令菜单失败", e);
        }
    }

    /**
     * 清理 Webhook URL 中的无效字符。
     *
     * @param raw   原始 Webhook URL
     * @return      清理后的 Webhook URL
     */
    private static String sanitizeWebhookUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return value;
        }
        value = value.replace("`", "").replace("\"", "").trim();
        int firstWhitespace = -1;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                firstWhitespace = i;
                break;
            }
        }
        if (firstWhitespace >= 0) {
            value = value.substring(0, firstWhitespace);
        }

        while (!value.isEmpty()) {
            char last = value.charAt(value.length() - 1);
            if (last == ',' || last == '，' || last == ';' || last == '；') {
                value = value.substring(0, value.length() - 1).trim();
                continue;
            }
            int type = Character.getType(last);
            if (Character.isWhitespace(last) || Character.isISOControl(last) || type == Character.FORMAT) {
                value = value.substring(0, value.length() - 1).trim();
                continue;
            }
            break;
        }
        return value;
    }
}
