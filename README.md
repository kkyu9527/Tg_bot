# tg-bot

基于 Spring Boot 3 和 Telegram Bot API 的私聊客服/消息中继机器人。用户在私聊中给机器人发消息，机器人会为每个用户在指定 Telegram 论坛群组里创建独立话题；主人在对应话题中回复后，消息会自动回流到用户私聊。

联系我: [![Telegram](https://img.shields.io/badge/Telegram-26A69A?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/kkyu9527s_bot)

## 功能特性

- 私聊消息自动转发到指定群组的用户专属话题
- 主人在群话题中回复，自动回流到对应用户私聊
- 自动创建、恢复、重建用户话题，并维护用户、话题、消息映射
- 支持文本、图片、视频、文件、音频、语音、贴纸、位置、联系人、投票、骰子、游戏等消息类型识别
- 支持媒体组/相册聚合转发和回流
- 支持回复关系同步，尽量保留两端对话上下文
- 支持消息编辑同步：文本和 caption 编辑会同步到另一端
- 支持 `/delete` 撤回两端已映射消息
- 支持 `/start` 人机验证，未验证用户不能发起中继
- 支持用户黑名单、分页查看、按钮操作和话题状态刷新
- 支持每个用户话题切换「文字模式 / 全消息模式」
- 支持低信任用户文本节流，降低新用户刷屏风险
- 启动时自动设置 Webhook 和 Bot 命令菜单
- 提示消息、命令消息、黑名单面板等临时消息会自动清理

## 工作流

1. 用户私聊机器人并发送 `/start`。
2. 用户完成简单的人机验证。
3. 机器人在配置的 Telegram 论坛群组中创建该用户的专属话题，并发送置顶的用户信息/操作面板。
4. 用户私聊机器人发送消息，消息被复制到该用户的话题。
5. 主人在该话题中发送消息，消息被复制回用户私聊。
6. 后续回复、撤回、编辑、黑名单和转发模式都通过数据库中的消息映射关系完成。

## 项目结构

```text
tg-bot
├── tg-bot-domain   # JPA 实体与仓储接口
├── tg-bot-service  # 核心业务逻辑、转发回流、命令、黑名单、Telegram API 封装
├── tg-bot-web      # Spring Boot 启动入口、Webhook 控制器、启动初始化配置
├── docker-compose.yml
└── pom.xml
```

## 技术栈

- Java 21
- Spring Boot 3.5.x
- Spring Web
- Spring Data JPA
- MySQL
- Lombok
- pengrad `java-telegram-bot-api`
- Docker / Docker Compose

## Bot 命令

### 私聊命令

| 命令 | 说明 |
| --- | --- |
| `/start` | 开始使用机器人；未验证用户会先进入人机验证 |
| `/info` | 查看当前 Telegram 账号信息 |
| `/delete` | 回复一条已转发/回流的消息后发送，用于撤回两端对应消息 |

### 群组命令

以下命令需要在配置的目标群组中使用，涉及管理能力的操作只允许 `TELEGRAM_BOT_OWNER_ID` 对应用户执行。

| 命令 | 说明 |
| --- | --- |
| `/chatid` | 获取当前群组 ID，方便配置 `TELEGRAM_BOT_GROUP_ID` |
| `/delete` | 在话题内回复一条已映射消息后发送，撤回两端对应消息 |
| `/close_topic` | 删除当前用户话题并清理相关映射数据 |
| `/user_config` | 打开当前话题的用户配置面板，可切换文字模式/全消息模式 |

### 黑名单命令

黑名单命令可在配置的目标群组中由主人发送，支持斜杠命令、英文命令和中文文本。

| 命令示例 | 说明 |
| --- | --- |
| `/block`、`拉黑` | 在用户话题内拉黑当前话题对应用户 |
| `/block 用户ID`、`拉黑 用户ID` | 拉黑指定用户 |
| `/unblock`、`取消拉黑` | 在用户话题内取消拉黑当前话题对应用户 |
| `/unblock 用户ID`、`取消拉黑 用户ID` | 取消拉黑指定用户 |
| `/blacklist`、`黑名单`、`查看黑名单` | 打开分页黑名单管理面板 |
| `/exit_blacklist`、`退出黑名单` | 关闭当前黑名单管理面板 |

## 部署前提

- 已创建 Telegram Bot，并拿到 Bot Token。
- 有一个 Telegram 论坛群组（Topics/话题已开启）。
- Bot 已加入该群组，并具备创建话题、发送消息、删除消息、置顶消息等必要权限。
- 应用有公网 HTTPS Webhook 地址，路径为 `/webhook`，例如 `https://example.com/webhook`。
- 准备一个 MySQL 数据库；默认库名为 `tg_bot_db`。

## 环境变量

### 必填配置

| 环境变量 | 说明 |
| --- | --- |
| `TELEGRAM_BOT_TOKEN` | Telegram Bot Token |
| `TELEGRAM_BOT_WEBHOOK_URL` | Telegram Webhook 地址，例如 `https://example.com/webhook` |
| `TELEGRAM_BOT_OWNER_ID` | 主人的 Telegram 用户 ID |
| `TELEGRAM_BOT_GROUP_ID` | 用于承载用户话题的 Telegram 群组 ID |
| `SPRING_DATASOURCE_URL` | MySQL JDBC 地址 |
| `SPRING_DATASOURCE_USERNAME` | MySQL 用户名 |
| `SPRING_DATASOURCE_PASSWORD` | MySQL 密码 |

### 常用可选配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring Profile |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | JPA ddl 策略 |
| `SPRING_JPA_SHOW_SQL` | `false` | 是否打印 SQL |
| `DB_USERNAME` | `root` | 本地运行时可作为数据库用户名兜底 |
| `DB_PASSWORD` | 空 | 本地运行时可作为数据库密码兜底 |

## 本地构建与运行

在项目根目录执行：

```bash
mvn clean package
```

运行 Web 模块生成的可执行 JAR：

```bash
java -jar tg-bot-web/target/tg-bot-web-0.0.1-SNAPSHOT.jar
```

默认服务端口为 `9599`，Webhook 接口为：

```text
POST /webhook
```

本地启动时建议至少配置这些环境变量：

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/tg_bot_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export SPRING_DATASOURCE_USERNAME='root'
export SPRING_DATASOURCE_PASSWORD='your_password'
export TELEGRAM_BOT_TOKEN='your_bot_token'
export TELEGRAM_BOT_OWNER_ID='your_telegram_user_id'
export TELEGRAM_BOT_GROUP_ID='your_forum_group_id'
export TELEGRAM_BOT_WEBHOOK_URL='https://your-domain.com/webhook'
```

## Docker 运行

项目提供了 `tg-bot-web/Dockerfile`，也可以直接使用已构建镜像：

```bash
docker pull kixyu9527/tg_bot:latest
```

项目根目录的 [`docker-compose.yml`](./docker-compose.yml) 是一个基础示例：

```yaml
services:
  app:
    image: kixyu9527/tg_bot:latest
    container_name: tg-bot-app
    restart: always
    networks:
      - 1panel-network
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/tg_bot_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: your_password
      SPRING_JPA_HIBERNATE_DDL_AUTO: update
      SPRING_JPA_SHOW_SQL: "false"
      SPRING_PROFILES_ACTIVE: dev
      TELEGRAM_BOT_TOKEN: "<你的 Bot Token>"
      TELEGRAM_BOT_OWNER_ID: "<你的 Telegram 用户 ID>"
      TELEGRAM_BOT_GROUP_ID: "<论坛群组 ID>"
      TELEGRAM_BOT_WEBHOOK_URL: "https://your-domain.com/webhook"
    ports:
      - "9599:9599"
```

把环境变量替换为实际值，并确保反向代理把公网 HTTPS 请求转发到容器的 `9599` 端口即可。