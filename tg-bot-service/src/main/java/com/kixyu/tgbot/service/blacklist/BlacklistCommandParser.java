package com.kixyu.tgbot.service.blacklist;

import java.util.Locale;

/**
 * 黑名单文本命令解析器。
 */
final class BlacklistCommandParser {

    /**
     * 工具类，禁止实例化。
     */
    private BlacklistCommandParser() {
    }

    /**
     * 解析黑名单文本命令。
     *
     * @param rawText 原始消息文本
     * @return        解析后的命令；无法解析时返回 null
     */
    static Command parse(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            return null;
        }

        String normalized = normalizeCommandText(text);
        Command directCommand = switch (normalized) {
            case "拉黑", "block", "ban" -> new Command(Action.BLOCK, null);
            case "取消拉黑", "解除拉黑", "unblock", "unban" -> new Command(Action.UNBLOCK, null);
            case "黑名单", "查看黑名单", "查看黑名单成员", "blacklist", "blocked" ->
                    new Command(Action.LIST, null);
            case "退出黑名单", "退出查看黑名单", "退出查看黑名单成员", "exit", "exit_blacklist", "close_blacklist" ->
                    new Command(Action.EXIT_LIST, null);
            default -> null;
        };
        if (directCommand != null) {
            return directCommand;
        }

        Long embeddedUnblockUserId = parseEmbeddedUnblockUserId(normalized);
        if (embeddedUnblockUserId != null) {
            return new Command(Action.UNBLOCK, embeddedUnblockUserId);
        }

        String[] parts = normalized.split("\\s+");
        if (parts.length >= 2) {
            Long userId = parseUserId(parts[1]);
            if (userId == null) {
                return null;
            }
            return switch (parts[0]) {
                case "拉黑", "block", "ban" -> new Command(Action.BLOCK, userId);
                case "取消拉黑", "解除拉黑", "unblock", "unban" -> new Command(Action.UNBLOCK, userId);
                default -> null;
            };
        }
        return null;
    }

    /**
     * 黑名单文本命令动作。
     */
    enum Action {
        BLOCK,
        UNBLOCK,
        LIST,
        EXIT_LIST
    }

    /**
     * 解析后的黑名单文本命令。
     *
     * @param action 黑名单动作
     * @param userId 目标用户 ID
     */
    record Command(Action action, Long userId) {
    }

    /**
     * 规范化命令文本。
     *
     * @param text 原始命令文本
     * @return     规范化后的命令文本
     */
    private static String normalizeCommandText(String text) {
        String normalized = text.trim();
        if (normalized.startsWith("/") || normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        int atIndex = normalized.indexOf('@');
        int spaceIndex = normalized.indexOf(' ');
        if (atIndex > 0 && (spaceIndex < 0 || atIndex < spaceIndex)) {
            String commandPart = normalized.substring(0, atIndex);
            String argsPart = spaceIndex > 0 ? normalized.substring(spaceIndex) : "";
            normalized = commandPart + argsPart;
        }
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 从内嵌取消拉黑命令中解析用户 ID。
     *
     * @param text 规范化后的命令文本
     * @return     用户 ID；无法解析时返回 null
     */
    private static Long parseEmbeddedUnblockUserId(String text) {
        String prefix = "unblock_";
        if (!text.startsWith(prefix)) {
            return null;
        }
        return parseUserId(text.substring(prefix.length()));
    }

    /**
     * 解析用户 ID。
     *
     * @param text 用户 ID 文本
     * @return     用户 ID；无法解析时返回 null
     */
    private static Long parseUserId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
