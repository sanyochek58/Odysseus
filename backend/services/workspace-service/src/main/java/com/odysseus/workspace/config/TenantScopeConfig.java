package com.odysseus.workspace.config;

import com.odysseus.events.TenantScope;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Связывает библиотеку common-events с тенантным контекстом сервиса. */
@Configuration(proxyBeanMethods = false)
public class TenantScopeConfig {

    @Bean
    TenantScope tenantScope() {
        return new TenantScope() {
            @Override
            public void runAsSystem(Runnable action) {
                TenantContext.runAsSystem(action);
            }

            @Override
            public void runAs(UUID workspaceId, Runnable action) {
                TenantContext.runAs(workspaceId, action);
            }
        };
    }
}
