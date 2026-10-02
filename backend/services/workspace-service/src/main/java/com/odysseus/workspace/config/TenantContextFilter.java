package com.odysseus.workspace.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Связывает запрос с workspace из проверенного JWT и подставляет роль участника из БД сервиса
 * (одна выборка {@link MemberRolePort} на запрос). Роли из токена отбрасываются; нет записи Member, нет ролей.
 * Аутентифицированный запрос без workspace получает 403.
 * Не бин: подключается только в цепочку Spring Security, чтобы не попасть в servlet-цепочку дважды.
 */
@RequiredArgsConstructor
class TenantContextFilter extends OncePerRequestFilter {

    private static final String ROLE_PREFIX = "ROLE_";

    private final ProblemDetailResponseWriter problemWriter;
    private final MemberRolePort memberRolePort;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            // Анонимные запросы (health) идут без тенанта; обращение к БД без контекста запрещено резолвером.
            chain.doFilter(request, response);
            return;
        }
        Optional<UUID> workspaceId = KeycloakJwtAuthenticationConverter.extractWorkspaceId(jwtAuthentication.getToken());
        if (workspaceId.isEmpty()) {
            problemWriter.write(request, response, HttpStatus.FORBIDDEN, "Токен не содержит ровно одну организацию");
            return;
        }
        try {
            TenantContext.runAs(workspaceId.get(), () -> {
                applyMemberRole(jwtAuthentication, workspaceId.get());
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new CheckedFilterException(e);
                }
            });
        } catch (CheckedFilterException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw (ServletException) e.getCause();
        }
    }

    /** Заменяет аутентификацию: прежние не-ролевые полномочия плюс роль Member в этом workspace. */
    private void applyMemberRole(JwtAuthenticationToken authentication, UUID workspaceId) {
        Jwt jwt = authentication.getToken();
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority.getAuthority() != null && !authority.getAuthority().startsWith(ROLE_PREFIX)) {
                authorities.add(authority);
            }
        }
        memberRolePort.findRole(workspaceId, jwt.getSubject())
                .ifPresent(role -> authorities.add(new SimpleGrantedAuthority(role.authority())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, authorities, authentication.getName()));
        SecurityContextHolder.setContext(context);
    }

    /** Проброс проверяемых исключений цепочки через Runnable. */
    private static final class CheckedFilterException extends RuntimeException {
        private CheckedFilterException(Exception cause) {
            super(cause);
        }
    }
}
