package com.odysseus.workspace.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.odysseus.workspace.config.ProblemDetailResponseWriter;
import com.odysseus.workspace.config.SecurityConfig;
import com.odysseus.workspace.config.SubscriptionExpiryPort;
import com.odysseus.workspace.config.SubscriptionGuardWebConfig;
import com.odysseus.workspace.exception.GlobalExceptionHandler;
import com.odysseus.workspace.service.InvitationService;
import com.odysseus.workspace.service.MemberService;
import com.odysseus.workspace.service.SubscriptionService;
import com.odysseus.workspace.service.WorkspaceService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

    protected MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // по умолчанию подписка действует
        org.mockito.Mockito.when(subscriptionExpiryPort.findExpiresAt(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(Instant.now().plusSeconds(3600)));
    }

    /** Токен пользователя организации workspace с ролями. */
    protected static RequestPostProcessor token(UUID workspaceId, String... roles) {
        SimpleGrantedAuthority[] authorities = new SimpleGrantedAuthority[roles.length];
        for (int i = 0; i < roles.length; i++) {
            authorities[i] = new SimpleGrantedAuthority("ROLE_" + roles[i]);
        }
        return jwt()
                .jwt(j -> j.subject("user-1")
                        .claim("email", "user@acme.io")
                        .claim("organization", Map.of("acme", Map.of("id", workspaceId.toString()))))
                .authorities(authorities);
    }

    @Configuration
    @EnableSpringDataWebSupport
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
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
