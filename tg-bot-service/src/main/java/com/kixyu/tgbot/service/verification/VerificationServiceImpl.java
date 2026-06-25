package com.kixyu.tgbot.service.verification;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
class VerificationServiceImpl implements VerificationService {

    private static final int OPTION_COUNT = 6;

    private final TelegramApiClient telegramApiClient;
    private final UserService userService;

    @Override
    public void sendChallenge(User user, Long privateChatId) {
        if (user == null || user.id() == null || privateChatId == null) {
            return;
        }
        userService.saveOrUpdateUserInfo(user.id(), user.username(), user.firstName(), user.lastName());

        Challenge challenge = generateChallenge();
        String displayName = Topic.generateTopicName(user.firstName(), user.lastName(), user.username(), user.id());
        String text = "👋 嗨，" + displayName + "！\n\n"
                + "请先完成一个简单验证，验证通过后就可以给我发消息。\n\n"
                + "请选择正确答案：\n"
                + challenge.question();

        try {
            telegramApiClient.execute(
                    telegramApiClient.createSendMessage(privateChatId, text)
                            .replyMarkup(buildKeyboard(user.id(), challenge))
            );
            log.info("已发送人机验证题，userId={}, privateChatId={}", user.id(), privateChatId);
        } catch (RuntimeException e) {
            log.warn("发送人机验证题失败，userId={}, privateChatId={}", user.id(), privateChatId, e);
        }
    }

    @Override
    public boolean handleVerificationCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.data() == null) {
            return false;
        }
        VerificationCallback parsed = parse(callbackQuery.data());
        if (parsed == null) {
            answer(callbackQuery, "验证数据无效，请重新发送 /start。", true);
            return false;
        }
        if (callbackQuery.from() == null || !parsed.userId().equals(callbackQuery.from().id())) {
            answer(callbackQuery, "这不是你的验证题。", true);
            return false;
        }
        if (!parsed.answer().equals(parsed.choice())) {
            answer(callbackQuery, "回答错误，请重新发送 /start 获取新的验证题。", true);
            deleteChallengeMessage(callbackQuery);
            return false;
        }

        User user = callbackQuery.from();
        userService.markVerified(user.id(), user.username(), user.firstName(), user.lastName());
        answer(callbackQuery, "验证通过", false);
        editChallengeMessage(callbackQuery);
        log.info("用户通过人机验证，userId={}", user.id());
        return true;
    }

    @Override
    public void remindVerificationRequired(User user, Long privateChatId) {
        if (user == null || user.id() == null || privateChatId == null) {
            return;
        }
        String text = "请先完成验证后再发送消息。发送 /start 可以重新获取验证题。";
        try {
            telegramApiClient.execute(telegramApiClient.createSendMessage(privateChatId, text));
        } catch (RuntimeException e) {
            log.warn("发送验证提示失败，userId={}, privateChatId={}", user.id(), privateChatId, e);
        }
    }

    private InlineKeyboardMarkup buildKeyboard(Long userId, Challenge challenge) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<Integer> options = challenge.options();
        markup.addRow(
                buildOption(userId, challenge.answer(), options.get(0)),
                buildOption(userId, challenge.answer(), options.get(1)),
                buildOption(userId, challenge.answer(), options.get(2))
        );
        markup.addRow(
                buildOption(userId, challenge.answer(), options.get(3)),
                buildOption(userId, challenge.answer(), options.get(4)),
                buildOption(userId, challenge.answer(), options.get(5))
        );
        return markup;
    }

    private InlineKeyboardButton buildOption(Long userId, int answer, int option) {
        return new InlineKeyboardButton(String.valueOf(option))
                .callbackData(CALLBACK_PREFIX + userId + ":" + answer + ":" + option);
    }

    private Challenge generateChallenge() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean addition = random.nextBoolean();
        int left;
        int right;
        int answer;
        if (addition) {
            left = random.nextInt(0, 101);
            right = random.nextInt(0, 101 - left);
            answer = left + right;
        } else {
            left = random.nextInt(0, 101);
            right = random.nextInt(0, left + 1);
            answer = left - right;
        }

        Set<Integer> options = new LinkedHashSet<>();
        options.add(answer);
        while (options.size() < OPTION_COUNT) {
            int offset = random.nextInt(-12, 13);
            int candidate = answer + offset;
            if (candidate >= 0 && candidate <= 100) {
                options.add(candidate);
            }
        }
        List<Integer> shuffledOptions = new ArrayList<>(options);
        Collections.shuffle(shuffledOptions);

        String operator = addition ? " + " : " - ";
        return new Challenge(left + operator + right + " = ?", answer, shuffledOptions);
    }

    private VerificationCallback parse(String data) {
        if (data == null || !data.startsWith(CALLBACK_PREFIX)) {
            return null;
        }
        String[] parts = data.split(":");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new VerificationCallback(
                    Long.parseLong(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void answer(CallbackQuery callbackQuery, String text, boolean showAlert) {
        telegramApiClient.answerCallback(callbackQuery, text, showAlert);
    }

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

    private Message accessibleCallbackMessage(CallbackQuery callbackQuery) {
        Object rawMessage = callbackQuery.maybeInaccessibleMessage();
        Message message = rawMessage instanceof Message m ? m : null;
        if (message == null || message.chat() == null || message.chat().id() == null || message.messageId() == null) {
            return null;
        }
        return message;
    }

    private record Challenge(String question, int answer, List<Integer> options) {
    }

    private record VerificationCallback(Long userId, Integer answer, Integer choice) {
    }
}
