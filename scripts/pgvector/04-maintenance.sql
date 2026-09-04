-- pgvector Maintenance
-- Run in IDE or copy individual queries into psql.
-- Default schema: rag_basic (set \set schema 'rag_basic' in psql)

-- Delete all chunks for a specific document
DELETE FROM rag_basic.chunks
WHERE metadata->>'documentId' = '';  -- paste document id here

-- Delete all chunks (clear entire schema)
DELETE FROM rag_basic.chunks;

-- Drop entire schema and recreate
DROP SCHEMA IF EXISTS rag_basic CASCADE;
CREATE SCHEMA rag_basic;

-- Recreate the chunks table (Spring AI default schema)
CREATE TABLE rag_basic.chunks (
    id VARCHAR(256) PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL,
    embedding VECTOR(1536) NOT NULL  -- adjust dimension to match your model
);

CREATE INDEX IF NOT EXISTS chunks_embedding_idx
    ON rag_basic.chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX IF NOT EXISTS chunks_metadata_idx
    ON rag_basic.chunks USING gin (metadata);

-- Check database size
SELECT pg_size_pretty(pg_database_size('rag')) AS database_size;

-- Check schema size
SELECT pg_size_pretty(
    pg_total_relation_size('rag_basic.chunks')
) AS chunks_table_size;

-- Vacuum and analyze
VACUUM ANALYZE rag_basic.chunks;

-- Check row count estimate vs actual
SELECT reltuples::bigint AS estimated_rows
FROM pg_class
WHERE relname = 'chunks' AND relnamespace = (
    SELECT oid FROM pg_namespace WHERE nspname = 'rag_basic'
);
