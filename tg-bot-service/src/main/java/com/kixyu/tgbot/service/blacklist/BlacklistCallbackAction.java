package com.kixyu.tgbot.service.blacklist;

/**
 * 解析后的黑名单按钮回调动作。
 *
 * @param action 动作名称
 * @param userId 目标用户 ID
 * @param page   页码
 */
record BlacklistCallbackAction(String action, long userId, int page) {

    /**
     * 解析黑名单按钮回调数据。
     *
     * @param data 回调数据
     * @return     解析后的回调动作；无法解析时返回 null
     */
    static BlacklistCallbackAction parse(String data) {
        String[] parts = data.split(":");
        if (parts.length < 3 || parts.length > 4) {
            return null;
        }
        String action = parts[1];
        long userId;
        int page = 0;
        try {
            if ("list_page".equals(action) || "list_open".equals(action) || "list_close".equals(action)) {
                userId = 0L;
                page = Integer.parseInt(parts[2]);
            } else {
                userId = Long.parseLong(parts[2]);
                if (parts.length == 4) {
                    page = Integer.parseInt(parts[3]);
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return new BlacklistCallbackAction(action, userId, page);
    }
}
