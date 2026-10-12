-- V5: Add Study Planner tables (study_plans, study_tasks, pomodoro_sessions) and FORCE RLS

-- 1. Study Plans
CREATE TABLE IF NOT EXISTS study_plans (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    exam_date DATE NOT NULL,
    hours_per_day DOUBLE PRECISION NOT NULL DEFAULT 2.0,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_study_plans_user_id ON study_plans(user_id);
CREATE INDEX IF NOT EXISTS idx_study_plans_user_status ON study_plans(user_id, status);

ALTER TABLE study_plans ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS study_plans_tenant_policy ON study_plans;
CREATE POLICY study_plans_tenant_policy ON study_plans
    FOR ALL
    USING (user_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (user_id::text = current_setting('app.current_tenant', true));

-- 2. Study Tasks
CREATE TABLE IF NOT EXISTS study_tasks (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES study_plans(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_date DATE NOT NULL,
    topic VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL DEFAULT 'STUDY',
    planned_minutes INT NOT NULL DEFAULT 60,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    completed_at TIMESTAMP WITH TIME ZONE,
    source_task_id UUID REFERENCES study_tasks(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_study_tasks_plan_id ON study_tasks(plan_id);
CREATE INDEX IF NOT EXISTS idx_study_tasks_user_date ON study_tasks(user_id, task_date);
CREATE INDEX IF NOT EXISTS idx_study_tasks_user_status ON study_tasks(user_id, status);

ALTER TABLE study_tasks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS study_tasks_tenant_policy ON study_tasks;
CREATE POLICY study_tasks_tenant_policy ON study_tasks
    FOR ALL
    USING (user_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (user_id::text = current_setting('app.current_tenant', true));

-- 3. Pomodoro Sessions
CREATE TABLE IF NOT EXISTS pomodoro_sessions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id UUID REFERENCES study_tasks(id) ON DELETE SET NULL,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    planned_seconds INT NOT NULL DEFAULT 1500,
    completed_at TIMESTAMP WITH TIME ZONE,
    counted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pomodoro_sessions_user ON pomodoro_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_pomodoro_sessions_task ON pomodoro_sessions(task_id);

ALTER TABLE pomodoro_sessions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS pomodoro_sessions_tenant_policy ON pomodoro_sessions;
CREATE POLICY pomodoro_sessions_tenant_policy ON pomodoro_sessions
    FOR ALL
    USING (user_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (user_id::text = current_setting('app.current_tenant', true));

-- 4. Tighten V3 and V4 RLS policies (strict tenant check without NULL bypass) and FORCE RLS
DROP POLICY IF EXISTS user_settings_tenant_policy ON user_settings;
CREATE POLICY user_settings_tenant_policy ON user_settings
    FOR ALL
    USING (user_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (user_id::text = current_setting('app.current_tenant', true));

DROP POLICY IF EXISTS activity_log_tenant_policy ON activity_log;
CREATE POLICY activity_log_tenant_policy ON activity_log
    FOR ALL
    USING (user_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (user_id::text = current_setting('app.current_tenant', true));

DROP POLICY IF EXISTS aptitude_attempts_tenant_policy ON aptitude_attempts;
CREATE POLICY aptitude_attempts_tenant_policy ON aptitude_attempts
    FOR ALL
    USING (user_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (user_id::text = current_setting('app.current_tenant', true));

DROP POLICY IF EXISTS aptitude_attempt_answers_tenant_policy ON aptitude_attempt_answers;
CREATE POLICY aptitude_attempt_answers_tenant_policy ON aptitude_attempt_answers
    FOR ALL
    USING (user_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (user_id::text = current_setting('app.current_tenant', true));

ALTER TABLE study_plans FORCE ROW LEVEL SECURITY;
ALTER TABLE study_tasks FORCE ROW LEVEL SECURITY;
ALTER TABLE pomodoro_sessions FORCE ROW LEVEL SECURITY;
ALTER TABLE user_settings FORCE ROW LEVEL SECURITY;
ALTER TABLE activity_log FORCE ROW LEVEL SECURITY;
ALTER TABLE aptitude_attempts FORCE ROW LEVEL SECURITY;
ALTER TABLE aptitude_attempt_answers FORCE ROW LEVEL SECURITY;
