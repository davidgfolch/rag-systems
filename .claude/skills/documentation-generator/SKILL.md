---
name: documentation-generator
description: Generate and maintain module documentation, architecture diagrams, and learning guides.
---

# Documentation Generator

Use this skill when creating or updating documentation for the RAG Systems repository.

## Documentation Locations

| Type | Location |
|------|----------|
| Project overview | `README.md` |
| Master plan | `docs/PLAN.md` |
| Architecture | `docs/architecture/` |
| ADRs | `docs/architecture/decision-records/` |
| Guides | `docs/guides/` |
| Comparisons | `docs/comparison/` |

## Documentation Standards

### Code Documentation
- Javadoc for public APIs and interfaces
- Self-documenting code (meaningful names)
- NO unnecessary comments in code

### Architecture Documentation
- Architecture Decision Records (ADRs) for significant decisions
- Mermaid/ASCII diagrams for component and data flows
- Document each layer's responsibility

### Module Documentation
Each runnable module (`apps/[module]`) has a `README.md` covering:
1. Purpose and scope
2. The RAG architecture it implements
3. Chunking/embedding/retrieval strategies used
4. Configuration (profiles, models)
5. How to run and test
6. Performance characteristics and trade-offs

### Learning Guides
Focus on teachability:
- Explain WHY before HOW
- Compare approaches with trade-offs
- Include benchmark numbers and metrics
- Reference the underlying concepts (chunking, embeddings, vector search)

## Readme Coverage Requirements

Always verify each new module has a `README.md`. Update the parent `README.md` module table when adding a module.

## Diagrams

When creating diagrams, prefer:
- Mermaid for architecture diagrams (renders on GitHub)
- ASCII for inline console-friendly diagrams

## Definition of Done
- All new modules documented
- READMEs updated in parent and module
- Learning guides reference real metrics/benchmarks
- No documentation-only commits left incomplete
