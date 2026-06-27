package com.kixyu.tgbot.web.config;

import com.kixyu.tgbot.config.TelegramBotProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Webhook 请求日志过滤器。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
class WebhookRequestLoggingFilter extends OncePerRequestFilter {

    private static final String TELEGRAM_SECRET_TOKEN_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramBotProperties telegramBotProperties;

    /**
     * 过滤 Webhook 请求，记录请求信息。
     *
     * @param request           HTTP 请求
     * @param response          HTTP 响应
     * @param filterChain       过滤器链
     * @throws ServletException 如果处理请求时发生 Servlet 异常
     * @throws IOException      如果处理请求时发生 I/O 异常
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean isWebhook = uri != null && uri.startsWith("/webhook");
        if (!isWebhook) {
            filterChain.doFilter(request, response);
            return;
        }

        long startNanos = System.nanoTime();
        String method = request.getMethod();
        String contentType = request.getContentType();
        long contentLength = request.getContentLengthLong();
        String userAgent = request.getHeader("User-Agent");
        String secretToken = request.getHeader(TELEGRAM_SECRET_TOKEN_HEADER);
        String expectedSecret = normalizeSecret(telegramBotProperties.getWebhookSecret());
        boolean secretValidationEnabled = expectedSecret != null;
        boolean secretTokenPresent = secretToken != null && !secretToken.isBlank();
        String remote = request.getRemoteAddr();

        try {
            if (secretValidationEnabled && !matchesSecret(expectedSecret, secretToken)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                log.warn("Webhook secret 校验失败，method={}, uri={}, remote={}, secretTokenPresent={}",
                        method, uri, remote, secretTokenPresent);
                return;
            }

            filterChain.doFilter(request, response);
        } finally {
            long tookMs = (System.nanoTime() - startNanos) / 1_000_000;
            int status = response.getStatus();
            log.info("Webhook 请求，status={}, tookMs={}, method={}, uri={}, remote={}, contentType={}, contentLength={}, userAgent={}, secretValidationEnabled={}, secretTokenPresent={}",
                    status, tookMs, method, uri, remote, contentType, contentLength, userAgent, secretValidationEnabled, secretTokenPresent);
        }
    }

    /**
     * 判断请求头中的 Webhook 密钥是否与配置值一致。
     *
     * @param expectedSecret   配置的 Webhook 密钥
     * @param actualSecret     请求头中的 Webhook 密钥
     * @return                 密钥一致时返回 true，否则返回 false
     */
    private static boolean matchesSecret(String expectedSecret, String actualSecret) {
        if (actualSecret == null) {
            return false;
        }
        byte[] expected = expectedSecret.getBytes(StandardCharsets.UTF_8);
        byte[] actual = actualSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * 标准化 Webhook 密钥配置。
     *
     * @param raw   原始 Webhook 密钥
     * @return      标准化后的 Webhook 密钥；未配置时返回 null
     */
    private static String normalizeSecret(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }
}
