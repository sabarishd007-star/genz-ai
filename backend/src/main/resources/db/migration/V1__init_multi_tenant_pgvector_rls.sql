-- 1. Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Multi-tenant vector_store table
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT current_setting('app.current_tenant', true),
    content TEXT,
    metadata JSONB,
    embedding VECTOR(768) NOT NULL
);

-- 3. HNSW Vector Index
CREATE INDEX IF NOT EXISTS idx_vector_store_hnsw
ON vector_store 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 4. Fast lookup indexes
CREATE INDEX IF NOT EXISTS idx_vector_store_tenant_id ON vector_store (tenant_id);
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_gin ON vector_store USING gin (metadata);
CREATE INDEX IF NOT EXISTS idx_vector_store_tenant_doc ON vector_store (tenant_id, (metadata->>'documentId'));
CREATE INDEX IF NOT EXISTS idx_vector_store_tenant_user ON vector_store (tenant_id, (metadata->>'userId'));

-- 5. Enable Row-Level Security (RLS)
ALTER TABLE vector_store ENABLE ROW LEVEL SECURITY;

-- 6. RLS Policy
DROP POLICY IF EXISTS tenant_isolation_policy ON vector_store;
CREATE POLICY tenant_isolation_policy ON vector_store
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
