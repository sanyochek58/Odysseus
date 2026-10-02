package com.odysseus.workspace.controller;

import com.odysseus.workspace.config.RequiresMembership;
import com.odysseus.workspace.config.SubscriptionNotRequired;
import com.odysseus.workspace.dto.WorkspaceRequest;
import com.odysseus.workspace.dto.WorkspaceResponse;
import com.odysseus.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    /**
     * Регистрирует workspace организации из токена. Подписки ещё нет, поэтому блокировка записи снята.
     * Записи Member до регистрации нет, поэтому роль не требуется: первый пользователь организации,
     * зарегистрировавший workspace, становится OWNER; повтор для той же организации даёт 409.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SubscriptionNotRequired
    @PreAuthorize("isAuthenticated()")
    public WorkspaceResponse create(@Valid @RequestBody WorkspaceRequest request, @AuthenticationPrincipal Jwt jwt) {
        return workspaceService.create(request, jwt.getSubject(), jwt.getClaimAsString("email"));
    }

    @GetMapping
    @RequiresMembership
    public Page<WorkspaceResponse> list(Pageable pageable) {
        return workspaceService.list(pageable);
    }

    @GetMapping("/{id}")
    @RequiresMembership
    public WorkspaceResponse get(@PathVariable UUID id) {
        return workspaceService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public WorkspaceResponse rename(@PathVariable UUID id, @Valid @RequestBody WorkspaceRequest request) {
        return workspaceService.rename(id, request);
    }
}
