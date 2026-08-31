# rag-agentic

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

```bash
.\scripts\run.bat rag-agentic --profile local
```

**Note**: Agentic RAG benefits most from a decent model. For local, an 8GB GPU (qwen3:8b) is recommended over pure CPU.

## Testing

```bash
.\scripts\test.bat rag-agentic
.\scripts\test.bat --architecture
```