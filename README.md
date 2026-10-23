# GenZ AI — Operational AI Infrastructure & Student Workspace

A modern, production-grade local AI platform and student workstation featuring a custom 17M Causal Transformer, document-grounded RAG with PostgreSQL `pgvector`, an adaptive FSRS v6 study planner, an aptitude testing engine, and a project architecture & viva defense workbench.

---

## 🏛️ The 6 Platform Pillars

1. **MiniGPT Causal Transformer**: 17M parameter causal decoder trained locally on RTX 3050 laptop GPU with custom Byte-Pair Encoding (BPE) tokenizer and real-time inference telemetry.
2. **FastAPI AI Microservice**: High-performance asynchronous Python service exposing Server-Sent Events (SSE) streaming (`/api/minigpt/generate`) and OpenAI-compatible `/v1/chat/completions`.
3. **Documents & RAG Intelligence**: PDF notes ingestion, 768-dim `pgvector` HNSW cosine similarity search, strict tenant metadata isolation, and citation-grounded chat.
4. **Study Planner & Memory Engine**: FullCalendar agenda scheduler with `ts-fsrs` (FSRS v6) spaced repetition flashcards and Pomodoro focus tracking.
5. **Aptitude & Assessment Engine**: Quantitative, logical, verbal, and technical examination tracks with Practice vs. Timed Exam modes and AI explainability.
6. **Project Builder & Viva Defense**: Spring Initializr scaffolding generator, Draft 2020-12 schema validation, React Flow roadmap graph visualizer, and server-side 0–10 viva defense scoring.

---

## 📂 Repository Structure

```
genz-ai/
├── GENZ_AI_MASTER_PROMPT.md     # Master prompt instructions & invariants
├── README.md                    # Operational guide & run steps
├── .env.example                 # Environment configuration template
├── docker-compose.yml           # Core infrastructure services (Postgres, Redis, MinIO)
├── docs/                        # Complete system specifications
│   ├── architecture/            # System context & component diagrams
│   ├── database/                # Full ER diagram (V1-V7) & RLS security rules
│   └── api/                     # Complete API endpoint tables
├── infrastructure/              # Database extensions & least-privilege init scripts
│   └── postgres/init.sql        # pgvector, uuid-ossp, genz_app (NOSUPERUSER, NOBYPASSRLS)
├── frontend/                    # React 18 + Vite + Tailwind + React Flow + FullCalendar
├── backend/                     # Spring Boot 3.3 Gateway & Core Domain API (Java 17/21)
├── ai-service/                  # FastAPI microservice serving MiniGPT with SSE
└── minigpt/                     # PyTorch 17M Transformer from scratch & training scripts
```

---

## 🚀 Step-by-Step Run Instructions

### 1. Start Infrastructure Services (Docker)
Ensure Docker Desktop is running, then launch PostgreSQL (with `pgvector`), Redis, and MinIO:
```bash
docker compose up -d
```

Verify that the least-privilege non-superuser role `genz_app` and extensions are active:
```bash
docker exec -it genz-postgres psql -U genz_user -d genz_ai -c "SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'genz_app';"
```
Expected output:
```text
 rolname  | rolsuper | rolbypassrls 
----------+----------+--------------
 genz_app | f        | f
```

### 2. Run the Core Backend API (Spring Boot)
The backend applies Flyway migrations (`V1` to `V7`), sets up RLS policies, and listens on port `8080`:
```bash
cd backend
./mvnw clean spring-boot:run
```
To run the automated test suite (including cross-user isolation and schema validation):
```bash
./mvnw test
```

### 3. Run the AI Microservice (FastAPI / MiniGPT)
In a new terminal window:
```bash
cd ai-service
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

### 4. Run the Web Client (Vite / React)
In another terminal window:
```bash
cd frontend
npm install
npm run dev
```
Open your browser at **[http://localhost:5173](http://localhost:5173)**.

---

## 🔒 Security Invariants Enforced

* **Strict `user_id` Tenant Scoping**: All queries run under PostgreSQL `FORCE ROW LEVEL SECURITY` matching `app.current_tenant`.
* **Least-Privilege Connection**: Application connects strictly as `genz_app` (`NOSUPERUSER NOBYPASSRLS`).
* **Server-Side Scoring**: Test timing, question scoring, and viva defense evaluations are strictly calculated on the server; client answers are treated strictly as data.
* **Deterministic Fallback**: When offline or if external LLM APIs are unavailable, the system smoothly falls back to schema-valid domain templates and keyword heuristics labeled `"indicative only"`.
