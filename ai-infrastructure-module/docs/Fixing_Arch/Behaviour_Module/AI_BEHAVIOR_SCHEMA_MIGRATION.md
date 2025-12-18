# AI Behavior Module – Schema Migration Guide

## Overview

The legacy behavior prototype stored commerce-centric columns (`view_count`, `click_count`, etc.) and was managed by Flyway. The new neutral module uses Liquibase change logs plus YAML-defined schemas/metrics. This document walks through migrating any local clones to the Liquibase-first model.

## 1. Remove Flyway artifacts

1. Drop or archive the old `behavior_metrics`, `behavior_events`, and Flyway metadata tables if they still exist.
2. Remove any `spring.flyway.*` properties that targeted behavior tables—the module no longer leverages Flyway.

## 2. Enable Liquibase for behavior tables

Add the Liquibase changelog to your Spring configuration (e.g., `application.yml` or profile-specific files):

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:/db/changelog/db.changelog-master.yaml
```

The bundled changelog lives inside `ai-infrastructure-module/ai-infrastructure-behavior/src/main/resources/db/changelog/` and creates:

- `behavior_signals`
- `behavior_signal_metrics`
- `behavior_insights`
- companion indexes/constraints used by the new module

## 3. Provide schema descriptors

Every adopter must supply YAML definitions for every ingested signal. By default the starter scans `classpath:/behavior/schemas/*.yml`. You can override the location via:

```yaml
ai:
  behavior:
    schemas:
      path: file:./config/behavior-schemas/*.yml
```

Validate your files (duplicates, missing attributes, etc.) with:

```bash
python ai-infrastructure-module/scripts/schema-doctor.py --schemas ./config/behavior-schemas
```

## 4. Configure metric projectors and insights

- Enable/disable projectors with `ai.behavior.processing.metrics.enabledProjectors`.
- The `/api/ai-behavior/users/{id}/metrics` and `/insights` responses now surface `kpis` (engagement, recency, diversity). Update any consumers or mocks accordingly.

## 5. Backfill (optional)

To reprocess historic events with the new projectors, export them to JSON/NDJSON and replay via:

```bash
python ai-infrastructure-module/scripts/signal-replay.py --source ./exports/behavior.ndjson --batch-size 25
```

## 6. Verification checklist

- [ ] Liquibase has successfully applied `db.changelog-master.yaml`.
- [ ] Schema YAMLs load without errors (`schema-doctor.py`).
- [ ] `/api/ai-behavior/schemas` responds with cacheable payload (ETag + max-age).
- [ ] `/api/ai-behavior/users/{id}/metrics` returns the `kpis` bundle.
- [ ] `/api/ai-behavior/users/{id}/insights` returns newly computed KPIs/segments.

Once all steps pass you can remove the old Flyway migrations from your deployment scripts—the Liquibase-driven schema is now canonical.
