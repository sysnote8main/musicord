FROM gradle:jdk21 AS builder
ARG APP_VERSION
WORKDIR /app
COPY . .
RUN APP_VERSION=${APP_VERSION} gradle shadowJar --no-daemon

FROM eclipse-temurin:21-jre AS runner
ARG APP_VERSION
WORKDIR /app
COPY --from=builder "/app/build/libs/musicord-${APP_VERSION}-all.jar" ./app.jar
ENTRYPOINT [ "java", "-jar", "/app/app.jar"]
