package com.kixyu.tgbot.service.verification;

import com.kixyu.tgbot.config.BotPolicyConstants;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.service.user.UserService;
import com.kixyu.tgbot.telegram.TelegramApiClient;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.EditMessageText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户人机验证服务实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
class VerificationServiceImpl implements VerificationService {

    private static final String VERIFY_ACTION = "start";

    private final TelegramApiClient telegramApiClient;
    private final UserService userService;
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<Long, Long> cooldownUntilByUserId = new ConcurrentHashMap<>();
    private final Map<Long, Integer> failedChallengeCountsByUserId = new ConcurrentHashMap<>();

    /**
     * 发送人机验证题。
     *
     * @param user          待验证用户
     * @param privateChatId 私聊聊天 ID
     */
    @Override
    public void sendChallenge(User user, Long privateChatId) {
        if (user == null || user.id() == null || privateChatId == null) {
            return;
        }
        userService.saveOrUpdateUserInfo(user.id(), user.username(), user.firstName(), user.lastName());

        Long cooldownUntil = cooldownUntilByUserId.get(user.id());
        long now = System.currentTimeMillis();
        if (cooldownUntil != null && cooldownUntil > now) {
            sendCooldownHint(user.id(), privateChatId, cooldownUntil, now);
            return;
        }
        if (cooldownUntil != null) {
            cooldownUntilByUserId.remove(user.id());
            failedChallengeCountsByUserId.remove(user.id());
        }

        cleanupChallenges(user.id(), now);
        Challenge challenge = generateChallenge(user.id());
        String displayName = Topic.generateTopicName(user.firstName(), user.lastName(), user.username(), user.id());
        String text = "👋 嗨，" + displayName + "！\n\n"
                + "请先完成一个简单验证，验证通过后就可以给我发消息。\n\n"
                + "请等待 " + BotPolicyConstants.formatDuration(BotPolicyConstants.VERIFICATION_MIN_CLICK_DELAY)
                + " 后，点击「开始对话」。\n"
                + "验证题将在 " + BotPolicyConstants.formatDuration(BotPolicyConstants.VERIFICATION_CHALLENGE_TTL)
                + " 后过期。";

        try {
            telegramApiClient.execute(
                    telegramApiClient.createSendMessage(privateChatId, text)
                            .replyMarkup(buildKeyboard(challenge))
            );
            log.info("已发送人机验证题，userId={}, privateChatId={}", user.id(), privateChatId);
        } catch (RuntimeException e) {
            log.warn("发送人机验证题失败，userId={}, privateChatId={}", user.id(), privateChatId, e);
        }
    }

    /**
     * 处理人机验证按钮回调。
     *
     * @param callbackQuery 回调查询对象
     * @return              验证通过时返回 true，否则返回 false
     */
    @Override
    public boolean handleVerificationCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.data() == null) {
            return false;
        }
        VerificationCallback parsed = parse(callbackQuery.data());
        if (parsed == null) {
            answer(callbackQuery, "⚠️ 验证数据无效，请重新发送 /start。", true);
            return false;
        }

        Challenge challenge = challenges.get(parsed.challengeId());
        if (challenge == null) {
            answer(callbackQuery, "⏳ 验证已失效，请重新发送 /start。", true);
            deleteChallengeMessage(callbackQuery);
            return false;
        }
        if (callbackQuery.from() == null || !challenge.userId().equals(callbackQuery.from().id())) {
            answer(callbackQuery, "🛡️ 这不是你的验证题。", true);
            return false;
        }

        long now = System.currentTimeMillis();
        Long cooldownUntil = cooldownUntilByUserId.get(challenge.userId());
        if (cooldownUntil != null && cooldownUntil > now) {
            answer(callbackQuery, "⏳ 验证次数过多，请 " + remainingSeconds(cooldownUntil, now) + " 秒后再试。", true);
            return false;
        }
        if (challenge.expiresAtMillis() <= now) {
            failChallenge(callbackQuery, parsed, challenge, now, "⏳ 验证已过期，请重新发送 /start。");
            return false;
        }

        long allowAt = challenge.createdAtMillis() + BotPolicyConstants.millis(BotPolicyConstants.VERIFICATION_MIN_CLICK_DELAY);
        if (now < allowAt) {
            failChallenge(callbackQuery, parsed, challenge, now, "⏱️ 点击太快啦。请重新发送 /start 获取新的验证题。");
            return false;
        }

        if (!VERIFY_ACTION.equals(parsed.choice())) {
            failChallenge(callbackQuery, parsed, challenge, now, "⚠️ 点错了，请重新发送 /start 获取新的验证题。");
            return false;
        }

        challenges.remove(parsed.challengeId());
        cooldownUntilByUserId.remove(challenge.userId());
        failedChallengeCountsByUserId.remove(challenge.userId());

        User user = callbackQuery.from();
        userService.markVerified(user.id(), user.username(), user.firstName(), user.lastName());
        answer(callbackQuery, "✅ 验证通过", false);
        editChallengeMessage(callbackQuery);
        log.info("用户通过人机验证，userId={}", user.id());
        return true;
    }

    /**
     * 向未验证用户发送验证提示。
     *
     * @param user          未验证用户
     * @param privateChatId 私聊聊天 ID
     */
    @Override
    public void remindVerificationRequired(User user, Long privateChatId) {
        if (user == null || user.id() == null || privateChatId == null) {
            return;
        }
        String text = "🛡️ 请先完成验证后再发送消息。\n\n发送 /start 可以重新获取验证题。";
        try {
            telegramApiClient.execute(telegramApiClient.createSendMessage(privateChatId, text));
        } catch (RuntimeException e) {
            log.warn("发送验证提示失败，userId={}, privateChatId={}", user.id(), privateChatId, e);
        }
    }

    /**
     * 处理未通过的验证题。
     *
     * @param callbackQuery 回调查询对象
     * @param parsed        解析后的回调数据
     * @param challenge     当前验证题
     * @param now           当前时间戳
     * @param message       未触发冷却时展示的提示
     */
    private void failChallenge(CallbackQuery callbackQuery, VerificationCallback parsed, Challenge challenge, long now, String message) {
        challenges.remove(parsed.challengeId());
        int failures = failedChallengeCountsByUserId.merge(challenge.userId(), 1, Integer::sum);
        if (failures >= BotPolicyConstants.VERIFICATION_MAX_FAILURES) {
            long until = now + BotPolicyConstants.millis(BotPolicyConstants.VERIFICATION_FAILURE_COOLDOWN);
            cooldownUntilByUserId.put(challenge.userId(), until);
            failedChallengeCountsByUserId.remove(challenge.userId());
            answer(callbackQuery, "⛔ 验证失败次数过多，请 "
                    + BotPolicyConstants.formatDuration(BotPolicyConstants.VERIFICATION_FAILURE_COOLDOWN)
                    + " 后重新发送 /start。", true);
            deleteChallengeMessage(callbackQuery);
            return;
        }
        answer(callbackQuery, message, true);
        deleteChallengeMessage(callbackQuery);
    }

    /**
     * 发送冷却提示。
     *
     * @param userId        用户 ID
     * @param privateChatId 私聊聊天 ID
     * @param cooldownUntil 冷却结束时间戳
     * @param now           当前时间戳
     */
    private void sendCooldownHint(Long userId, Long privateChatId, long cooldownUntil, long now) {
        String text = "⏳ 验证失败次数过多，请 " + remainingSeconds(cooldownUntil, now) + " 秒后再发送 /start。";
        try {
            telegramApiClient.execute(telegramApiClient.createSendMessage(privateChatId, text));
        } catch (RuntimeException e) {
            log.warn("发送验证冷却提示失败，userId={}, privateChatId={}", userId, privateChatId, e);
        }
    }

    /**
     * 清理指定用户的旧验证题和全局过期验证题。
     *
     * @param userId 用户 ID
     * @param now    当前时间戳
     */
    private void cleanupChallenges(Long userId, long now) {
        challenges.entrySet().removeIf(entry -> {
            Challenge challenge = entry.getValue();
            return challenge == null
                    || challenge.expiresAtMillis() <= now
                    || (userId != null && userId.equals(challenge.userId()));
        });
    }

    /**
     * 构建人机验证选项键盘。
     *
     * @param challenge 验证题
     * @return          验证选项键盘
     */
    private InlineKeyboardMarkup buildKeyboard(Challenge challenge) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<String> options = challenge.options();
        markup.addRow(
                buildOption(challenge.id(), options.get(0)),
                buildOption(challenge.id(), options.get(1))
        );
        markup.addRow(
                buildOption(challenge.id(), options.get(2)),
                buildOption(challenge.id(), options.get(3))
        );
        return markup;
    }

    /**
     * 构建单个验证按钮。
     *
     * @param challengeId 验证题 ID
     * @param action      按钮动作
     * @return            验证按钮
     */
    private InlineKeyboardButton buildOption(String challengeId, String action) {
        return new InlineKeyboardButton(labelForAction(action))
                .callbackData(CALLBACK_PREFIX + challengeId + ":" + action);
    }

    /**
     * 生成人类可读的按钮文案。
     *
     * @param action 按钮动作
     * @return       按钮文案
     */
    private String labelForAction(String action) {
        return switch (action) {
            case VERIFY_ACTION -> "开始对话";
            case "help" -> "查看帮助";
            case "back" -> "返回";
            case "retry" -> "重新发送";
            default -> "继续";
        };
    }

    /**
     * 生成一道语义按钮验证题。
     *
     * @param userId 用户 ID
     * @return       验证题
     */
    private Challenge generateChallenge(Long userId) {
        long now = Instant.now().toEpochMilli();
        List<String> options = new ArrayList<>(List.of(VERIFY_ACTION, "help", "back", "retry"));
        Collections.shuffle(options);
        Challenge challenge = new Challenge(
                UUID.randomUUID().toString().replace("-", ""),
                userId,
                now,
                now + BotPolicyConstants.millis(BotPolicyConstants.VERIFICATION_CHALLENGE_TTL),
                List.copyOf(options)
        );
        challenges.put(challenge.id(), challenge);
        return challenge;
    }

    /**
     * 解析验证回调数据。
     *
     * @param data 回调数据
     * @return     解析后的验证回调数据；解析失败时返回 null
     */
    private VerificationCallback parse(String data) {
        if (data == null || !data.startsWith(CALLBACK_PREFIX)) {
            return null;
        }
        String[] parts = data.split(":");
        if (parts.length != 3) {
            return null;
        }
        String challengeId = parts[1];
        String choice = parts[2];
        if (challengeId == null || challengeId.isBlank() || choice == null || choice.isBlank()) {
            return null;
        }
        return new VerificationCallback(challengeId, choice);
    }

    /**
     * 计算剩余等待秒数。
     *
     * @param targetMillis 目标时间戳
     * @param nowMillis    当前时间戳
     * @return             剩余秒数
     */
    private long remainingSeconds(long targetMillis, long nowMillis) {
        return Math.max(1L, (long) Math.ceil((targetMillis - nowMillis) / 1000.0));
    }

    /**
     * 回复 Telegram 回调查询。
     *
     * @param callbackQuery 回调查询对象
     * @param text          提示文本
     * @param showAlert     是否弹窗展示
     */
    private void answer(CallbackQuery callbackQuery, String text, boolean showAlert) {
        telegramApiClient.answerCallback(callbackQuery, text, showAlert);
    }

    /**
     * 将验证消息更新为通过状态。
     *
     * @param callbackQuery 回调查询对象
     */
    private void editChallengeMessage(CallbackQuery callbackQuery) {
        Message message = accessibleCallbackMessage(callbackQuery);
        if (message == null) {
            return;
        }
        try {
            telegramApiClient.execute(new EditMessageText(message.chat().id(), message.messageId(), "✅ 验证通过"));
        } catch (RuntimeException e) {
            log.warn("更新验证消息失败，chatId={}, messageId={}", message.chat().id(), message.messageId(), e);
        }
    }

    /**
     * 删除验证失败的验证消息。
     *
     * @param callbackQuery 回调查询对象
     */
    private void deleteChallengeMessage(CallbackQuery callbackQuery) {
        Message message = accessibleCallbackMessage(callbackQuery);
        if (message == null) {
            return;
        }
        try {
            telegramApiClient.execute(new DeleteMessage(message.chat().id(), message.messageId()));
        } catch (RuntimeException e) {
            log.warn("删除验证消息失败，chatId={}, messageId={}", message.chat().id(), message.messageId(), e);
        }
    }

    /**
     * 从回调查询中获取可访问的消息对象。
     *
     * @param callbackQuery 回调查询对象
     * @return              可访问的消息；不可访问时返回 null
     */
    private Message accessibleCallbackMessage(CallbackQuery callbackQuery) {
        Object rawMessage = callbackQuery.maybeInaccessibleMessage();
        Message message = rawMessage instanceof Message m ? m : null;
        if (message == null || message.chat() == null || message.chat().id() == null || message.messageId() == null) {
            return null;
        }
        return message;
    }

    /**
     * 人机验证题数据。
     *
     * @param id              验证题 ID
     * @param userId          用户 ID
     * @param createdAtMillis 创建时间戳
     * @param expiresAtMillis 过期时间戳
     * @param options         候选按钮动作
     */
    private record Challenge(String id, Long userId, long createdAtMillis, long expiresAtMillis, List<String> options) {
    }

    /**
     * 人机验证回调数据。
     *
     * @param challengeId 验证题 ID
     * @param choice      用户选择的动作
     */
    private record VerificationCallback(String challengeId, String choice) {
    }
}
