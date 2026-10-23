package com.genzai.project.template;

import java.util.*;

public class DomainBlueprintTemplates {

    public static Map<String, Object> getTemplateForDomain(String domain, String title, String idea) {
        String normalized = domain != null ? domain.trim().toLowerCase() : "";
        if (normalized.contains("ai") || normalized.contains("ml") || normalized.contains("machine") || normalized.contains("data")) {
            return getAiMlTemplate(title, idea);
        } else if (normalized.contains("mobile") || normalized.contains("cloud") || normalized.contains("ios") || normalized.contains("android")) {
            return getMobileCloudTemplate(title, idea);
        } else {
            return getFullStackWebTemplate(title, idea);
        }
    }

    public static Map<String, Object> getFullStackWebTemplate(String title, String idea) {
        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("problemStatement", "Modern web users encounter fragmentation and latency across monolithic applications; " + title + " provides an integrated reactive web solution solving: " + idea);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("mvp", List.of(
                "Secure multi-tenant authentication with JWT access and refresh token rotation",
                "Real-time event streaming and live dashboard status reporting",
                "CRUD entity workflows with pagination and strict input validation",
                "Idempotent activity logging and metric telemetry"
        ));
        features.put("later", List.of(
                "Automated PDF report exports and scheduled batch summaries",
                "OAuth2 third-party social single-sign-on integration",
                "Role-based granular permission matrices with custom access control"
        ));
        blueprint.put("features", features);

        List<Map<String, String>> techStack = List.of(
                Map.of("layer", "Frontend Client", "choice", "React 18 + Vite + TypeScript", "reason", "Type-safety, ultra-fast Hot Module Replacement, and modern reactive component trees."),
                Map.of("layer", "UI Styling", "choice", "Tailwind CSS", "reason", "Atomic utility classes ensuring rapid layout iteration and cohesive dark-mode theming."),
                Map.of("layer", "Application Gateway", "choice", "Spring Boot 3.3 (Java 21)", "reason", "Enterprise-grade REST routing, Virtual Threads throughput, and mature security filters."),
                Map.of("layer", "Persistence Layer", "choice", "PostgreSQL 16", "reason", "ACID transactional guarantees, native JSONB support, and Row-Level Security isolation."),
                Map.of("layer", "Database Evolution", "choice", "Flyway Migration", "reason", "Deterministic versioned schema changes avoiding runtime Hibernate DDL corruption."),
                Map.of("layer", "Distributed Caching", "choice", "Redis", "reason", "Sub-millisecond session caching and rate-limiting counters.")
        );
        blueprint.put("techStack", techStack);

        String erDiagram = """
        erDiagram
            USERS ||--o{ PROJECTS : owns
            USERS ||--o{ ACTIVITY_LOGS : records
            PROJECTS ||--o{ MILESTONES : contains
            PROJECTS ||--o{ BLUEPRINTS : tracks
            BLUEPRINTS ||--o{ VIVA_QUESTIONS : provides
            USERS ||--o{ VIVA_ANSWERS : submits
        """;
        blueprint.put("erDiagram", erDiagram.trim());

        List<Map<String, Object>> entities = List.of(
                Map.of("name", "User", "attributes", List.of("id UUID", "email VARCHAR", "fullName VARCHAR", "role VARCHAR", "createdAt TIMESTAMPTZ")),
                Map.of("name", "Project", "attributes", List.of("id UUID", "userId UUID", "title VARCHAR", "domain VARCHAR", "status VARCHAR")),
                Map.of("name", "Milestone", "attributes", List.of("id UUID", "projectId UUID", "phase VARCHAR", "title VARCHAR", "status VARCHAR")),
                Map.of("name", "ActivityLog", "attributes", List.of("id UUID", "userId UUID", "action VARCHAR", "details JSONB", "timestamp TIMESTAMPTZ"))
        );
        blueprint.put("entities", entities);

        List<Map<String, String>> apiEndpoints = List.of(
                Map.of("method", "POST", "path", "/api/auth/login", "purpose", "Authenticate user credentials and issue JWT tokens"),
                Map.of("method", "GET", "path", "/api/projects", "purpose", "List all scoped projects for the current authenticated tenant"),
                Map.of("method", "POST", "path", "/api/projects", "purpose", "Generate a new project specification and blueprint"),
                Map.of("method", "GET", "path", "/api/projects/{id}", "purpose", "Fetch full project blueprint, milestone tree, and viva status"),
                Map.of("method", "PATCH", "path", "/api/projects/{id}/milestones/{mid}", "purpose", "Update milestone progress status (TODO/DOING/DONE)")
        );
        blueprint.put("apiEndpoints", apiEndpoints);

        String archDiagram = """
        graph TD
            Client[React Vite Client :5173] -->|HTTPS REST / JWT| Gateway[Spring Boot 3.3 Gateway :8080]
            Gateway -->|Spring Security & RLS| Postgres[(PostgreSQL 16 :5432)]
            Gateway -->|Session Cache| Redis[(Redis Cache :6379)]
            Gateway -->|Event Stream| SSE[SSE Streaming Handler]
        """;
        blueprint.put("architectureDiagram", archDiagram.trim());

        Map<String, Object> roadmap = Map.of(
                "phases", List.of(
                        Map.of("name", "Phase 1: Foundation & Security", "milestones", List.of(
                                Map.of("title", "Database Provisioning & Flyway Migrations", "description", "Initialize PostgreSQL schemas, connection pooling, and baseline user tables."),
                                Map.of("title", "Stateless JWT Authentication", "description", "Configure Spring Security filters, password encoders, and token issuers.")
                        )),
                        Map.of("name", "Phase 2: Core Domain & Services", "milestones", List.of(
                                Map.of("title", "Project Entity CRUD & Scoping", "description", "Implement repository layers, service business logic, and tenant validation."),
                                Map.of("title", "Real-Time Event Stream Delivery", "description", "Implement Server-Sent Events controller endpoints for live telemetry.")
                        )),
                        Map.of("name", "Phase 3: Optimization & Defense Prep", "milestones", List.of(
                                Map.of("title", "Distributed Redis Rate Limiting", "description", "Deploy bucket token rate limiting protecting endpoints from abuse."),
                                Map.of("title", "Viva Examination & Documentation Export", "description", "Generate architectural README and prepare technical defense artifacts.")
                        ))
                )
        );
        blueprint.put("roadmap", roadmap);

        List<Map<String, String>> fileTree = List.of(
                Map.of("path", "src/main/java/com/genzai/config/SecurityConfig.java", "purpose", "Configures security filter chains, CORS, and stateless JWT verification"),
                Map.of("path", "src/main/java/com/genzai/controller/ProjectController.java", "purpose", "REST controller handling project lifecycle requests"),
                Map.of("path", "src/main/java/com/genzai/service/ProjectService.java", "purpose", "Core business logic enforcing validation and RLS session context"),
                Map.of("path", "src/main/resources/db/migration/V1__init.sql", "purpose", "Flyway schema migration script defining initial tables"),
                Map.of("path", "frontend/src/pages/ProjectBuilderPage.tsx", "purpose", "Frontend user interface managing project workflow and viva defense")
        );
        blueprint.put("fileTree", fileTree);

        List<String> risks = List.of(
                "Cross-tenant data leakage if database sessions fail to reset tenant context between thread pool re-use",
                "Reverse proxy timeout on long-lived SSE connections if periodic heartbeat ping events are missed",
                "Performance degradation on complex join queries if database foreign keys lack proper B-Tree indexes"
        );
        blueprint.put("risks", risks);

        List<Map<String, String>> vivaQuestions = List.of(
                Map.of("question", "How does Row-Level Security (RLS) guarantee isolation in multi-tenant PostgreSQL?", "modelAnswer", "RLS enforces policy checks at the database engine level for every query. When non-superuser roles execute queries, PostgreSQL filters rows where user_id matches app.current_tenant, preventing application-layer query omissions from leaking tenant data.", "topic", "Security & Multi-Tenancy"),
                Map.of("question", "Why choose JWT over server-side HTTP session cookies in distributed architectures?", "modelAnswer", "JWTs are cryptographically signed and stateless, allowing any backend instance to verify claims without distributed session lookup synchronization across nodes.", "topic", "Authentication & Systems"),
                Map.of("question", "What is the purpose of database Flyway migrations versus JPA hibernate.ddl-auto=update?", "modelAnswer", "JPA ddl-auto is non-deterministic and can corrupt production schemas or drop constraints. Flyway migrations provide immutable, version-controlled SQL scripts verified via cryptographic checksums.", "topic", "Database Engineering"),
                Map.of("question", "How does Server-Sent Events (SSE) compare to WebSockets for live status updates?", "modelAnswer", "SSE operates over standard HTTP/1.1 or HTTP/2 with unidirectional server-to-client streaming, native browser reconnection, and no complex firewall traversal issues compared to bidirectional WebSockets.", "topic", "Systems & Networking"),
                Map.of("question", "How do you prevent thread pool starvation in Spring MVC when handling asynchronous streaming?", "modelAnswer", "By utilizing SseEmitter or WebFlux reactive streams executed on a dedicated asynchronous TaskExecutor pool, preventing Tomcat request acceptor threads from blocking.", "topic", "Concurrency & Performance"),
                Map.of("question", "Why is password hashing with BCrypt superior to plain SHA-256?", "modelAnswer", "BCrypt incorporates automatic salting and a configurable work factor (cost parameter) making brute-force dictionary and rainbow table attacks computationally intractable.", "topic", "Security & Cryptography"),
                Map.of("question", "How do database indexes improve search performance and what are their write tradeoffs?", "modelAnswer", "Indexes (such as B-Tree) reduce lookup time from O(N) sequential scans to O(log N) tree traversals, but introduce additional write amplification and storage overhead during INSERT/UPDATE operations.", "topic", "Database Engineering"),
                Map.of("question", "What is the difference between optimistic and pessimistic locking in relational databases?", "modelAnswer", "Optimistic locking verifies a version column before commit without locking rows during read, whereas pessimistic locking acquires database row-level exclusive locks (SELECT FOR UPDATE) blocking other transactions.", "topic", "Database Concurrency"),
                Map.of("question", "How do you handle API idempotency for payment or resource creation requests?", "modelAnswer", "By requiring clients to supply an Idempotency-Key header, storing executed responses in an atomic cache (like Redis), and returning cached results for duplicate keys.", "topic", "API Design"),
                Map.of("question", "What are the key differences between monolithic architectures and microservices?", "modelAnswer", "Monoliths offer single-deployment simplicity and immediate in-memory method calls, while microservices enable independent scalability, fault domain isolation, and polyglot technology stacks at the cost of distributed systems complexity.", "topic", "Software Architecture")
        );
        blueprint.put("vivaQuestions", vivaQuestions);

        return blueprint;
    }

    public static Map<String, Object> getAiMlTemplate(String title, String idea) {
        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("problemStatement", "AI workloads require specialized hardware scheduling, retrieval grounding, and edge latency guarantees; " + title + " addresses this via: " + idea);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("mvp", List.of(
                "Document chunking, embedding generation, and pgvector cosine similarity storage",
                "Retrieval-Augmented Generation (RAG) with exact page and line citations",
                "Local causal transformer inference with KV-cache optimization",
                "Hardware and VRAM telemetry reporting via streaming SSE"
        ));
        features.put("later", List.of(
                "Distributed parameter-efficient fine-tuning (LoRA / QLoRA) pipelines",
                "Automated benchmark evaluation metrics (ROUGE, BLEU, and LLM-as-a-Judge)",
                "Semantic caching layer reducing redundant LLM inference calls"
        ));
        blueprint.put("features", features);

        List<Map<String, String>> techStack = List.of(
                Map.of("layer", "AI Model Core", "choice", "PyTorch 2.x (CUDA / RTX 3050)", "reason", "Direct tensor control, customized causal self-attention, and BPE tokenizer execution."),
                Map.of("layer", "Inference Microservice", "choice", "FastAPI + Uvicorn", "reason", "Asynchronous Python event loop with native Server-Sent Events (SSE) streaming."),
                Map.of("layer", "Vector Persistence", "choice", "PostgreSQL 16 + pgvector", "reason", "HNSW cosine distance vector indexing alongside relational metadata in a single ACID store."),
                Map.of("layer", "Orchestration Gateway", "choice", "Spring Boot 3.3 + Spring AI", "reason", "Enterprise client routing, metadata tenant filtering, and structured JSON parsing."),
                Map.of("layer", "Frontend Studio", "choice", "React 18 + Vite + Tailwind CSS", "reason", "Interactive model playground with sampling parameters and live token streaming.")
        );
        blueprint.put("techStack", techStack);

        String erDiagram = """
        erDiagram
            USERS ||--o{ DOCUMENTS : uploads
            DOCUMENTS ||--o{ DOCUMENT_CHUNKS : segmented_into
            DOCUMENT_CHUNKS ||--o{ VECTOR_STORE : indexed_as
            USERS ||--o{ CHAT_SESSIONS : conducts
            CHAT_SESSIONS ||--o{ CHAT_MESSAGES : contains
            CHAT_MESSAGES ||--o{ MESSAGE_CITATIONS : references
        """;
        blueprint.put("erDiagram", erDiagram.trim());

        List<Map<String, Object>> entities = List.of(
                Map.of("name", "Document", "attributes", List.of("id UUID", "userId UUID", "fileName VARCHAR", "fileSize BIGINT", "createdAt TIMESTAMPTZ")),
                Map.of("name", "DocumentChunk", "attributes", List.of("id UUID", "documentId UUID", "chunkIndex INT", "content TEXT", "tokenCount INT")),
                Map.of("name", "VectorStore", "attributes", List.of("id UUID", "embedding VECTOR(768)", "metadata JSONB", "tenantId UUID")),
                Map.of("name", "ChatMessage", "attributes", List.of("id UUID", "sessionId UUID", "role VARCHAR", "content TEXT", "tokensGenerated INT"))
        );
        blueprint.put("entities", entities);

        List<Map<String, String>> apiEndpoints = List.of(
                Map.of("method", "POST", "path", "/api/rag/documents/upload", "purpose", "Ingest PDF/text files, segment into chunks, and persist embeddings"),
                Map.of("method", "POST", "path", "/api/rag/chat", "purpose", "Query vector store with cosine similarity and stream grounded answer"),
                Map.of("method", "GET", "path", "/api/minigpt/status", "purpose", "Retrieve GPU device, VRAM allocation, and model weights telemetry"),
                Map.of("method", "POST", "path", "/api/minigpt/generate", "purpose", "Stream tokens autoregressively from local causal transformer"),
                Map.of("method", "GET", "path", "/api/projects/{id}/viva", "purpose", "Fetch technical viva defense questions specific to AI architecture")
        );
        blueprint.put("apiEndpoints", apiEndpoints);

        String archDiagram = """
        graph TD
            UI[React Client] -->|Stream Chat| Spring[Spring Boot Gateway :8080]
            Spring -->|Tenant Filtered HNSW| PgVector[(pgvector Store :5432)]
            Spring -->|Inference Context| FastApi[FastAPI AI-Service :8000]
            FastApi -->|CUDA Tensors| MiniGPT[PyTorch MiniGPT 17M LLM]
        """;
        blueprint.put("architectureDiagram", archDiagram.trim());

        Map<String, Object> roadmap = Map.of(
                "phases", List.of(
                        Map.of("name", "Phase 1: Architecture & Vector Pipeline", "milestones", List.of(
                                Map.of("title", "PostgreSQL pgvector Extension Setup", "description", "Provision pgvector, create 768-dimensional vector_store with HNSW cosine index."),
                                Map.of("title", "Document Chunking & Tokenizer", "description", "Implement sliding window recursive chunking and Byte-Pair Encoding tokenization.")
                        )),
                        Map.of("name", "Phase 2: Local Transformer Serving", "milestones", List.of(
                                Map.of("title", "FastAPI SSE Streaming Server", "description", "Build asynchronous streaming endpoint /api/minigpt/generate using Starlette."),
                                Map.of("title", "KV-Cache Autoregressive Decoder", "description", "Implement Key-Value cache tensor preallocation to achieve linear O(N) generation.")
                        )),
                        Map.of("name", "Phase 3: Multi-Tenant Grounding & Defense", "milestones", List.of(
                                Map.of("title", "Multi-Tenant Vector Isolation", "description", "Enforce strict tenant metadata filtering preventing cross-tenant vector leakage."),
                                Map.of("title", "Viva Examination Simulator", "description", "Implement automated scoring and evaluation rubric for defense preparation.")
                        ))
                )
        );
        blueprint.put("roadmap", roadmap);

        List<Map<String, String>> fileTree = List.of(
                Map.of("path", "minigpt/src/minigpt/model.py", "purpose", "PyTorch implementation of 17M Causal Transformer and Multi-Head Attention"),
                Map.of("path", "ai-service/main.py", "purpose", "FastAPI microservice serving MiniGPT with SSE streaming and hardware telemetry"),
                Map.of("path", "backend/src/main/java/com/genzai/rag/service/RagService.java", "purpose", "Orchestrates tenant-filtered vector retrieval and prompt construction"),
                Map.of("path", "infrastructure/postgres/init.sql", "purpose", "Database extension init script provisioning pgvector and least-privilege role"),
                Map.of("path", "frontend/src/pages/MiniGptStudio.tsx", "purpose", "Web playground for real-time model sampling and GPU performance telemetry")
        );
        blueprint.put("fileTree", fileTree);

        List<String> risks = List.of(
                "GPU Out-of-Memory (OOM) errors during autoregressive generation if context exceeds VRAM budget",
                "Hallucination on factual inquiries if vector search fails to retrieve relevant grounding chunks",
                "Cosine similarity degradation if documents are chunked across semantic boundary mid-sentence"
        );
        blueprint.put("risks", risks);

        List<Map<String, String>> vivaQuestions = List.of(
                Map.of("question", "How does scaled dot-product attention compute token relationships?", "modelAnswer", "Scaled dot-product attention computes softmax(Q * K^T / sqrt(d_k)) * V. Scaling by sqrt(d_k) prevents dot products from growing excessively large in high dimensions, which would cause the softmax gradient to vanish.", "topic", "Transformer Architecture"),
                Map.of("question", "Why is a causal mask applied in autoregressive language models?", "modelAnswer", "A causal mask sets attention weights to negative infinity for all future token positions (j > i), ensuring that predictions for token i depend solely on preceding tokens, preserving autoregressive causality.", "topic", "Deep Learning"),
                Map.of("question", "Explain the difference between HNSW and IVFFlat index types in pgvector.", "modelAnswer", "IVFFlat partitions vector space into Voronoi cells via k-means clustering requiring training, whereas HNSW constructs a multi-layer proximity graph offering higher recall and faster queries with slightly higher memory footprint.", "topic", "Vector Databases"),
                Map.of("question", "How does KV-caching eliminate quadratic complexity during text generation?", "modelAnswer", "Instead of recalculating attention Keys and Values for all past tokens at every step, past K and V tensors are cached in GPU memory, reducing generation of token t from O(t^2) to O(t).", "topic", "Model Optimization"),
                Map.of("question", "What is the role of Byte-Pair Encoding (BPE) in tokenization?", "modelAnswer", "BPE iteratively merges the most frequent pairs of bytes or characters into subword tokens, balancing vocabulary size with sequence length while gracefully handling out-of-vocabulary words.", "topic", "Natural Language Processing"),
                Map.of("question", "How do you guarantee that pgvector similarity search does not leak documents across tenants?", "modelAnswer", "By combining PostgreSQL Row-Level Security with explicit SQL filter expressions (userId = :userId) inside the HNSW nearest-neighbor query, ensuring cross-tenant vectors are never evaluated.", "topic", "Security & Multi-Tenancy"),
                Map.of("question", "What is Gradient Accumulation and when should it be used?", "modelAnswer", "Gradient Accumulation computes gradients over multiple mini-batches before executing optimizer.step(), simulating a large batch size within constrained GPU VRAM limits.", "topic", "Deep Learning Training"),
                Map.of("question", "How does RAG compare to model fine-tuning for proprietary organizational data?", "modelAnswer", "RAG enables dynamic real-time knowledge updates, verifiable citations, and immediate access revocation without expensive retraining or catastrophic forgetting risks.", "topic", "RAG & LLM Systems"),
                Map.of("question", "What causes softmax gradient vanishing in unscaled attention?", "modelAnswer", "For large d_k, dot products grow large, pushing the softmax function into regions with near-zero gradients. Scaling by sqrt(d_k) maintains unit variance and stable backpropagation.", "topic", "Mathematics of Deep Learning"),
                Map.of("question", "What metrics are used to evaluate RAG system quality?", "modelAnswer", "RAG evaluation assesses retrieval quality via Recall@K and MRR, and generation quality via faithfulness (groundedness in context) and answer relevance.", "topic", "AI Evaluation")
        );
        blueprint.put("vivaQuestions", vivaQuestions);

        return blueprint;
    }

    public static Map<String, Object> getMobileCloudTemplate(String title, String idea) {
        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("problemStatement", "Distributed mobile clients require offline synchronization, low-latency edge caching, and scalable cloud microservices; " + title + " delivers: " + idea);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("mvp", List.of(
                "Cross-platform client application with local SQLite offline cache",
                "Bi-directional delta synchronization with conflict resolution algorithms",
                "Cloud push notifications and real-time state synchronization",
                "Secure OAuth2 / OIDC token authentication and biometric security"
        ));
        features.put("later", List.of(
                "Geo-distributed edge CDN caching and multi-region failover",
                "End-to-end client-side encryption for sensitive user assets",
                "Background location tracking and proximity event alerts"
        ));
        blueprint.put("features", features);

        List<Map<String, String>> techStack = List.of(
                Map.of("layer", "Mobile Client", "choice", "React Native / Expo + TypeScript", "reason", "Cross-platform iOS and Android native code generation from a single robust codebase."),
                Map.of("layer", "Local Persistence", "choice", "SQLite / WatermelonDB", "reason", "High-performance observable local offline storage with lazy-loaded queries."),
                Map.of("layer", "Cloud API Gateway", "choice", "Spring Boot 3.3 (Cloud Native)", "reason", "Stateless microservice clustering, JWT verification, and resilient rate-limiting."),
                Map.of("layer", "Cloud Storage & Database", "choice", "PostgreSQL 16 + MinIO S3", "reason", "Relational persistence with multi-tenant RLS combined with S3 object storage for media."),
                Map.of("layer", "Message Broker", "choice", "Redis Pub/Sub", "reason", "Ultra-low latency message fanout and synchronization event distribution.")
        );
        blueprint.put("techStack", techStack);

        String erDiagram = """
        erDiagram
            DEVICES ||--o{ SYNC_SESSIONS : registers
            USERS ||--o{ DEVICES : owns
            USERS ||--o{ CLOUD_RESOURCES : creates
            CLOUD_RESOURCES ||--o{ RESOURCE_VERSIONS : tracks
            SYNC_SESSIONS ||--o{ SYNC_MUTATIONS : applies
        """;
        blueprint.put("erDiagram", erDiagram.trim());

        List<Map<String, Object>> entities = List.of(
                Map.of("name", "Device", "attributes", List.of("id UUID", "userId UUID", "deviceToken VARCHAR", "platform VARCHAR", "lastSync TIMESTAMPTZ")),
                Map.of("name", "Resource", "attributes", List.of("id UUID", "userId UUID", "title VARCHAR", "version INT", "isDeleted BOOLEAN")),
                Map.of("name", "SyncMutation", "attributes", List.of("id UUID", "deviceId UUID", "entityName VARCHAR", "payload JSONB", "appliedAt TIMESTAMPTZ")),
                Map.of("name", "PushNotification", "attributes", List.of("id UUID", "deviceId UUID", "title VARCHAR", "status VARCHAR", "sentAt TIMESTAMPTZ"))
        );
        blueprint.put("entities", entities);

        List<Map<String, String>> apiEndpoints = List.of(
                Map.of("method", "POST", "path", "/api/sync/pull", "purpose", "Fetch changes committed to the cloud since client's last synchronization timestamp"),
                Map.of("method", "POST", "path", "/api/sync/push", "purpose", "Submit offline mutations and resolve conflict strategies atomically"),
                Map.of("method", "POST", "path", "/api/devices/register", "purpose", "Register mobile push notification token with current user profile"),
                Map.of("method", "GET", "path", "/api/projects/{id}", "purpose", "Retrieve project blueprint and mobile sync topology"),
                Map.of("method", "GET", "path", "/api/projects/{id}/export/readme", "purpose", "Export comprehensive technical README for the cloud architecture")
        );
        blueprint.put("apiEndpoints", apiEndpoints);

        String archDiagram = """
        graph TD
            Mobile[Mobile Client SQLite] -->|Offline Queue| SyncEngine[Sync Adapter]
            SyncEngine -->|HTTPS Sync API| Gateway[Spring Cloud Gateway :8080]
            Gateway -->|ACID Persistence| Postgres[(PostgreSQL 16 :5432)]
            Gateway -->|Media Blobs| MinIO[(MinIO S3 Storage :9000)]
            Gateway -->|Fanout Events| Redis[(Redis Pub/Sub :6379)]
        """;
        blueprint.put("architectureDiagram", archDiagram.trim());

        Map<String, Object> roadmap = Map.of(
                "phases", List.of(
                        Map.of("name", "Phase 1: Local Offline Foundation", "milestones", List.of(
                                Map.of("title", "SQLite Local Schema Setup", "description", "Configure local database tables with change tracking timestamps."),
                                Map.of("title", "Mutation Queue Architecture", "description", "Build persistent offline queue ensuring mutations survive app termination.")
                        )),
                        Map.of("name", "Phase 2: Cloud Gateway & Delta Sync", "milestones", List.of(
                                Map.of("title", "Sync Pull/Push API Endpoints", "description", "Implement server delta sync using version vectors and conflict resolution."),
                                Map.of("title", "Push Notification Service", "description", "Integrate FCM/APNs push notification triggers on remote resource changes.")
                        )),
                        Map.of("name", "Phase 3: Security & Viva Defense", "milestones", List.of(
                                Map.of("title", "Device Biometric Auth & Token Vault", "description", "Secure tokens in iOS Keychain / Android Keystore with biometric gating."),
                                Map.of("title", "Viva Examination & Architecture Audit", "description", "Prepare technical defense on distributed sync and CAP theorem tradeoffs.")
                        ))
                )
        );
        blueprint.put("roadmap", roadmap);

        List<Map<String, String>> fileTree = List.of(
                Map.of("path", "mobile/src/services/SyncService.ts", "purpose", "Manages local offline queue and server delta reconciliation"),
                Map.of("path", "mobile/src/database/schema.ts", "purpose", "Defines SQLite tables and indexes for local offline storage"),
                Map.of("path", "backend/src/main/java/com/genzai/sync/SyncController.java", "purpose", "Handles push and pull synchronization requests"),
                Map.of("path", "backend/src/main/java/com/genzai/config/SecurityConfig.java", "purpose", "Enforces stateless JWT validation for mobile clients"),
                Map.of("path", "frontend/src/pages/ProjectBuilderPage.tsx", "purpose", "Web console to inspect mobile cloud architecture and viva bank")
        );
        blueprint.put("fileTree", fileTree);

        List<String> risks = List.of(
                "Merge conflicts when multiple offline devices modify the same record simultaneously",
                "High battery consumption on mobile clients if polling frequency is not throttled",
                "Data loss on client crash if local transactions are not committed before UI updates"
        );
        blueprint.put("risks", risks);

        List<Map<String, String>> vivaQuestions = List.of(
                Map.of("question", "How does Last-Write-Wins (LWW) conflict resolution work and what are its risks?", "modelAnswer", "LWW compares timestamps of concurrent mutations and overwrites earlier versions with the latest. The risk is clock drift between devices causing legitimate updates to be inadvertently overwritten.", "topic", "Distributed Systems"),
                Map.of("question", "How do Vector Clocks or Version Vectors guarantee causal consistency?", "modelAnswer", "Vector clocks maintain an array of logical clock counters across all participating nodes, allowing nodes to determine whether two events are causally related or concurrent without synchronized physical clocks.", "topic", "Distributed Systems"),
                Map.of("question", "How does the CAP theorem apply to offline-first mobile applications?", "modelAnswer", "Offline-first clients operate in a partitioned state (P) and prioritize Availability (A) by allowing local writes, choosing AP and reconciling consistency (C) eventually upon network reconnection.", "topic", "System Architecture"),
                Map.of("question", "Why store sensitive mobile tokens in Android Keystore / iOS Keychain rather than AsyncStorage?", "modelAnswer", "AsyncStorage stores unencrypted plaintext in application sandbox directories, vulnerable to rooted devices or backup extraction. Hardware Keystore/Keychain utilizes dedicated secure enclaves with hardware-backed encryption.", "topic", "Mobile Security"),
                Map.of("question", "How does delta synchronization minimize mobile network bandwidth?", "modelAnswer", "Instead of transferring full entity graphs, delta sync sends only modified fields and created/deleted IDs since the client's last known synchronization anchor timestamp.", "topic", "Mobile & Cloud Systems"),
                Map.of("question", "How do background refresh tasks function on iOS without triggering OS termination?", "modelAnswer", "iOS limits background execution through BackgroundTasks framework (BGAppRefreshTask), allowing brief opportunistic executions scheduled by the operating system based on battery and usage patterns.", "topic", "Mobile Architecture"),
                Map.of("question", "What is exponential backoff with jitter in mobile networking?", "modelAnswer", "When API requests fail, clients delay retries by 2^n intervals with randomized jitter added, preventing thousands of mobile devices from overwhelming recovering backend servers in a thundering herd.", "topic", "Reliability Engineering"),
                Map.of("question", "How do you handle schema migrations in local client databases like SQLite?", "modelAnswer", "By checking SQLite PRAGMA user_version on startup, executing sequential migration scripts inside database transactions, and incrementing user_version atomically.", "topic", "Client Database Engineering"),
                Map.of("question", "What is the difference between APNs/FCM Silent Push notifications and Display Push notifications?", "modelAnswer", "Display notifications present UI alerts directly to the user, while silent (content-available) push notifications wake the background application to synchronize data before the user opens it.", "topic", "Cloud Push Infrastructure"),
                Map.of("question", "How do API gateways manage rate limiting across mobile client IP churn?", "modelAnswer", "Since mobile device IPs frequently change across cell towers and Wi-Fi, rate limiting must key on authenticated User ID or Device UUID tokens stored in distributed Redis sliding-window counters rather than IP addresses.", "topic", "Cloud Architecture")
        );
        blueprint.put("vivaQuestions", vivaQuestions);

        return blueprint;
    }
}
