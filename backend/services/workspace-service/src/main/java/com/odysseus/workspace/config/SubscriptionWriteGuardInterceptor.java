package com.odysseus.workspace.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Блокирует запись (POST, PUT, PATCH, DELETE) с момента {@code expiresAt} подписки: 403 ProblemDetail.
 * Нет подписки или нет контекста workspace: запись тоже запрещена.
 */
@RequiredArgsConstructor
class SubscriptionWriteGuardInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final SubscriptionExpiryPort subscriptionExpiryPort;
    private final ProblemDetailResponseWriter problemWriter;
    private final Clock clock;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!WRITE_METHODS.contains(request.getMethod()) || isExempt(handler)) {
            return true;
        }
        Optional<UUID> workspaceId = TenantContext.currentWorkspaceId();
        if (workspaceId.isEmpty()) {
            problemWriter.write(request, response, HttpStatus.FORBIDDEN, "Запись без контекста workspace запрещена");
            return false;
        }
        Optional<Instant> expiresAt = subscriptionExpiryPort.findExpiresAt(workspaceId.get());
        if (expiresAt.isEmpty() || !clock.instant().isBefore(expiresAt.get())) {
            problemWriter.write(request, response, HttpStatus.FORBIDDEN, "Подписка workspace истекла, запись запрещена");
            return false;
        }
        return true;
    }

    private static boolean isExempt(Object handler) {
        return handler instanceof HandlerMethod method && method.hasMethodAnnotation(SubscriptionNotRequired.class);
    }
}
