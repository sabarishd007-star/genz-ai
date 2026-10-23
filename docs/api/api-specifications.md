# GenZ AI — API Contract & Endpoint Specifications

This document catalogs all endpoints exposed by the Core Spring Boot 3.3 Gateway (`:8080`) and the Edge AI Microservice (`:8000`).

---

## 1. Authentication Endpoints (`/api/auth`)

| Method | Endpoint | Request Body | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | `{ email, password, fullName }` | Creates user account with encrypted BCrypt password and returns JWT tokens. |
| `POST` | `/api/auth/login` | `{ email, password }` | Authenticates credentials and returns access & refresh tokens. |
| `POST` | `/api/auth/refresh` | `{ refreshToken }` | Validates refresh token and issues a refreshed access token. |

---

## 2. Dashboard Endpoints (`/api/dashboard`)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/dashboard/summary` | Returns aggregated metrics: `documentsCount`, `studyPlansCount`, `aptitudeScore`, `activeStreakDays`, `userTimezone`, `activeProjectsCount`, and `milestoneCompletionRate`. |

---

## 3. Documents & RAG Endpoints (`/api/documents`, `/api/rag`)

| Method | Endpoint | Request Body | Description |
|---|---|---|---|
| `GET` | `/api/documents` | Query: `subject`, `query` | Lists uploaded courseware documents filtered by authenticated tenant. |
| `POST` | `/api/documents/upload` | `multipart/form-data` | Ingests file, extracts chunks, generates embeddings, and saves to `pgvector`. |
| `DELETE` | `/api/documents/{id}` | None | Deletes document, associated chunks, and vector store embeddings. |
| `GET` | `/api/documents/{id}/chunks`| None | Inspects extracted chunks, token counts, and chunk indices. |
| `POST` | `/api/rag/chat` | `{ query, documentIds[] }` | Executes tenant-filtered cosine similarity search and streams grounded answer with citations. |

---

## 4. Aptitude Engine Endpoints (`/api/aptitude`)

| Method | Endpoint | Request Body | Description |
|---|---|---|---|
| `GET` | `/api/aptitude/tracks` | None | Returns track metadata (Quantitative, Logical, Verbal, CS Core). |
| `POST` | `/api/aptitude/attempts` | `{ trackId, mode, questionCount, timeLimitMinutes }` | Initiates practice or timed exam attempt. |
| `POST` | `/api/aptitude/attempts/{id}/answer` | `{ questionId, selectedOption, timeSpentSeconds }` | Records individual answer; returns instant feedback if in Practice mode. |
| `POST` | `/api/aptitude/attempts/{id}/submit` | None | Concludes test and returns score percentage and question-by-question review. |
| `GET` | `/api/aptitude/history` | None | Lists past test attempts and timestamps. |
| `GET` | `/api/aptitude/analytics` | None | Returns topic-level mastery rates and weak areas. |
| `POST` | `/api/aptitude/explain` | `{ questionId, userQuery }` | Provides AI reasoning and step-by-step mathematical explanation. |

---

## 5. Study Planner Endpoints (`/api/study`)

| Method | Endpoint | Request Body | Description |
|---|---|---|---|
| `GET` | `/api/study/plans` | None | Lists active study plans and daily calendar tasks. |
| `POST` | `/api/study/plans` | `{ title, examDate, hoursPerDay, timezone, topics[] }` | Generates spaced study schedule. |
| `PATCH` | `/api/study/tasks/{id}/status` | `{ status: "TODO"|"DONE" }` | Updates task completion status. |
| `POST` | `/api/study/pomodoro/start` | `{ taskId, plannedMinutes }` | Starts a 25-minute focus session. |
| `POST` | `/api/study/pomodoro/complete` | `{ sessionId, elapsedSeconds }` | Completes session; logs idempotent `activity_log` row if ≥90% elapsed. |

---

## 6. Project Builder & Viva Endpoints (`/api/projects`)

| Method | Endpoint | Request Body | Description |
|---|---|---|---|
| `POST` | `/api/projects` | `{ title, domain, idea, constraints, contextDocumentId }` | Validates schema (Draft 2020-12) and generates blueprint, milestones, and viva bank. |
| `GET` | `/api/projects` | None | Lists all projects owned by the authenticated tenant. |
| `GET` | `/api/projects/{id}` | None | Returns full project blueprint and milestones (404 for other tenants). |
| `POST` | `/api/projects/{id}/regenerate` | Query: `section` | Regenerates a specific section (e.g. `erDiagram`, `architectureDiagram`). |
| `PATCH` | `/api/projects/{id}/milestones/{mid}` | `{ status: "TODO"|"DOING"|"DONE" }` | Updates milestone; logs idempotent `PROJECT_MILESTONE_COMPLETED` on DONE. |
| `GET` | `/api/projects/{id}/viva` | None | Returns 10 viva questions with student's previous evaluations. |
| `POST` | `/api/projects/{id}/viva/{qid}/answer` | `{ answerText }` | Server-side scores answer (0–10) on correctness, completeness, and clarity. |
| `GET` | `/api/projects/{id}/export/readme` | None | Streams server-rendered Markdown README. |
| `GET` | `/api/projects/{id}/export/jdl` | None | Exports JHipster JDL entity specifications. |

---

## 7. Edge AI Microservice Endpoints (`:8000`)

| Method | Endpoint | Request Body | Description |
|---|---|---|---|
| `POST` | `/v1/chat/completions` | OpenAI format | Standard client endpoint for Spring AI and external tooling. |
| `POST` | `/api/minigpt/generate` | `{ prompt, max_tokens, temperature, top_k }` | Server-Sent Events (SSE) autoregressive token streaming from MiniGPT. |
| `GET` | `/api/minigpt/status` | None | Real-time CUDA device, VRAM allocation, and parameter telemetry. |
