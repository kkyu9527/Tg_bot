package com.kixyu.tgbot.service.blacklist;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;

/**
 * 黑名单命令与按钮回调处理服务。
 */
public interface BlacklistCommandService {

    /**
     * 尝试处理群内黑名单管理消息。
     *
     * @param message Telegram 消息
     * @param chat    消息所在聊天
     * @return 如果消息已被黑名单功能消费则返回 true，否则返回 false
     */
    boolean handleIfBlacklistMessage(Message message, Chat chat);

    /**
     * 尝试处理黑名单相关按钮回调。
     *
     * @param callbackQuery Telegram 回调查询
     * @return 如果回调已被黑名单功能消费则返回 true，否则返回 false
     */
    boolean handleIfBlacklistCallback(CallbackQuery callbackQuery);
}
