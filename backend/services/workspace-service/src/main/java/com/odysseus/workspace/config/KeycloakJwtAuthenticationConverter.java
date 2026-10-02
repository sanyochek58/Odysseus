package com.odysseus.workspace.config;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Превращает проверенный JWT Keycloak в аутентификацию: роли из {@code realm_access.roles}
 * (только известные {@link WorkspaceRole}), workspace из claim организации.
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Claim Keycloak Organizations. Нужен маппер с «Add organization id»: {@code {"alias": {"id": "uuid"}}}. */
    static final String ORGANIZATION_CLAIM = "organization";

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_KEY = "roles";
    private static final String ORGANIZATION_ID_KEY = "id";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, extractAuthorities(jwt), jwt.getSubject());
    }

    /**
     * workspaceId из claim организации. Пусто, если организаций нет, их больше одной
     * (клиент запрашивает конкретную через scope {@code organization:<alias>}) или id не UUID.
     */
    public static Optional<UUID> extractWorkspaceId(Jwt jwt) {
        if (!(jwt.getClaims().get(ORGANIZATION_CLAIM) instanceof Map<?, ?> organizations)
                || organizations.size() != 1) {
            return Optional.empty();
        }
        if (!(organizations.values().iterator().next() instanceof Map<?, ?> organization)
                || !(organization.get(ORGANIZATION_ID_KEY) instanceof String id)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        if (!(jwt.getClaims().get(REALM_ACCESS_CLAIM) instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get(ROLES_KEY) instanceof Collection<?> roles)) {
            return List.of();
        }
        Set<WorkspaceRole> known = EnumSet.noneOf(WorkspaceRole.class);
        for (WorkspaceRole candidate : WorkspaceRole.values()) {
            if (roles.contains(candidate.name())) {
                known.add(candidate);
            }
        }
        return known.stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();
    }
}
