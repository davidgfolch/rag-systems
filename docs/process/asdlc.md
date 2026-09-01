# ASDLC - Agentic Software Development Life Cycle

The working process every change to this repository must follow. Anything an
agent (or a human reviewer) modifies must stay verifiable and simple.

## Rules

### 1. Every change must be verified

After any code modification:

1. Run the module tests and the full suite:
   `.\scripts\test.bat` (Windows) or `./scripts/test.sh` (Unix).
2. Run SonarQube and fix every issue it reports:
   `.\scripts\sonar.bat scan <token>`.
3. The quality gate must pass before code is committed or merged:
   `new_coverage >= 80%`, `new_duplicated_lines_density < 3%`, `new_violations == 0`.

Architecture rules are enforced automatically by the `ArchitectureTest` suites
in every module - run `.\scripts\test.bat --architecture` (Windows) or
`./scripts/test.sh --architecture` (Unix) after significant changes.

### 2. Keep code simple

- Prefer **modern Java** over manual boilerplate: `record`s, compact
  constructors, and fluent builders.
- **Avoid regular/verbose constructors** and hand-written getters/setters.
  Use an idiomatic equivalent (Lombok where the team keeps it, otherwise
  records or compact constructors). JPA entities keep only the required
  constructors.
- One responsibility per class/method (see `architecture-guidelines.md`).

### 3. Use `var` when the type is clear

Use `var` for local variables when the type is obvious from the initializer,
e.g. `var result = sut.createConversation("   ");`. Keep explicit types on
method signatures, fields, and anywhere `var` would hide intent.

### 4. Suffix bean classes; keep domain beans plain

To avoid fully-qualified types and name collisions between layers:

- **JPA `@Entity` classes** end with `Entity` (e.g. `ConversationEntity`).
- **OpenAPI contract DTOs** end with `DTO` (or a transfer suffix:
  `Request`/`Response`/`Result`) (e.g. `ConversationDTO`, `PageDTO`).
- **Pure domain beans** (non-entity, non-DTO) keep plain names (e.g.
  `Chunk`, `Document`).

These rules are enforced by the architecture tests:
`rag-memory` `ArchitectureTest` (entities) and `rag-contract` `DtoNamingTest`
(DTOs).

## Enforcement

| Rule | Where enforced |
|------|----------------|
| Tests + SonarQube after every change | This document, `.claude/rules/architecture-guidelines.md` |
| Modern Java / no verbose constructors | This document, review checklist |
| `var` when type is clear | This document |
| `Entity`/`DTO` bean suffixes | `rag-memory/.../ArchitectureTest`, `rag-contract/.../DtoNamingTest` |