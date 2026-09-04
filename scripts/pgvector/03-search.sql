-- pgvector Similarity Search
-- Run in IDE or copy individual queries into psql.
-- Default schema: rag_basic (set \set schema 'rag_basic' in psql)

-- Top 5 most similar chunks by embedding vector
-- Replace the vector literal with an actual embedding, or use the text-based search below
SELECT id,
       left(content, 150) AS content_preview,
       metadata->>'documentId' AS doc_id,
       metadata->>'chunkIndex' AS chunk_idx,
       round((1 - (embedding <=> '[0.0, 0.0, ...]'::vector))::numeric, 4) AS similarity
FROM rag_basic.chunks
ORDER BY embedding <=> '[0.0, 0.0, ...]'::vector
LIMIT 5;

-- Find chunks containing a keyword (ILIKE), ordered by embedding distance
-- Useful when you don't have a pre-computed query embedding
SELECT id,
       left(content, 150) AS content_preview,
       metadata->>'documentId' AS doc_id,
       metadata->>'chunkIndex' AS chunk_idx
FROM rag_basic.chunks
WHERE content ILIKE '%machine learning%'
ORDER BY embedding <=> (
    SELECT embedding
    FROM rag_basic.chunks
    WHERE content ILIKE '%machine learning%'
    LIMIT 1
)
LIMIT 5;

-- Find chunks within a single document
SELECT id,
       left(content, 150) AS content_preview,
       metadata->>'chunkIndex' AS chunk_idx
FROM rag_basic.chunks
WHERE metadata->>'documentId' = ''  -- paste document id here
ORDER BY (metadata->>'chunkIndex')::int;

-- Pairwise distance matrix between first 5 chunks
-- Useful for checking embedding quality
SELECT a.id AS chunk_a,
       b.id AS chunk_b,
       round((a.embedding <=> b.embedding)::numeric, 4) AS distance
FROM rag_basic.chunks a
CROSS JOIN rag_basic.chunks b
WHERE a.id < b.id
ORDER BY distance
LIMIT 15;
