package com.odysseus.workspace.service;

import com.odysseus.workspace.config.TenantContext;
import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.dto.WorkspaceRequest;
import com.odysseus.workspace.dto.WorkspaceResponse;
import com.odysseus.workspace.entity.Member;
import com.odysseus.workspace.entity.Workspace;
import com.odysseus.workspace.exception.ConflictException;
import com.odysseus.workspace.exception.NotFoundException;
import com.odysseus.workspace.mapper.WorkspaceMapper;
import com.odysseus.workspace.repository.MemberRepository;
import com.odysseus.workspace.repository.WorkspaceRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace текущего тенанта. Идентификатор workspace берётся только из TenantContext (JWT),
 * идентификатор из пути лишь сверяется: чужой workspace неотличим от несуществующего.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final MemberRepository memberRepository;
    private final SubscriptionService subscriptionService;
    private final WorkspaceMapper workspaceMapper;

    /** Регистрирует workspace организации из токена: владелец, пробная подписка и событие подписки. */
    @Transactional
    public WorkspaceResponse create(WorkspaceRequest request, String ownerUserId, String ownerEmail) {
        UUID workspaceId = TenantContext.requireWorkspaceId();
        if (workspaceRepository.existsById(workspaceId)) {
            throw new ConflictException("Workspace уже зарегистрирован");
        }
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(workspaceId)
                .name(request.name().strip())
                .build());
        memberRepository.save(Member.builder()
                .userId(ownerUserId)
                .email(ownerEmail == null ? null : ownerEmail.toLowerCase())
                .role(WorkspaceRole.OWNER)
                .build());
        subscriptionService.createTrial();
        return workspaceMapper.toResponse(workspace);
    }

    @Transactional(readOnly = true)
    public Page<WorkspaceResponse> list(Pageable pageable) {
        return workspaceRepository.findAll(pageable).map(workspaceMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID id) {
        return workspaceMapper.toResponse(find(id));
    }

    @Transactional
    public WorkspaceResponse rename(UUID id, WorkspaceRequest request) {
        Workspace workspace = find(id);
        workspace.setName(request.name().strip());
        return workspaceMapper.toResponse(workspaceRepository.save(workspace));
    }

    /** Чужой id неотличим от несуществующего: сверка с тенантом явно, не только через @TenantId. */
    private Workspace find(UUID id) {
        if (!TenantContext.requireWorkspaceId().equals(id)) {
            throw new NotFoundException("Workspace не найден");
        }
        return workspaceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Workspace не найден"));
    }
}
