-- V7: Add Project Builder & Viva Defense Tables with strict Row-Level Security (RLS)

-- 1. Projects Table
CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    domain VARCHAR(100) NOT NULL,
    idea TEXT NOT NULL,
    constraints JSONB DEFAULT '{}'::jsonb,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_projects_user_id ON projects(user_id);

-- 2. Project Blueprints Table
CREATE TABLE IF NOT EXISTS project_blueprints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    version INT NOT NULL DEFAULT 1,
    content JSONB NOT NULL,
    source VARCHAR(50) NOT NULL, -- 'LLM' or 'TEMPLATE'
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_blueprints_project_id ON project_blueprints(project_id);
CREATE INDEX IF NOT EXISTS idx_project_blueprints_user_id ON project_blueprints(user_id);

-- 3. Project Milestones Table
CREATE TABLE IF NOT EXISTS project_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    blueprint_version INT NOT NULL DEFAULT 1,
    phase VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO', -- 'TODO', 'DOING', 'DONE'
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_milestones_project_id ON project_milestones(project_id);
CREATE INDEX IF NOT EXISTS idx_project_milestones_user_id ON project_milestones(user_id);

-- 4. Viva Questions Table
CREATE TABLE IF NOT EXISTS viva_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blueprint_id UUID NOT NULL REFERENCES project_blueprints(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    question TEXT NOT NULL,
    model_answer TEXT NOT NULL,
    topic VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_viva_questions_blueprint_id ON viva_questions(blueprint_id);
CREATE INDEX IF NOT EXISTS idx_viva_questions_user_id ON viva_questions(user_id);

-- 5. Viva Answers Table
CREATE TABLE IF NOT EXISTS viva_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    question_id UUID NOT NULL REFERENCES viva_questions(id) ON DELETE CASCADE,
    answer_text TEXT NOT NULL,
    scores JSONB NOT NULL,
    feedback TEXT NOT NULL,
    judged_by VARCHAR(50) NOT NULL, -- 'LLM' or 'HEURISTIC'
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_viva_answers_user_id ON viva_answers(user_id);
CREATE INDEX IF NOT EXISTS idx_viva_answers_question_id ON viva_answers(question_id);

-- ---------------------------------------------------------------------------
-- Row-Level Security Policies (FORCE RLS)
-- ---------------------------------------------------------------------------

ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS projects_tenant_policy ON projects;
CREATE POLICY projects_tenant_policy ON projects
    FOR ALL
    USING (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE project_blueprints ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_blueprints FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS project_blueprints_tenant_policy ON project_blueprints;
CREATE POLICY project_blueprints_tenant_policy ON project_blueprints
    FOR ALL
    USING (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE project_milestones ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_milestones FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS project_milestones_tenant_policy ON project_milestones;
CREATE POLICY project_milestones_tenant_policy ON project_milestones
    FOR ALL
    USING (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE viva_questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE viva_questions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS viva_questions_tenant_policy ON viva_questions;
CREATE POLICY viva_questions_tenant_policy ON viva_questions
    FOR ALL
    USING (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE viva_answers ENABLE ROW LEVEL SECURITY;
ALTER TABLE viva_answers FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS viva_answers_tenant_policy ON viva_answers;
CREATE POLICY viva_answers_tenant_policy ON viva_answers
    FOR ALL
    USING (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- ---------------------------------------------------------------------------
-- Grants to least-privilege role genz_app
-- ---------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON projects TO genz_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON project_blueprints TO genz_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON project_milestones TO genz_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON viva_questions TO genz_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON viva_answers TO genz_app;
