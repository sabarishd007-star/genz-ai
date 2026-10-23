-- V6: Fix all RLS policies to use NULLIF(current_setting(...), '')::uuid
--     so an empty or missing tenant GUC yields 0 rows instead of a cast error.
--     Also enables FORCE ROW LEVEL SECURITY on any tables not already covered,
--     and fixes the vector_store policy to use the uuid-based tenant column.

-- -----------------------------------------------------------------------
-- vector_store: switch from text tenant_id to uuid comparison
-- -----------------------------------------------------------------------
DROP POLICY IF EXISTS tenant_isolation_policy ON vector_store;
CREATE POLICY tenant_isolation_policy ON vector_store
    FOR ALL
    USING  (tenant_id::text = COALESCE(NULLIF(current_setting('app.current_tenant', true), ''), '00000000-0000-0000-0000-000000000000'))
    WITH CHECK (tenant_id::text = COALESCE(NULLIF(current_setting('app.current_tenant', true), ''), '00000000-0000-0000-0000-000000000000'));
ALTER TABLE vector_store FORCE ROW LEVEL SECURITY;

-- -----------------------------------------------------------------------
-- study_plans
-- -----------------------------------------------------------------------
DROP POLICY IF EXISTS study_plans_tenant_policy ON study_plans;
CREATE POLICY study_plans_tenant_policy ON study_plans
    FOR ALL
    USING  (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- -----------------------------------------------------------------------
-- study_tasks
-- -----------------------------------------------------------------------
DROP POLICY IF EXISTS study_tasks_tenant_policy ON study_tasks;
CREATE POLICY study_tasks_tenant_policy ON study_tasks
    FOR ALL
    USING  (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- -----------------------------------------------------------------------
-- pomodoro_sessions
-- -----------------------------------------------------------------------
DROP POLICY IF EXISTS pomodoro_sessions_tenant_policy ON pomodoro_sessions;
CREATE POLICY pomodoro_sessions_tenant_policy ON pomodoro_sessions
    FOR ALL
    USING  (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- -----------------------------------------------------------------------
-- user_settings
-- -----------------------------------------------------------------------
DROP POLICY IF EXISTS user_settings_tenant_policy ON user_settings;
CREATE POLICY user_settings_tenant_policy ON user_settings
    FOR ALL
    USING  (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- -----------------------------------------------------------------------
-- activity_log
-- -----------------------------------------------------------------------
DROP POLICY IF EXISTS activity_log_tenant_policy ON activity_log;
CREATE POLICY activity_log_tenant_policy ON activity_log
    FOR ALL
    USING  (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- -----------------------------------------------------------------------
-- aptitude_attempts
-- -----------------------------------------------------------------------
DROP POLICY IF EXISTS aptitude_attempts_tenant_policy ON aptitude_attempts;
CREATE POLICY aptitude_attempts_tenant_policy ON aptitude_attempts
    FOR ALL
    USING  (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- -----------------------------------------------------------------------
-- aptitude_attempt_answers
-- -----------------------------------------------------------------------
DROP POLICY IF EXISTS aptitude_attempt_answers_tenant_policy ON aptitude_attempt_answers;
CREATE POLICY aptitude_attempt_answers_tenant_policy ON aptitude_attempt_answers
    FOR ALL
    USING  (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);
