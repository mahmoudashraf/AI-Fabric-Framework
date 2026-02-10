# Curated Modes Packs (OSS / Pro-ready)

## Goal
Make it easy to enable a **coherent orchestration setup** (profile + modes + prompt bundles) with **one config key**, without adding custom code or bypassing framework guardrails.

Curated packs are **transparent**:
- They only ship **configuration defaults** and **prompt templates**.
- They do **not** replace the pipeline, validators, confirmation handling, or security.

## How it works
1) Add a curated pack dependency (example: catalog pack).
2) Set `ai.curated.pack` in your app configuration.
3) The framework loads the pack YAML from:
   - `classpath:ai-curated/packs/<pack>.yml`
4) Pack properties are added with **lowest precedence** (application config can override them deterministically).

Implementation detail:
- Core loads the pack via `CuratedPackEnvironmentPostProcessor`.

## Available packs
- `commerce` → production chat defaults for e-commerce flows:
  - `navigator` (keep current behavior)
  - `navigator_deep` (deep retrieval, actions disabled)
  - `executor` (action-first; optionally restricted retrieval)
  - `cart_assistant` (action-oriented commerce helper)

Planned (not shipped in this repo yet):
- `catalog`
- `support`

## Enable a pack
Example (commerce):

```yaml
ai:
  curated:
    pack: commerce
```

Dependency (Maven):

```xml
<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-curated-commerce</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Override pack defaults (recommended pattern)
Because pack properties are loaded as **defaults**, you can override anything explicitly in your app:

```yaml
ai:
  orchestration:
    profile: PRODUCTION_CHAT
    strict-mode-routing: true
```

## Optional: READ→RAG probe visibility (debug)
Some modes may treat READ actions as “helper tools”: if a READ action returns an empty successful payload, the orchestrator can fall back to RAG.

By default, the final response does **not** include any extra metadata about the attempted READ action (to keep payloads minimal).

To surface the attempted READ action in the final response (as `result.metadata.readProbe`), enable it per mode:

```yaml
ai:
  orchestration:
    modes:
      cart_assistant:
        expose-read-probe-fallback-attempt: true
```

## Prompt bundle pinning
Each pack enables its prompt overrides by adding an overlay bundle version via:
- `ai.prompts.bundle.overlays`

The framework resolves templates by trying overlay versions first, then falling back to the base bundle version (`ai.prompts.bundle.base-version`, default `v1`).

You can override it in your app if you need to run a different prompt bundle version.

## Optional: retrieval hints (strict contract)
The intent extractor may optionally return a retrieval hint in the top-level metadata:
- `metadata.retrievalQueryHint`

The orchestrator will only apply it when:
- there is exactly **one** retrieval intent in the response
- the hint passes strict validation (bounded length, no obvious PII markers)

This is designed to be **safe and fail-closed**: invalid/unsafe hints are ignored deterministically.
