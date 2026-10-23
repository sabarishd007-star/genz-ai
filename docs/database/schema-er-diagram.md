# GenZ AI — Database Schema & Entity-Relationship Specifications

This document defines the relational and vector database schemas derived directly from Flyway migrations (`V1` through `V7`).

---

## 1. Complete Entity-Relationship Diagram

```mermaid
erDiagram
    USERS ||--o{ USER_SETTINGS : configures
    USERS ||--o{ ACTIVITY_LOG : records
    USERS ||--o{ DOCUMENTS : owns
    DOCUMENTS ||--o{ DOCUMENT_CHUNKS : splits_into
    DOCUMENT_CHUNKS ||--o{ VECTOR_STORE : embeds_as

    USERS ||--o{ APTITUDE_ATTEMPTS : attempts
    APTITUDE_TRACKS ||--o{ APTITUDE_QUESTIONS : contains
    APTITUDE_ATTEMPTS ||--o{ APTITUDE_ANSWERS : evaluates
    APTITUDE_QUESTIONS ||--o{ APTITUDE_ANSWERS : answers

    USERS ||--o{ STUDY_PLANS : schedules
    STUDY_PLANS ||--o{ STUDY_TASKS : contains
    USERS ||--o{ POMODORO_SESSIONS : executes

    USERS ||--o{ PROJECTS : architects
    PROJECTS ||--o{ PROJECT_BLUEPRINTS : versions
    PROJECTS ||--o{ PROJECT_MILESTONES : tracks
    PROJECT_BLUEPRINTS ||--o{ VIVA_QUESTIONS : formulates
    USERS ||--o{ VIVA_ANSWERS : defends
    VIVA_QUESTIONS ||--o{ VIVA_ANSWERS : reviews

    USERS {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar role
        timestamptz created_at
    }

    USER_SETTINGS {
        uuid id PK
        uuid user_id FK
        varchar timezone
        jsonb preferences
    }

    ACTIVITY_LOG {
        uuid id PK
        uuid user_id FK
        varchar activity_type
        text description
        jsonb metadata
        timestamptz created_at
    }

    DOCUMENTS {
        uuid id PK
        uuid user_id FK
        varchar file_name
        varchar subject
        bigint file_size
        timestamptz created_at
    }

    DOCUMENT_CHUNKS {
        uuid id PK
        uuid document_id FK
        uuid user_id FK
        int chunk_index
        text content
        int token_count
    }

    VECTOR_STORE {
        uuid id PK
        uuid tenant_id
        text content
        jsonb metadata
        vector embedding
    }

    APTITUDE_TRACKS {
        uuid id PK
        varchar track_code UK
        varchar title
        varchar category
        int default_duration_minutes
    }

    APTITUDE_QUESTIONS {
        uuid id PK
        uuid track_id FK
        text question_text
        jsonb options
        varchar correct_option
        text explanation
        varchar difficulty
    }

    APTITUDE_ATTEMPTS {
        uuid id PK
        uuid user_id FK
        uuid track_id FK
        varchar mode
        int score
        double score_pct
        varchar status
        timestamptz started_at
        timestamptz submitted_at
    }

    APTITUDE_ANSWERS {
        uuid id PK
        uuid attempt_id FK
        uuid question_id FK
        varchar selected_option
        boolean is_correct
        int time_spent_seconds
    }

    STUDY_PLANS {
        uuid id PK
        uuid user_id FK
        varchar title
        varchar status
        date exam_date
        double hours_per_day
        varchar timezone
    }

    STUDY_TASKS {
        uuid id PK
        uuid plan_id FK
        uuid user_id FK
        date task_date
        varchar topic
        int planned_minutes
        varchar status
    }

    POMODORO_SESSIONS {
        uuid id PK
        uuid user_id FK
        uuid task_id FK
        int planned_seconds
        int elapsed_seconds
        boolean counted
        timestamptz started_at
        timestamptz completed_at
    }

    PROJECTS {
        uuid id PK
        uuid user_id FK
        varchar title
        varchar domain
        text idea
        jsonb constraints
        varchar status
        timestamptz created_at
    }

    PROJECT_BLUEPRINTS {
        uuid id PK
        uuid project_id FK
        uuid user_id FK
        int version
        jsonb content
        varchar source
        timestamptz created_at
    }

    PROJECT_MILESTONES {
        uuid id PK
        uuid project_id FK
        uuid user_id FK
        int blueprint_version
        varchar phase
        varchar title
        text description
        varchar status
        int position
    }

    VIVA_QUESTIONS {
        uuid id PK
        uuid blueprint_id FK
        uuid user_id FK
        text question
        text model_answer
        varchar topic
    }

    VIVA_ANSWERS {
        uuid id PK
        uuid user_id FK
        uuid question_id FK
        text answer_text
        jsonb scores
        text feedback
        varchar judged_by
        timestamptz created_at
    }
```

---

## 2. Row-Level Security (RLS) Policy Specifications

All tenant-scoped tables are secured with `ENABLE ROW LEVEL SECURITY` and `FORCE ROW LEVEL SECURITY`.

### Isolation Pattern
```sql
CREATE POLICY <table_name>_tenant_policy ON <table_name>
    FOR ALL
    USING (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE <table_name> FORCE ROW LEVEL SECURITY;
```

### Least-Privilege Role: `genz_app`
* Role creation flags: `NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE`.
* Granted DML access (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) on all domain tables.
* Explicitly denied access to Flyway internal metadata:
  ```sql
  REVOKE ALL ON TABLE flyway_schema_history FROM genz_app;
  ```
