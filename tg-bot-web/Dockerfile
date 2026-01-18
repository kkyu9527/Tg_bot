FROM eclipse-temurin:21-jre

WORKDIR /app

ARG JAR_FILE=target/tg-bot-web-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 9599

ENV SPRING_PROFILES_ACTIVE=dev

ENTRYPOINT ["java","-jar","/app/app.jar"]

