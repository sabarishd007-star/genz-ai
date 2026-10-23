package com.genzai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fail-fast guard: verifies at startup that the datasource role used by the
 * application is NOT a superuser and does NOT have BYPASSRLS.
 * <p>
 * If either flag is true the application will throw an exception and refuse
 * to start, preventing silent RLS bypass in any environment.
 */
@Component
@Slf4j
public class StartupRoleCheck {

    private final JdbcTemplate jdbcTemplate;

    public StartupRoleCheck(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyAppRole() {
        String sql = """
                SELECT rolname, rolsuper, rolbypassrls
                  FROM pg_roles
                 WHERE rolname = current_user
                """;

        jdbcTemplate.query(sql, rs -> {
            String roleName   = rs.getString("rolname");
            boolean isSuper   = rs.getBoolean("rolsuper");
            boolean bypassRls = rs.getBoolean("rolbypassrls");

            if (isSuper) {
                throw new IllegalStateException(
                    "SECURITY: datasource role '%s' is a superuser. " +
                    "RLS is silently bypassed for superusers. " +
                    "Set APP_DB_USER to a non-superuser role (e.g. genz_app)."
                    .formatted(roleName));
            }
            if (bypassRls) {
                throw new IllegalStateException(
                    "SECURITY: datasource role '%s' has BYPASSRLS. " +
                    "All row-level security policies are inactive. " +
                    "Revoke BYPASSRLS or use a different role."
                    .formatted(roleName));
            }

            log.info("StartupRoleCheck passed: role='{}' superuser={} bypassRls={}",
                     roleName, isSuper, bypassRls);
        });
    }
}
