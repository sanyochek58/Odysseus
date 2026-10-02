package com.odysseus.workspace.repository;

import com.odysseus.workspace.entity.Subscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Все запросы автоматически фильтруются по тенанту (@TenantId). */
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /** Подписка текущего workspace (одна на тенант). */
    Optional<Subscription> findFirstByOrderByCreatedAtAsc();
}
