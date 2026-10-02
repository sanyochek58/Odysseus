package com.odysseus.workspace.service;

import com.odysseus.workspace.config.TenantContext;
import com.odysseus.workspace.dto.SubscriptionResponse;
import com.odysseus.workspace.entity.Subscription;
import com.odysseus.workspace.entity.SubscriptionExtensionKey;
import com.odysseus.workspace.event.SubscriptionChangedEvent;
import com.odysseus.workspace.exception.ConflictException;
import com.odysseus.workspace.exception.NotFoundException;
import com.odysseus.workspace.mapper.SubscriptionMapper;
import com.odysseus.workspace.repository.SubscriptionExtensionKeyRepository;
import com.odysseus.workspace.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Подписка текущего workspace: чтение, продление, пробный период. Платежей нет. */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** Пробный срок при создании workspace. */
    public static final int TRIAL_DAYS = 30;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionExtensionKeyRepository extensionKeyRepository;
    private final OutboxService outboxService;
    private final SubscriptionMapper subscriptionMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrent() {
        return subscriptionMapper.toResponse(find());
    }

    /** Создаёт пробную подписку нового workspace. Вызывается из транзакции создания workspace. */
    @Transactional
    public Subscription createTrial() {
        Instant expiresAt = clock.instant().plus(Duration.ofDays(TRIAL_DAYS));
        Subscription subscription = subscriptionRepository.save(Subscription.builder().expiresAt(expiresAt).build());
        publishChanged(expiresAt);
        return subscription;
    }

    /**
     * Продлевает от текущего срока, а если он истёк, от текущего момента. Повтор ключа это 409.
     * Ключ вставляется первым и сразу сбрасывается в БД: гонку решает уникальный индекс (workspace_id, key),
     * проигравшая транзакция откатывается целиком, продления не происходит.
     */
    @Transactional
    public SubscriptionResponse extend(int days, String idempotencyKey) {
        Subscription subscription = find();
        registerKey(idempotencyKey);
        Instant now = clock.instant();
        Instant base = subscription.getExpiresAt().isAfter(now) ? subscription.getExpiresAt() : now;
        subscription.setExpiresAt(base.plus(Duration.ofDays(days)));
        Subscription saved = subscriptionRepository.save(subscription);
        publishChanged(saved.getExpiresAt());
        return subscriptionMapper.toResponse(saved);
    }

    private void registerKey(String idempotencyKey) {
        try {
            extensionKeyRepository.saveAndFlush(
                    SubscriptionExtensionKey.builder().idempotencyKey(idempotencyKey).build());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Продление с таким Idempotency-Key уже выполнено");
        }
    }

    private Subscription find() {
        return subscriptionRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new NotFoundException("Подписка не найдена"));
    }

    private void publishChanged(Instant expiresAt) {
        UUID workspaceId = TenantContext.requireWorkspaceId();
        UUID eventId = UUID.randomUUID();
        SubscriptionChangedEvent event = new SubscriptionChangedEvent(
                eventId, clock.instant(), workspaceId, SubscriptionChangedEvent.CURRENT_VERSION, expiresAt);
        outboxService.save(SubscriptionChangedEvent.TOPIC, workspaceId, workspaceId, eventId, event);
    }
}
