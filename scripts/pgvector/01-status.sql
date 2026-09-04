-- pgvector Status & Schema Inspection
-- Run in IDE or copy individual queries into psql.
-- Default schema: rag_basic (set \set schema 'rag_basic' in psql)

-- Connection test
SELECT 1 AS connected;

-- List schemas with table counts
SELECT s.schema_name,
       (SELECT count(*) FROM information_schema.tables t
        WHERE t.table_schema = s.schema_name) AS table_count
FROM information_schema.schemata s
WHERE s.schema_name NOT IN ('pg_catalog', 'information_schema')
ORDER BY s.schema_name;

-- Tables in current schema
SELECT table_name, table_type
FROM information_schema.tables
WHERE table_schema = :'schema'
ORDER BY table_name;

-- Chunk table structure
\d rag_basic.chunks

-- Column details
SELECT column_name, data_type, is_nullable,
       CASE WHEN column_default IS NOT NULL THEN 'YES' ELSE 'NO' END AS has_default
FROM information_schema.columns
WHERE table_schema = :'schema' AND table_name = 'chunks'
ORDER BY ordinal_position;

-- Indexes on chunks table
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = :'schema' AND tablename = 'chunks'
ORDER BY indexname;

-- Vector dimensions
SELECT array_length(embedding::float[], 1) AS dimensions,
       count(*) AS total_chunks
FROM rag_basic.chunks;

-- pgvector extension status
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';
