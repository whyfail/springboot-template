# syntax=docker/dockerfile:1.7
# Multi-stage build: Maven build with a cacheable local repository, slim JRE runtime image,
# non-root user, read-only-root-fs friendly (only /tmp is written).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY mvnw ./
COPY .mvn ./.mvn
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -q -DskipUnitTests package

FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /build/target/{{ name }}-*.jar /app/app.jar
USER 10001
EXPOSE 8080 9090
# JVM tuning arrives via JAVA_TOOL_OPTIONS (e.g. -XX:MaxRAMPercentage=75 in compose/k8s).
ENV SERVER_PORT=8080 \
    MANAGEMENT_PORT=9090
ENTRYPOINT ["sh", "-c", "exec java $JAVA_TOOL_OPTIONS -jar /app/app.jar"]
