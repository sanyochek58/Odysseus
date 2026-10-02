package com.odysseus.workspace.config;

import java.lang.reflect.Field;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.hibernate.event.spi.PreUpsertEvent;
import org.hibernate.event.spi.PreUpsertEventListener;
import org.hibernate.integrator.spi.Integrator;

/**
 * Запрещает запись сущностей с фиктивным тенантом.
 * Сессия должна быть открыта в том же контексте, что активен сейчас (иначе в {@code workspace_id} попал бы
 * {@link TenantContext#NO_TENANT}), а сущности с {@code @TenantId} нельзя писать в системном контексте.
 */
public class TenantWriteGuard implements Integrator,
        PreInsertEventListener, PreUpdateEventListener, PreDeleteEventListener, PreUpsertEventListener {

    private static final ClassValue<Boolean> TENANT_SCOPED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (field.isAnnotationPresent(TenantId.class)) {
                        return true;
                    }
                }
            }
            return false;
        }
    };

    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext, SessionFactoryImplementor sessionFactory) {
        EventListenerRegistry registry = sessionFactory.getServiceRegistry().requireService(EventListenerRegistry.class);
        registry.appendListeners(EventType.PRE_INSERT, this);
        registry.appendListeners(EventType.PRE_UPDATE, this);
        registry.appendListeners(EventType.PRE_DELETE, this);
        registry.appendListeners(EventType.PRE_UPSERT, this);
    }

    @Override
    public boolean onPreInsert(PreInsertEvent event) {
        check(event.getSession(), event.getEntity());
        return false;
    }

    @Override
    public boolean onPreUpdate(PreUpdateEvent event) {
        check(event.getSession(), event.getEntity());
        return false;
    }

    @Override
    public boolean onPreDelete(PreDeleteEvent event) {
        check(event.getSession(), event.getEntity());
        return false;
    }

    @Override
    public boolean onPreUpsert(PreUpsertEvent event) {
        check(event.getSession(), event.getEntity());
        return false;
    }

    private static void check(SharedSessionContractImplementor session, Object entity) {
        UUID current = TenantContext.currentIdentifier();
        if (current == null) {
            throw new IllegalStateException("Запись вне контекста тенанта");
        }
        if (!current.equals(session.getTenantIdentifierValue())) {
            throw new IllegalStateException("Тенант сессии не совпадает с текущим контекстом");
        }
        if (TenantContext.SYSTEM.equals(current) && entity != null && TENANT_SCOPED.get(entity.getClass())) {
            throw new IllegalStateException("Запись тенантной сущности в системном контексте запрещена");
        }
    }
}
