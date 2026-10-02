package com.odysseus.workspace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.json.JsonMapper;

class SubscriptionWriteGuardInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private final UUID workspaceId = UUID.randomUUID();
    private final SubscriptionExpiryPort port = mock(SubscriptionExpiryPort.class);
    private final SubscriptionWriteGuardInterceptor interceptor = new SubscriptionWriteGuardInterceptor(
            port, new ProblemDetailResponseWriter(JsonMapper.builder().build()), Clock.fixed(NOW, ZoneOffset.UTC));

    private boolean preHandle(String method, Object handler, MockHttpServletResponse response) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/workspaces");
        return TenantContext.callAs(workspaceId, () -> {
            try {
                return interceptor.preHandle(request, response, handler);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    @DisplayName("preHandle: запись при действующей подписке разрешена")
    void preHandle_activeSubscription_allows() {
        when(port.findExpiresAt(workspaceId)).thenReturn(Optional.of(NOW.plusSeconds(60)));

        assertThat(preHandle("POST", new Object(), new MockHttpServletResponse())).isTrue();
    }

    @Test
    @DisplayName("preHandle: запись после expiresAt даёт 403 ProblemDetail")
    void preHandle_expiredSubscription_forbiddenProblemDetail() throws Exception {
        when(port.findExpiresAt(workspaceId)).thenReturn(Optional.of(NOW.minusSeconds(1)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(preHandle("PATCH", new Object(), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":403");
    }

    @Test
    @DisplayName("preHandle: ровно в момент expiresAt запись уже запрещена")
    void preHandle_expiresNow_forbidden() {
        when(port.findExpiresAt(workspaceId)).thenReturn(Optional.of(NOW));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(preHandle("DELETE", new Object(), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("preHandle: без подписки запись запрещена")
    void preHandle_noSubscription_forbidden() {
        when(port.findExpiresAt(any())).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(preHandle("PUT", new Object(), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("preHandle: чтение при истёкшей подписке разрешено без обращения к порту")
    void preHandle_readRequest_allowsWithoutLookup() {
        assertThat(preHandle("GET", new Object(), new MockHttpServletResponse())).isTrue();
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("preHandle: метод с @SubscriptionNotRequired пропускается (продление)")
    void preHandle_exemptHandler_allows() throws NoSuchMethodException {
        Method renew = ExemptController.class.getDeclaredMethod("renew");
        HandlerMethod handler = new HandlerMethod(new ExemptController(), renew);

        assertThat(preHandle("POST", handler, new MockHttpServletResponse())).isTrue();
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("preHandle: запись вне контекста workspace запрещена")
    void preHandle_noTenantContext_forbidden() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/v1/workspaces"), response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(port);
    }

    static class ExemptController {
        @SubscriptionNotRequired
        void renew() {
        }
    }
}
