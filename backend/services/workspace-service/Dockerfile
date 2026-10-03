# Образ собирается через make image SVC=workspace-service (bootJar кладётся в build/app.jar).
# Этап 1: распаковка jar по слоям (Spring Boot jarmode tools), JDK 25.
# Базовые образы по дайджесту индекса (make image-digest IMG=<образ:тег>). Тег: eclipse-temurin:25-jdk
FROM eclipse-temurin:25-jdk@sha256:8c0a84ea11c8f6ed52600fc19f1040121f2a162998e9f50a5faebbbad9172dcc AS builder
WORKDIR /builder
ARG JAR_FILE=build/app.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Этап 2: рантайм, только JRE 25, без root.
# Тег: eclipse-temurin:25-jre
FROM eclipse-temurin:25-jre@sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636
WORKDIR /application
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
COPY --from=builder --chown=app:app /builder/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /builder/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /builder/extracted/application/ ./
USER 10001:10001
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:${SERVER_PORT:-8081}/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "application.jar"]
