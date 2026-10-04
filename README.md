# GenZ AI

A modern, production-grade AI platform architecture featuring high-performance backend services, vector database storage, distributed caching, and microservice-driven AI workloads.

---

## Repository Structure

```
genz-ai/
├── GENZ_AI_MASTER_SPEC.md       # Master technical specification
├── GENZ_AI_MASTER_PROMPT.md     # Master prompt instructions
├── README.md                    # Project documentation & onboarding
├── .env.example                 # Template for environment variables
├── .env                         # Local environment configuration
├── .gitignore                   # Version control ignore rules
├── docker-compose.yml           # Core infrastructure services (Postgres, Redis, MinIO)
├── docs/                        # Project documentation
│   ├── architecture/            # System & component architecture diagrams
│   ├── database/                # Schema designs & migrations
│   ├── api/                     # OpenAPI/Swagger specs & contracts
│   └── evaluation/              # AI benchmarking & evaluation metrics
├── infrastructure/              # Infrastructure scripts & configs
│   └── postgres/
│       └── init.sql             # Postgres extension initialization (pgvector, uuid-ossp)
├── frontend/                    # Web frontend client
├── backend/                     # Core backend API service (Spring Boot)
└── ai-service/                  # AI / RAG microservice (FastAPI / Python)
```

---

## Quickstart (Phase 1: Foundation)

### 1. Prerequisites
- [Docker & Docker Compose](https://www.docker.com/) (Docker v20+)
- [Git](https://git-scm.com/)

### 2. Environment Setup
Verify or copy the environment configuration:
```bash
cp .env.example .env
```

### 3. Start Core Infrastructure Services
Launch PostgreSQL (with `pgvector`), Redis, and MinIO in detached mode:
```bash
docker compose up -d
```

### 4. Verify Database & Extensions
Confirm that `pgvector` and `uuid-ossp` extensions have been initialized properly:
```bash
docker exec -it genz-postgres psql -U genz_user -d genz_ai -c "\dx"
```

Expected output:
```text
                                  List of installed extensions
   Name    | Version |   Schema   |                            Description                            
-----------+---------+------------+-------------------------------------------------------------------
 plpgsql   | 1.0     | pg_catalog | PL/pgSQL procedural language
 uuid-ossp | 1.4     | public     | generate universally unique identifiers (UUIDs)
 vector    | 0.8.0   | public     | vector data type and ivfflat and hnsw access methods
```

### 5. Services Endpoints

| Service | Port | Description |
|---|---|---|
| PostgreSQL (pgvector) | `5432` | Relational & vector embeddings storage |
| Redis | `6379` | In-memory caching & session management |
| MinIO S3 API | `9000` | Object storage |
| MinIO Web Console | `9001` | Object storage admin console (User: `genz_minio`) |
