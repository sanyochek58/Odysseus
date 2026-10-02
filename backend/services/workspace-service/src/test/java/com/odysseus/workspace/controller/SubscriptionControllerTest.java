package com.odysseus.workspace.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.odysseus.workspace.dto.SubscriptionResponse;
import com.odysseus.workspace.exception.NotFoundException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SubscriptionControllerTest extends ApiTestBase {

    private static final String URL = "/api/v1/subscriptions/current/extensions";

    private final SubscriptionResponse subscription =
            new SubscriptionResponse(Instant.parse("2027-01-01T00:00:00Z"), Instant.now(), Instant.now());

    @Test
    @DisplayName("GET /subscriptions/current: срок подписки")
    void current_authenticated_returnsSubscription() throws Exception {
        when(subscriptionService.getCurrent()).thenReturn(subscription);

        mvc.perform(get("/api/v1/subscriptions/current").with(token(WORKSPACE_A, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    @DisplayName("GET /subscriptions/current: подписки нет, 404")
    void current_none_returns404() throws Exception {
        when(subscriptionService.getCurrent()).thenThrow(new NotFoundException("Подписка не найдена"));

        mvc.perform(get("/api/v1/subscriptions/current").with(token(WORKSPACE_A, "MEMBER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST extensions: OWNER продлевает")
    void extend_owner_returns200() throws Exception {
        when(subscriptionService.extend(30)).thenReturn(subscription);

        mvc.perform(post(URL).with(token(WORKSPACE_A, "OWNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":30}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST extensions: продление работает при истёкшей подписке")
    void extend_expiredSubscription_notBlockedByGuard() throws Exception {
        when(subscriptionExpiryPort.findExpiresAt(any())).thenReturn(Optional.of(Instant.now().minusSeconds(60)));
        when(subscriptionService.extend(30)).thenReturn(subscription);

        mvc.perform(post(URL).with(token(WORKSPACE_A, "OWNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":30}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST extensions: days вне диапазона, 400")
    void extend_daysOutOfRange_returns400() throws Exception {
        mvc.perform(post(URL).with(token(WORKSPACE_A, "OWNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":1000}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("POST extensions: ADMIN, 403")
    void extend_admin_returns403() throws Exception {
        mvc.perform(post(URL).with(token(WORKSPACE_A, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":30}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("POST extensions: подписки нет, 404")
    void extend_none_returns404() throws Exception {
        when(subscriptionService.extend(30)).thenThrow(new NotFoundException("Подписка не найдена"));

        mvc.perform(post(URL).with(token(WORKSPACE_A, "OWNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":30}"))
                .andExpect(status().isNotFound());
    }
}
