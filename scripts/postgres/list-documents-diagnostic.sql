-- Document listing diagnostic for rag-basic
-- Run in pgAdmin against the rag database, default schema rag_basic.
-- The /documents endpoint groups the chunks table by metadata->>'documentId'.
-- This reproduces that logic and surfaces why stray groups appear alongside a named PDF.

-- 1. Reproduce the API's listDocuments() grouping (what /documents shows)
SELECT metadata->>'documentId'      AS document_id,
       count(*)                     AS chunk_count,
       (array_agg(metadata ORDER BY metadata->>'chunkIndex'))[1] AS first_meta
FROM rag_basic.chunks
WHERE metadata->>'documentId' IS NOT NULL
GROUP BY metadata->>'documentId'
ORDER BY chunk_count DESC, document_id;

-- 2. Same grouping but with the sample file name from the first chunk's metadata
SELECT metadata->>'documentId'        AS document_id,
       count(*)                       AS chunk_count,
       (array_agg(metadata ORDER BY metadata->>'chunkIndex'))[1]->>'file_name' AS sample_file_name
FROM rag_basic.chunks
GROUP BY metadata->>'documentId'
ORDER BY chunk_count DESC, document_id;

-- 3. Sample metadata per grouping (reveals missing/empty documentId, stray source_file)
SELECT metadata->>'documentId' AS document_id,
       count(*)                AS chunk_count,
       (array_agg(metadata ORDER BY metadata->>'chunkIndex'))[1] AS first_meta
FROM rag_basic.chunks
GROUP BY metadata->>'documentId'
HAVING metadata->>'documentId' IS DISTINCT FROM '%Mastering Kafka Streams%'
ORDER BY chunk_count DESC;

-- 4. Ingest jobs / files tracked by rag-basic side tables (if any)
SELECT 'ingestion_jobs' AS source, count(*) AS rows FROM rag_basic.ingestion_jobs
UNION ALL
SELECT 'documents', count(*) FROM rag_basic.documents
UNION ALL
SELECT 'chunks', count(*) FROM rag_basic.chunks;

-- 5. Chunks with no documentId at all (empty/missing metadata key)
SELECT metadata->>'documentId' AS document_id, count(*) AS orphans
FROM rag_basic.chunks
WHERE metadata->>'documentId' IS NULL OR metadata->>'documentId' = ''
GROUP BY metadata->>'documentId';

-- 6. Full distinct metadata keys present across all chunks (to spot the real discriminator)
SELECT DISTINCT jsonb_object_keys(metadata) AS metadata_key
FROM rag_basic.chunks
ORDER BY metadata_key;


-- =====================================================================
-- DELETE QUERIES  (run individually in pgAdmin, uncomment to execute)
-- =====================================================================

-- 7. Preview: rows to be removed by the "empty documentId" delete below
SELECT metadata->>'documentId' AS document_id, count(*) AS orphans
FROM rag_basic.chunks
WHERE metadata->>'documentId' IS NULL OR metadata->>'documentId' = ''
GROUP BY metadata->>'documentId';

-- 8. DELETE: remove chunks with empty/missing documentId (the stray "1 chunks" group)
DELETE FROM rag_basic.chunks
WHERE metadata->>'documentId' IS NULL OR metadata->>'documentId' = '';

-- 9. DELETE: remove chunks for a specific stray documentId (set the id first)
-- DELETE FROM rag_basic.chunks
-- WHERE metadata->>'documentId' = 'a7c80838-4141-4d24-b8c7-e13e64f752e2';

-- 10. DELETE ALL: wipe every chunk in the rag_basic schema (full reset)
-- TRUNCATE TABLE rag_basic.chunks RESTART IDENTITY CASCADE;
