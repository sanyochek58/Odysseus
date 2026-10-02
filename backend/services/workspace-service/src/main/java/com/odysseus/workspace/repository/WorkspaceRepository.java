package com.odysseus.workspace.repository;

import com.odysseus.workspace.entity.Workspace;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Все запросы автоматически фильтруются по тенанту (@TenantId). */
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
}
