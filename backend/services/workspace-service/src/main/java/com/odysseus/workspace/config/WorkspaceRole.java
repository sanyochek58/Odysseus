package com.odysseus.workspace.config;

/**
 * Роли участника workspace. Источник: realm-роли Keycloak ({@code realm_access.roles}).
 * В {@code @PreAuthorize} использовать {@code hasRole('OWNER')} и т.п.
 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    TECH_LEAD,
    MANAGER,
    MEMBER;

    public String authority() {
        return "ROLE_" + name();
    }
}
