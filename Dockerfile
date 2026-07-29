FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-alpine AS builder
ARG APP_VERSION=0.1.0
ARG GITHUB_SHA=unknown
ARG BUILD_TIME=unknown
ENV GITHUB_SHA=$GITHUB_SHA
ENV BUILD_TIME=$BUILD_TIME
WORKDIR /app
COPY gradlew ./
COPY gradle/ ./gradle/
RUN ./gradlew --version --no-daemon -q
COPY build.gradle.kts gradle.properties settings.gradle.kts* ./
RUN ./gradlew dependencies --no-daemon -q || true
COPY src/ ./src/
RUN ./gradlew shadowJar --no-daemon -q -PappVersion="$APP_VERSION"

FROM eclipse-temurin:25-jre-alpine AS runner
RUN apk upgrade --no-cache \
    && addgroup -S typetype \
    && adduser -S typetype -G typetype
WORKDIR /app
COPY --from=builder /app/build/libs/typetype-server-all.jar app.jar
USER typetype
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
