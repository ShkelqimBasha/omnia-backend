# syntax=docker/dockerfile:1

############################
# Stage 1: Build
############################
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./

RUN mvn --batch-mode \
    --no-transfer-progress \
    dependency:go-offline

COPY src ./src

RUN mvn --batch-mode \
    --no-transfer-progress \
    clean package \
    -DskipTests


############################
# Stage 2: Runtime
############################
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install \
        --yes \
        --no-install-recommends \
        curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd \
        --system \
        --gid 10001 \
        omnia \
    && useradd \
        --system \
        --uid 10001 \
        --gid omnia \
        --home-dir /app \
        --shell /usr/sbin/nologin \
        omnia \
    && mkdir -p /app/uploads \
    && chown -R omnia:omnia /app

COPY --from=build \
    --chown=omnia:omnia \
    /workspace/target/*.jar \
    /app/app.jar

USER 10001:10001

ENV JAVA_TOOL_OPTIONS="\
-XX:MaxRAMPercentage=75.0 \
-XX:+UseG1GC \
-Djava.security.egd=file:/dev/urandom"

EXPOSE 8080

STOPSIGNAL SIGTERM

HEALTHCHECK \
    --interval=30s \
    --timeout=5s \
    --start-period=30s \
    --retries=3 \
    CMD curl \
        --fail \
        --silent \
        --show-error \
        http://127.0.0.1:8080/actuator/health/readiness \
        || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]