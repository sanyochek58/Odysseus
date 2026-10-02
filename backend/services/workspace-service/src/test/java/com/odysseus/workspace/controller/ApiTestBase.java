package com.odysseus.workspace.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.odysseus.workspace.config.ClockConfig;
import com.odysseus.workspace.config.MemberRolePort;
import com.odysseus.workspace.config.PageSerializationConfig;
import com.odysseus.workspace.config.ProblemDetailResponseWriter;
import com.odysseus.workspace.config.SecurityConfig;
import com.odysseus.workspace.config.SubscriptionExpiryPort;
import com.odysseus.workspace.config.SubscriptionGuardWebConfig;
import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Общая часть срезовых тестов контроллеров: каждый тест объявляет {@code @WebMvcTest(<Controller>.class)} и мокает
 * свой сервис через {@code @MockitoBean}. Здесь подключаются реальные SecurityConfig, блокировка записи по подписке,
 * обработчик ошибок и сериализация страниц; порты (подписка, роль) замоканы, JWT поддельный (jwt()).
 */
@Import({SecurityConfig.class, ProblemDetailResponseWriter.class, SubscriptionGuardWebConfig.class,
        GlobalExceptionHandler.class, ClockConfig.class, PageSerializationConfig.class})
@TestPropertySource(properties = "KEYCLOAK_ISSUER_URI=http://localhost/realms/odysseus")
abstract class ApiTestBase {

    protected static final UUID WORKSPACE_A = UUID.randomUUID();
    protected static final UUID WORKSPACE_B = UUID.randomUUID();
    protected static final String USER_ID = "user-1";

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected SubscriptionExpiryPort subscriptionExpiryPort;
    @MockitoBean
    protected MemberRolePort memberRolePort;
    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @BeforeEach
    void setUpSubscription() {
        // по умолчанию подписка действует
        when(subscriptionExpiryPort.findExpiresAt(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(Instant.now().plusSeconds(3600)));
    }

    /**
     * Токен пользователя user-1 организации workspace (email подтверждён). Роль токен не несёт:
     * она задаётся записью Member, здесь через мок {@link MemberRolePort}. Без роли записи Member нет.
     */
    protected RequestPostProcessor token(UUID workspaceId, String... roles) {
        when(memberRolePort.findRole(workspaceId, USER_ID)).thenReturn(roles.length == 0
                ? Optional.empty()
                : Optional.of(WorkspaceRole.valueOf(roles[0])));
        return tokenOf(workspaceId, true);
    }

    /** Токен user-1 организации workspace без ролей в самом токене; email_verified задаётся явно. */
    protected static RequestPostProcessor tokenOf(UUID workspaceId, boolean emailVerified) {
        return jwt()
                .jwt(j -> j.subject(USER_ID)
                        .claim("email", "user@acme.io")
                        .claim("email_verified", emailVerified)
                        .claim("organization", Map.of("acme", Map.of("id", workspaceId.toString()))));
    }
}
