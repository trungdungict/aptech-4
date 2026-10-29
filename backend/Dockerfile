# Multi-stage Spring Boot API image (Java 21). Pin major tags; avoid `latest`.
# Build context: backend/ (see .dockerignore — .env and target/ excluded).

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src ./src
RUN ./mvnw -B -DskipTests package \
	&& cp target/aptech4-api-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN apk add --no-cache curl \
	&& addgroup -S aptech && adduser -S aptech -G aptech

COPY --from=build /workspace/app.jar /app/app.jar
USER aptech

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
	CMD curl -fsS "http://127.0.0.1:8080/api/v1/health" || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
