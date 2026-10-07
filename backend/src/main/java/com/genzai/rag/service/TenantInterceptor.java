package com.genzai.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantInterceptor {

    private final JdbcTemplate jdbcTemplate;

    public void setTenantContext(String tenantId) {
        String safeTenant = (tenantId == null || tenantId.isBlank()) ? "anonymous" : tenantId.replace("'", "''");
        jdbcTemplate.execute("SET LOCAL app.current_tenant = '" + safeTenant + "';");
        log.debug("PostgreSQL session tenant set to: {}", safeTenant);
    }
}
