package com.odysseus.workspace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

class TenantContextFilterTest {

    private static final UUID WORKSPACE_A = UUID.randomUUID();
    private static final UUID WORKSPACE_B = UUID.randomUUID();
    private static final String USER_ID = "user-1";

    private final MemberRolePort memberRolePort = mock(MemberRolePort.class);
    private final TenantContextFilter filter =
            new TenantContextFilter(new ProblemDetailResponseWriter(JsonMapper.builder().build()), memberRolePort);

    private final List<UUID> seenTenants = new ArrayList<>();
    private final List<Authentication> seenAuthentications = new ArrayList<>();
    private final FilterChain chain = (request, response) -> {
        seenTenants.add(TenantContext.currentWorkspaceId().orElse(null));
        seenAuthentications.add(SecurityContextHolder.getContext().getAuthentication());
    };

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt token(UUID workspaceId) {
        return Jwt.withTokenValue("token").header("alg", "none").subject(USER_ID)
                .claim("organization", Map.of("acme", Map.of("id", workspaceId.toString())))
                .build();
    }

    private static void authenticate(Jwt jwt, GrantedAuthority... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(authorities)));
    }

    private List<String> roles() {
        assertThat(seenAuthentications).hasSize(1);
        return seenAuthentications.getFirst().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("doFilter: OWNER в A и MEMBER в B, с токеном B получает только MEMBER")
    void doFilter_ownerInAMemberInB_tokenB_getsMemberOnly() throws Exception {
        when(memberRolePort.findRole(WORKSPACE_A, USER_ID)).thenReturn(Optional.of(WorkspaceRole.OWNER));
        when(memberRolePort.findRole(WORKSPACE_B, USER_ID)).thenReturn(Optional.of(WorkspaceRole.MEMBER));
        authenticate(token(WORKSPACE_B));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(roles()).containsExactly("ROLE_MEMBER");
        assertThat(seenTenants).containsExactly(WORKSPACE_B);
        verify(memberRolePort, never()).findRole(eq(WORKSPACE_A), any());
    }

    @Test
    @DisplayName("doFilter: нет записи Member, ролей нет")
    void doFilter_noMember_noRoles() throws Exception {
        when(memberRolePort.findRole(WORKSPACE_A, USER_ID)).thenReturn(Optional.empty());
        authenticate(token(WORKSPACE_A));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(roles()).noneMatch(authority -> authority.startsWith("ROLE_"));
        assertThat(seenAuthentications.getFirst().getName()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("doFilter: роли из токена отбрасываются, остальные полномочия сохраняются, роль из БД")
    void doFilter_tokenRoles_replacedByMemberRole() throws Exception {
        when(memberRolePort.findRole(WORKSPACE_A, USER_ID)).thenReturn(Optional.of(WorkspaceRole.ADMIN));
        authenticate(token(WORKSPACE_A), new SimpleGrantedAuthority("ROLE_OWNER"),
                new SimpleGrantedAuthority("SCOPE_openid"));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(roles()).containsExactlyInAnyOrder("ROLE_ADMIN", "SCOPE_openid");
    }

    @Test
    @DisplayName("doFilter: роль читается в контексте тенанта токена ровно один раз")
    void doFilter_roleLookup_onceInsideTenantContext() throws Exception {
        List<UUID> lookupTenants = new ArrayList<>();
        when(memberRolePort.findRole(WORKSPACE_A, USER_ID)).thenAnswer(invocation -> {
            lookupTenants.add(TenantContext.requireWorkspaceId());
            return Optional.of(WorkspaceRole.MEMBER);
        });
        authenticate(token(WORKSPACE_A));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(lookupTenants).containsExactly(WORKSPACE_A);
        verify(memberRolePort, times(1)).findRole(any(), any());
    }

    @Test
    @DisplayName("doFilter: токен без организации, 403 и роль не читается")
    void doFilter_noOrganization_returns403() throws Exception {
        authenticate(Jwt.withTokenValue("token").header("alg", "none").subject(USER_ID).claim("scope", "x").build());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(seenAuthentications).isEmpty();
        verifyNoInteractions(memberRolePort);
    }

    @Test
    @DisplayName("doFilter: анонимный запрос идёт без тенанта и без чтения роли")
    void doFilter_anonymous_passesWithoutTenant() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(seenTenants).containsExactly((UUID) null);
        verifyNoInteractions(memberRolePort);
    }
}
