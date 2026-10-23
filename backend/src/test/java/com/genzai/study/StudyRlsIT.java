package com.genzai.study;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StudyRlsIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    private final UUID planIdA = UUID.randomUUID();
    private final UUID taskIdA = UUID.randomUUID();
    private final UUID pomodoroIdA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Ensure non-superuser test role exists for strict RLS policy enforcement
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

        // Seed test users in users table
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password, full_name, role) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
            userA, "userA_" + userA + "@example.com", "pass", "User A", "USER"
        );
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password, full_name, role) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
            userB, "userB_" + userB + "@example.com", "pass", "User B", "USER"
        );

        // Seed Study Plan for User A
        jdbcTemplate.update(
            """
            INSERT INTO study_plans (id, user_id, title, status, exam_date, hours_per_day, timezone)
            VALUES (?, ?, 'User A Study Plan', 'ACTIVE', ?, 2.5, 'Asia/Kolkata')
            """,
            planIdA, userA, LocalDate.now().plusDays(30)
        );

        // Seed Study Task for User A
        jdbcTemplate.update(
            """
            INSERT INTO study_tasks (id, plan_id, user_id, task_date, topic, type, planned_minutes, status)
            VALUES (?, ?, ?, ?, 'Algorithms & Data Structures', 'STUDY', 60, 'PENDING')
            """,
            taskIdA, planIdA, userA, LocalDate.now()
        );

        // Seed Pomodoro Session for User A
        jdbcTemplate.update(
            """
            INSERT INTO pomodoro_sessions (id, user_id, task_id, planned_seconds, counted)
            VALUES (?, ?, ?, 1500, false)
            """,
            pomodoroIdA, userA, taskIdA
        );
    }

    @AfterEach
    void tearDown() {
        // Reset role and tenant before cleaning up
        jdbcTemplate.execute("RESET ROLE;");
        jdbcTemplate.execute("RESET app.current_tenant;");

        // Clean up test fixtures
        jdbcTemplate.update("DELETE FROM pomodoro_sessions WHERE user_id IN (?, ?)", userA, userB);
        jdbcTemplate.update("DELETE FROM study_tasks WHERE user_id IN (?, ?)", userA, userB);
        jdbcTemplate.update("DELETE FROM study_plans WHERE user_id IN (?, ?)", userA, userB);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", userA, userB);
    }

    @Test
    @DisplayName("User A can read their own study plan, tasks, and pomodoro sessions")
    void userA_CanReadOwnStudyData() {
        jdbcTemplate.execute("SET ROLE rls_test_role;");
        jdbcTemplate.execute(String.format("SET app.current_tenant = '%s';", userA));

        Integer planCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM study_plans WHERE id = ?",
            Integer.class,
            planIdA
        );
        Integer taskCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM study_tasks WHERE id = ?",
            Integer.class,
            taskIdA
        );
        Integer pomodoroCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pomodoro_sessions WHERE id = ?",
            Integer.class,
            pomodoroIdA
        );

        assertThat(planCount).isEqualTo(1);
        assertThat(taskCount).isEqualTo(1);
        assertThat(pomodoroCount).isEqualTo(1);
    }

    @Test
    @DisplayName("User B querying User A's study plans receives 0 rows (isolated by RLS)")
    void getPlan_EnforcesUserIsolation() {
        jdbcTemplate.execute("SET ROLE rls_test_role;");
        jdbcTemplate.execute(String.format("SET app.current_tenant = '%s';", userB));

        Integer planCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM study_plans WHERE id = ?",
            Integer.class,
            planIdA
        );
        Integer totalPlans = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM study_plans",
            Integer.class
        );

        assertThat(planCount).isEqualTo(0);
        assertThat(totalPlans).isEqualTo(0);
    }

    @Test
    @DisplayName("User B querying User A's study tasks receives 0 rows (isolated by RLS)")
    void getTask_EnforcesUserIsolation() {
        jdbcTemplate.execute("SET ROLE rls_test_role;");
        jdbcTemplate.execute(String.format("SET app.current_tenant = '%s';", userB));

        Integer taskCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM study_tasks WHERE id = ?",
            Integer.class,
            taskIdA
        );
        Integer totalTasks = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM study_tasks",
            Integer.class
        );

        assertThat(taskCount).isEqualTo(0);
        assertThat(totalTasks).isEqualTo(0);
    }

    @Test
    @DisplayName("User B querying User A's pomodoro sessions receives 0 rows (isolated by RLS)")
    void getPomodoro_EnforcesUserIsolation() {
        jdbcTemplate.execute("SET ROLE rls_test_role;");
        jdbcTemplate.execute(String.format("SET app.current_tenant = '%s';", userB));

        Integer pomodoroCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pomodoro_sessions WHERE id = ?",
            Integer.class,
            pomodoroIdA
        );
        Integer totalSessions = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pomodoro_sessions",
            Integer.class
        );

        assertThat(pomodoroCount).isEqualTo(0);
        assertThat(totalSessions).isEqualTo(0);
    }

    @Test
    @DisplayName("Empty or unset tenant yields 0 rows without cast exception (NULLIF verification)")
    void emptyTenant_Returns0Rows() {
        jdbcTemplate.execute("SET ROLE rls_test_role;");

        // Test with app.current_tenant reset
        jdbcTemplate.execute("RESET app.current_tenant;");

        Integer plansUnset = jdbcTemplate.queryForObject("SELECT count(*) FROM study_plans", Integer.class);
        Integer tasksUnset = jdbcTemplate.queryForObject("SELECT count(*) FROM study_tasks", Integer.class);
        Integer pomodoroUnset = jdbcTemplate.queryForObject("SELECT count(*) FROM pomodoro_sessions", Integer.class);

        assertThat(plansUnset).isEqualTo(0);
        assertThat(tasksUnset).isEqualTo(0);
        assertThat(pomodoroUnset).isEqualTo(0);

        // Test with app.current_tenant explicitly set to empty string
        jdbcTemplate.execute("SET app.current_tenant = '';");

        Integer plansEmpty = jdbcTemplate.queryForObject("SELECT count(*) FROM study_plans", Integer.class);
        Integer tasksEmpty = jdbcTemplate.queryForObject("SELECT count(*) FROM study_tasks", Integer.class);
        Integer pomodoroEmpty = jdbcTemplate.queryForObject("SELECT count(*) FROM pomodoro_sessions", Integer.class);

        assertThat(plansEmpty).isEqualTo(0);
        assertThat(tasksEmpty).isEqualTo(0);
        assertThat(pomodoroEmpty).isEqualTo(0);
    }

    @Test
    @DisplayName("Cross-tenant modification attempts are blocked: UPDATE and DELETE affect 0 rows")
    void crossTenantModification_BlockedByRls() {
        jdbcTemplate.execute("SET ROLE rls_test_role;");
        jdbcTemplate.execute(String.format("SET app.current_tenant = '%s';", userB));

        // User B attempts to rename User A's study plan
        int rowsUpdated = jdbcTemplate.update(
            "UPDATE study_plans SET title = 'Hacked Plan' WHERE id = ?",
            planIdA
        );
        assertThat(rowsUpdated).isEqualTo(0);

        // User B attempts to delete User A's study task
        int rowsDeleted = jdbcTemplate.update(
            "DELETE FROM study_tasks WHERE id = ?",
            taskIdA
        );
        assertThat(rowsDeleted).isEqualTo(0);

        // Switch back to User A and confirm data remains intact and unchanged
        jdbcTemplate.execute(String.format("SET app.current_tenant = '%s';", userA));
        String title = jdbcTemplate.queryForObject(
            "SELECT title FROM study_plans WHERE id = ?",
            String.class,
            planIdA
        );
        assertThat(title).isEqualTo("User A Study Plan");
    }
}
