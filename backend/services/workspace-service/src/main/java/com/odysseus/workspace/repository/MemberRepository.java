package com.odysseus.workspace.repository;

import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.entity.Member;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Все запросы автоматически фильтруются по тенанту (@TenantId). */
public interface MemberRepository extends JpaRepository<Member, UUID> {

    boolean existsByUserId(String userId);

    /** Роль пользователя в workspace одним запросом. Условие по workspace явное в дополнение к @TenantId. */
    @Query("select m.role from Member m where m.workspaceId = :workspaceId and m.userId = :userId")
    Optional<WorkspaceRole> findRole(@Param("workspaceId") UUID workspaceId, @Param("userId") String userId);

    boolean existsByEmailIgnoreCase(String email);

    /** Блокирует строки владельцев до конца транзакции: сериализует проверку «последний OWNER». */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Member> findAllByRole(WorkspaceRole role);
}
