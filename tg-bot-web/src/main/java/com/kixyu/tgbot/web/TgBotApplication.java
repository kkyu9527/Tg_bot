package com.kixyu.tgbot.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Telegram Bot Web 模块启动入口。
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.kixyu.tgbot")
@EntityScan(basePackages = "com.kixyu.tgbot.domain.entity")
@EnableJpaRepositories(basePackages = "com.kixyu.tgbot.domain.repository")
public class TgBotApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TgBotApplication.class, args);
    }
}
