# ASDLC - Agentic Software Development Life Cycle

The working process every change to this repository must follow. Anything an
agent (or a human reviewer) modifies must stay verifiable and simple.

## Parallel Development Quick Start

Develop features in isolated per-feature clones so several can proceed in
parallel, using `scripts\dev.bat` (Windows) / `scripts/dev.sh` (Linux/Mac):

```bash
.\scripts\dev.bat new my-feature   # clone to ..\rag-systems-my-feature, branch feat/my-feature
.\scripts\dev.bat check [module]   # tests, then SonarQube scan if tests pass
.\scripts\dev.bat commit "msg"     # stage all + commit
.\scripts\dev.bat push             # push feat/my-feature
.\scripts\dev.bat pr "title"       # open PR vs main
.\scripts\dev.bat auto-merge       # poll PR checks, auto-merge when green, resolve conflicts, then delete clone
.\scripts\dev.bat merge            # alias of auto-merge
```

`dev new` copies the auto-generated SonarQube token into the clone's
`.env.secrets` so SonarQube always works there. Each clone is independent; run
the commands from inside the clone you are working on. `dev auto-merge` (and
its alias `dev merge`) deletes its own clone after a successful merge; add
`-k` to keep it.

## Plan Approval

Any new plan (feature, bug fix, or improvement) must be accepted before
implementation begins. Once accepted, the plan enters the SDLC and must
follow every step below without shortcuts:

1. Working in a per-feature clone on branch `feat/<name>` (via `dev new`);
   never commit to `main` directly.
2. Modify code only inside the clone's `workdir`.
3. **Execute all tests** after implementation (see Rule 1 and the Stop-gate
   hooks in `.claude/settings.json` and `.opencode/plugin/sdlc-gate.ts`).
4. `dev check` (tests + SonarQube) must pass before merging.
5. `dev push`, then `dev pr`.
6. `dev auto-merge` orchestrates the merge: poll checks, auto-merge green
   PRs, attempt to auto-resolve conflicts, and delete the clone on success.

## Gate Hooks

The "run tests after implementation" rule is enforced by agent hooks on both
coding assistants used in this repo. They do not block execution; they inject
a reminder into the agent's context before finishing, so the rule cannot be
silently skipped:

- **Claude Code** — a `Stop` hook registered in `.claude/settings.json` calls
  `.claude/hooks/stop-gate.sh` (Unix) / `stop-gate.bat` (Windows). When the
  hook fires it inspects the working tree; if Java source was modified on a
  `feat/*` branch, it emits a `stopReason` instructing the agent to run the
  full suite (`scripts/test.bat` / `scripts/test.sh`) and `dev check [module]`
  before finishing. Silent on `main` and clean branches.
- **opencode** — a plugin registered in `.opencode/opencode.json`
  (`.opencode/plugin/sdlc-gate.ts`). It injects the SDLC gate into the
  assistant's system prompt on every turn so the rules are always present, and
  it annotates `edit`/`write` of a `.java` file with a test reminder.

Why hooks instead of a strict hard-block: hooks run every time, whereas the
real merge gate (`dev auto-merge`, which merges only green PRs) is where a
failing test is decisive. Hooks keep normal flows unannoyed while guaranteeing
the requirement is surfaced.

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
in every module - they run as part of every test run. Use `.\scripts\test.bat`
(Windows) or `./scripts/test.sh` (Unix) after significant changes.

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
| Tests + SonarQube after every change | This document, `.claude/rules/architecture-guidelines.md`, Stop-gate hook |
| Tests always run after implementation | Stop-gate hooks: `.claude/settings.json` (Claude Code), `.opencode/plugin/sdlc-gate.ts` (opencode) |
| Auto-merge green PRs + conflict resolution | `scripts/dev.bat`/`dev.sh` `auto-merge` |
| Modern Java / no verbose constructors | This document, review checklist |
| `var` when type is clear | This document |
| `Entity`/`DTO` bean suffixes | `rag-memory/.../ArchitectureTest`, `rag-contract/.../DtoNamingTest` |