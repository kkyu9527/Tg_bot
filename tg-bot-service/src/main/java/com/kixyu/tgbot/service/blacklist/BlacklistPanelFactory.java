package com.kixyu.tgbot.service.blacklist;

import com.kixyu.tgbot.config.BotPolicyConstants;
import com.kixyu.tgbot.domain.entity.Topic;
import com.kixyu.tgbot.domain.entity.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import java.util.List;

/**
 * 黑名单分页管理面板构建工具。
 */
final class BlacklistPanelFactory {

    private static final String COMMAND_HELP_TEXT = """

            文本命令：
            .拉黑：在当前用户话题拉黑该用户
            .取消拉黑：在当前用户话题取消拉黑
            .拉黑 用户ID：按用户 ID 拉黑
            .取消拉黑 用户ID：按用户 ID 取消拉黑
            .黑名单：查看黑名单成员
            .退出黑名单：关闭当前黑名单面板""";

    /**
     * 工具类，禁止实例化。
     */
    private BlacklistPanelFactory() {
    }

    /**
     * 构建黑名单分页管理面板文本。
     *
     * @param blockedUsers 已拉黑用户列表
     * @param page         页码
     * @return             面板文本
     */
    static String buildText(List<User> blockedUsers, int page) {
        int total = blockedUsers == null ? 0 : blockedUsers.size();
        if (total == 0) {
            return "✅ 当前没有已拉黑的用户。"
                    + COMMAND_HELP_TEXT
                    + buildAutoDeleteHint();
        }

        int totalPages = totalPages(total);
        return "🧾 黑名单成员\n\n"
                + "共 " + total + " 人，当前第 " + (page + 1) + " / " + totalPages + " 页。"
                + COMMAND_HELP_TEXT
                + buildAutoDeleteHint();
    }

    /**
     * 构建黑名单分页管理面板按钮。
     *
     * @param blockedUsers 已拉黑用户列表
     * @param page         页码
     * @return             面板按钮
     */
    static InlineKeyboardMarkup buildKeyboard(List<User> blockedUsers, int page) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        int total = blockedUsers == null ? 0 : blockedUsers.size();
        if (total == 0) {
            markup.addRow(new InlineKeyboardButton("关闭").callbackData("bl:list_close:0"));
            return markup;
        }

        int pageSize = BotPolicyConstants.BLACKLIST_PAGE_SIZE;
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        for (int i = fromIndex; i < toIndex; i++) {
            User user = blockedUsers.get(i);
            if (user == null || user.getUserId() == null) {
                continue;
            }
            String text = compactButtonText(displayName(user), user.getUserId());
            markup.addRow(new InlineKeyboardButton(text).callbackData("bl:list_unblock:" + user.getUserId() + ":" + page));
        }

        int totalPages = totalPages(total);
        int previousPage = Math.max(0, page - 1);
        int nextPage = Math.min(totalPages - 1, page + 1);
        InlineKeyboardButton previous = new InlineKeyboardButton("上一页").callbackData("bl:list_page:" + previousPage);
        InlineKeyboardButton indicator = new InlineKeyboardButton((page + 1) + "/" + totalPages).callbackData("bl:list_page:" + page);
        InlineKeyboardButton next = new InlineKeyboardButton("下一页").callbackData("bl:list_page:" + nextPage);
        markup.addRow(previous, indicator, next);
        markup.addRow(new InlineKeyboardButton("关闭").callbackData("bl:list_close:0"));
        return markup;
    }

    /**
     * 将请求页码修正到有效范围内。
     *
     * @param requestedPage 请求页码
     * @param total         总数量
     * @return              有效页码
     */
    static int normalizePage(int requestedPage, int total) {
        int totalPages = totalPages(total);
        if (requestedPage < 0) {
            return 0;
        }
        return Math.min(requestedPage, totalPages - 1);
    }

    /**
     * 计算总页数。
     *
     * @param total 总数量
     * @return      总页数，至少为 1
     */
    private static int totalPages(int total) {
        int pageSize = Math.max(1, BotPolicyConstants.BLACKLIST_PAGE_SIZE);
        return Math.max(1, (int) Math.ceil(total / (double) pageSize));
    }

    /**
     * 构建黑名单用户按钮文本。
     *
     * @param name   展示名称
     * @param userId 用户 ID
     * @return       按钮文本
     */
    private static String compactButtonText(String name, Long userId) {
        String displayName = name == null || name.isBlank() ? "User " + userId : name;
        if (displayName.length() > 18) {
            displayName = displayName.substring(0, 18) + "...";
        }
        return userId + " (" + displayName  + ")";
    }

    /**
     * 构建黑名单列表自动删除提示。
     *
     * @return 自动删除提示文本
     */
    private static String buildAutoDeleteHint() {
        String durationText = BotPolicyConstants.formatDuration(BotPolicyConstants.BLOCKED_LIST_AUTO_DELETE_DELAY);
        return "\n\n⏱️ 提示：这条黑名单管理面板将在 " + durationText + " 后自动删除。";
    }

    /**
     * 获取用户展示名称。
     *
     * @param user 用户实体
     * @return     展示名称
     */
    private static String displayName(User user) {
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return "@" + user.getUsername();
        }
        return Topic.generateTopicName(user.getFirstName(), user.getLastName(), null, user.getUserId());
    }
}
