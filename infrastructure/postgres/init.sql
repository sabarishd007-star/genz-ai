-- Enable pgvector extension for RAG embeddings
CREATE EXTENSION IF NOT EXISTS vector;

-- Ensure UUID generation is available
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Schema creation confirmation
SELECT 'Database initialized with pgvector and uuid extensions' AS status;
