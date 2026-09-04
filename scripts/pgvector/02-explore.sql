-- pgvector Content Exploration
-- Run in IDE or copy individual queries into psql.
-- Default schema: rag_basic (set \set schema 'rag_basic' in psql)

-- Total chunks
SELECT count(*) AS total_chunks
FROM rag_basic.chunks;

-- Chunks per document
SELECT metadata->>'documentId' AS document_id,
       count(*) AS chunk_count,
       min(id) AS first_chunk_id,
       max(id) AS last_chunk_id
FROM rag_basic.chunks
WHERE metadata->>'documentId' IS NOT NULL
GROUP BY metadata->>'documentId'
ORDER BY chunk_count DESC;

-- List all chunks with content preview
SELECT id,
       left(content, 120) AS content_preview,
       metadata->>'documentId' AS doc_id,
       metadata->>'chunkIndex' AS chunk_idx
FROM rag_basic.chunks
ORDER BY metadata->>'documentId', (metadata->>'chunkIndex')::int
LIMIT 20;

-- Full content of a specific chunk
SELECT id, content, metadata
FROM rag_basic.chunks
WHERE id = '';  -- paste chunk id here

-- Metadata fields breakdown
SELECT DISTINCT jsonb_object_keys(metadata) AS metadata_key
FROM rag_basic.chunks;

-- Chunks with most content
SELECT id,
       length(content) AS content_length,
       metadata->>'documentId' AS doc_id,
       metadata->>'chunkIndex' AS chunk_idx
FROM rag_basic.chunks
ORDER BY length(content) DESC
LIMIT 10;
