package com.genzai.aptitude;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AptitudeRlsIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("genzai_rls_test_db")
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
              IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'rls_test_role') THEN
                CREATE ROLE rls_test_role NOSUPERUSER NOBYPASSRLS;
              END IF;
              GRANT USAGE ON SCHEMA public TO rls_test_role;
              GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO rls_test_role;
            END
            $$;
        """);

        // Insert test user records
        jdbcTemplate.update("INSERT INTO users (id, email, password, full_name, role) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                userA, "userA@example.com", "pass", "User A", "USER");
        jdbcTemplate.update("INSERT INTO users (id, email, password, full_name, role) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                userB, "userB@example.com", "pass", "User B", "USER");

        // Insert test question
        UUID questionId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO aptitude_questions (id, content_hash, track, topic, question, options_json, correct_answer, source)
            VALUES (?, 'hash_rls_test', 'QUANTITATIVE', 'PERCENTAGE', 'Sample Q?', '["1","2","3","4","5"]', 'B', 'TEST')
            ON CONFLICT (content_hash) DO NOTHING
        """, questionId);

        UUID actualQId = jdbcTemplate.queryForObject(
                "SELECT id FROM aptitude_questions WHERE content_hash = 'hash_rls_test'",
                UUID.class
        );

        UUID attemptId = UUID.randomUUID();
        // Insert attempt for User B
        jdbcTemplate.update("INSERT INTO aptitude_attempts (id, user_id, track, mode, total_questions, correct_count, score_pct, status) VALUES (?, ?, 'QUANTITATIVE', 'PRACTICE', 10, 8, 80.0, 'SUBMITTED')",
                attemptId, userB);

        // Insert answer for User B
        jdbcTemplate.update("INSERT INTO aptitude_attempt_answers (id, attempt_id, user_id, question_id, selected_option, is_correct, time_spent_seconds) VALUES (gen_random_uuid(), ?, ?, ?, 'B', true, 30)",
                attemptId, userB, actualQId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("RESET ROLE;");
        jdbcTemplate.execute("RESET app.current_tenant;");
    }

    @Test
    @DisplayName("With app.current_tenant = User A, querying User B attempts and answers returns 0 rows")
    void rls_IsolatesUserBDataFromUserA() {
        // Execute under non-superuser role
        jdbcTemplate.execute("SET ROLE rls_test_role;");
        jdbcTemplate.execute(String.format("SET app.current_tenant = '%s';", userA));

        Integer userBRowsVisible = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM aptitude_attempts WHERE user_id = ?",
                Integer.class,
                userB
        );
        Integer userBAnswersVisible = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM aptitude_attempt_answers WHERE user_id = ?",
                Integer.class,
                userB
        );

        assertThat(userBRowsVisible).isEqualTo(0);
        assertThat(userBAnswersVisible).isEqualTo(0);
    }

    @Test
    @DisplayName("With no tenant set, querying returns 0 rows for attempts and answers")
    void rls_UnsetTenantReturnsZeroRows() {
        jdbcTemplate.execute("SET ROLE rls_test_role;");
        jdbcTemplate.execute("RESET app.current_tenant;");

        Integer totalAttempts = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM aptitude_attempts",
                Integer.class
        );
        Integer totalAnswers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM aptitude_attempt_answers",
                Integer.class
        );

        assertThat(totalAttempts).isEqualTo(0);
        assertThat(totalAnswers).isEqualTo(0);
    }
}
