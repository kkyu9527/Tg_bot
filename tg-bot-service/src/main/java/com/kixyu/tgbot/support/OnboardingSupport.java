package com.kixyu.tgbot.support;

import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.topic.TopicService;
import com.kixyu.tgbot.service.user.UserService;
import com.kixyu.tgbot.telegram.TelegramApiErrorUtil;
import com.kixyu.tgbot.config.TelegramBotProperties;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.model.ForumTopic;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.UserProfilePhotos;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.CreateForumTopic;
import com.pengrad.telegrambot.request.EditForumTopic;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.GetUserProfilePhotos;
import com.pengrad.telegrambot.request.PinChatMessage;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.CreateForumTopicResponse;
import com.pengrad.telegrambot.response.GetFileResponse;
import com.pengrad.telegrambot.response.GetUserProfilePhotosResponse;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OnboardingSupport {

    private static final String BLOCKED_TOPIC_NAME_PREFIX = "🚫 ";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final TelegramApiClient telegramApiClient;
    private final TopicService topicService;
    private final UserService userService;
    private final TelegramBotProperties telegramBotProperties;

    /**
     * 获取配置中的群组 ID。
     *
     * @return 群组 ID
     */
    public Long getGroupId() {
        return telegramBotProperties.getGroupId();
    }

    /**
     * 根据用户 ID 和聊天 ID 查询话题。
     *
     * @param userId 用户 ID
     * @param chatId 聊天 ID
     * @return 话题实体，可为空
     */
    public Optional<Topic> getTopicByUserIdAndChatId(Long userId, String chatId) {
        return topicService.getTopicByUserIdAndChatId(userId, chatId);
    }

    /**
     * 删除用户在指定聊天中的话题及其消息。
     *
     * @param userId 用户 ID
     * @param chatId 聊天 ID
     */
    public void handleTopicDeletion(Long userId, String chatId) {
        topicService.handleTopicDeletion(userId, chatId);
    }

    public Topic createTopic(Long userId, String username, String firstName, String lastName, Long topicId, String chatId) {
        return topicService.createTopic(userId, username, firstName, lastName, topicId, chatId);
    }

    /**
     * 向用户私聊窗口发送欢迎消息。
     *
     * @param user          目标用户
     * @param privateChatId 私聊聊天 ID
     */
    public void sendWelcomeToUser(User user, Long privateChatId) {
        String displayName = Topic.generateTopicName(user.firstName(), user.lastName(), user.username(), user.id());
        String text =
                "👋 嗨，" + displayName + "！\n\n" +
                        "🆔 你的用户 ID：" + user.id() + "\n\n" +
                        "📨 现在可以直接给我发消息，我会帮你转发给主人～\n\n" +
                        "💡 当前默认仅支持转发「纯文本消息」。如需转发图片、视频等，请联系主人开启「全消息模式」✨";
        try {
            userService.saveOrUpdateUserInfo(
                    user.id(),
                    user.username(),
                    user.firstName(),
                    user.lastName()
            );
        } catch (RuntimeException e) {
            log.warn("保存或更新用户信息失败，userId={}", user.id(), e);
        }
        try {
            telegramApiClient.execute(new SendMessage(privateChatId.longValue(), text));
        } catch (RuntimeException e) {
            log.warn("发送欢迎消息失败，userId={}, privateChatId={}", user.id(), privateChatId, e);
        }
    }

    /**
     * 判断是否为占位的 topicId（大于 Integer 最大值）。
     *
     * @param topicId 话题 ID
     * @return 是否为占位 ID
     */
    public boolean isPlaceholderTopicId(Long topicId) {
        return topicId != null && topicId > Integer.MAX_VALUE;
    }

    /**
     * 校验指定群聊中的话题是否仍然存在且可编辑。
     *
     * @param groupChatId 群聊 ID 字符串
     * @param topic       本地话题映射
     * @return 话题是否存活
     */
    public boolean isForumTopicAlive(String groupChatId, Topic topic) {
        if (topic == null || topic.getTopicId() == null || topic.getTopicId() > Integer.MAX_VALUE) {
            return false;
        }

        Long groupChatIdLong = parseChatIdLong(groupChatId);
        if (groupChatIdLong == null) {
            return false;
        }

        String topicName = topic.getTopicName();
        if (topicName == null || topicName.isBlank()) {
            topicName = "Topic " + topic.getTopicId();
        }

        try {
            BaseResponse response = telegramApiClient.execute(new EditForumTopic(groupChatIdLong, topic.getTopicId()).name(topicName));
            boolean ok = response != null && (response.isOk() || TelegramApiErrorUtil.looksLikeNotModified(response));
            if (!ok) {
                log.warn("话题存活校验失败，groupChatId={}, topicId={}, responseOk={}, error={}",
                        groupChatId, topic.getTopicId(), response == null ? null : response.isOk(), response == null ? null : response.description());
            }
            return ok;
        } catch (RuntimeException e) {
            log.warn("话题存活校验异常，groupChatId={}, topicId={}", groupChatId, topic.getTopicId(), e);
            return false;
        }
    }

    /**
     * 按拉黑状态同步话题名前缀。
     *
     * @param topic   话题映射
     * @param blocked 是否已拉黑
     */
    public void syncBlockedTopicName(Topic topic, boolean blocked) {
        if (topic == null || topic.getTopicId() == null || topic.getChatId() == null) {
            return;
        }
        Long groupChatIdLong = parseChatIdLong(topic.getChatId());
        if (groupChatIdLong == null) {
            return;
        }

        String currentTopicName = topic.getTopicName();
        if (currentTopicName == null || currentTopicName.isBlank()) {
            currentTopicName = "Topic " + topic.getTopicId();
        }
        String normalizedTopicName = removeBlockedTopicNamePrefix(currentTopicName);
        String nextTopicName = blocked ? BLOCKED_TOPIC_NAME_PREFIX + normalizedTopicName : normalizedTopicName;
        if (nextTopicName.equals(currentTopicName)) {
            return;
        }

        try {
            BaseResponse response = telegramApiClient.execute(
                    new EditForumTopic(groupChatIdLong, topic.getTopicId())
                            .name(nextTopicName)
            );
            if (response == null || !response.isOk()) {
                log.warn("同步拉黑话题名前缀失败，topicId={}, userId={}, blocked={}, responseOk={}, error={}",
                        topic.getTopicId(), topic.getUserId(), blocked, response == null ? null : response.isOk(), response == null ? null : response.description());
                return;
            }
            topic.setTopicName(nextTopicName);
            topicService.saveTopic(topic);
            log.info("已同步拉黑话题名前缀，topicId={}, userId={}, blocked={}, topicName={}",
                    topic.getTopicId(), topic.getUserId(), blocked, nextTopicName);
        } catch (RuntimeException e) {
            log.warn("同步拉黑话题名前缀异常，topicId={}, userId={}, blocked={}", topic.getTopicId(), topic.getUserId(), blocked, e);
        }
    }

    private String removeBlockedTopicNamePrefix(String topicName) {
        String result = topicName == null ? "" : topicName.strip();
        while (result.startsWith(BLOCKED_TOPIC_NAME_PREFIX)) {
            result = result.substring(BLOCKED_TOPIC_NAME_PREFIX.length()).stripLeading();
        }
        return result;
    }

    /**
     * 重建用户话题并更新本地映射，同时在新话题中发送提示消息并尝试置顶。
     *
     * @param user        用户
     * @param groupChatId 群聊 ID 字符串
     */
    public void recreateAndUpdateTopic(User user, String groupChatId) {
        log.info("准备重建话题并更新映射，userId={}, groupChatId={}, username={}", user.id(), groupChatId, user.username());
        handleTopicDeletion(user.id(), groupChatId);

        String topicName = Topic.generateTopicName(user.firstName(), user.lastName(), user.username(), user.id());
        Long threadId = createForumTopic(groupChatId, topicName);
        if (threadId == null) {
            log.warn("重建话题失败，userId={}, groupChatId={}", user.id(), groupChatId);
            return;
        }

        topicService.createTopic(
                user.id(),
                user.username(),
                user.firstName(),
                user.lastName(),
                threadId,
                groupChatId
        );

        String caption = buildNewUserCaption(user);
        Message sentMessage = sendNewUserMessageToTopic(groupChatId, threadId, user, caption);
        if (sentMessage != null && sentMessage.messageId() != null) {
            topicService.getTopicByTopicId(threadId).ifPresent(savedTopic -> {
                savedTopic.setWelcomeMessageId(sentMessage.messageId().longValue());
                topicService.saveTopic(savedTopic);
            });
            pinMessage(groupChatId, sentMessage.messageId());
        }
        log.info("重建话题并更新映射完成，userId={}, groupChatId={}, threadId={}, pinnedMessageId={}",
                user.id(), groupChatId, threadId, sentMessage == null ? null : sentMessage.messageId());
    }

    /**
     * 确保用户信息卡片存在并尝试置顶。
     *
     * @param groupChatId 群聊 ID 字符串
     * @param topic       用户话题映射
     * @param user        用户
     */
    public void ensureWelcomeMessagePinned(String groupChatId, Topic topic, User user) {
        if (topic == null || topic.getTopicId() == null || user == null) {
            return;
        }

        Long welcomeMessageId = topic.getWelcomeMessageId();
        if (welcomeMessageId != null && welcomeMessageId <= Integer.MAX_VALUE) {
            pinMessage(groupChatId, welcomeMessageId.intValue());
            return;
        }

        String caption = buildNewUserCaption(user);
        Message sentMessage = sendNewUserMessageToTopic(groupChatId, topic.getTopicId(), user, caption);
        if (sentMessage == null || sentMessage.messageId() == null) {
            log.warn("补发用户信息卡片失败，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, topic.getTopicId());
            return;
        }

        topic.setWelcomeMessageId(sentMessage.messageId().longValue());
        topicService.saveTopic(topic);
        pinMessage(groupChatId, sentMessage.messageId());
        log.info("已补发并尝试置顶用户信息卡片，userId={}, groupChatId={}, threadId={}, messageId={}",
                user.id(), groupChatId, topic.getTopicId(), sentMessage.messageId());
    }

    /**
     * 在指定群聊中创建一个新的论坛话题。
     *
     * @param groupChatId 群聊 ID 字符串
     * @param topicName   话题名称
     * @return 创建成功时返回话题的 threadId，否则返回 null
     */
    public Long createForumTopic(String groupChatId, String topicName) {
        Long groupChatIdLong = parseChatIdLong(groupChatId);
        if (groupChatIdLong == null) {
            return null;
        }

        try {
            CreateForumTopicResponse response = telegramApiClient.execute(new CreateForumTopic(groupChatIdLong, topicName));
            if (response == null || !response.isOk()) {
                log.warn("创建论坛话题失败，groupChatId={}, topicName={}, responseOk={}, error={}",
                        groupChatId, topicName, response == null ? null : response.isOk(), response == null ? null : response.description());
                return null;
            }
            ForumTopic forumTopic = response.forumTopic();
            return forumTopic == null ? null : forumTopic.messageThreadId();
        } catch (RuntimeException e) {
            log.warn("创建论坛话题异常，groupChatId={}, topicName={}", groupChatId, topicName, e);
            return null;
        }
    }

    /**
     * 构建用户配置的内联键盘。
     *
     * @param topicId       话题 ID
     * @param groupChatId   群聊 ID 字符串
     * @return          内联键盘标记up
     */
    private InlineKeyboardMarkup buildBlockInlineKeyboard(Long topicId, String groupChatId) {
        Topic topic = null;
        if (topicId != null && groupChatId != null) {
            topic = topicService.getTopicByTopicId(topicId)
                    .filter(t -> groupChatId.equals(t.getChatId()))
                    .orElse(null);
        }
        if (topic != null) {
            return buildUserConfigKeyboard(topic);
        }

        return new InlineKeyboardMarkup();
    }

    /**
     * 构建用户配置的内联键盘。
     *
     * @param topic 话题实体对象
     * @return 内联键盘标记
     */
    public InlineKeyboardMarkup buildUserConfigKeyboard(Topic topic) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

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
     * 构建拉黑用户的内联键盘按钮。
     *
     * @param userId 用户 ID
     * @return 内联键盘按钮
     */
    private InlineKeyboardButton buildBlockButton(Long userId) {
        boolean blocked = userService.isBlocked(userId);
        String blockText = blocked ? "取消拉黑" : "拉黑此用户";
        String blockAction = blocked ? "unblock" : "block";
        String blockData = "bl:" + blockAction + ":" + userId;
        return new InlineKeyboardButton(blockText).callbackData(blockData);
    }

    /**
     * 向新用户对应的话题发送提示消息，优先发送带头像的图片消息。
     *
     * @param groupChatId     群聊 ID 字符串
     * @param messageThreadId 话题 threadId
     * @param user            用户
     * @param caption         消息文案
     * @return Telegram 返回的消息对象，可为空
     */
    public Message sendNewUserMessageToTopic(String groupChatId, Long messageThreadId, User user, String caption) {
        Long groupChatIdLong = parseChatIdLong(groupChatId);
        if (groupChatIdLong == null) {
            return null;
        }

        byte[] avatarBytes = downloadUserAvatarBytes(user.id());
        InlineKeyboardMarkup markup = buildBlockInlineKeyboard(messageThreadId, groupChatId);
        if (avatarBytes != null && avatarBytes.length > 0) {
            try {
                SendResponse response = telegramApiClient.execute(
                        new SendPhoto(groupChatIdLong.longValue(), avatarBytes)
                                .fileName("avatar.jpg")
                                .caption(caption)
                                .messageThreadId(messageThreadId)
                                .replyMarkup(markup)
                );
                return response == null ? null : response.message();
            } catch (RuntimeException e) {
                log.warn("发送新用户图片消息异常，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, messageThreadId, e);
                return null;
            }
        }

        try {
            SendResponse response = telegramApiClient.execute(
                    new SendMessage(groupChatIdLong.longValue(), caption)
                            .messageThreadId(messageThreadId)
                            .replyMarkup(markup)
            );
            return response == null ? null : response.message();
        } catch (RuntimeException e) {
            log.warn("发送新用户文本消息异常，userId={}, groupChatId={}, threadId={}", user.id(), groupChatId, messageThreadId, e);
            return null;
        }
    }

    /**
     * 在指定群聊中置顶一条消息。
     *
     * @param groupChatId 群聊 ID 字符串
     * @param messageId   消息 ID
     */
    public boolean pinMessage(String groupChatId, Integer messageId) {
        Long groupChatIdLong = parseChatIdLong(groupChatId);
        if (groupChatIdLong == null || messageId == null) {
            return false;
        }
        try {
            BaseResponse response = telegramApiClient.execute(
                    new PinChatMessage(groupChatIdLong, messageId).disableNotification(true)
            );
            if (response == null || !response.isOk()) {
                log.warn("置顶消息失败，groupChatId={}, messageId={}, responseOk={}, error={}",
                        groupChatId, messageId, response == null ? null : response.isOk(), response == null ? null : response.description());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("置顶消息失败，groupChatId={}, messageId={}", groupChatId, messageId, e);
            return false;
        }
    }

    /**
     * 构建新用户提示消息文案。
     *
     * @param user 用户
     * @return 格式化后的提示文本
     */
    public String buildNewUserCaption(User user) {
        String displayName = Topic.generateTopicName(user.firstName(), user.lastName(), user.username(), user.id());

        StringBuilder text = new StringBuilder();
        text.append("✨ 新用户已开始对话\n\n");
        text.append("👤 名字：").append(displayName).append("\n");
        text.append("🆔 用户 ID：").append(user.id());
        if (user.username() != null && !user.username().isBlank()) {
            text.append("\n📛 用户名：@").append(user.username());
        }
        return text.toString();
    }

    /**
     * 下载用户头像的二进制数据，优先选取分辨率最高的一张。
     *
     * @param userId 用户 ID
     * @return 头像二进制数据；若失败或不存在头像则返回 null
     */
    public byte[] downloadUserAvatarBytes(Long userId) {
        try {
            GetUserProfilePhotosResponse photosResponse = telegramApiClient.execute(new GetUserProfilePhotos(userId).limit(1));
            UserProfilePhotos photos = photosResponse == null ? null : photosResponse.photos();
            if (photos == null || photos.totalCount() == null || photos.totalCount() == 0) {
                return null;
            }

            PhotoSize[][] photoGroups = photos.photos();
            if (photoGroups == null || photoGroups.length == 0 || photoGroups[0] == null || photoGroups[0].length == 0) {
                return null;
            }

            PhotoSize best = java.util.Arrays.stream(photoGroups[0])
                    .max(Comparator.comparingLong(p -> p.fileSize() == null ? 0L : p.fileSize()))
                    .orElse(photoGroups[0][photoGroups[0].length - 1]);

            GetFileResponse fileResponse = telegramApiClient.execute(new GetFile(best.fileId()));
            File tgFile = fileResponse == null ? null : fileResponse.file();
            if (tgFile == null || tgFile.filePath() == null || tgFile.filePath().isBlank()) {
                return null;
            }

            String token = telegramBotProperties.getToken();
            String url = "https://api.telegram.org/file/bot" + token + "/" + tgFile.filePath();

            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }

            return null;
        } catch (Exception e) {
            log.warn("下载用户头像失败，userId={}", userId, e);
            return null;
        }
    }

    /**
     * 将字符串形式的聊天 ID 安全地解析为 Long。
     *
     * @param groupChatId 聊天 ID 字符串
     * @return 解析成功的 Long 值，失败时返回 null
     */
    public Long parseChatIdLong(String groupChatId) {
        if (groupChatId == null || groupChatId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(groupChatId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
