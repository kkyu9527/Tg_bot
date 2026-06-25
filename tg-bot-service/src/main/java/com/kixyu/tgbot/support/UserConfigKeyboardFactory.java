package com.kixyu.tgbot.support;

import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.topic.TopicService;
import com.kixyu.tgbot.service.user.UserService;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户话题配置按钮键盘工厂。
 */
@Component
@RequiredArgsConstructor
public class UserConfigKeyboardFactory {

    private final TopicService topicService;
    private final UserService userService;

    /**
     * 根据话题 ID 和群聊 ID 构建配置按钮键盘。
     *
     * @param topicId     话题 ID
     * @param groupChatId 群聊 ID
     * @return 配置按钮键盘；未找到话题时返回空键盘
     */
    public InlineKeyboardMarkup buildForTopic(Long topicId, String groupChatId) {
        Topic topic = null;
        if (topicId != null && groupChatId != null) {
            topic = topicService.getTopicByTopicId(topicId)
                    .filter(t -> groupChatId.equals(t.getChatId()))
                    .orElse(null);
        }
        return topic == null ? new InlineKeyboardMarkup() : buildForTopic(topic);
    }

    /**
     * 根据话题实体构建配置按钮键盘。
     *
     * @param topic 话题实体
     * @return 配置按钮键盘
     */
    public InlineKeyboardMarkup buildForTopic(Topic topic) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        if (topic == null) {
            return markup;
        }

        Long userId = topic.getUserId();
        if (userId != null) {
            InlineKeyboardButton blockButton = buildBlockButton(userId);
            InlineKeyboardButton listButton = new InlineKeyboardButton("已拉黑用户列表")
                    .callbackData("bl:list:0");
            markup.addRow(blockButton, listButton);
        }

        Long topicId = topic.getTopicId();
        if (topicId != null) {
            boolean fullMode = Boolean.TRUE.equals(topic.getFullMode());
            String textOnlyLabel = fullMode ? "文字模式" : "✅ 文字模式";
            String fullModeLabel = fullMode ? "✅ 全消息模式" : "全消息模式";
            InlineKeyboardButton textOnlyButton = new InlineKeyboardButton(textOnlyLabel)
                    .callbackData("md:text:" + topicId);
            InlineKeyboardButton fullModeButton = new InlineKeyboardButton(fullModeLabel)
                    .callbackData("md:full:" + topicId);
            markup.addRow(textOnlyButton, fullModeButton);
        }

        return markup;
    }

    /**
     * 根据用户当前黑名单状态构建拉黑或取消拉黑按钮。
     *
     * @param userId 用户 ID
     * @return 黑名单操作按钮
     */
    private InlineKeyboardButton buildBlockButton(Long userId) {
        boolean blocked = userService.isBlocked(userId);
        String blockText = blocked ? "取消拉黑" : "拉黑此用户";
        String blockAction = blocked ? "unblock" : "block";
        return new InlineKeyboardButton(blockText).callbackData("bl:" + blockAction + ":" + userId);
    }
}
