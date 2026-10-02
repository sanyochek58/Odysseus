package com.odysseus.workspace.repository;

import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.entity.Member;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Все запросы автоматически фильтруются по тенанту (@TenantId). */
public interface MemberRepository extends JpaRepository<Member, UUID> {

    boolean existsByUserId(String userId);

    boolean existsByEmailIgnoreCase(String email);

    /** Блокирует строки владельцев до конца транзакции: сериализует проверку «последний OWNER». */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Member> findAllByRole(WorkspaceRole role);
}
