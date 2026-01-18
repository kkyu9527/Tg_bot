package com.kixyu.tgbot.web.controller;

import com.kixyu.tgbot.service.WebhookService;
import com.pengrad.telegrambot.utility.BotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.pengrad.telegrambot.model.Update;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping
    public String handleWebhook(@RequestBody String body) {
        Update update = null;
        try {
            update = BotUtils.parseUpdate(body);
            webhookService.handleWebhook(update);
        } catch (RuntimeException e) {
            Integer updateId = update == null ? null : update.updateId();
            log.error("Webhook 处理异常，updateId={}, bodyLength={}", updateId, body == null ? null : body.length(), e);
        }
        return "OK";
    }
}
