package com.odysseus.workspace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Resource Server: JWT realm {@code odysseus}, issuer из {@code KEYCLOAK_ISSUER_URI} без значения по умолчанию.
 * Всё, кроме health, требует аутентификации. Роли проверяются {@code @PreAuthorize};
 * источник роли это запись Member в БД (см. {@link TenantContextFilter}), не claims токена.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailResponseWriter problemWriter,
            MemberRolePort memberRolePort) throws Exception {
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
}
