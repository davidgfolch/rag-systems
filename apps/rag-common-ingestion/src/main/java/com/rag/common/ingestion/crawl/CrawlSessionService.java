package com.rag.common.ingestion.crawl;

import com.rag.common.core.domain.Document;
import com.rag.common.core.repositories.DocumentStorePort;
import com.rag.common.core.util.WebUrls;
import com.rag.common.ingestion.AsyncIngestionService;
import com.rag.common.ingestion.web.WebPageFetcher;
import com.rag.contract.model.PageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.rag.common.core.domain.MetadataKeys.SOURCE;
import static com.rag.common.core.domain.MetadataKeys.SOURCE_TYPE;
import static com.rag.common.core.domain.MetadataKeys.TITLE;

/**
 * Server-side state for an interactive breadth-first crawl. The TUI drives one
 * page at a time: each step ingests the current page asynchronously, ranks its
 * outbound links with the supplied question and resolves the next page. The
 * initial set is the root plus up to {@code maxInitialLinks} ranked root links;
 * links discovered on later pages extend the frontier but not the initial set.
 */
public class CrawlSessionService {

    public static final int DEFAULT_MAX_INITIAL_LINKS = 5;
    public static final int DEFAULT_MAX_PAGES = 50;

    private static final Logger log = LoggerFactory.getLogger(CrawlSessionService.class);
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final String WEB = "web";

    private final WebPageFetcher fetcher;
    private final AsyncIngestionService asyncIngestion;
    private final DocumentStorePort documentStore;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public CrawlSessionService(WebPageFetcher fetcher, AsyncIngestionService asyncIngestion,
                               DocumentStorePort documentStore) {
        this.fetcher = fetcher;
        this.asyncIngestion = asyncIngestion;
        this.documentStore = documentStore;
    }

    public StartResult start(String url, int maxInitialLinks, int maxPages, boolean sameHostOnly) {
        evictExpired();
        var page = fetcher.fetch(url);
        var session = new Session(UUID.randomUUID().toString(), WebUrls.host(url),
                Math.max(0, maxInitialLinks), Math.max(1, maxPages), sameHostOnly);
        session.current = page;
        session.known.add(WebUrls.key(page.getUrl()));
        session.existingSources.addAll(existingSourceKeys());
        sessions.put(session.id, session);
        log.info("Crawl session {} started at {} (maxInitialLinks={}, maxPages={}, sameHostOnly={})",
                session.id, page.getUrl(), session.maxInitialLinks, session.maxPages, sameHostOnly);
        return new StartResult(session.id, pageInfo(page), 1, true);
    }

    public StepResult step(String sessionId, String question) {
        var session = require(sessionId);
        synchronized (session) {
            session.touch();
            if (session.current == null) throw new NoPageException(sessionId);
            var page = session.current;
            int skippedBefore = session.skipped;
            var documentId = submit(session, page);
            session.pages++;
            if (session.initialRemaining > 0) session.initialRemaining--;
            enqueue(session, page, question);
            session.current = session.pages < session.maxPages ? fetchNext(session) : null;
            var result = new StepResult(session.id, documentId, page.getUrl(), page.getTitle(),
                    documentId != null, session.initialRemaining, session.current != null,
                    session.skipped - skippedBefore, pageInfo(session.current));
            log.info("Crawl {} step: page={} ingested={} initialRemaining={} hasMore={} skipped={}",
                    session.id, page.getUrl(), result.ingested(), result.initialRemaining(),
                    result.hasMore(), result.skippedLinks());
            return result;
        }
    }

    public boolean cancel(String sessionId) {
        var removed = sessions.remove(sessionId);
        if (removed != null) log.info("Crawl session {} cancelled", sessionId);
        return removed != null;
    }

    private String submit(Session session, PageDTO page) {
        if (session.existingSources.contains(WebUrls.key(page.getUrl()))) {
            log.info("Crawl {} skipping already ingested page {}", session.id, page.getUrl());
            return null;
        }
        var metadata = Map.<String, Object>of(SOURCE_TYPE, WEB, SOURCE, page.getUrl(),
                TITLE, Objects.toString(page.getTitle(), page.getUrl()));
        var documentId = asyncIngestion.submit(new Document(UUID.randomUUID().toString(), page.getText(), metadata));
        session.known.add(WebUrls.key(page.getUrl()));
        return documentId;
    }

    private void enqueue(Session session, PageDTO page, String question) {
        var links = page.getLinks();
        if (links == null || links.isEmpty()) return;
        int added = 0;
        for (var link : fetcher.rankLinks(links, question)) {
            if (isEnqueuable(session, link)) {
                session.frontier.add(link);
                added++;
            } else {
                session.skipped++;
            }
        }
        if (session.pages == 1) {
            int capacity = Math.max(0, session.maxPages - session.pages);
            session.initialRemaining += Math.min(added, Math.min(session.maxInitialLinks, capacity));
        }
    }

    private static boolean isEnqueuable(Session session, String link) {
        var key = WebUrls.key(link);
        if (key == null || !session.known.add(key)) return false;
        if (session.sameHostOnly && !session.host.equals(WebUrls.host(link))) return false;
        return !session.existingSources.contains(key);
    }

    private PageDTO fetchNext(Session session) {
        while (!session.frontier.isEmpty()) {
            var next = session.frontier.poll();
            try {
                var page = fetcher.fetch(next);
                if (page == null || Objects.toString(page.getText(), "").isBlank()) {
                    session.skipped++;
                    continue;
                }
                session.known.add(WebUrls.key(page.getUrl()));
                return page;
            } catch (RuntimeException e) {
                session.skipped++;
                log.warn("Crawl {} could not fetch {}: {}", session.id, next, e.getMessage());
            }
        }
        return null;
    }

    private Set<String> existingSourceKeys() {
        if (documentStore == null) return Set.of();
        return documentStore.listDocuments().stream()
                .map(document -> document.metadata().get(SOURCE))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(WebUrls::key)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Session require(String sessionId) {
        evictExpired();
        var session = sessions.get(sessionId);
        if (session == null) throw new SessionNotFoundException(sessionId);
        return session;
    }

    private void evictExpired() {
        var cutoff = Instant.now().minus(SESSION_TTL);
        sessions.entrySet().removeIf(entry -> entry.getValue().lastAccess.isBefore(cutoff));
    }

    private static PageInfo pageInfo(PageDTO page) {
        return page == null ? null : new PageInfo(page.getUrl(), page.getTitle());
    }

    public record PageInfo(String url, String title) {}

    public record StartResult(String sessionId, PageInfo page, int initialRemaining, boolean hasMore) {}

    public record StepResult(String sessionId, String documentId, String pageUrl, String title,
                             boolean ingested, int initialRemaining, boolean hasMore,
                             int skippedLinks, PageInfo nextPage) {}

    private static final class Session {
        final String id;
        final String host;
        final int maxInitialLinks;
        final int maxPages;
        final boolean sameHostOnly;
        final Deque<String> frontier = new ArrayDeque<>();
        final Set<String> known = new HashSet<>();
        final Set<String> existingSources = new HashSet<>();
        PageDTO current;
        int initialRemaining = 1;
        int pages;
        int skipped;
        Instant lastAccess = Instant.now();

        Session(String id, String host, int maxInitialLinks, int maxPages, boolean sameHostOnly) {
            this.id = id;
            this.host = host;
            this.maxInitialLinks = maxInitialLinks;
            this.maxPages = maxPages;
            this.sameHostOnly = sameHostOnly;
        }

        void touch() {
            lastAccess = Instant.now();
        }
    }

    public static class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(String sessionId) {
            super("No such crawl session: " + sessionId);
        }
    }

    public static class NoPageException extends RuntimeException {
        public NoPageException(String sessionId) {
            super("Crawl session has no page left: " + sessionId);
        }
    }
}
