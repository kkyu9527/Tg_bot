package com.kixyu.tgbot.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class WebhookRequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
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
        String secretToken = request.getHeader("X-Telegram-Bot-Api-Secret-Token");
        String remote = request.getRemoteAddr();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long tookMs = (System.nanoTime() - startNanos) / 1_000_000;
            int status = response.getStatus();
            log.info("Webhook 请求，status={}, tookMs={}, method={}, uri={}, remote={}, contentType={}, contentLength={}, userAgent={}, secretToken={}",
                    status, tookMs, method, uri, remote, contentType, contentLength, userAgent, secretToken);
        }
    }
}

