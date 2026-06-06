FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY gradlew ./
COPY gradle/ ./gradle/
RUN ./gradlew --version --no-daemon -q
COPY build.gradle.kts settings.gradle.kts* ./
RUN ./gradlew dependencies --no-daemon -q || true
COPY src/ ./src/
RUN ./gradlew shadowJar --no-daemon -q

FROM eclipse-temurin:25-jre-alpine AS runner
RUN addgroup -S typetype && adduser -S typetype -G typetype
WORKDIR /app
COPY --from=builder /app/build/libs/typetype-server-all.jar app.jar
USER typetype
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
