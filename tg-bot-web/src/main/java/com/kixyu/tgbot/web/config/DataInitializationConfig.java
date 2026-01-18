package com.kixyu.tgbot.web.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializationConfig implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        log.info("应用启动完成，数据库表将在首次访问时自动创建");
        log.info("MessageTopic 表将自动创建以存储用户、话题和消息之间的映射关系");
    }
}
