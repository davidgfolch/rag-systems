package com.rag.contract.ws;

public final class ApiPaths {

    public static final String DOCUMENTS = "/api/documents";
    public static final String INGEST = "/api/documents/ingest";
    public static final String INGEST_FILE = "/api/documents/ingest-file";
    public static final String INGEST_FILE_ASYNC = "/api/documents/ingest-file-async";
    public static final String INGEST_STATUS = "/api/documents/ingest-status/{id}";
    public static final String INGEST_URL = "/api/documents/ingest-url";
    public static final String DOCUMENT_BY_ID = "/api/documents/{documentId}";
    public static final String QUERY = "/api/query";
    public static final String PROVIDER = "/api/provider";
    public static final String CATALOG = "/api/provider/catalog";
    public static final String CATALOG_REFRESH = "/api/provider/catalog/refresh";
    public static final String PROVIDER_CHAT = "/api/provider/chat";
    public static final String PROVIDER_EMBEDDING = "/api/provider/embedding";
    public static final String PROVIDER_CONFIGURE = "/api/provider/configure";
    public static final String EMBED = "/api/embed";
    public static final String COMPLETE = "/api/complete";
    public static final String CHAT_STREAM = "/api/chat/stream";
    public static final String FETCH = "/api/fetch";
    public static final String CONVERSATIONS = "/api/conversations";
    public static final String CONVERSATIONS_MESSAGES = "/api/conversations/{id}/messages";

    private ApiPaths() {}
}
