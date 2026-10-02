package com.odysseus.workspace.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.odysseus.workspace.config.MemberRolePort;
import com.odysseus.workspace.config.ProblemDetailResponseWriter;
import com.odysseus.workspace.config.SecurityConfig;
import com.odysseus.workspace.config.SubscriptionExpiryPort;
import com.odysseus.workspace.config.SubscriptionGuardWebConfig;
import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.exception.GlobalExceptionHandler;
import com.odysseus.workspace.service.InvitationService;
import com.odysseus.workspace.service.MemberService;
import com.odysseus.workspace.service.SubscriptionService;
import com.odysseus.workspace.service.WorkspaceService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * Веб-слой без БД: реальные контроллеры, SecurityConfig, блокировка записи и обработчик ошибок,
 * сервисы и порт подписки замоканы. JWT поддельный (jwt() из spring-security-test).
 * Контекст собран на spring-test (MockMvc + springSecurity()), потому что стартера с {@code @WebMvcTest}
 * (spring-boot-starter-webmvc-test) в каталоге зависимостей нет; при его добавлении заменить на {@code @WebMvcTest}.
 */
@SpringJUnitWebConfig(ApiTestBase.TestConfig.class)
abstract class ApiTestBase {

    protected static final UUID WORKSPACE_A = UUID.randomUUID();
    protected static final UUID WORKSPACE_B = UUID.randomUUID();
    protected static final String USER_ID = "user-1";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    protected WorkspaceService workspaceService;
    @MockitoBean
    protected MemberService memberService;
    @MockitoBean
    protected InvitationService invitationService;
    @MockitoBean
    protected SubscriptionService subscriptionService;
    @MockitoBean
    protected SubscriptionExpiryPort subscriptionExpiryPort;
    @MockitoBean
    protected MemberRolePort memberRolePort;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // по умолчанию подписка действует
        org.mockito.Mockito.when(subscriptionExpiryPort.findExpiresAt(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(Instant.now().plusSeconds(3600)));
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

    @Configuration
    @EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @Import({SecurityConfig.class, ProblemDetailResponseWriter.class, SubscriptionGuardWebConfig.class,
            GlobalExceptionHandler.class, WorkspaceController.class, MemberController.class,
            InvitationController.class, SubscriptionController.class})
    static class TestConfig {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        java.time.Clock clock() {
            return java.time.Clock.systemUTC();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
