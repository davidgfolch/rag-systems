# rag-agentic

> **Status: planned.** The module has its pom and Maven artifact wired into the reactor, but **no implementation source yet** — the features below describe the roadmap. Target port **8083** (`RAG_AGENTIC_URL`).

Agentic RAG: retrieval augmented with AI agents, tool calling, and multi-step / self-reflective search.

## Purpose

Demonstrate agent-driven RAG where an LLM agent plans which tools to call, performs multi-hop retrieval, and reflects on whether retrieved context is sufficient before answering.

## Layers

```
com.rag.agentic
├── agents/        - Query planning, retrieval, reflection agents
├── tools/         - Tool implementations (search, document reader, web)
├── orchestration/ - Agent loop and tool dispatch
└── api/           - REST controllers
```

## Features

| Feature | Description |
|---------|-------------|
| Query planning | Agent decomposes complex queries |
| Multi-step retrieval | Iterative search refinement |
| Tool calling | Agent invokes search/web/doc tools |
| Self-reflection | Agent checks sufficiency before answering |

## Running

Not yet implemented. When built, it will start like the other modules (rag-provider must be up first):

```bash
.\scripts\run.bat rag-agentic --profile local        # http://localhost:8083
```

**Note**: Agentic RAG benefits most from a decent model. For local, an 8GB GPU (qwen3:8b) is recommended over pure CPU.

## Related

- [rag-basic](../rag-basic/README.md) — the baseline it will extend
- [README module table](../../README.md#modules--strategy)
- [Comparing the modules](../../README.md) — how rag-* modules are evaluated side by side