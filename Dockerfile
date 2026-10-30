# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies in a separate layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src/ src/
RUN mvn package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup -u 1001

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=5 --start-period=30s \
  CMD wget --spider -q http://localhost:8080/actuator/health/readiness || exit 1

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
