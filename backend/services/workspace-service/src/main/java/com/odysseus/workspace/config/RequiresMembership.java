package com.odysseus.workspace.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Доступ только участнику workspace из токена: любая роль из записи Member.
 * Без записи Member ролей нет, ответ 403 ProblemDetail.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'TECH_LEAD', 'MANAGER', 'MEMBER')")
public @interface RequiresMembership {
}
