package com.odysseus.workspace.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Общая база интеграционных тестов: один Postgres и один Kafka на весь модуль (статические контейнеры),
 * полный контекст приложения, JWT поддельный ({@code jwt()}), реальный Keycloak не нужен.
 * Изоляция тестов: каждый тест работает в своих случайных workspace, очистка БД не требуется.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://localhost/realms/odysseus");
        registry.add("odysseus.outbox.poll-interval-ms", () -> "200");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Декодер не используется (jwt() подставляет аутентификацию), мок исключает обращение к issuer. */
    @MockitoBean
    protected JwtDecoder jwtDecoder;

    protected static RequestPostProcessor token(UUID workspaceId, String userId) {
        return token(workspaceId, userId, userId + "@acme.io", true);
    }

    protected static RequestPostProcessor token(UUID workspaceId, String userId, String email, boolean emailVerified) {
        return jwt().jwt(j -> j.subject(userId)
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("organization", Map.of("acme", Map.of("id", workspaceId.toString()))));
    }

    /** Токен без claim организации. */
    protected static RequestPostProcessor tokenWithoutOrganization(String userId) {
        return jwt().jwt(j -> j.subject(userId).claim("email", userId + "@acme.io").claim("email_verified", true));
    }

    /** Добавляет участника напрямую в БД (JDBC, мимо Hibernate), чтобы подготовить роли без API. */
    protected void insertMember(UUID workspaceId, String userId, String role) {
        jdbc.update("INSERT INTO member (id, workspace_id, user_id, email, role) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), workspaceId, userId, userId + "@acme.io", role);
    }
}
