package com.odysseus.workspace.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Снимает блокировку записи по истёкшей подписке с метода контроллера.
 * Только для продления подписки: иначе workspace с истёкшим сроком не сможет продлиться.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubscriptionNotRequired {
}
