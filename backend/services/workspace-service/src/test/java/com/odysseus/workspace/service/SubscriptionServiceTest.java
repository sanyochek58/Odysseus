package com.odysseus.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odysseus.workspace.config.TenantContext;
import com.odysseus.workspace.entity.Subscription;
import com.odysseus.workspace.entity.SubscriptionExtensionKey;
import com.odysseus.events.SubscriptionChangedEvent;
import com.odysseus.events.outbox.OutboxService;
import com.odysseus.workspace.exception.ConflictException;
import com.odysseus.workspace.exception.NotFoundException;
import com.odysseus.workspace.mapper.SubscriptionMapperImpl;
import com.odysseus.workspace.repository.SubscriptionExtensionKeyRepository;
import com.odysseus.workspace.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private final UUID workspaceId = UUID.randomUUID();

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionExtensionKeyRepository extensionKeyRepository;
    @Mock
    private OutboxService outboxService;

    private SubscriptionService service() {
        return new SubscriptionService(subscriptionRepository, extensionKeyRepository, outboxService, new SubscriptionMapperImpl(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("createTrial: пробный срок и событие в outbox")
    void createTrial_newWorkspace_savesAndPublishes() {
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        Subscription saved = TenantContext.callAs(workspaceId, () -> service().createTrial());

        Instant expected = NOW.plus(Duration.ofDays(SubscriptionService.TRIAL_DAYS));
        assertThat(saved.getExpiresAt()).isEqualTo(expected);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).save(eq(SubscriptionChangedEvent.TOPIC), eq(workspaceId), eq(workspaceId), any(UUID.class),
                event.capture());
        SubscriptionChangedEvent payload = (SubscriptionChangedEvent) event.getValue();
        assertThat(payload.workspaceId()).isEqualTo(workspaceId);
        assertThat(payload.expiresAt()).isEqualTo(expected);
        assertThat(payload.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("extend: действующая подписка продлевается от текущего срока")
    void extend_active_extendsFromExpiry() {
        Subscription subscription = Subscription.builder().expiresAt(NOW.plus(Duration.ofDays(5))).build();
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(subscription)).thenReturn(subscription);

        var response = TenantContext.callAs(workspaceId, () -> service().extend(30, "k1"));

        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(35)));
        verify(outboxService).save(eq(SubscriptionChangedEvent.TOPIC), eq(workspaceId), eq(workspaceId), any(UUID.class),
                any(SubscriptionChangedEvent.class));
    }

    @Test
    @DisplayName("extend: истёкшая подписка продлевается от текущего момента")
    void extend_expired_extendsFromNow() {
        Subscription subscription = Subscription.builder().expiresAt(NOW.minus(Duration.ofDays(10))).build();
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(subscription)).thenReturn(subscription);

        var response = TenantContext.callAs(workspaceId, () -> service().extend(7, "k1"));

        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("extend: подписки нет, 404")
    void extend_noSubscription_throwsNotFound() {
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().extend(7, "k1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getCurrent: возвращает срок подписки")
    void getCurrent_existing_returnsExpiry() {
        Subscription subscription = Subscription.builder().expiresAt(NOW).build();
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(subscription));

        assertThat(service().getCurrent().expiresAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("getCurrent: подписки нет (чужая не видна), 404")
    void getCurrent_none_throwsNotFound() {
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getCurrent()).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("extend: повтор Idempotency-Key, 409 и повторного продления нет")
    void extend_duplicateKey_throwsConflictWithoutExtending() {
        Subscription subscription = Subscription.builder().expiresAt(NOW.plus(Duration.ofDays(5))).build();
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(subscription));
        when(extensionKeyRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> TenantContext.callAs(workspaceId, () -> service().extend(30, "k1")))
                .isInstanceOf(ConflictException.class);

        assertThat(subscription.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(5)));
        verify(subscriptionRepository, never()).save(any());
        verify(outboxService, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("extend: ключ сохраняется до продления")
    void extend_newKey_storesKey() {
        Subscription subscription = Subscription.builder().expiresAt(NOW.plus(Duration.ofDays(5))).build();
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(subscription)).thenReturn(subscription);

        TenantContext.callAs(workspaceId, () -> service().extend(30, "k1"));

        ArgumentCaptor<SubscriptionExtensionKey> key = ArgumentCaptor.forClass(SubscriptionExtensionKey.class);
        verify(extensionKeyRepository).saveAndFlush(key.capture());
        assertThat(key.getValue().getIdempotencyKey()).isEqualTo("k1");
    }
}
