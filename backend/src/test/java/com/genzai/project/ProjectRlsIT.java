package com.genzai.project;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ProjectRlsIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("genzai_project_rls_db")
     .withUsername("postgres")
     .withPassword("postgres")
     .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    @BeforeEach
    void setUpTestData() {
        // Create test non-superuser role to test RLS policies without superuser bypass
        jdbcTemplate.execute("""
            DO $$
            BEGIN
              IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'project_rls_role') THEN
                CREATE ROLE project_rls_role NOSUPERUSER NOBYPASSRLS;
              END IF;
              GRANT USAGE ON SCHEMA public TO project_rls_role;
              GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO project_rls_role;
            END
            $$;
        """);

        // Switch to the non-superuser role and set tenant to User A
        jdbcTemplate.execute("SET ROLE project_rls_role;");
        jdbcTemplate.execute(String.format("SET app.current_tenant = '%s';", userA));

        // Insert projects, blueprints, and milestones as User A
        jdbcTemplate.execute(String.format("""
            INSERT INTO projects (id, user_id, title, domain, idea, status)
            VALUES ('%s', '%s', 'Project A', 'Full-stack web', 'User A project idea', 'ACTIVE');
        """, UUID.randomUUID(), userA));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("RESET ROLE;");
        jdbcTemplate.execute("RESET app.current_tenant;");
    }

    @Test
    @DisplayName("User A can read their own projects under non-superuser RLS role")
    void userACanReadOwnProjects() {
        jdbcTemplate.execute("SET ROLE project_rls_role;");
        jdbcTemplate.execute(String.format("SET app.current_tenant = '%s';", userA));

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM projects", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("User B cannot see User A's projects: returns 0 rows under non-superuser RLS role")
    void userBCannotSeeUserAProjects() {
        jdbcTemplate.execute("SET ROLE project_rls_role;");
        jdbcTemplate.execute(String.format("SET app.current_tenant = '%s';", userB));

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM projects", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("Empty or missing tenant session yields 0 rows due to NULLIF RLS policy")
    void emptyTenantYieldsZeroRows() {
        jdbcTemplate.execute("SET ROLE project_rls_role;");
        jdbcTemplate.execute("RESET app.current_tenant;");

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM projects", Integer.class);
        assertThat(count).isEqualTo(0);
    }
}
