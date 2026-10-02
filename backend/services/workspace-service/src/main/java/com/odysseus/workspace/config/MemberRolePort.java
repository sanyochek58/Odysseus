package com.odysseus.workspace.config;

import java.util.Optional;
import java.util.UUID;

/**
 * Порт к роли участника в БД сервиса. Источник прав: запись Member для пары (workspace из JWT, sub).
 * Вызывается внутри {@link TenantContext} запроса один раз на запрос.
 */
public interface MemberRolePort {

    /** Роль пользователя в workspace. Пусто, если пользователь не участник: ролей нет. */
    Optional<WorkspaceRole> findRole(UUID workspaceId, String userId);
}
