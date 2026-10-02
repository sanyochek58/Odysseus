package com.odysseus.workspace.repository;

import com.odysseus.workspace.entity.Invitation;
import com.odysseus.workspace.entity.InvitationStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Все запросы автоматически фильтруются по тенанту (@TenantId). */
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    boolean existsByEmailAndStatus(String email, InvitationStatus status);
}
