---
name: spring-integration-tests
description: Avoid the recurring Spring Boot + Spring AI testing pitfalls in this repo (bean overrides, embedding model wiring, Mockito imports, JSON assertions). Use when writing or fixing unit/integration tests that touch Spring context, embedding/chat adapters, or MockMvc.
---

# Spring Integration Tests — Common Pitfalls

Use this skill whenever writing or debugging tests that boot a Spring context
(`@SpringBootTest`), exercise an Spring AI adapter (`EmbeddingModel`,
`ChatModel`), or assert on REST responses via MockMvc. It captures the errors
committed repeatedly during past implementations so they are not repeated.

## 0. Scope: `@MockitoBean` is for context/integration tests, NOT unit tests

`@MockitoBean` only exists inside a Spring **application-context** test
(`@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`, ...). It swaps a mock in
for a real bean while Spring constructs the whole context.

- **Integration / context test** (`@SpringBootTest` + MockMvc): you boot the
  context, so you replace a bean with `@MockitoBean`. This is where the
  pitfalls below live (that is the scope of RagBasicApplicationContextTest).
- **Unit test**: you never boot Spring. You instantiate the class under test
  directly (`sut = new SomeService(mockedDep)`) and pass
  `mock(SomeDep.class)` — no `@MockitoBean`, no `@SpringBootTest`. (See
  `test-implementer` skill: SUT var must be `sut`.)

Pick the mechanism by **scope**, not by "integration" naming alone:
`@MockitoBean` ⇒ context test; `mock(...)` ⇒ unit test.

## 1. Replacing a real bean: use `@MockitoBean`, not a `@Bean` in `@TestConfiguration`

A nested `@Configuration` class inside a `@SpringBootTest` that declares a
`@Bean` with the same name/type as an existing production bean does **NOT**
reliably override it. Spring may still construct the real bean and use it.

Instead, replace the bean with a mock using `@MockitoBean` (or `@MockitoSpyBean`):

```java
@SpringBootTest
class FooTest {
    @MockitoBean
    private com.rag.common.services.EmbeddingModel domainEmbeddingModel;
}
```

`@MockitoBean` registers a mock and replaces the real bean in the context
before dependent components are constructed.

## 2. Correct `@MockitoBean` import (Spring 6.2 / Boot 3.4+)

The `@MockitoBean` annotation moved package in Spring 6.2. Use:

```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;
```

NOT the old `org.springframework.boot.test.mock.mockito.MockitoBean` — that
package no longer exists on the Spring 6.2 / Boot 3.4+ classpath and fails at
compile time with "cannot find symbol".

## 3. Mock the layer where the dependency is actually consumed

This codebase bridges Spring AI and the domain layer. There are **two**
embedding types:

- `org.springframework.ai.embedding.EmbeddingModel` — the Spring AI interface.
- `com.rag.common.services.EmbeddingModel` — the **domain** interface.

`InMemoryVectorStore`, `IngestionService`, and the retrieval pipeline consume
the **domain** `EmbeddingModel`, which is backed by the adapter
`SpringAiEmbeddingModel` (which wraps the Spring AI bean).

If you mock the Spring AI `EmbeddingModel`, the domain `EmbeddingModel`
(`SpringAiEmbeddingModel`) was already constructed with the real delegate
reference, so the mock never takes effect and you get NPEs at runtime.

**Rule:** find the actual injected type of your SUT/consumer (read the
constructor / `@Autowired` field) and mock **that** type. When in doubt, mock
`com.rag.common.services.EmbeddingModel` for vector-store and retrieval tests.

## 4. Spring AI interface pitfalls

- `org.springframework.ai.embedding.EmbeddingModel` is an **interface** that
  requires implementing **both** `call(EmbeddingRequest)` **and**
  `embed(Document)`. `AbstractEmbeddingModel` does not provide useful defaults
  for both, so extending it and implementing only one method leaves the other
  abstract → compile/runtime failure. Implement both.
- `EmbeddingResponse(List<Embedding>)` takes a `List`; `Embedding(float[]
  content, int index)` takes the vector array and an index.
- `EmbeddingRequest(List.of(text), options)` — the options (2nd arg) may be
  `null`.

## 5. Domain model accessors

- `com.rag.common.domain.Document` exposes text via **`getText()`**, not
  `getContent()`.
- Confirm the actual method against `apps/rag-common/src/main/java/.../Document.java`
  before writing test stubs.

## 6. JSON assertion gotchas (MockMvc)

- Verify the actual response shape before asserting with `jsonPath`. Check the
  controller + the generated OpenAPI DTO.
- `QueryResponse` uses **`$.results`** as the result-list field, **not**
  `$.chunks`. (The domain/older API may use `chunks`; the OpenAPI contract DTO
  uses `results`.)
- For `List<Float>` returned by the domain `EmbeddingModel.embed(String)`,
  remember it's a `List<Float>`, so build it element-by-element (e.g.
  `ArrayList`) rather than `List.of(...)`.

## 7. Validate before building the whole test

Before running the full suite, sanity-check each of these for a
`@SpringBootTest`:

1. Which embedding/vector-store beans are used (read `RagBasicConfig` /
   equivalent config class: bean names like `localSpringAiEmbeddingModel`,
   `embeddingModel`, `vectorStore`).
2. Is the vector store switched to in-memory
   (`rag.vector-store.type=simple`) and JDBC/DataSource autoconfig excluded so
   no Postgres connection is attempted?
3. Which type is actually injected into the SUT (see §3).

A quick way to catch wiring mistakes is a minimal `contextLoads()` test that
boots the context and asserts the key beans are non-null before you write the
full flow test.

## Checklist — before you run tests

- [ ] `@MockitoBean` import is the Spring 6.2 one (§2).
- [ ] Mocked bean is the **domain** `EmbeddingModel` where the pipeline consumes it (§3).
- [ ] All abstract Spring AI methods implemented (§4).
- [ ] Used `getText()` not `getContent()` (§5).
- [ ] `jsonPath` uses the correct field names from the actual DTO (§6).
- [ ] Vector store is `simple` and DB autoconfig is excluded for `@SpringBootTest` (§7).
