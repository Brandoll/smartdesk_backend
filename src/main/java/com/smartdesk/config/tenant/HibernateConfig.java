package com.smartdesk.config.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class HibernateConfig implements HibernatePropertiesCustomizer {

    private final MultiTenantConnectionProviderImpl multiTenantConnectionProvider;
    private final CurrentTenantIdentifierResolverImpl currentTenantIdentifierResolver;

    public HibernateConfig(MultiTenantConnectionProviderImpl multiTenantConnectionProvider,
                           CurrentTenantIdentifierResolverImpl currentTenantIdentifierResolver) {
        this.multiTenantConnectionProvider = multiTenantConnectionProvider;
        this.currentTenantIdentifierResolver = currentTenantIdentifierResolver;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, multiTenantConnectionProvider);
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, currentTenantIdentifierResolver);
    }
}
