package com.odysseus.workspace.repository;

import com.odysseus.workspace.entity.SubscriptionExtensionKey;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Все запросы автоматически фильтруются по тенанту (@TenantId). */
public interface SubscriptionExtensionKeyRepository extends JpaRepository<SubscriptionExtensionKey, UUID> {
}
