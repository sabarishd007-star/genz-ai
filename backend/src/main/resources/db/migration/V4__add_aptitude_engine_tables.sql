-- V4: Add Aptitude Engine tables (aptitude_questions, aptitude_attempts, aptitude_attempt_answers) with RLS

-- 1. Global Question Bank
CREATE TABLE IF NOT EXISTS aptitude_questions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    content_hash VARCHAR(64) UNIQUE NOT NULL,
    track VARCHAR(32) NOT NULL,
    topic VARCHAR(64) NOT NULL,
    difficulty VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    difficulty_approximate BOOLEAN DEFAULT FALSE,
    question TEXT NOT NULL,
    options_json TEXT NOT NULL,
    correct_answer VARCHAR(1) NOT NULL,
    rationale TEXT,
    source VARCHAR(32) NOT NULL,
    flagged_for_review BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_aptitude_questions_track ON aptitude_questions(track);
CREATE INDEX IF NOT EXISTS idx_aptitude_questions_track_topic ON aptitude_questions(track, topic);
CREATE INDEX IF NOT EXISTS idx_aptitude_questions_content_hash ON aptitude_questions(content_hash);

-- 2. User Aptitude Attempts
CREATE TABLE IF NOT EXISTS aptitude_attempts (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    track VARCHAR(32) NOT NULL,
    mode VARCHAR(32) NOT NULL DEFAULT 'PRACTICE',
    total_questions INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    score_pct DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    time_limit_seconds INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_aptitude_attempts_user_id ON aptitude_attempts(user_id);
CREATE INDEX IF NOT EXISTS idx_aptitude_attempts_user_track ON aptitude_attempts(user_id, track);
CREATE INDEX IF NOT EXISTS idx_aptitude_attempts_user_started ON aptitude_attempts(user_id, started_at DESC);

-- RLS for aptitude_attempts
ALTER TABLE aptitude_attempts ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS aptitude_attempts_tenant_policy ON aptitude_attempts;
CREATE POLICY aptitude_attempts_tenant_policy ON aptitude_attempts
    FOR ALL
    USING (user_id::text = current_setting('app.current_tenant', true) OR current_setting('app.current_tenant', true) IS NULL OR current_setting('app.current_tenant', true) = '')
    WITH CHECK (user_id::text = current_setting('app.current_tenant', true) OR current_setting('app.current_tenant', true) IS NULL OR current_setting('app.current_tenant', true) = '');

-- 3. Individual Answers per Attempt
CREATE TABLE IF NOT EXISTS aptitude_attempt_answers (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES aptitude_attempts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES aptitude_questions(id) ON DELETE CASCADE,
    selected_option VARCHAR(1),
    is_correct BOOLEAN,
    time_spent_seconds INT DEFAULT 0,
    answered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attempt_question UNIQUE (attempt_id, question_id)
);

CREATE INDEX IF NOT EXISTS idx_aptitude_attempt_answers_attempt ON aptitude_attempt_answers(attempt_id);
CREATE INDEX IF NOT EXISTS idx_aptitude_attempt_answers_user ON aptitude_attempt_answers(user_id);

-- RLS for aptitude_attempt_answers
ALTER TABLE aptitude_attempt_answers ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS aptitude_attempt_answers_tenant_policy ON aptitude_attempt_answers;
CREATE POLICY aptitude_attempt_answers_tenant_policy ON aptitude_attempt_answers
    FOR ALL
    USING (user_id::text = current_setting('app.current_tenant', true) OR current_setting('app.current_tenant', true) IS NULL OR current_setting('app.current_tenant', true) = '')
    WITH CHECK (user_id::text = current_setting('app.current_tenant', true) OR current_setting('app.current_tenant', true) IS NULL OR current_setting('app.current_tenant', true) = '');
