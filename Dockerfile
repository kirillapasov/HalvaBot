FROM openjdk:17-jdk-slim
WORKDIR /app
COPY out/artifacts/HalvaBot_jar/HalvaBot.jar /app/Halva.jar

ENV BOT_JAR=HalvaBot.jar
ENTRYPOINT ["java", "-jar", "/app/Halva.jar"]