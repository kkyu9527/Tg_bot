package com.kixyu.tgbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Telegram 机器人配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramBotProperties {

    /**
     * Telegram 机器人访问令牌。
     */
    private String token;

    /**
     * 机器人的主人用户 ID，用于识别「主人」身份。
     */
    private Long ownerId;

    /**
     * 用于承载话题的群组 ID。
     */
    private Long groupId;

    /**
     * Telegram Webhook 回调地址。
     */
    private String webhookUrl;

    /**
     * Telegram Webhook 请求密钥，用于校验请求确实由 Telegram 推送。
     */
    private String webhookSecret;
}
