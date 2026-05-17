# ProdOps Studio — Integration Layer UI Design

## Design Context

This document covers the UI for ProdOps Studio's Integration Layer: the evidence engine that connects products, runs scans, produces findings, maps them to services and packages, tracks milestones, and governs evidence-based delivery.

These pages live under `studio.produs.com` and use the same design system established in the ProdUS Network UI Design (light Apple-like aesthetic, white canvas, 8px radius, pastel service identity).

The Integration Layer UI must communicate a clear chain at every step:

```
Connect → Scan → Findings → Services → Package → Milestones → Evidence → Review → Handoff
```

Every screen should make it obvious where the user is in this chain and what comes next.

---

## Design System Additions

### Scan & Finding Colors

| Token | Value | Usage |
|---|---|---|
| `--severity-critical` | `#dc2626` | Critical findings, blockers |
| `--severity-critical-bg` | `#fef2f2` | Critical finding backgrounds |
| `--severity-high` | `#ea580c` | High severity findings |
| `--severity-high-bg` | `#fff7ed` | High finding backgrounds |
| `--severity-medium` | `#d97706` | Medium severity findings |
| `--severity-medium-bg` | `#fffbeb` | Medium finding backgrounds |
| `--severity-low` | `#2563eb` | Low severity findings |
| `--severity-low-bg` | `#eff6ff` | Low finding backgrounds |
| `--severity-info` | `#64748b` | Informational findings |
| `--severity-info-bg` | `#f8fafc` | Info finding backgrounds |

### Finding Status Colors

| Status | Color | Chip style |
|---|---|---|
| New | `#6366f1` on `#eef2ff` | Indigo |
| Open | `#ea580c` on `#fff7ed` | Orange |
| Resolved | `#059669` on `#ecfdf5` | Green |
| Regressed | `#dc2626` on `#fef2f2` | Red |
| Accepted Risk | `#d97706` on `#fffbeb` | Amber |
| False Positive | `#64748b` on `#f1f5f9` | Gray |
| Insufficient Evidence | `#7c3aed` on `#f5f3ff` | Purple |

### Confidence Indicators

| Confidence | Visual |
|---|---|
| High | Three filled dots `●●●` green |
| Medium | Two filled, one empty `●●○` amber |
| Low | One filled, two empty `●○○` gray |

### Scan Status Icons

| Status | Visual |
|---|---|
| Queued | `◌` gray circle outline |
| Running | `◉` pulsing indigo |
| Completed | `✓` green check |
| Failed | `✗` red cross |
| Cancelled | `⊘` gray slash |

---

## Page Designs

### 1. Products Overview

**Route:** `studio.produs.com/products`

**Purpose:** All products with health status, last scan, and primary actions.

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Products                                       [+ New Product]  │
│  Your products and their production readiness                    │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  ┌─────────────────────────────────────────────────────┐│   │
│  │  │                                                     ││   │
│  │  │  E-Commerce Platform              Health: 42/100    ││   │
│  │  │  ████████░░░░░░░░░░░░                               ││   │
│  │  │                                                     ││   │
│  │  │  Stack: Next.js · Node · PostgreSQL · AWS           ││   │
│  │  │  Stage: Built, not production-ready                 ││   │
│  │  │                                                     ││   │
│  │  │  ┌─────────┐ ┌──────────┐ ┌─────────┐ ┌────────┐ ││   │
│  │  │  │ 3 Crit  │ │ 7 High   │ │ 12 Med  │ │ 5 Low  │ ││   │
│  │  │  │ ●       │ │ ●        │ │ ●       │ │ ●      │ ││   │
│  │  │  └─────────┘ └──────────┘ └─────────┘ └────────┘ ││   │
│  │  │                                                     ││   │
│  │  │  Last scan: 2h ago · 4 tools ran                    ││   │
│  │  │  Package: Security Hardening (draft)                ││   │
│  │  │                                                     ││   │
│  │  │  [View Diagnosis]  [Open Package]  [Rescan]         ││   │
│  │  │                                                     ││   │
│  │  └─────────────────────────────────────────────────────┘│   │
│  │                                                          │   │
│  │  ┌─────────────────────────────────────────────────────┐│   │
│  │  │                                                     ││   │
│  │  │  Mobile Banking App                Health: 71/100   ││   │
│  │  │  ██████████████░░░░░░                               ││   │
│  │  │                                                     ││   │
│  │  │  Stack: React Native · Express · MongoDB            ││   │
│  │  │  Stage: Production, needs improvement               ││   │
│  │  │                                                     ││   │
│  │  │  ┌─────────┐ ┌──────────┐ ┌─────────┐ ┌────────┐ ││   │
│  │  │  │ 0 Crit  │ │ 2 High   │ │ 5 Med   │ │ 8 Low  │ ││   │
│  │  │  │         │ │ ●        │ │ ●       │ │ ●      │ ││   │
│  │  │  └─────────┘ └──────────┘ └─────────┘ └────────┘ ││   │
│  │  │                                                     ││   │
│  │  │  Last scan: 3d ago                                  ││   │
│  │  │  Package: In Progress · M2/4 complete               ││   │
│  │  │                                                     ││   │
│  │  │  [View Diagnosis]  [Open Workspace]  [Rescan]       ││   │
│  │  │                                                     ││   │
│  │  └─────────────────────────────────────────────────────┘│   │
│  │                                                          │   │
│  │  ┌─────────────────────────────────────────────────────┐│   │
│  │  │                                                     ││   │
│  │  │  + Connect a new product                            ││   │
│  │  │                                                     ││   │
│  │  │  Connect a GitHub repo, provide a URL, or describe  ││   │
│  │  │  your product to get an evidence-based diagnosis.   ││   │
│  │  │                                                     ││   │
│  │  │  [Connect Product]                                  ││   │
│  │  │                                                     ││   │
│  │  └─────────────────────────────────────────────────────┘│   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Components:**
- Product card with health bar (colored gradient from red→amber→green based on score)
- Severity summary chips (mini colored boxes with count)
- Action buttons contextual to product state
- Empty state card with dashed border for new product

---

### 2. Connect Product (Wizard)

**Route:** `studio.produs.com/products/new`

**Purpose:** Guided product connection. Three paths: repo, URL, or manual.

**Step 1: Choose connection method**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Products             Connect Your Product                     │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  ① Connect  →  ② Configure  →  ③ First Scan  →  Done     │  │
│  │  ●━━━━━━━━━━━━━○━━━━━━━━━━━━━━━○━━━━━━━━━━━━━━○           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  How would you like to connect your product?                     │
│                                                                  │
│  ┌──────────────────┐ ┌──────────────────┐ ┌────────────────┐  │
│  │                  │ │                  │ │                │  │
│  │  ┌────────────┐  │ │  ┌────────────┐  │ │  ┌──────────┐ │  │
│  │  │    ≋≋≋     │  │ │  │   🌐       │  │ │  │   📝     │ │  │
│  │  │   GitHub   │  │ │  │   URL      │  │ │  │  Manual  │ │  │
│  │  └────────────┘  │ │  └────────────┘  │ │  └──────────┘ │  │
│  │                  │ │                  │ │                │  │
│  │  Connect a       │ │  Scan a live     │ │  Describe your │  │
│  │  GitHub repo.    │ │  or staging URL. │ │  product and   │  │
│  │  We'll detect    │ │  We'll check     │ │  we'll guide   │  │
│  │  your stack and  │ │  performance,    │ │  you through   │  │
│  │  scan for issues.│ │  security, and   │ │  the intake.   │  │
│  │                  │ │  accessibility.  │ │                │  │
│  │  ★ Recommended   │ │                  │ │                │  │
│  │                  │ │                  │ │                │  │
│  │  [Connect →]     │ │  [Enter URL →]   │ │  [Start →]     │  │
│  │                  │ │                  │ │                │  │
│  └──────────────────┘ └──────────────────┘ └────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 🔒 Privacy: We request only the permissions needed to     │  │
│  │ scan. Source code is cloned temporarily and deleted after  │  │
│  │ scanning. You can disconnect anytime.                     │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Step 2: Configure (GitHub path)**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Back              Configure Scan                              │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  ① Connect  →  ② Configure  →  ③ First Scan  →  Done     │  │
│  │  ✓━━━━━━━━━━━━━●━━━━━━━━━━━━━━━○━━━━━━━━━━━━━━○           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ REPOSITORY                                                │  │
│  │                                                           │  │
│  │ Organization: [mahmoudashraf ▾]                            │  │
│  │ Repository:   [ecommerce-platform ▾]                      │  │
│  │ Branch:       [main ▾]                                    │  │
│  │                                                           │  │
│  │ Detected stack:                                           │  │
│  │ [Next.js] [Node.js] [PostgreSQL] [Docker] [Terraform]    │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ PRODUCT INFO                                              │  │
│  │                                                           │  │
│  │ Product name                                              │  │
│  │ ┌─────────────────────────────────────────────────────┐  │  │
│  │ │ E-Commerce Platform                                  │  │  │
│  │ └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │ Stage                                                     │  │
│  │ ○ Idea / Pre-build                                        │  │
│  │ ● Built but not production-ready                          │  │
│  │ ○ Running in production                                   │  │
│  │ ○ Production but needs improvement                        │  │
│  │                                                           │  │
│  │ Live/Staging URL (optional)                               │  │
│  │ ┌─────────────────────────────────────────────────────┐  │  │
│  │ │ https://staging.myecommerce.com                      │  │  │
│  │ └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ SCAN DEPTH                                                │  │
│  │                                                           │  │
│  │ ● L1: Safe static scan (Recommended)                      │  │
│  │   Inspects files, configs, dependencies, and manifests.   │  │
│  │   No code execution. Fast and safe.                       │  │
│  │                                                           │  │
│  │ ○ L2: Dependency-aware scan                               │  │
│  │   May install dependencies for deeper analysis.           │  │
│  │   Requires additional consent.                            │  │
│  │                                                           │  │
│  │ ○ L3: Include URL scan                                    │  │
│  │   Adds Lighthouse and ZAP baseline for your staging URL.  │  │
│  │   Requires domain authorization.                          │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ PERMISSIONS REQUESTED                                     │  │
│  │                                                           │  │
│  │ ✓ Read repository content (files, manifests, configs)     │  │
│  │ ✓ Read repository metadata (branches, commits)            │  │
│  │ ✗ No write access to your repository                      │  │
│  │ ✗ No access to other repositories                         │  │
│  │                                                           │  │
│  │ Source code is cloned temporarily and deleted after scan.  │  │
│  │ Only findings and evidence are retained.                  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│                                    [Back]  [Start First Scan →]  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Step 3: First Scan (live progress)**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Scanning: E-Commerce Platform                                   │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  ① Connect  →  ② Configure  →  ③ First Scan  →  Done     │  │
│  │  ✓━━━━━━━━━━━━━✓━━━━━━━━━━━━━━━●━━━━━━━━━━━━━━○           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                                                           │  │
│  │  Scan depth: L1 Safe Static Scan                          │  │
│  │  Branch: main                                             │  │
│  │  Started: 2 minutes ago                                   │  │
│  │                                                           │  │
│  │  Overall: ██████████████░░░░░░░░ 65%                      │  │
│  │                                                           │  │
│  │  ┌─────────────────────────────────────────────────────┐ │  │
│  │  │                                                     │ │  │
│  │  │  ✓  Gitleaks          Secrets scan          Done    │ │  │
│  │  │     Found: 2 exposed secrets                        │ │  │
│  │  │                                                     │ │  │
│  │  │  ✓  OSV-Scanner       Dependencies         Done    │ │  │
│  │  │     Found: 14 vulnerable dependencies               │ │  │
│  │  │                                                     │ │  │
│  │  │  ◉  Semgrep           Code security      Running   │ │  │
│  │  │     Scanning 247 files...                           │ │  │
│  │  │                                                     │ │  │
│  │  │  ◌  Trivy             Container/IaC       Queued   │ │  │
│  │  │     Waiting for Semgrep to complete                 │ │  │
│  │  │                                                     │ │  │
│  │  └─────────────────────────────────────────────────────┘ │  │
│  │                                                           │  │
│  │  Early findings (updating live):                          │  │
│  │                                                           │  │
│  │  ● 2 critical    ● 5 high    ● 7 medium                  │  │
│  │                                                           │  │
│  │                                            [Cancel Scan]  │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  💡 Your diagnosis report will be ready in about 1 minute.       │
│     We'll summarize findings and recommend services.             │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 3. Diagnosis Dashboard

**Route:** `studio.produs.com/products/{id}/diagnosis`

**Purpose:** The primary findings view. Shows what's wrong, how confident we are, and what services to consider.

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Products    E-Commerce Platform                    [Rescan]   │
│                                                                  │
│  [Overview]  [Diagnosis]  [Package]  [Workspace]  [Evidence]     │
│              ──────────                                          │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ HEALTH SCORE                                             │   │
│  │                                                          │   │
│  │  ┌────────┐                                              │   │
│  │  │        │                                              │   │
│  │  │   42   │  27 findings across 6 categories             │   │
│  │  │  /100  │  Last scan: 2h ago · L1 depth · 4 tools     │   │
│  │  │        │                                              │   │
│  │  └────────┘  3 critical issues must be resolved before   │   │
│  │              this product is production-ready.            │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ AI SUMMARY                                               │   │
│  │                                                          │   │
│  │  "This product has two exposed secrets in the            │   │
│  │   repository (one Stripe key, one database URL), 14      │   │
│  │   vulnerable dependencies including 3 with known         │   │
│  │   exploits, no CI/CD pipeline detected, and no           │   │
│  │   monitoring or backup configuration found.              │   │
│  │                                                          │   │
│  │   Recommended starting point: Security Hardening         │   │
│  │   package with CI/CD Foundation as a dependency."        │   │
│  │                                                          │   │
│  │  Confidence: ●●● High · Based on: 4 scanner results     │   │
│  │                                                          │   │
│  │  [Build Package from Recommendations →]                  │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ FINDINGS BY SEVERITY                                     │   │
│  │                                                          │   │
│  │ [All (27)] [Critical (3)] [High (7)] [Med (12)] [Low (5)]│   │
│  │                                                          │   │
│  │ Filter: [All Categories ▾] [All Sources ▾] [All Status ▾]│   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  ● CRITICAL                                              │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ Exposed Stripe secret key              🔒 Secrets  │ │   │
│  │  │                                                    │ │   │
│  │  │ src/config/payments.ts:42                          │ │   │
│  │  │ Secret type: Stripe secret key                     │ │   │
│  │  │ Fingerprint: sk_live_****9f3a                      │ │   │
│  │  │                                                    │ │   │
│  │  │ Source: Gitleaks · Confidence: ●●● High            │ │   │
│  │  │ Status: [New]                                      │ │   │
│  │  │                                                    │ │   │
│  │  │ → Recommends: Security Hardening, Secrets Mgmt     │ │   │
│  │  │                                                    │ │   │
│  │  │ [View Detail] [Accept Risk] [Mark False Positive]  │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ Exposed database connection string     🔒 Secrets  │ │   │
│  │  │                                                    │ │   │
│  │  │ .env.production:7                                  │ │   │
│  │  │ Secret type: PostgreSQL connection URL             │ │   │
│  │  │ Fingerprint: postgres://****@prod-db              │ │   │
│  │  │                                                    │ │   │
│  │  │ Source: Gitleaks · Confidence: ●●● High            │ │   │
│  │  │ Status: [New]                                      │ │   │
│  │  │                                                    │ │   │
│  │  │ → Recommends: Security Hardening, Cloud Config     │ │   │
│  │  │                                                    │ │   │
│  │  │ [View Detail] [Accept Risk] [Mark False Positive]  │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ Critical RCE in express <4.19.2       📦 Dependency│ │   │
│  │  │                                                    │ │   │
│  │  │ package.json · express@4.17.1                      │ │   │
│  │  │ CVE-2024-XXXX · CVSS 9.8                          │ │   │
│  │  │                                                    │ │   │
│  │  │ Source: OSV-Scanner · Confidence: ●●● High         │ │   │
│  │  │ Status: [New]                                      │ │   │
│  │  │                                                    │ │   │
│  │  │ → Recommends: Dependency Review, Security Patching │ │   │
│  │  │                                                    │ │   │
│  │  │ [View Detail] [Accept Risk] [Mark False Positive]  │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ● HIGH                                                  │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ SQL injection risk in user query       🛡 Code     │ │   │
│  │  │                                                    │ │   │
│  │  │ src/api/users.ts:89                               │ │   │
│  │  │ Unsanitized user input in database query          │ │   │
│  │  │                                                    │ │   │
│  │  │ Source: Semgrep · Confidence: ●●● High             │ │   │
│  │  │ Status: [New]                                      │ │   │
│  │  │                                                    │ │   │
│  │  │ [View Detail]                                      │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ... (more findings)                                     │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ FINDINGS BY CATEGORY                                     │   │
│  │                                                          │   │
│  │  🔒 Secrets           2 findings    ████░░░░░░ 2 crit   │   │
│  │  📦 Dependencies      14 findings   ██████████ 1 crit   │   │
│  │  🛡 Code Security     5 findings    ██████░░░░ 3 high   │   │
│  │  ☁ Cloud/Infra        3 findings    ████░░░░░░ 1 high   │   │
│  │  📊 Performance       2 findings    ██░░░░░░░░ 0 high   │   │
│  │  📝 Documentation     1 finding     █░░░░░░░░░ info     │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 4. Finding Detail

**Route:** `studio.produs.com/products/{id}/findings/{findingId}`

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Diagnosis    Finding Detail                                   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  ● CRITICAL    Exposed Stripe secret key                 │   │
│  │                                                          │   │
│  │  Status: [New]   Category: 🔒 Secrets                    │   │
│  │  Source: Gitleaks · Confidence: ●●● High                 │   │
│  │  First detected: 2h ago · Scan: #47                      │   │
│  │  Fingerprint: sk_live_****9f3a                           │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ LOCATION                                                 │   │
│  │                                                          │   │
│  │  File: src/config/payments.ts                            │   │
│  │  Line: 42                                                │   │
│  │                                                          │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │  40 │ const stripe = require('stripe');          │   │   │
│  │  │  41 │                                            │   │   │
│  │  │▶ 42 │ const client = stripe('sk_live_****');     │   │   │
│  │  │  43 │                                            │   │   │
│  │  │  44 │ module.exports = { client };               │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │  ⚠ Full secret value redacted for safety                 │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ AI EXPLANATION                                           │   │
│  │                                                          │   │
│  │  "A Stripe secret key is hardcoded in source code.       │   │
│  │   This key provides full access to your Stripe account   │   │
│  │   including payments, refunds, and customer data.        │   │
│  │   Anyone with repository access can extract this key.    │   │
│  │                                                          │   │
│  │   Immediate action: Rotate this key in the Stripe        │   │
│  │   dashboard. Move the key to a secrets manager or        │   │
│  │   environment variable. Consider scanning git history    │   │
│  │   for previously committed secrets."                     │   │
│  │                                                          │   │
│  │  Confidence: ●●● High                                    │   │
│  │  Basis: Direct scanner detection of Stripe key pattern   │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ SERVICE RECOMMENDATION                                   │   │
│  │                                                          │   │
│  │  This finding maps to:                                   │   │
│  │                                                          │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │ 🟣 Security Hardening                            │   │   │
│  │  │    → Secrets scan and rotation                   │   │   │
│  │  │    → Secrets management setup                    │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │ ☁ Cloud/DevOps Foundation                        │   │   │
│  │  │    → Environment configuration audit             │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │  [Add to Package →]                                      │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ SCAN HISTORY                                             │   │
│  │                                                          │   │
│  │  Scan #47 · Today 2:15pm     [New]        Gitleaks      │   │
│  │  (No previous scans for this product)                    │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ ACTIONS                                                  │   │
│  │                                                          │   │
│  │ [Accept Risk]  [Mark False Positive]  [Add to Package]   │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Accept Risk dialog:**

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│  Accept Risk                                             │
│                                                          │
│  You're accepting this critical finding without          │
│  fixing it. This will be recorded in your product's      │
│  evidence trail.                                         │
│                                                          │
│  Reason (required):                                      │
│  ┌──────────────────────────────────────────────────┐   │
│  │ This is a test key, not production. Rotating     │   │
│  │ after staging migration.                         │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  Review date:                                            │
│  ┌──────────────────────────┐                           │
│  │ 2026-06-17 (30 days) ▾  │                           │
│  └──────────────────────────┘                           │
│                                                          │
│  ⚠ Critical findings with accepted risk will be          │
│  flagged during milestone review and handoff.            │
│                                                          │
│                        [Cancel]  [Accept Risk]           │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

### 5. Package Builder

**Route:** `studio.produs.com/products/{id}/package/build`

**Purpose:** Turn diagnosis findings into a structured, dependency-aware package with milestones.

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Product    Build Package                                      │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ ① Services → ② Dependencies → ③ Milestones → ④ Review    │  │
│  │ ●━━━━━━━━━━━○━━━━━━━━━━━━━━━━━○━━━━━━━━━━━━━━○            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ RECOMMENDED SERVICES                                      │  │
│  │                                                           │  │
│  │ Based on 27 findings from your diagnosis:                 │  │
│  │                                                           │  │
│  │ ┌─────────────────────────────────────────────────────┐  │  │
│  │ │ ☑  🟣 Security Hardening             ★ Recommended  │  │  │
│  │ │                                                     │  │  │
│  │ │  Secrets scan & rotation, dependency patching,      │  │  │
│  │ │  code vulnerability remediation, auth review        │  │  │
│  │ │                                                     │  │  │
│  │ │  Addresses: 3 critical, 5 high findings             │  │  │
│  │ │  Confidence: ●●● High                               │  │  │
│  │ │  Budget band: $2,500 – $5,000                       │  │  │
│  │ │  Duration: 1 – 3 weeks                              │  │  │
│  │ └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │ ┌─────────────────────────────────────────────────────┐  │  │
│  │ │ ☑  🔵 CI/CD Foundation               ★ Recommended  │  │  │
│  │ │                                                     │  │  │
│  │ │  Pipeline setup, automated testing, staging deploy  │  │  │
│  │ │                                                     │  │  │
│  │ │  Addresses: No CI/CD detected (inference)           │  │  │
│  │ │  Confidence: ●●○ Medium (no pipeline files found)   │  │  │
│  │ │  Budget band: $1,500 – $3,000                       │  │  │
│  │ │  Duration: 1 – 2 weeks                              │  │  │
│  │ └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │ ┌─────────────────────────────────────────────────────┐  │  │
│  │ │ ☐  🟢 Monitoring & Alerting                         │  │  │
│  │ │                                                     │  │  │
│  │ │  APM, error tracking, uptime, log aggregation       │  │  │
│  │ │                                                     │  │  │
│  │ │  Addresses: No monitoring config detected            │  │  │
│  │ │  Confidence: ●○○ Low (absence of evidence)          │  │  │
│  │ │  Budget band: $1,000 – $2,500                       │  │  │
│  │ └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │ ┌─────────────────────────────────────────────────────┐  │  │
│  │ │ ☐  📝 Documentation & Handoff                       │  │  │
│  │ │                                                     │  │  │
│  │ │  Deployment docs, runbook, API docs, support setup  │  │  │
│  │ │                                                     │  │  │
│  │ │  Addresses: No documentation found                   │  │  │
│  │ │  Budget band: $800 – $1,500                          │  │  │
│  │ └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ BROWSE FULL CATALOG                                       │  │
│  │                                                           │  │
│  │ [Cloud/DevOps] [Database] [Scale] [Launch] [Support]     │  │
│  │ [View All Services →]                                     │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ PACKAGE SUMMARY                    2 services selected    │  │
│  │                                                           │  │
│  │ 🟣 Security Hardening   $2,500 – $5,000                  │  │
│  │ 🔵 CI/CD Foundation     $1,500 – $3,000                  │  │
│  │ ─────────────────────────────────────                     │  │
│  │ Estimated total:        $4,000 – $8,000                   │  │
│  │ Estimated duration:     2 – 4 weeks                       │  │
│  │                                                           │  │
│  │                            [Back]  [Check Dependencies →] │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Step 2: Dependency check**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ DEPENDENCY CHECK                                          │  │
│  │                                                           │  │
│  │ ✓ Security Hardening → CI/CD Foundation                   │  │
│  │   "Security scanning should run in CI. CI/CD is included."│  │
│  │                                                           │  │
│  │ ⚠ Security Hardening → Monitoring (not selected)          │  │
│  │   "Without monitoring, you won't detect new              │  │
│  │    vulnerabilities in production after hardening."        │  │
│  │                                                           │  │
│  │   [Add Monitoring]  [Accept Risk]                         │  │
│  │                                                           │  │
│  │ ⚠ CI/CD Foundation → Staging Environment (not detected)   │  │
│  │   "CI/CD needs a staging target. If you have one,        │  │
│  │    confirm below. Otherwise, add Cloud setup."           │  │
│  │                                                           │  │
│  │   [I have staging]  [Add Cloud Setup]                    │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│                              [Back]  [Continue to Milestones →]  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 6. Milestone Review

**Route:** `studio.produs.com/workspaces/{id}/milestones/{milestoneId}/review`

**Purpose:** Evidence-based review with automated checks, AI comparison, and human decision.

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Workspace    Review: M1 Security Hardening                    │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ ACCEPTANCE STATUS                           4/5 criteria  │   │
│  │                                                          │   │
│  │ ████████████████████░░░░░░ 80%                           │   │
│  │                                                          │   │
│  │ Team submitted this milestone for review 3h ago.          │   │
│  │ Automated checks have run. AI comparison complete.        │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ CRITERIA & EVIDENCE                                      │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ ✓ LIKELY PASS  No exposed secrets remain           │ │   │
│  │  │                                                    │ │   │
│  │  │ Check: Automated (Gitleaks rescan)                 │ │   │
│  │  │ Result: 0 secrets detected (was 2)                 │ │   │
│  │  │ Evidence: Scan #52 results attached                │ │   │
│  │  │                                                    │ │   │
│  │  │ AI note: "Both previously detected secrets have    │ │   │
│  │  │ been removed. No new secrets found. Gitleaks ran   │ │   │
│  │  │ against latest commit on main branch."             │ │   │
│  │  │                                                    │ │   │
│  │  │ Confidence: ●●● High                               │ │   │
│  │  │                                                    │ │   │
│  │  │ [View Scan Report]                                 │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ ✓ LIKELY PASS  Critical dependencies patched       │ │   │
│  │  │                                                    │ │   │
│  │  │ Check: Automated (OSV-Scanner rescan)              │ │   │
│  │  │ Result: 0 critical, 2 moderate (was 3 critical)    │ │   │
│  │  │ Evidence: Scan #52 + PR #47 (dependency update)    │ │   │
│  │  │                                                    │ │   │
│  │  │ Confidence: ●●● High                               │ │   │
│  │  │                                                    │ │   │
│  │  │ [View Scan Report]  [View PR #47]                  │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ ✓ LIKELY PASS  Code vulnerabilities remediated     │ │   │
│  │  │                                                    │ │   │
│  │  │ Check: Automated (Semgrep rescan)                  │ │   │
│  │  │ Result: 0 high/critical findings (was 3 high)      │ │   │
│  │  │ Evidence: Scan #52 + PR #48, #49                   │ │   │
│  │  │                                                    │ │   │
│  │  │ Confidence: ●●● High                               │ │   │
│  │  │                                                    │ │   │
│  │  │ [View Scan Report]  [View PRs]                     │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ ✓ LIKELY PASS  Auth/RBAC reviewed                  │ │   │
│  │  │                                                    │ │   │
│  │  │ Check: Human review required                       │ │   │
│  │  │ Evidence: Role matrix document uploaded             │ │   │
│  │  │                                                    │ │   │
│  │  │ AI note: "Document covers admin, user, and guest   │ │   │
│  │  │ roles with endpoint-level permissions. Matches     │ │   │
│  │  │ acceptance criteria structure."                     │ │   │
│  │  │                                                    │ │   │
│  │  │ Confidence: ●●○ Medium (document review needed)    │ │   │
│  │  │                                                    │ │   │
│  │  │ [View Document]                                    │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ ✗ INSUFFICIENT  Runtime scan clean                 │ │   │
│  │  │                                                    │ │   │
│  │  │ Check: Automated (ZAP baseline)                    │ │   │
│  │  │ Result: NOT RUN — no staging URL authorized        │ │   │
│  │  │                                                    │ │   │
│  │  │ AI note: "This criterion requires a ZAP baseline   │ │   │
│  │  │ scan against the staging URL. No URL has been      │ │   │
│  │  │ authorized for runtime scanning."                  │ │   │
│  │  │                                                    │ │   │
│  │  │ [Authorize Staging URL]  [Accept Risk]             │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ BEFORE / AFTER COMPARISON                                │   │
│  │                                                          │   │
│  │           Before (Scan #47)      After (Scan #52)        │   │
│  │  Secrets:     2 critical      →     0                    │   │
│  │  Dependencies: 3 critical     →     0 critical           │   │
│  │  Code vulns:  3 high          →     0 high               │   │
│  │  Total:       27 findings     →     9 findings           │   │
│  │  Health:      42/100          →     74/100               │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ YOUR DECISION                                            │   │
│  │                                                          │   │
│  │ ○ Accept milestone (release payment for M1)              │   │
│  │ ○ Accept with conditions                                 │   │
│  │ ○ Request revision                                       │   │
│  │                                                          │   │
│  │ Note (optional):                                         │   │
│  │ ┌──────────────────────────────────────────────────┐    │   │
│  │ │                                                  │    │   │
│  │ └──────────────────────────────────────────────────┘    │   │
│  │                                                          │   │
│  │ ⚠ 1 criterion has insufficient evidence. You can still   │   │
│  │   accept if you're satisfied with the other criteria.    │   │
│  │                                                          │   │
│  │                                   [Submit Decision]       │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 7. Evidence Center

**Route:** `studio.produs.com/products/{id}/evidence`

**Purpose:** Central view of all evidence artifacts for a product across all scans, milestones, and reviews.

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Product    Evidence Center                    [Upload Evidence]│
│                                                                  │
│  [Overview]  [Diagnosis]  [Package]  [Workspace]  [Evidence]     │
│                                                                  ──────────│
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ FILTERS                                                  │   │
│  │                                                          │   │
│  │ Type: [All ▾]  Source: [All ▾]  Milestone: [All ▾]       │   │
│  │ Status: [All ▾]                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  SCAN RESULTS                                            │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ 📊 Scan #52 — Full L1 rescan                       │ │   │
│  │  │    Today 1:30pm · 4 tools · 9 findings             │ │   │
│  │  │    Milestone: M1 Security Hardening                │ │   │
│  │  │    Status: ✓ Verified                              │ │   │
│  │  │                                    [View Report]    │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ 📊 Scan #47 — Initial diagnosis scan               │ │   │
│  │  │    Yesterday 2:15pm · 4 tools · 27 findings        │ │   │
│  │  │    Milestone: —                                    │ │   │
│  │  │    Status: ✓ Baseline                              │ │   │
│  │  │                                    [View Report]    │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  TEAM SUBMISSIONS                                        │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ 🔀 PR #47 — Rotate secrets, add env management     │ │   │
│  │  │    Submitted by: DevCraft Solutions                 │ │   │
│  │  │    Milestone: M1 · Criterion: Secret rotation      │ │   │
│  │  │    Source: GitHub                                   │ │   │
│  │  │                                    [View on GitHub] │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ 🔀 PR #48 — Patch express and 12 dependencies      │ │   │
│  │  │    Submitted by: DevCraft Solutions                 │ │   │
│  │  │    Milestone: M1 · Criterion: Dependency patching  │ │   │
│  │  │    Source: GitHub                                   │ │   │
│  │  │                                    [View on GitHub] │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ 📄 RBAC Role Matrix Document                        │ │   │
│  │  │    Uploaded by: Sarah Kim (DevCraft)                │ │   │
│  │  │    Milestone: M1 · Criterion: Auth/RBAC review     │ │   │
│  │  │    Source: Manual upload                            │ │   │
│  │  │                                    [View Document]  │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ 🔀 PR #49 — Fix SQL injection in user query        │ │   │
│  │  │    Submitted by: DevCraft Solutions                 │ │   │
│  │  │    Milestone: M1 · Criterion: Code vuln fix        │ │   │
│  │  │    Source: GitHub                                   │ │   │
│  │  │                                    [View on GitHub] │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Upload Evidence dialog:**

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│  Upload Evidence                                         │
│                                                          │
│  Milestone: [M1 Security Hardening ▾]                    │
│  Criterion: [Auth/RBAC reviewed ▾]                       │
│                                                          │
│  Evidence type:                                          │
│  ○ Pull request URL                                      │
│  ○ CI run URL                                            │
│  ● Document / screenshot                                 │
│  ○ Deployment log URL                                    │
│  ○ Runbook                                               │
│  ○ Manual note                                           │
│                                                          │
│  File:                                                   │
│  ┌──────────────────────────────────────────────────┐   │
│  │         Drop file here or click to browse        │   │
│  │         PDF, PNG, JPG, MD, TXT (max 10MB)        │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  Summary:                                                │
│  ┌──────────────────────────────────────────────────┐   │
│  │ RBAC role matrix covering admin, user, and       │   │
│  │ guest roles with endpoint-level permissions.     │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│                            [Cancel]  [Upload Evidence]   │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

### 8. Workspace Overview

**Route:** `studio.produs.com/workspaces/{id}`

**Purpose:** Active project view with milestone progress, scan status, team, and activity.

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Workspaces    E-Commerce Platform × DevCraft       [Actions ▾]│
│                                                                  │
│  [Overview]  [Milestones]  [Evidence]  [Messages]  [Handoff]     │
│  ─────────                                                       │
│                                                                  │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐         │
│  │ Progress      │ │ Budget        │ │ Health        │         │
│  │               │ │               │ │               │         │
│  │ ██████░░ 50%  │ │ $2,800 /      │ │  42 → 74     │         │
│  │ 2/4 milestones│ │ $5,600 total  │ │  (+32 pts)   │         │
│  │               │ │ 50% released  │ │               │         │
│  └───────────────┘ └───────────────┘ └───────────────┘         │
│                                                                  │
│  ┌───────────────┐ ┌───────────────┐                            │
│  │ Timeline      │ │ Team          │                            │
│  │               │ │               │                            │
│  │ Started: 3 May│ │ DevCraft      │                            │
│  │ Due: 31 May   │ │ Solutions     │                            │
│  │ On track      │ │ 3 members     │                            │
│  │               │ │ [Message →]   │                            │
│  └───────────────┘ └───────────────┘                            │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ MILESTONES                                               │   │
│  │                                                          │   │
│  │  ✓  M1: Security Hardening              Accepted        │   │
│  │      5/5 criteria met · Scan #52 verified                │   │
│  │      Payment: $2,800 released                            │   │
│  │                                                          │   │
│  │  ◷  M2: CI/CD Foundation                In Review       │   │
│  │      Team submitted 1h ago                               │   │
│  │      3/4 criteria checked · 1 pending                    │   │
│  │      [Review Milestone →]                                │   │
│  │                                                          │   │
│  │  ○  M3: Monitoring Setup                Not Started     │   │
│  │      Depends on: M2 (CI/CD for deployment pipeline)      │   │
│  │                                                          │   │
│  │  ○  M4: Documentation & Handoff         Not Started     │   │
│  │      Depends on: M1, M2, M3                              │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ SCAN STATUS                                              │   │
│  │                                                          │   │
│  │  Latest: Scan #52       L1 · 4 tools · Completed         │   │
│  │                                                          │   │
│  │  Findings trend:                                          │   │
│  │                                                          │   │
│  │  Scan #47 (intake)   ████████████████████████████  27     │   │
│  │  Scan #50 (rescan)   ████████████████░░░░░░░░░░░  16     │   │
│  │  Scan #52 (rescan)   █████████░░░░░░░░░░░░░░░░░   9     │   │
│  │                                                          │   │
│  │                              [Run New Scan]  [History]   │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ RECENT ACTIVITY                                          │   │
│  │                                                          │   │
│  │  ● 1h   Team submitted M2 for review                    │   │
│  │  ● 3h   Scan #52 completed (M1 rescan)                  │   │
│  │  ● 3h   You accepted M1 Security Hardening              │   │
│  │  ○ 1d   Team pushed PR #51 (CI pipeline config)         │   │
│  │  ○ 2d   Team pushed PR #50 (GitHub Actions setup)       │   │
│  │  ○ 5d   You accepted M1 submission                      │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 9. Handoff Readiness

**Route:** `studio.produs.com/workspaces/{id}/handoff`

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Workspace    Handoff Readiness                                │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ READINESS SCORE                         6/8 verified      │   │
│  │                                                          │   │
│  │ ████████████████████████░░░░░░ 75%                       │   │
│  │                                                          │   │
│  │ 2 items need attention before handoff approval.           │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ SECURITY                                                 │   │
│  │                                                          │   │
│  │ ✓  No critical secrets in latest scan                    │   │
│  │    Evidence: Scan #52 · Gitleaks · 0 secrets             │   │
│  │                                                          │   │
│  │ ✓  No critical dependencies                              │   │
│  │    Evidence: Scan #52 · OSV-Scanner · 0 critical         │   │
│  │                                                          │   │
│  │ ✓  No high code vulnerabilities                          │   │
│  │    Evidence: Scan #52 · Semgrep · 0 high/critical        │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ INFRASTRUCTURE                                           │   │
│  │                                                          │   │
│  │ ✓  CI/CD pipeline operational                            │   │
│  │    Evidence: GitHub Actions · Last run passing            │   │
│  │                                                          │   │
│  │ ✓  Monitoring configured                                 │   │
│  │    Evidence: Datadog dashboard screenshot attached        │   │
│  │                                                          │   │
│  │ ✗  Backup strategy not verified                          │   │
│  │    Missing: No backup evidence or restore test found     │   │
│  │    → Team needs to provide backup log and restore test   │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ DOCUMENTATION                                            │   │
│  │                                                          │   │
│  │ ✓  Deployment documentation exists                       │   │
│  │    Evidence: deployment-guide.md in repository            │   │
│  │                                                          │   │
│  │ ✗  Runbook not found                                     │   │
│  │    Missing: No operational runbook uploaded               │   │
│  │    → Team needs to provide operational runbook           │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ AI HANDOFF SUMMARY                                       │   │
│  │                                                          │   │
│  │ "Security posture improved significantly. CI/CD and      │   │
│  │  monitoring are operational. Two items remain: backup     │   │
│  │  verification and operational runbook. Both are           │   │
│  │  standard handoff requirements. Recommend requesting     │   │
│  │  these from the team before approving handoff."          │   │
│  │                                                          │   │
│  │ Confidence: ●●● High                                     │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │ [Approve Handoff]  (disabled — 2 items missing)          │   │
│  │                                                          │   │
│  │ [Request Remaining Items from Team]                      │   │
│  │                                                          │   │
│  │ [Request Support Package]                                │   │
│  │ Ongoing operations support for items not yet met.        │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 10. Admin Integration Health

**Route:** `studio.produs.com/admin/integrations`

**Purpose:** Platform admin view of scanner health, queue status, and integration errors.

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Integration Health                                     Admin    │
│                                                                  │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐         │
│  │ Queue         │ │ Workers       │ │ Scan Success  │         │
│  │               │ │               │ │               │         │
│  │ 3 jobs        │ │ 4/5 active    │ │ 97.2%         │         │
│  │ 1 running     │ │ 1 idle        │ │ Last 24h      │         │
│  │ 2 queued      │ │               │ │               │         │
│  └───────────────┘ └───────────────┘ └───────────────┘         │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ SCANNER STATUS                                           │   │
│  │                                                          │   │
│  │  Tool          Version    Status    Success   Avg Time   │   │
│  │  ─────────────────────────────────────────────────────   │   │
│  │  Gitleaks      8.18.1     ● OK      99.1%     12s       │   │
│  │  Semgrep       1.67.0     ● OK      96.8%     45s       │   │
│  │  OSV-Scanner   1.7.1      ● OK      98.5%     8s        │   │
│  │  Trivy         0.51.0     ● OK      95.2%     32s       │   │
│  │  Checkov       3.2.0      ● OK      94.1%     28s       │   │
│  │  Lighthouse    12.0.0     ⚠ Slow    91.0%     120s      │   │
│  │  ZAP           2.15.0     ● OK      89.3%     90s       │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ RECENT FAILURES                                          │   │
│  │                                                          │   │
│  │  ✗ Scan #54 · Trivy · 1h ago                            │   │
│  │    Error: Container timeout (>300s)                      │   │
│  │    Product: Mobile Banking App                           │   │
│  │    [View Logs]  [Retry]                                  │   │
│  │                                                          │   │
│  │  ✗ Scan #51 · Lighthouse · 5h ago                       │   │
│  │    Error: URL unreachable (staging down)                 │   │
│  │    Product: E-Commerce Platform                          │   │
│  │    [View Logs]                                           │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ INTEGRATIONS                                             │   │
│  │                                                          │   │
│  │  GitHub App      ● Connected     12 repos    [Manage]    │   │
│  │  Snyk            ○ Not connected             [Connect]   │   │
│  │  SonarQube       ○ Not connected             [Connect]   │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ AUDIT LOG (Recent)                                       │   │
│  │                                                          │   │
│  │  1h ago   Scan #54 started    Admin: system              │   │
│  │  1h ago   Repo cloned         Product: Mobile Banking    │   │
│  │  1h ago   Trivy failed        Timeout exceeded           │   │
│  │  1h ago   Repo clone deleted  Product: Mobile Banking    │   │
│  │  3h ago   Scan #52 completed  Product: E-Commerce        │   │
│  │  3h ago   AI summary created  Findings: 9                │   │
│  │                                                          │   │
│  │  [View Full Audit Log →]                                 │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 11. CI Evidence Upload (Team View)

**Route:** `studio.produs.com/workspaces/{id}/evidence/upload`

**Purpose:** For teams who run scans in their own CI and upload results.

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Workspace    Upload CI Evidence                               │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  Upload scan results from your CI/CD pipeline            │   │
│  │                                                          │   │
│  │  Your source code stays in your environment. Upload      │   │
│  │  only the scan outputs (SARIF, JSON, logs).              │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ UPLOAD SCAN RESULTS                                      │   │
│  │                                                          │   │
│  │  Tool: [Select tool ▾]                                   │   │
│  │        Gitleaks / Semgrep / OSV / Trivy / Other          │   │
│  │                                                          │   │
│  │  Format: [Auto-detect ▾]                                 │   │
│  │          SARIF / JSON / Text / HTML                       │   │
│  │                                                          │   │
│  │  Branch: [main ▾]                                        │   │
│  │  Commit: [auto-detect from file or enter manually]       │   │
│  │                                                          │   │
│  │  File:                                                   │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │         Drop SARIF/JSON file here                │   │   │
│  │  │         or click to browse                       │   │   │
│  │  │         Max 50MB per file                        │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │                                  [Upload & Process]      │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ CI INTEGRATION SNIPPETS                                  │   │
│  │                                                          │   │
│  │  [GitHub Actions]  [GitLab CI]  [Manual Upload]          │   │
│  │                                                          │   │
│  │  Add this step to your GitHub Actions workflow:           │   │
│  │                                                          │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │  - name: Upload to ProdOps                       │   │   │
│  │  │    uses: prodops/ci-upload@v1                     │   │   │
│  │  │    with:                                         │   │   │
│  │  │      api-key: ${{ secrets.PRODOPS_API_KEY }}      │   │   │
│  │  │      product-id: prod_abc123                      │   │   │
│  │  │      files: |                                    │   │   │
│  │  │        gitleaks-report.sarif                      │   │   │
│  │  │        semgrep-results.json                       │   │   │
│  │  │        osv-scanner-results.json                   │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │  [Copy Snippet]                                          │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ PREVIOUS UPLOADS                                         │   │
│  │                                                          │   │
│  │  Gitleaks SARIF · Today 11:00am · 0 findings     ✓      │   │
│  │  Semgrep JSON · Today 11:00am · 2 findings       ✓      │   │
│  │  OSV JSON · Today 11:01am · 4 findings           ✓      │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Scan Report Modal (Shared Component)

Used from diagnosis, evidence center, and milestone review when clicking "View Report":

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Scan Report #52                                         [×]     │
│                                                                  │
│  Product: E-Commerce Platform                                    │
│  Branch: main · Commit: a3f7e2d                                  │
│  Depth: L1 Safe Static · Duration: 1m 47s                        │
│  Completed: Today 1:32pm                                         │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ TOOL RESULTS                                             │   │
│  │                                                          │   │
│  │  ✓ Gitleaks        12s     0 findings (was 2)            │   │
│  │  ✓ OSV-Scanner     8s      4 findings (was 14)           │   │
│  │  ✓ Semgrep         45s     3 findings (was 5)            │   │
│  │  ✓ Trivy           32s     2 findings (was 6)            │   │
│  │                                                          │   │
│  │  Total: 9 findings (was 27) · 18 resolved                │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ FINDING BREAKDOWN                                        │   │
│  │                                                          │   │
│  │  0 critical (was 3)   ↓                                  │   │
│  │  2 high (was 7)       ↓                                  │   │
│  │  5 medium (was 12)    ↓                                  │   │
│  │  2 low (was 5)        ↓                                  │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  [Download Raw Output]  [View All Findings]                      │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Rescan Comparison View

Shown when viewing diagnosis after a rescan, comparing against previous baseline:

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  RESCAN COMPARISON     Scan #47 → Scan #52                       │
│                                                                  │
│  ┌────────────────────────────┐ ┌────────────────────────────┐  │
│  │ BEFORE (Scan #47)          │ │ AFTER (Scan #52)           │  │
│  │ Yesterday 2:15pm           │ │ Today 1:32pm               │  │
│  │                            │ │                            │  │
│  │ Health: 42/100             │ │ Health: 74/100             │  │
│  │                            │ │                            │  │
│  │ ● 3 critical               │ │ ● 0 critical     ↓ -3     │  │
│  │ ● 7 high                   │ │ ● 2 high         ↓ -5     │  │
│  │ ● 12 medium                │ │ ● 5 medium       ↓ -7     │  │
│  │ ● 5 low                    │ │ ● 2 low          ↓ -3     │  │
│  │                            │ │                            │  │
│  │ Total: 27                  │ │ Total: 9                   │  │
│  └────────────────────────────┘ └────────────────────────────┘  │
│                                                                  │
│  CHANGES                                                         │
│                                                                  │
│  18 resolved    0 regressed    0 new    9 remaining              │
│                                                                  │
│  [View Resolved]  [View Remaining]                               │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Mobile Adaptations

### Mobile Diagnosis (Phone)

```
┌──────────────────────────────────────┐
│ ← E-Commerce Platform       [Rescan]│
├──────────────────────────────────────┤
│                                      │
│  Health: 42/100                      │
│  ████████░░░░░░░░░░ 42%              │
│                                      │
│  [3 Crit] [7 High] [12 Med] [5 Low] │
│                                      │
├──────────────────────────────────────┤
│ AI Summary                           │
│ "Two exposed secrets, 14 vulnerable  │
│  dependencies, no CI/CD..."          │
│ [Read more]                          │
├──────────────────────────────────────┤
│ [All] [Critical] [High] [Med] [Low] │
├──────────────────────────────────────┤
│ ● CRITICAL                           │
│                                      │
│ ┌────────────────────────────────┐  │
│ │ 🔒 Exposed Stripe secret key  │  │
│ │ src/config/payments.ts:42     │  │
│ │ Gitleaks · ●●● High          │  │
│ │ [New]              [View →]   │  │
│ └────────────────────────────────┘  │
│                                      │
│ ┌────────────────────────────────┐  │
│ │ 🔒 Exposed database URL       │  │
│ │ .env.production:7             │  │
│ │ Gitleaks · ●●● High          │  │
│ │ [New]              [View →]   │  │
│ └────────────────────────────────┘  │
│                                      │
└──────────────────────────────────────┘
```

### Mobile Milestone Review

```
┌──────────────────────────────────────┐
│ ← M1 Security Hardening             │
├──────────────────────────────────────┤
│                                      │
│  Acceptance: 4/5 criteria            │
│  ████████████████████░░░░░ 80%       │
│                                      │
├──────────────────────────────────────┤
│                                      │
│ ┌────────────────────────────────┐  │
│ │ ✓ No exposed secrets           │  │
│ │ Gitleaks rescan · 0 found      │  │
│ │ ●●● High          [Evidence]   │  │
│ └────────────────────────────────┘  │
│                                      │
│ ┌────────────────────────────────┐  │
│ │ ✓ Dependencies patched         │  │
│ │ OSV rescan · 0 critical        │  │
│ │ ●●● High          [Evidence]   │  │
│ └────────────────────────────────┘  │
│                                      │
│ ┌────────────────────────────────┐  │
│ │ ✗ Runtime scan not run         │  │
│ │ No staging URL authorized      │  │
│ │ [Authorize URL] [Accept Risk]  │  │
│ └────────────────────────────────┘  │
│                                      │
├──────────────────────────────────────┤
│ DECISION                             │
│ ○ Accept  ○ Conditions  ○ Revise    │
│                                      │
│ [Submit Decision]                    │
└──────────────────────────────────────┘
```

---

## Studio Sidebar Navigation

```
┌──────────────────────┐
│                      │
│  STUDIO              │
│  ┄┄┄┄┄┄┄┄┄┄┄┄┄┄     │
│  Dashboard           │
│  Products        (2) │
│  Workspaces      (1) │
│                      │
│  TOOLS               │
│  ┄┄┄┄┄┄┄┄┄┄┄┄┄┄     │
│  Integrations        │
│  Service Catalog     │
│                      │
│  ADMIN               │
│  ┄┄┄┄┄┄┄┄┄┄┄┄┄┄     │
│  Integration Health  │
│  Audit Log           │
│                      │
│  ───────────         │
│  Settings            │
│                      │
└──────────────────────┘
```

---

## Key Interaction Patterns

### Severity chips

```
┌───────────┐  ┌────────────┐  ┌──────────┐  ┌─────────┐  ┌──────────┐
│ ● Critical│  │ ● High     │  │ ● Medium │  │ ● Low   │  │ ● Info   │
│   #dc2626 │  │   #ea580c  │  │  #d97706 │  │  #2563eb│  │  #64748b │
│   on      │  │   on       │  │  on      │  │  on     │  │  on      │
│   #fef2f2 │  │   #fff7ed  │  │  #fffbeb │  │  #eff6ff│  │  #f8fafc │
└───────────┘  └────────────┘  └──────────┘  └─────────┘  └──────────┘
```

### Finding status transitions (animated)

```
[New] ─→ [Open] ─→ [Resolved] ✓
                ─→ [Accepted Risk] ⚠
                ─→ [False Positive] ○
[Resolved] ─→ [Regressed] ✗  (rescan found it again)
```

Status chip changes animate with a brief color fade transition (0.3s).

### Scan progress animation

```
Tool line:  ◌  Queued       (gray, static)
            ◉  Running      (indigo, pulse animation)
            ✓  Completed    (green, fade-in)
            ✗  Failed       (red, fade-in)

Overall bar: Smooth width transition as tools complete
```

### Health score gauge

```
┌────────┐
│        │
│   42   │   ← Large number, color coded:
│  /100  │      0-30: red
│        │      31-60: amber
└────────┘      61-80: blue
                81-100: green

Ring or semi-circle gauge with colored arc
```

### Before/After comparison

Side-by-side cards with directional arrows (↓ for improvement, ↑ for regression). Numbers animate count-up on scroll-into-view. Resolved items fade from red to green.

---

## Empty States

### No Products

```
┌──────────────────────────────────────────────────────┐
│                                                      │
│          ┌───────────────┐                          │
│          │   📦          │                          │
│          └───────────────┘                          │
│                                                      │
│          No products connected yet                   │
│                                                      │
│          Connect a GitHub repo or enter a URL         │
│          to get your first evidence-based diagnosis.  │
│                                                      │
│          [Connect Your First Product]                │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### No Findings (Clean Scan)

```
┌──────────────────────────────────────────────────────┐
│                                                      │
│          ┌───────────────┐                          │
│          │   ✓           │                          │
│          └───────────────┘                          │
│                                                      │
│          No critical or high findings detected       │
│                                                      │
│          Your product passed the L1 safe static      │
│          scan with only minor issues.                │
│                                                      │
│          This doesn't mean the product is secure.    │
│          Consider a deeper scan or runtime check.    │
│                                                      │
│          [Run L2 Scan]  [Add URL for L3 Scan]       │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### No Evidence Submitted

```
┌──────────────────────────────────────────────────────┐
│                                                      │
│          ┌───────────────┐                          │
│          │   📎          │                          │
│          └───────────────┘                          │
│                                                      │
│          No evidence submitted yet                   │
│                                                      │
│          Teams submit evidence as they complete       │
│          milestone criteria: PRs, scan results,      │
│          screenshots, deployment logs, and docs.     │
│                                                      │
│          Waiting for team to begin delivery.         │
│                                                      │
└──────────────────────────────────────────────────────┘
```
