package com.odysseus.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.odysseus.workspace.entity.Subscription;
import com.odysseus.workspace.repository.SubscriptionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscriptionExpiryAdapterTest {

    private final SubscriptionRepository repository = mock(SubscriptionRepository.class);
    private final SubscriptionExpiryAdapter adapter = new SubscriptionExpiryAdapter(repository);

    @Test
    @DisplayName("findExpiresAt: есть подписка, возвращает срок")
    void findExpiresAt_existing_returnsExpiry() {
        Instant expiresAt = Instant.parse("2026-12-01T00:00:00Z");
        when(repository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.of(Subscription.builder().expiresAt(expiresAt).build()));

        assertThat(adapter.findExpiresAt(UUID.randomUUID())).contains(expiresAt);
    }

    @Test
    @DisplayName("findExpiresAt: подписки нет, пусто (запись запрещена)")
    void findExpiresAt_none_returnsEmpty() {
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertThat(adapter.findExpiresAt(UUID.randomUUID())).isEmpty();
    }
}
