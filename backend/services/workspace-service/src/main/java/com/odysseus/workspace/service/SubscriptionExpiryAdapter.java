package com.odysseus.workspace.service;

import com.odysseus.workspace.config.SubscriptionExpiryPort;
import com.odysseus.workspace.repository.SubscriptionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Реализация порта блокировки записи поверх {@code Subscription}. Читает в контексте тенанта запроса,
 * чужая подписка недоступна из-за {@code @TenantId}.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryAdapter implements SubscriptionExpiryPort {

    private final SubscriptionRepository subscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findExpiresAt(UUID workspaceId) {
        return subscriptionRepository.findFirstByOrderByCreatedAtAsc()
                .map(subscription -> subscription.getExpiresAt());
    }
}
