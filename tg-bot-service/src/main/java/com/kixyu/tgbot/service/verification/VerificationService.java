package com.kixyu.tgbot.service.verification;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.User;

/**
 * 用户人机验证服务。
 */
public interface VerificationService {

    String CALLBACK_PREFIX = "vf:";

    /**
     * 发送人机验证题。
     *
     * @param user          待验证用户
     * @param privateChatId 私聊聊天 ID
     */
    void sendChallenge(User user, Long privateChatId);

    /**
     * 处理人机验证回调。
     *
     * @param callbackQuery 回调查询对象
     * @return 验证通过时返回 true，否则返回 false
     */
    boolean handleVerificationCallback(CallbackQuery callbackQuery);

    /**
     * 向未验证用户发送验证提示。
     *
     * @param user          未验证用户
     * @param privateChatId 私聊聊天 ID
     */
    void remindVerificationRequired(User user, Long privateChatId);
}
