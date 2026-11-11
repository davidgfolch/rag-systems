package com.rag.common.retrieval.testfixture;

import com.rag.common.retrieval.store.PgVectorStoreAdapter;
import org.springframework.ai.vectorstore.VectorStore;

import javax.sql.DataSource;

/** Builds {@link PgVectorStoreAdapter} instances through the production constructors. */
public final class TestPgVectorStoreAdapters {

    private static final String SCHEMA = "public";
    private static final String TABLE = "chunks";

    private TestPgVectorStoreAdapters() {
    }

    public static PgVectorStoreAdapter inMemory(VectorStore delegate) {
        return new PgVectorStoreAdapter(delegate, null, SCHEMA, TABLE);
    }

    public static PgVectorStoreAdapter of(VectorStore delegate, DataSource dataSource) {
        return new PgVectorStoreAdapter(delegate, dataSource, SCHEMA, TABLE);
    }

    public static PgVectorStoreAdapter withTable(VectorStore delegate, DataSource dataSource, String table) {
        return new PgVectorStoreAdapter(delegate, dataSource, SCHEMA, table);
    }
}
