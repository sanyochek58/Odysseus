package com.odysseus.workspace.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Связывает запрос с workspace из проверенного JWT. Аутентифицированный запрос без workspace получает 403.
 * Не бин: подключается только в цепочку Spring Security, чтобы не попасть в servlet-цепочку дважды.
 */
@RequiredArgsConstructor
class TenantContextFilter extends OncePerRequestFilter {

    private final ProblemDetailResponseWriter problemWriter;

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

    /** Проброс проверяемых исключений цепочки через Runnable. */
    private static final class CheckedFilterException extends RuntimeException {
        private CheckedFilterException(Exception cause) {
            super(cause);
        }
    }
}
