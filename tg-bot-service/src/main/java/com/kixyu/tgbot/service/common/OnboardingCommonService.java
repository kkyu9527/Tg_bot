package com.kixyu.tgbot.service.common;

import com.kixyu.tgbot.domain.entity.Message;
import com.kixyu.tgbot.domain.entity.Topic;
import com.pengrad.telegrambot.model.Chat;

public interface OnboardingCommonService {

    /**
     * 删除私聊与群聊中成对的消息（从私聊入口触发）。
     *
     * @param updateId         更新 ID，用于日志追踪
     * @param userId           私聊用户 ID
     * @param privateChatId    私聊会话 ID
     * @param repliedMessageId 被回复的私聊消息 ID
     */
    void deletePairedMessagesFromPrivate(Integer updateId, Long userId, Long privateChatId, Long repliedMessageId);

    /**
     * 删除群聊与私聊中成对的消息（从群聊入口触发）。
     *
     * @param updateId         更新 ID，用于日志追踪
     * @param groupId          群聊 ID
     * @param repliedMessageId 被回复的群聊消息 ID
     */
    void deletePairedMessagesFromGroup(Integer updateId, Long groupId, Long repliedMessageId);

    /**
     * 删除与话题关联的所有 Telegram 消息及本地消息映射数据。
     *
     * @param updateId 更新 ID，用于日志追踪
     * @param topic    需要清理的本地话题实体
     */
    void deleteTopicMessagesAndMapping(Integer updateId, Topic topic);

    /**
     * 删除 Telegram 中指定群聊的话题。
     *
     * @param groupId  群聊 ID
     * @param threadId 话题线程 ID
     */
    void deleteForumTopic(Long groupId, Long threadId);

    /**
     * 向指定会话发送纯文本消息。
     *
     * @param chatId 会话 ID，可以是群聊或私聊
     * @param text   文本内容
     */
    void sendText(Long chatId, String text);

    /**
     * 根据原始消息 ID 或转发消息 ID 查询消息映射。
     *
     * @param messageId 原始或转发消息 ID
     * @return          消息映射实体，找不到时返回 {@code null}
     */
    Message findMessageMapping(Long messageId);

    /**
     * 根据消息映射查询有效的话题。
     *
     * @param mapping   消息映射实体
     * @return          关联的话题，找不到或无效时返回 {@code null}
     */
    Topic findValidTopic(Message mapping);

    /**
     * 判断当前命令是否为无效的群主命令。
     * <p>
     * 包含以下校验：
     * <ul>
     *     <li>消息、发送人或聊天信息为空</li>
     *     <li>聊天 ID 与配置的群聊 ID 不一致</li>
     *     <li>发送人不是配置的群主</li>
     * </ul>
     *
     * @param message       Telegram 消息
     * @param chat          Telegram 聊天信息
     * @return {@code true} 表示命令无效，应直接返回；{@code false} 表示命令校验通过
     */
    boolean isInvalidGroupOwnerCommand(com.pengrad.telegrambot.model.Message message, Chat chat);
}
