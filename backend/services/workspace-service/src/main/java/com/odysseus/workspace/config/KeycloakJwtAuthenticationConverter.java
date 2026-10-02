package com.odysseus.workspace.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Превращает проверенный JWT Keycloak в аутентификацию без ролей: Keycloak даёт только личность (sub)
 * и организацию. Глобальные {@code realm_access.roles} игнорируются. Роль workspace добавляет
 * {@link TenantContextFilter} из записи Member в БД сервиса.
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /**
     * Claim Keycloak Organizations: {@code {"alias": {"id": "uuid"}}}. Маппер Organization Membership должен иметь
     * {@code addOrganizationId=true}, {@code jsonType.label=JSON} и явно {@code multivalued=true}: в realm-импорте
     * умолчание UI не применяется, без multivalued Keycloak 26.4 отдаёт {@code {"alias": {}}} без id.
     */
    static final String ORGANIZATION_CLAIM = "organization";

    private static final String ORGANIZATION_ID_KEY = "id";
    private static final String EMAIL_CLAIM = "email";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    /** Email из токена, только если {@code email_verified} строго boolean true. Иначе пусто. */
    public static Optional<String> extractVerifiedEmail(Jwt jwt) {
        if (!Boolean.TRUE.equals(jwt.getClaims().get(EMAIL_VERIFIED_CLAIM))
                || !(jwt.getClaims().get(EMAIL_CLAIM) instanceof String email)
                || email.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(email);
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
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
}
