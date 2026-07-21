package com.smartdesk.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Applies idempotent schema changes to every tenant schema. Hibernate's
 * ddl-auto only updates the default schema and therefore cannot maintain the
 * schemas selected dynamically by the multi-tenant connection provider.
 */
@Slf4j
@Service
public class TenantSchemaMigrationService {

    // PostgreSQL identifiers created by seed/import scripts may contain underscores.
    // Keep the allow-list strict because the schema name is interpolated into DDL.
    private static final Pattern VALID_SCHEMA = Pattern.compile("[a-z0-9_]+");

    private final JdbcTemplate jdbcTemplate;

    public TenantSchemaMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateTenantSchemas() {
        List<String> schemas = jdbcTemplate.queryForList(
                "SELECT subdomain FROM public.tenants WHERE is_active = true",
                String.class
        );

        for (String schema : schemas) {
            if (schema == null || !VALID_SCHEMA.matcher(schema).matches()) {
                throw new IllegalStateException("Invalid tenant schema name: " + schema);
            }

            jdbcTemplate.execute("ALTER TABLE \"" + schema
                    + "\".tickets ADD COLUMN IF NOT EXISTS ai_suggested_solution TEXT");
            log.info("Tenant schema {} is up to date", schema);
        }
    }
}
