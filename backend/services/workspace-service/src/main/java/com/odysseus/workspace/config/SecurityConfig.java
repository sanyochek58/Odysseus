package com.odysseus.workspace.config;

import java.util.List;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Resource Server: JWT realm {@code odysseus}, issuer из {@code KEYCLOAK_ISSUER_URI} без значения по умолчанию,
 * claim aud обязан содержать значение из {@code spring.security.oauth2.resourceserver.jwt.audiences}
 * ({@code KEYCLOAK_AUDIENCE}, по умолчанию {@code odysseus-api}); иначе 401.
 * Всё, кроме health, требует аутентификации. Роли проверяются {@code @PreAuthorize};
 * источник роли это запись Member в БД (см. {@link TenantContextFilter}), не claims токена.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    static final String AUDIENCES_PROPERTY = "spring.security.oauth2.resourceserver.jwt.audiences";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailResponseWriter problemWriter,
            MemberRolePort memberRolePort, Environment environment) throws Exception {
        requireAudiences(environment);
        BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
        BearerTokenAccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();
        // Bearer-обработчики ставят статус и WWW-Authenticate, тело дописываем ProblemDetail.
        AuthenticationEntryPoint entryPoint = (request, response, exception) -> {
            bearerEntryPoint.commence(request, response, exception);
            problemWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Требуется аутентификация");
        };
        AccessDeniedHandler accessDenied = (request, response, exception) -> {
            bearerAccessDenied.handle(request, response, exception);
            problemWriter.write(request, response, HttpStatus.FORBIDDEN, "Недостаточно прав");
        };
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .addFilterAfter(new TenantContextFilter(problemWriter, memberRolePort), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Без {@code audiences} Spring Boot не проверяет claim aud и принимает токены любого клиента realm.
     * Поэтому пустая настройка это ошибка старта, а не тихое отключение проверки.
     */
    static List<String> requireAudiences(Environment environment) {
        List<String> audiences = Binder.get(environment)
                .bind(AUDIENCES_PROPERTY, Bindable.listOf(String.class))
                .orElse(List.of());
        if (audiences.isEmpty() || audiences.stream().anyMatch(a -> a == null || a.isBlank())) {
            throw new IllegalStateException("Не задан " + AUDIENCES_PROPERTY + ": проверка aud обязательна");
        }
        return audiences;
    }
}
