FROM eclipse-temurin:17-jdk-alpine AS ppe-builder
WORKDIR /ppe
RUN apk add --no-cache git && \
    git clone --depth 1 https://github.com/InfinityLoop1308/PipePipeExtractor.git . && \
    GRADLE_USER_HOME=/ppe/.gradle ./gradlew :extractor:publishToMavenLocal :timeago-parser:publishToMavenLocal --no-daemon -q

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY --from=ppe-builder /root/.m2 /root/.m2
COPY gradlew ./
COPY gradle/ ./gradle/
RUN ./gradlew --version --no-daemon -q
COPY build.gradle.kts settings.gradle.kts* ./
RUN ./gradlew dependencies --no-daemon -q || true
COPY src/ ./src/
RUN ./gradlew shadowJar --no-daemon -q

FROM eclipse-temurin:21-jre-alpine AS runner
RUN addgroup -S typetype && adduser -S typetype -G typetype
WORKDIR /app
COPY --from=builder /app/build/libs/typetype-server-all.jar app.jar
USER typetype
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
