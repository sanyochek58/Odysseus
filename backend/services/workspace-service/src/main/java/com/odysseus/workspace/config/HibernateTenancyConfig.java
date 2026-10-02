package com.odysseus.workspace.config;

import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Регистрирует резолвер тенанта в Hibernate. */
@Configuration(proxyBeanMethods = false)
public class HibernateTenancyConfig {

    @Bean
    HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer() {
        return properties -> properties.put(
                MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new TenantIdentifierResolver());
    }
}
