package com.mtbs.shared.multitenancy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {

    private TenantContext() {}

    private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();
    private static final ThreadLocal<Long>   CURRENT_TENANT = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
        log.debug("TenantContext: set tenantId={}", tenantId);
    }

    public static void setCurrentSchema(String schema) {
        CURRENT_SCHEMA.set(schema);
        log.debug("TenantContext: set schemaName={}", schema);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static String getSchemaName() {
        return CURRENT_SCHEMA.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_SCHEMA.remove();
        log.debug("TenantContext: cleared");
    }

    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }
}