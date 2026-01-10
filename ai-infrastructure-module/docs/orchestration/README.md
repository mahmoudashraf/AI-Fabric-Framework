# Orchestration Documentation

This folder contains documentation for the RAG Orchestrator pipeline architecture.

## Documents

| Document | Description |
|----------|-------------|
| [ORCHESTRATION_RESULT_NORMALIZATION.md](ORCHESTRATION_RESULT_NORMALIZATION.md) | Provider-agnostic contract and normalization rules for final orchestration results |
| [ORCHESTRATOR_PIPELINE_REFACTORING.md](ORCHESTRATOR_PIPELINE_REFACTORING.md) | Overview of the refactoring from monolithic to pipeline architecture |
| [PIPELINE_ARCHITECTURE.md](PIPELINE_ARCHITECTURE.md) | Detailed architecture guide for the pipeline pattern |
| [PIPELINE_STEPS_REFERENCE.md](PIPELINE_STEPS_REFERENCE.md) | Reference documentation for all pipeline steps |
| [RAG_EXTRACTION_ASSESSMENT.md](RAG_EXTRACTION_ASSESSMENT.md) | Assessment for extracting RAG into a separate module |
| [PROGRESSIVE_INTENT_EXTRACTION_FALLBACK_PLAN.md](PROGRESSIVE_INTENT_EXTRACTION_FALLBACK_PLAN.md) | Plan for compound-first intent extraction with bounded repair and multi-step fallback (plus orchestration vs generation provider config) |

## Quick Links

### For Developers

- **Adding a new step?** See [Pipeline Steps Reference](PIPELINE_STEPS_REFERENCE.md#creating-custom-steps)
- **Understanding the flow?** See [Pipeline Architecture - Data Flow](PIPELINE_ARCHITECTURE.md#data-flow)
- **Extending RAG?** See [RAG Extraction Assessment](RAG_EXTRACTION_ASSESSMENT.md)

### For Architects

- **Why pipeline pattern?** See [Refactoring Overview](ORCHESTRATOR_PIPELINE_REFACTORING.md#problem-statement)
- **Design principles?** See [Architecture - Design Principles](PIPELINE_ARCHITECTURE.md#design-principles)
- **Module extraction?** See [RAG Extraction Assessment](RAG_EXTRACTION_ASSESSMENT.md)

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                       RAGOrchestrator                            │
│                   (delegates to Pipeline)                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                 DefaultOrchestrationPipeline                     │
│                                                                  │
│   ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│   │Security  │→│Access    │→│PII       │→│Compliance│→ ...     │
│   │(10)      │ │(20)      │ │(30)      │ │(40)      │          │
│   └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
│                                                                  │
│   ... →┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│        │Intent    │→│Handling  │→│Metadata  │→│Suggest   │→ ...│
│        │(50)      │ │(60)      │ │(70)      │ │(80)      │     │
│        └──────────┘ └──────────┘ └──────────┘ └──────────┘     │
│                                                                  │
│   ... →┌──────────┐ ┌──────────┐                                │
│        │Sanitize  │→│History   │                                │
│        │(90)      │ │(100)     │                                │
│        └──────────┘ └──────────┘                                │
└─────────────────────────────────────────────────────────────────┘
```

## Key Benefits

| Benefit | Description |
|---------|-------------|
| **Testability** | Each step tested in isolation |
| **Maintainability** | Changes isolated to specific steps |
| **Extensibility** | New steps added without touching existing code |
| **Observability** | Step-level timing, logging, metrics |
| **Security** | Fail-closed pattern at security gates |

## Related Documentation

- [Orchestrator User Guide](../../../Final_Documentation/System_Archtecture_Guides/Orchestrator_User_Guide.md) - User-facing guide
- [AI Fabric Framework Philosophy](../../../Final_Documentation/Development_Guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md) - Design principles
- [AI LLM Code Generation Guide](../../../Final_Documentation/Development_Guides/AI_LLM_CODE_GENERATION_GUIDE.md) - Coding standards
