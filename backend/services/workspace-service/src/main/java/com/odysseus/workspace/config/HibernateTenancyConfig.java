package com.odysseus.workspace.config;

import java.util.List;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.jpa.boot.spi.JpaSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Регистрирует резолвер тенанта и защиту от SQL и записи вне контекста тенанта. */
@Configuration(proxyBeanMethods = false)
public class HibernateTenancyConfig {

    @Bean
    HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer() {
        return properties -> {
            properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new TenantIdentifierResolver());
            properties.put(JdbcSettings.STATEMENT_INSPECTOR, new TenantStatementGuard());
            properties.put(JpaSettings.INTEGRATOR_PROVIDER, (IntegratorProvider) () -> List.of(new TenantWriteGuard()));
        };
    }
}
