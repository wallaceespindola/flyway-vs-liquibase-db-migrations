# Flyway vs Liquibase — DB Migrations
# Multi-stage build: Maven builds the jar, a slim JRE runs it.
# Author: Wallace Espindola

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first so dependency resolution is cached independently of source changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package


FROM eclipse-temurin:21-jre-alpine AS runtime

# Run as a non-root user.
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=build /build/target/flyway-vs-liquibase-db-migrations.jar app.jar

# Both H2 databases live here. Mount a volume to keep migration history across container restarts.
RUN mkdir -p /app/data && chown -R app:app /app
VOLUME ["/app/data"]

USER app

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/v1/health > /dev/null 2>&1 || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
