package com.smartdesk.config.tenant;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> currentTenant = new InheritableThreadLocal<>();
    public static final String DEFAULT_TENANT = "public";

    public static void setCurrentTenant(String tenant) {
        log.debug("Setting tenant to " + tenant);
        currentTenant.set(tenant);
    }

    public static String getCurrentTenant() {
        String tenant = currentTenant.get();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }

    public static void clear() {
        currentTenant.remove();
    }
}
