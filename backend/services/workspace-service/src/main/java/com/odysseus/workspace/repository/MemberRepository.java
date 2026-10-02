package com.odysseus.workspace.repository;

import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.entity.Member;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Все запросы автоматически фильтруются по тенанту (@TenantId). */
public interface MemberRepository extends JpaRepository<Member, UUID> {

    boolean existsByUserId(String userId);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(WorkspaceRole role);
}
