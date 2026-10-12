   # GenZ AI — Master Prompt & Engineering Specifications

## 1. Project Overview & Vision
GenZ AI is an intelligent student workspace and local AI platform integrating:
- **Scratch-Built LLM (MiniGPT)**: Causal transformer trained locally on RTX 3050 laptop GPU with desktop & web studio.
- **RAG & Document Intelligence**: Courseware & notes processing with pgvector embeddings, citation extraction, and tenant isolation.
- **Study Planner**: Adaptive spaced-repetition exam and syllabus scheduler.
- **Aptitude Engine**: Quantitative, logical, verbal, and technical aptitude testing with instant scoring and explainability.
- **Project Builder**: System architect, roadmap planner, milestone tracker, and viva defense evaluator for academic projects.

---

## 2. ARCHITECTURE RULES

### Core Guardrails & Invariants
1. **Strict `user_id` Scoping & Isolation**:
   - Every domain entity and table (`documents`, `document_chunks`, `activity_log`, `user_settings`, aptitude attempts, etc.) must carry and filter on `user_id`.
   - Every `pgvector` retrieval query must apply metadata filtering with `userId` to prevent any cross-tenant data leakage.

2. **Server-Side Scoring and Timing**:
   - Test timing, question evaluation, submission scoring, streaks, and review schedules are computed and validated exclusively on the server.
   - The client never dictates or trusts its own scores or timestamps.

3. **Offline LLM Provider with Offline Fallback**:
   - System must function out-of-the-box without external cloud API keys.
   - LLM abstraction falls back to local provider (Ollama / MiniGPT / deterministic mock provider) when no external API key is configured.

4. **Flyway Migrations Only**:
   - All database schema changes must be versioned through Flyway migration scripts (`V*__*.sql`).
   - JPA/Hibernate DDL must not run destructive changes in production.

5. **Activity Log & Timezone Awareness**:
   - Every meaningful user event (login, doc upload, question solved, test completed, study plan generated) logs to `activity_log`.
   - Streaks and daily summaries are calculated relative to the user's configured timezone (default: `Asia/Kolkata`).

### REFERENCE SOURCES (patterns only; do not copy wholesale; check licenses)
- **Dashboard / Sidebar UI**: TailAdmin free React (MIT), shadcn-admin
- **Calendar view**: FullCalendar (MIT) with its React connector
- **Flashcards & Spaced Repetition**: `ts-fsrs` (MIT, FSRS v6 scheduler)
- **Scaffold generation**: Spring Initializr (Apache-2.0); diagrams: Mermaid
- **Settings / Profile UI**: TailAdmin free React (MIT)
- **Streaming**: `@microsoft/fetch-event-source` in the browser; `SseEmitter` (Spring MVC) with periodic heartbeat events; FastAPI SSE docs for `ai-service`
- **ai-service Dockerfile**: FastAPI Docker guide (exec-form `CMD`, requirements layer cached first). Keep MiniGPT Studio separate from aptitude, study, and project features.
- **Chat + Document UX**: AnythingLLM; API design for ingestion/citations: PrivateGPT
- **UX Exploration**: Open WebUI (UX ideas only; custom license)
- **Aptitude Seed Data**: AQuA-RAT (Quantitative only). Validate and clean every row before importing to discard noisy rows, malformed options, and corrupted encodings.
- **Diagrams**: Mermaid (for ER schemas, system architectures, and workflows)
- **Backend**: Spring AI docs for PGvector metadata filtering (`user_id`), structured output validation, RAG, and ETL pipeline
- **Least-privilege DB role & RLS**: PostgreSQL row security docs; app must connect as a non-superuser, non-BYPASSRLS role (table owners must enforce `FORCE ROW LEVEL SECURITY`)

---

## 3. Technology Stack & Guidelines
- **Frontend**: React 18+ / Vite / TypeScript / Tailwind CSS / Lucide Icons / React Router v6 / TanStack React Query.
- **Core Backend**: Spring Boot 3.x / Java 21 / Spring AI / PostgreSQL (`pgvector`) / Redis / MinIO S3.
- **AI Microservice**: FastAPI / Python 3.11 / PyTorch CUDA / Uvicorn SSE streaming.
- **Edge LLM**: Custom 17M MiniGPT Causal Transformer with BPE Tokenizer.
