package com.odysseus.workspace.controller;

import com.odysseus.workspace.config.RequiresMembership;
import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.dto.MemberResponse;
import com.odysseus.workspace.dto.MemberRoleRequest;
import com.odysseus.workspace.service.MemberService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Участники workspace из токена. */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    @RequiresMembership
    public Page<MemberResponse> list(Pageable pageable) {
        return memberService.list(pageable);
    }

    @GetMapping("/{id}")
    @RequiresMembership
    public MemberResponse get(@PathVariable UUID id) {
        return memberService.get(id);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('OWNER')")
    public MemberResponse changeRole(@PathVariable UUID id, @Valid @RequestBody MemberRoleRequest request) {
        return memberService.changeRole(id, request.role());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public void remove(@PathVariable UUID id, Authentication authentication) {
        memberService.remove(id, callerRole(authentication));
    }

    /** Роль вызывающего из полномочий, выставленных TenantContextFilter по записи Member; нет роли это null. */
    private static WorkspaceRole callerRole(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        for (WorkspaceRole role : WorkspaceRole.values()) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                if (role.authority().equals(authority.getAuthority())) {
                    return role;
                }
            }
        }
        return null;
    }
}
