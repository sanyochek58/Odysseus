package com.odysseus.workspace.controller;

import com.odysseus.workspace.config.KeycloakJwtAuthenticationConverter;
import com.odysseus.workspace.dto.InvitationRequest;
import com.odysseus.workspace.dto.InvitationResponse;
import com.odysseus.workspace.dto.MemberResponse;
import com.odysseus.workspace.exception.ForbiddenOperationException;
import com.odysseus.workspace.service.InvitationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public InvitationResponse create(@Valid @RequestBody InvitationRequest request, @AuthenticationPrincipal Jwt jwt) {
        return invitationService.create(request, jwt.getSubject());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public Page<InvitationResponse> list(Pageable pageable) {
        return invitationService.list(pageable);
    }

    /** Отзыв приглашения. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public InvitationResponse revoke(@PathVariable UUID id) {
        return invitationService.revoke(id);
    }

    /**
     * Принятие приглашения текущим пользователем. Email из токена учитывается только при
     * {@code email_verified = true}, иначе 403; затем он должен совпасть с приглашённым.
     */
    @PostMapping("/{id}/accept")
    public MemberResponse accept(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String email = KeycloakJwtAuthenticationConverter.extractVerifiedEmail(jwt)
                .orElseThrow(() -> new ForbiddenOperationException("Email в токене не подтверждён"));
        return invitationService.accept(id, jwt.getSubject(), email);
    }
}
