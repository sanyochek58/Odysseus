package com.odysseus.workspace.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token").header("alg", "none").subject("user-1");
    }

    @Test
    @DisplayName("extractWorkspaceId: одна организация с UUID даёт workspaceId")
    void extractWorkspaceId_singleOrganization_returnsId() {
        UUID workspaceId = UUID.randomUUID();
        Jwt token = jwt().claim("organization", Map.of("acme", Map.of("id", workspaceId.toString()))).build();

        assertThat(KeycloakJwtAuthenticationConverter.extractWorkspaceId(token)).contains(workspaceId);
    }

    @Test
    @DisplayName("extractWorkspaceId: нет claim, список алиасов, несколько организаций или не UUID дают пусто")
    void extractWorkspaceId_invalidClaim_returnsEmpty() {
        List<Jwt> tokens = List.of(
                jwt().claim("other", "x").build(),
                jwt().claim("organization", List.of("acme")).build(),
                jwt().claim("organization", Map.of(
                        "acme", Map.of("id", UUID.randomUUID().toString()),
                        "beta", Map.of("id", UUID.randomUUID().toString()))).build(),
                jwt().claim("organization", Map.of("acme", Map.of("id", "not-uuid"))).build(),
                jwt().claim("organization", Map.of("acme", Map.of())).build());

        tokens.forEach(token -> assertThat(KeycloakJwtAuthenticationConverter.extractWorkspaceId(token)).isEmpty());
    }

    @Test
    @DisplayName("convert: глобальные realm-роли игнорируются, ролей нет, имя из sub")
    void convert_realmRoles_ignored() {
        Jwt token = jwt().claim("realm_access", Map.of("roles", List.of("OWNER", "MEMBER", "ADMIN"))).build();

        AbstractAuthenticationToken authentication = converter.convert(token);

        assertThat(authentication.getName()).isEqualTo("user-1");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .noneMatch(authority -> authority.startsWith("ROLE_"));
    }

    @Test
    @DisplayName("extractVerifiedEmail: email_verified = true даёт email")
    void extractVerifiedEmail_verified_returnsEmail() {
        Jwt token = jwt().claim("email", "user@acme.io").claim("email_verified", true).build();

        assertThat(KeycloakJwtAuthenticationConverter.extractVerifiedEmail(token)).contains("user@acme.io");
    }

    @Test
    @DisplayName("extractVerifiedEmail: false, строка \"true\", нет claim или нет email дают пусто")
    void extractVerifiedEmail_notVerified_returnsEmpty() {
        List<Jwt> tokens = List.of(
                jwt().claim("email", "user@acme.io").claim("email_verified", false).build(),
                jwt().claim("email", "user@acme.io").claim("email_verified", "true").build(),
                jwt().claim("email", "user@acme.io").build(),
                jwt().claim("email_verified", true).build(),
                jwt().claim("email", " ").claim("email_verified", true).build());

        tokens.forEach(token -> assertThat(KeycloakJwtAuthenticationConverter.extractVerifiedEmail(token)).isEmpty());
    }
}
