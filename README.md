# tg-bot

基于 Spring Boot 的 Telegram 机器人服务，采用分层多模块结构（domain / service / web），通过 Webhook 接收并处理来自 Telegram 的消息。

> 部署使用方式非常简单：在你的编排（如 docker-compose、Kubernetes、1Panel 等）里为容器配置好环境变量即可。

## 项目结构

- `tg-bot-domain`：领域模型与仓储接口
- `tg-bot-service`：业务逻辑与 Telegram API 封装
- `tg-bot-web`：Spring Boot 启动入口、Webhook 控制器、配置等
- `docker-compose.yml`：示例编排文件（使用已构建好的镜像 `kixyu9527/tg_bot:latest`）

## 构建与运行

### 本地构建

在项目根目录执行：

```bash
mvn clean package
```

打包完成后，可在 `tg-bot-web/target/` 下看到可运行的 JAR，例如：

```bash
java -jar tg-bot-web/target/tg-bot-web-0.0.1-SNAPSHOT.jar
```

### 使用 Docker 运行

项目已提供 Web 模块的 Dockerfile（`tg-bot-web/Dockerfile`），也可以直接使用已经构建好的镜像：

```bash
docker pull kixyu9527/tg_bot:latest
```

运行镜像时，只需在编排里配置好环境变量即可，下面有示例。

## 环境变量说明

### 数据库相关

- `SPRING_DATASOURCE_URL`：数据库连接 URL，例如  
  `jdbc:mysql://mysql:3306/tg_bot_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
- `SPRING_DATASOURCE_USERNAME`：数据库用户名
- `SPRING_DATASOURCE_PASSWORD`：数据库密码

（在本地运行时也可以通过 `DB_USERNAME` / `DB_PASSWORD` 这种形式传入，Spring Boot 会自动解析）

### Telegram Bot 相关

- `TELEGRAM_BOT_TOKEN`：Bot 的 Token（必须）
- `TELEGRAM_BOT_OWNER_ID`：Bot 拥有者的 Telegram 用户 ID（可选）
- `TELEGRAM_BOT_GROUP_ID`：默认使用的群组 ID（可选）
- `TELEGRAM_BOT_WEBHOOK_URL`：Webhook 回调地址（必须）

### 其他常用配置

- `SPRING_PROFILES_ACTIVE`：Spring Profile，例如 `dev` / `prod`
- `SPRING_JPA_HIBERNATE_DDL_AUTO`：JPA ddl 策略，示例中为 `update`
- `SPRING_JPA_SHOW_SQL`：是否打印 SQL 日志

## docker-compose 示例

项目根目录已提供一个简单的编排示例 [`docker-compose.yml`](./docker-compose.yml)，核心就是在服务的 `environment` 中加入上述环境变量，例如：

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
      TELEGRAM_BOT_GROUP_ID: "<默认群组 ID，可选>"
      TELEGRAM_BOT_WEBHOOK_URL: "https://your-domain.com/webhook"
    ports:
      - "9599:9599"
```

把以上环境变量替换为你自己的实际值，并在你的平台（docker-compose、Kubernetes、1Panel 等）里应用这份编排即可完成部署。

## 本地开发提示

- 默认 Web 服务端口为 `9599`
- 默认数据库名为 `tg_bot_db`，可通过 `SPRING_DATASOURCE_URL` 或 `spring.datasource.url` 调整
- JPA 默认开启自动建表（`ddl-auto=update`）
