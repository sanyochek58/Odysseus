package com.odysseus.workspace.service;

import com.odysseus.workspace.config.MemberRolePort;
import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.repository.MemberRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Реализация порта ролей поверх {@code Member}: одна выборка роли по (workspace, userId). */
@Service
@RequiredArgsConstructor
public class MemberRoleAdapter implements MemberRolePort {

    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspaceRole> findRole(UUID workspaceId, String userId) {
        if (workspaceId == null || userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return memberRepository.findRole(workspaceId, userId);
    }
}
