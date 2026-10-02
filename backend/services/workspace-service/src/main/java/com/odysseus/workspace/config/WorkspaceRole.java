package com.odysseus.workspace.config;

/**
 * Роли участника workspace. Источник: {@code Member.role} в БД сервиса для пары (workspace из JWT, sub),
 * не realm-роли Keycloak.
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
