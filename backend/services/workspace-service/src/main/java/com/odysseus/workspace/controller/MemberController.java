package com.odysseus.workspace.controller;

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
    public Page<MemberResponse> list(Pageable pageable) {
        return memberService.list(pageable);
    }

    @GetMapping("/{id}")
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
    public void remove(@PathVariable UUID id) {
        memberService.remove(id);
    }
}
