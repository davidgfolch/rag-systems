---
name: rules
description: Code conventions (imports, local variable style) to follow for every change under ASDLC in the RAG Systems monorepo.
---

# Code Conventions (ASDLC)

Follow these conventions for any code you write or edit in this repository,
alongside `docs/process/asdlc.md` and `.claude/rules/architecture-guidelines.md`.

## 1. Use imports, avoid inline fully-qualified names

Import every type you reference at the top of the file. Do **not** spell out
fully-qualified names inline at the point of use. Inline references are
harder to read, bloat the usage site, and are easy to get wrong.

Avoid this:

```java
private final org.springframework.ai.vectorstore.VectorStore delegate;
...
private org.springframework.ai.document.Document toSpringDocument(Chunk chunk) {
    ...
    return new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(...,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
}
```

Prefer importing the types once at the top:

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

private final VectorStore delegate;
...
private Document toSpringDocument(Chunk chunk) { ... }
```

Exemptions: Java's own `java.*` / `javax.*` may keep inline-only usage when
used sparingly. A fully-qualified name is acceptable only when two types share
a short name in the same file and an import would be ambiguous.

## 2. Use `var` when the type is obvious

Use `var` for local variables and loop variables when the type is clear from
the initializer. This keeps the code DRY — the type appears nowhere else, so
repeating it is redundant.

```java
var request = SearchRequest.builder().query(query).topK(topK).build();
var docs = chunks.stream().map(this::toSpringDocument).toList();

try (var statement = connection.createStatement()) { ... }
```

Keep explicit types on:

- method signatures and parameters,
- class fields,
- method return types,
- anywhere `var` would hide the intent of the code.

## 3. Use common abbreviated names

Prefer short, conventional names for local variables, parameters, fields, and
loop variables. This keeps lines short and the code readable at a glance.

Core set (use everywhere):

| Abbrew. | Full name |
|---------|-----------|
| `req`        | request |
| `res`        | response |
| `conn`       | connection (`java.sql.Connection`) |
| `ds`         | dataSource (`javax.sql.DataSource`) |
| `docs`       | documents (`List<Document>`) |
| `doc`        | document (singular param/local) |
| `docSummary` | `DocumentSummary` (singular) |
| `docsSummaries` | `List<DocumentSummary>` |
| `chunks` | `List<Chunk>` |
| `rs`         | resultSet (`java.sql.ResultSet`) |
| `pstmt`      | preparedStatement (`java.sql.PreparedStatement`) |
| `md`         | metadata (`Map<String, Object>` — locals/params only) |
| `om`         | objectMapper (`com.fasterxml.jackson.databind.ObjectMapper`) |
| `msg`        | message (ChatMessageDTO / TextMessage, singular) |
| `msgs`       | messages |
| `docId`      | documentId |
| `repo`       | repository (field/param for a `*Repository`) |
| `sb`         | StringBuilder |
| `sources`    | sources (`List<Chunk>`) — keep full form |

```java
var req = SearchRequest.builder().query(query).topK(topK).build();
try (Connection conn = ds.getConnection();
     PreparedStatement pstmt = conn.prepareStatement(sql);
     ResultSet rs = pstmt.executeQuery()) {
    List<Document> docs = rs...;
}
```

Keep the full type name when:

- it is the domain/public contract — e.g. `metadata`, `documentId`, `embedding`,
  `createdAt` as **record/entity fields** (abbreviating these breaks the public
  API / column binding);
- it is a Spring bean/`@ConfigurationProperties` property name that must bind to
  config keys (e.g. `baseUrl`, `schema`, `tableName`);
- the single-letter/short form would hurt clarity (e.g. keep `query`, `chunk`,
  `results`, `source` rather than inventing terse forms).

For types without a canonical short form, use `var` (section 2) and pick a
concise plural (`msgs`) that stays readable.

## 4. Structured logging is mandatory

Every concrete service, controller, handler, adapter, and client class MUST log
through a SLF4J logger. This is required for all features and modules.

```java
private static final Logger log = LoggerFactory.getLogger(ChatService.class);
```

Conventions:

- Use `info` for lifecycle milestones (ask received, retrieval count,
  generation start/complete, connection opened/closed).
- Use `warn` for retryable/degraded conditions (timeouts, rejections,
  close failures).
- Use `error` for failures, always with the throwable as the last argument.
- Use `debug` for internal details not needed at INFO.
- Always `{}` placeholders with arguments; never string concatenation.
- Never log sensitive content (credentials, full document bodies).
- Do not add manual MDC keys for `trace_id`/`span_id` — OpenTelemetry injects
  them; rely on that for correlation.

Logger field name is always `log` (never `logger`), and it is
`private static final`. Do not use Lombok `@Slf4j`.

## Enforcement

Prefer consistency with the surrounding file. If an existing block already uses
explicit types, match it rather than introducing a mix. These conventions apply
to new code and to lines you are intentionally editing; do not reformat code
you are not modifying.
