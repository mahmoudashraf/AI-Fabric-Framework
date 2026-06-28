# AI Fabric Framework Public Repo Separation and Release Plan

Status: execution in progress; runtime/connector boundary corrected on 2026-06-10

Created: 2026-06-08

Purpose: separate AI Fabric Framework into a clean public open-source repository while keeping LoomAI Platform, Shopify Companion, Partner UI, deployment operations, customer handoff material, and commercial product code private.

This plan is deliberately practical. It defines the cleanup, extraction, verification, release, and private-product integration steps needed before the first public framework release.

---

## 1) Target Outcome

Create a public framework repo:

```text
Loom-AI-Labs/ai-fabric-framework
```

Keep the private product repo:

```text
TheBaseRepo
```

Recommended local layout:

```text
/Users/mahmoudashraf/Downloads/Projects/
  ai-fabric-framework/        public framework repo
  TheBaseRepo/                private product/platform repo
```

Public framework repo contains:

- reusable framework code
- provider modules
- vector modules
- curated generic packs
- public examples
- public docs
- public CI
- Maven release workflow

Private product repo contains:

- LoomAI Platform backend/UI
- Partner UI
- Shopify Bridge and Shopify Companion product code
- Coolify/Hetzner/platform deployment operations
- deployable runtime service
- deployable generic REST connector service
- private handoff/context files
- commercial product strategy and roadmap
- customer-specific configuration
- live deployment and auth material

---

## 2) Non-Negotiable Boundary Rules

1. The current private repo must not be made public.
2. The public repo must not include private product, ops, customer, rollout, or deployment material.
3. The public framework must be useful as a framework, not a crippled demo.
4. The platform must consume the framework through Maven, not through copied product/framework code tangles.
5. Development can stay near through sibling repos and local Maven install.
6. The first public release should be a preview release, not a stable 1.0 claim.
7. Public docs must not claim unsupported product/platform capabilities.
8. Public source history must not expose private files. Prefer a clean fresh public repo over publishing filtered monorepo history for the first release.

---

## 3) Recommended License Direction

The user direction is now fully open-source framework.

Recommended first license:

```text
Apache License 2.0
```

Why:

- already referenced by the current parent POM
- permissive for commercial use
- patent grant is useful for infrastructure/framework code
- friendly to enterprise adoption

Required cleanup before public release:

- replace the current abbreviated `LICENSE` with the full Apache 2.0 license text
- remove dual-license / enterprise-license wording from the public framework POM
- remove or rewrite internal docs that discuss license keys, paid framework tiers, or commercial license gates inside the public framework
- keep commercial platform licensing strategy private

Do not publish with conflicting `MIT`, `Apache 2.0`, and proprietary-enterprise signals mixed together.

---

## 4) First Public Repo Contents

Start with this public set:

```text
ai-infrastructure-module/
README.md
LICENSE
CONTRIBUTING.md
SECURITY.md
CODE_OF_CONDUCT.md
.github/workflows/
examples/
```

Framework modules intended for first release:

- `ai-infrastructure-core`
- `ai-infrastructure-actions-connector`
- `ai-infrastructure-actions-registry`
- `ai-infrastructure-actions-registry-liquibase`
- `ai-infrastructure-retrieval-connector`
- `ai-infrastructure-data-sync`
- `ai-infrastructure-relay`
- `ai-infrastructure-indexing`
- `ai-infrastructure-pii`
- `ai-infrastructure-governance`
- `ai-fabric-starter`
- `ai-fabric-provider-starter`
- `curated/ai-curated-default`
- `curated/ai-curated-commerce`
- `curated/ai-curated-support`
- `ai-infrastructure-rag`
- `ai-infrastructure-web`
- `providers/*`
- `ai-infrastructure-relationship-query`
- `ai-infrastructure-chat-session`
- `ai-infrastructure-behavior`
- `victor-databases/*`
- `ai-infrastructure-migration`

Exclude from public source release unless deliberately rewritten:

- `ai-fabric-runtime` deployable service
- `ai-infrastructure-generic-rest-connector` deployable service
- `integration-Testing/*` if it depends on private environment, live credentials, or noisy internal assumptions
- private product docs
- private rollout docs
- platform/Shopify/ProdUS/Coolify/Hetzner docs
- any LLM handoff docs
- any `.env`, secret, token, key, or private operator material

---

## 5) Detailed Execution Plan

### Phase 0 - Freeze The Boundary

Goal: avoid moving unstable or private code into the public repo by accident.

Tasks:

- [ ] Keep current repo private.
- [ ] Do not create a public GitHub Release from the current private repo.
- [ ] Decide public repo name: recommended `ai-fabric-framework`.
- [ ] Decide public license: recommended Apache 2.0.
- [ ] Decide first version: recommended `0.1.0-preview`.
- [ ] Keep `Platform-V10` product branch private.
- [ ] Create a temporary release branch in the private repo, for example `framework-public-extraction-prep`.

Commands:

```bash
git checkout -b framework-public-extraction-prep
git status --short
```

Owner input needed:

- confirm repo name
- confirm license
- create public GitHub repo or approve Codex to create it if GitHub auth supports it

### Phase 1 - Inventory Public vs Private Material

Goal: classify every file that could enter the public repo.

Tasks:

- [ ] Generate file inventory for framework source.
- [ ] Identify docs under `ai-infrastructure-module/docs` that contain commercial/private/product-specific language.
- [ ] Identify package names and public API names that leak LoomAI Platform, Shopify, ProdUS, Coolify, Railway, Hetzner, or customer-specific assumptions.
- [ ] Identify runtime config examples that include private domains, deployment IDs, or platform operator URLs.
- [ ] Identify bundled model/assets and confirm they are legally distributable.

Commands:

```bash
rg --files ai-infrastructure-module | sort > /tmp/ai-fabric-framework-file-inventory.txt
rg -n "LoomAI|Shopify|ProdUS|Coolify|Railway|Hetzner|sslip|loomai\\.pro|token|secret|api[_-]?key|private|handoff|billing|license key|enterprise license" ai-infrastructure-module README.md LICENSE
```

Completion criteria:

- file inventory exists
- private/product references are either removed, rewritten, or intentionally documented as generic examples

### Phase 2 - Clean Licensing and Public Metadata

Goal: make public licensing coherent.

Tasks:

- [ ] Replace root `LICENSE` in public repo with full Apache 2.0 text.
- [ ] Update `ai-infrastructure-module/pom.xml` license section to Apache 2.0 only.
- [ ] Remove enterprise/proprietary license entry from the framework POM.
- [ ] Fix README badge/license language.
- [ ] Fix README Java version from `Java 17+` to Java 21 if the POM remains Java 21.
- [ ] Add public `NOTICE` only if needed.
- [ ] Add public `SECURITY.md`.
- [ ] Add public `CONTRIBUTING.md`.

Completion criteria:

- public repo has one clear OSS license story
- no public doc says MIT if the release is Apache 2.0
- no public doc says enterprise license is required for framework modules

### Phase 3 - Clean Public Documentation

Goal: public docs should explain how to use the framework, not reveal product strategy.

Tasks:

- [ ] Create a new public root README focused on framework usage.
- [ ] Keep a short architecture guide for reusable primitives.
- [ ] Keep `GITHUB_PACKAGES_RELEASE_GUIDE.md`, adjusted to the public repo name.
- [ ] Add a simple Spring Boot quickstart.
- [ ] Add at least one minimal example app.
- [ ] Remove old monetization docs from public source.
- [ ] Remove productization/strategy docs from public source.
- [ ] Remove private LLM/session/context docs from public source.

Public README should cover:

- what AI Fabric Framework is
- Maven setup
- minimum Spring Boot example
- provider setup
- RAG/vector setup
- actions setup
- module list
- contribution and security links

Completion criteria:

- a new developer can install and run a minimal app without product/platform knowledge
- docs do not expose LoomAI Platform roadmap or private operational details

### Phase 4 - Clean Framework Build and Maven Coordinates

Goal: public Maven packages should be consumable and coherent.

Tasks:

- [ ] Rename parent/BOM artifact if desired. Current: `ai-fabric-spring-boot-starter`; clearer future name: `ai-fabric-bom`.
- [ ] Keep current artifact if avoiding larger migration for first preview.
- [ ] Set version to `0.1.0-preview`.
- [ ] Confirm dependency-management artifact IDs match real child artifact IDs.
- [ ] Confirm all child POMs inherit the same version.
- [ ] Confirm Java 21 is intentional and documented.
- [ ] Add source/javadoc artifacts if desired for GitHub Packages. Required later for Maven Central.
- [ ] Keep GitHub Packages distribution management pointed to the new public repo.

Commands:

```bash
mvn -f ai-infrastructure-module/pom.xml versions:set \
  -DnewVersion=0.1.0-preview \
  -DgenerateBackupPoms=false

mvn -B -V --no-transfer-progress -f ai-infrastructure-module/pom.xml validate
```

Completion criteria:

- framework reactor validates
- consumer BOM imports resolve real artifact names
- version/tag convention is locked

### Phase 5 - Extract To New Public Repo

Goal: create a clean public repository with no private history.

Recommended method for first release: fresh repo copy, not history filtering.

Reason:

- history filtering can still preserve accidental references if done wrong
- a fresh public repo avoids exposing old product/platform commits
- the private product repo remains the long-lived operational monorepo

Steps:

```bash
cd /Users/mahmoudashraf/Downloads/Projects
git clone git@github.com:mahmoudashraf/ai-fabric-framework.git ai-fabric-framework
cd ai-fabric-framework
```

Copy only reusable framework modules into `ai-infrastructure-module/`. Do not copy private deployable product services:

- `ai-fabric-runtime`
- `ai-infrastructure-generic-rest-connector`

Copy only cleaned public root files:

```bash
cp /Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/README.md ./README.md
cp /Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/LICENSE ./LICENSE
```

Then add public repo support files:

```text
.github/workflows/ai-fabric-framework-github-packages-release.yml
CONTRIBUTING.md
SECURITY.md
CODE_OF_CONDUCT.md
examples/
```

Completion criteria:

- public repo contains no `Platfrom/`
- public repo contains no `product-services/`
- public repo contains no `Final_Documentation/`
- public repo contains no `doc/Productization/`
- public repo contains no private rollout/session/handoff docs

### Phase 6 - Run Exposure and Secret Scans

Goal: prove the public repo does not leak private product material.

Required scans:

```bash
rg -n "LoomAI|Shopify|ProdUS|Coolify|Railway|Hetzner|sslip|loomai\\.pro|private|handoff|deploymentId|consumerId|billing|partner|platform admin" .
rg -n "token|secret|password|api[_-]?key|private[_-]?key|BEGIN (RSA|OPENSSH|PRIVATE)|Bearer |Authorization" .
git ls-files | rg "(^|/)(\\.env|.*\\.secret|.*private.*|.*handoff.*|.*session.*)$"
```

Recommended tool scans if installed:

```bash
gitleaks detect --source . --redact
trufflehog filesystem . --no-update --only-verified
```

Completion criteria:

- no secrets
- no private product docs
- no live deployment URLs except generic examples
- no customer-specific identifiers
- no private roadmap/context files

### Phase 7 - Add Public CI

Goal: public repo verifies framework quality without private services.

CI workflows:

- framework Maven validate/compile/test
- publish GitHub Packages on release
- optional docs/build check
- optional dependency review

Minimum public CI:

```bash
mvn -B -V --no-transfer-progress -f ai-infrastructure-module/pom.xml -DskipITs test
```

If full tests are too heavy, first public CI can run:

```bash
mvn -B -V --no-transfer-progress -f ai-infrastructure-module/pom.xml -DskipTests compile
```

and a targeted test workflow for stable modules.

Completion criteria:

- CI does not need private secrets
- CI does not call live LoomAI Platform, Shopify, Coolify, ProdUS, or customer services
- release workflow has `contents: write` and `packages: write`

### Phase 8 - First Public Preview Release

Goal: release `0.1.0-preview`.

Steps:

```bash
git status --short
mvn -B -V --no-transfer-progress -f ai-infrastructure-module/pom.xml validate
mvn -B -V --no-transfer-progress -f ai-infrastructure-module/pom.xml -DskipTests compile
git add .
git commit -m "Release AI Fabric Framework 0.1.0-preview"
git tag ai-fabric-framework-v0.1.0-preview
git push origin main
git push origin ai-fabric-framework-v0.1.0-preview
```

Then create GitHub Release:

```text
Tag: ai-fabric-framework-v0.1.0-preview
Title: AI Fabric Framework 0.1.0 Preview
```

Release notes should say:

- preview release
- Java 21
- Spring Boot 3.2.x
- Maven packages available from GitHub Packages
- source archive attached
- API stability not guaranteed yet

Completion criteria:

- GitHub Release exists
- GitHub Packages contains framework artifacts
- source archive and checksum are attached
- release notes do not mention private products

### Phase 9 - Private Product Repo Integration

Goal: keep private products near framework development without exposing product code.

Local development:

```bash
cd /Users/mahmoudashraf/Downloads/Projects/ai-fabric-framework
mvn -f ai-infrastructure-module/pom.xml clean install

cd /Users/mahmoudashraf/Downloads/Projects/TheBaseRepo
mvn -f Platfrom/backend/pom.xml test
```

Private product Maven consumption:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/mahmoudashraf/ai-fabric-framework</url>
  </repository>
</repositories>
```

Private product dependency example:

```xml
<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-fabric-starter</artifactId>
  <version>0.1.0-preview</version>
</dependency>
```

Private CI options:

1. consume released framework package
2. consume snapshot package
3. checkout sibling public framework repo, run `mvn install`, then test private product integration

Completion criteria:

- private repo no longer requires copied framework source for released framework functionality
- product integration tests prove the platform/Shopify/Partner code works against the released framework package
- public framework changes can be tested against private product code before release

### Phase 10 - Private Repo Cleanup After Release

Goal: keep product repo close but clean.

Tasks:

- [x] Keep `ai-infrastructure-module` in the private repo only as the product-services container for deployable runtime/connector services.
- [x] Do not keep reusable framework library source in the private repo as a source mirror.
- [ ] Add a private process: framework changes are authored in public repo, then consumed by private repo through Maven.
- [ ] Keep private product integration tests in private repo.
- [ ] Keep private deployment docs out of public framework.
- [ ] Update private LLM context files with the new repo boundary.

Longer-term options:

- Git submodule for public framework source inside private repo.
- Git subtree mirror.
- Maven-only consumption.

Recommended first option:

```text
Maven-only consumption for releases, local mvn install for active development.
```

Avoid submodules until the boundary is stable.

---

## 6) Owner Inputs Needed

The owner should provide or decide:

- public repo name
- final OSS license
- whether GitHub Packages should be public or private during preview
- whether to keep artifact group `com.ai.fabric`
- whether first version is `0.1.0-preview`
- whether to rename parent/BOM artifact before first release
- whether public repo should include examples immediately or after first preview
- whether the source release should include bundled ONNX model assets

---

## 7) Codex-Executable Work Items

Codex can do:

- create the cleaned public README
- rewrite framework docs
- clean POM metadata
- replace license text after owner confirms license
- create `CONTRIBUTING.md`, `SECURITY.md`, and `CODE_OF_CONDUCT.md`
- create public CI workflows
- run Maven validation/compile/tests
- run exposure scans
- prepare source archive
- update private repo integration docs
- update LLM context files

Codex should not do without explicit owner approval:

- make current private repo public
- publish a GitHub Release
- push to a new public repo if ownership/name is not confirmed
- delete private product code from the private repo
- expose private tokens or private handoff files

---

## 8) Release Readiness Checklist

Code:

- [ ] framework reactor validates
- [ ] framework reactor compiles
- [ ] stable targeted tests pass
- [ ] no private product dependency required
- [ ] no private runtime/service dependency required

Docs:

- [ ] README is public-safe
- [ ] quickstart works
- [ ] module list is accurate
- [ ] Java/Spring versions match POM
- [ ] no private strategy docs
- [ ] no product/platform claims that are not framework-backed

License:

- [ ] full Apache 2.0 text or owner-selected OSS license
- [ ] no conflicting MIT/proprietary claims
- [ ] POM license metadata matches root license

Security:

- [ ] `rg` exposure scans pass
- [ ] `gitleaks` or equivalent scan passes
- [ ] no `.env`, secret files, tokens, private keys, handoff files, or private session docs

Release:

- [ ] version set to `0.1.0-preview`
- [ ] tag `ai-fabric-framework-v0.1.0-preview`
- [ ] GitHub Release created
- [ ] Maven packages published
- [ ] framework-only source archive attached
- [ ] checksum attached

Private product integration:

- [ ] private product repo can consume released Maven package
- [ ] local sibling-repo development flow documented
- [ ] product integration tests run against the package

---

## 9) First Release Recommendation

Recommended first public release posture:

```text
AI Fabric Framework 0.1.0 Preview
License: Apache 2.0
Distribution: GitHub source release + GitHub Packages
Maven Central: deferred
API stability: preview, not guaranteed
Product/platform: private, not included
```

Recommended next release posture:

```text
AI Fabric Framework 0.2.0 Preview
Add cleaned examples
Add stronger public docs
Add source/javadoc artifacts
Prepare Maven Central metadata
```

Recommended stable release posture:

```text
AI Fabric Framework 1.0.0
Only after API boundaries, docs, license, CI, examples, and private product integration are stable.
```

---

## 10) Current Status

2026-06-28 update:

- Public framework release `0.3.1` exists at tag `ai-fabric-framework-v0.3.1` and GitHub release `https://github.com/Loom-AI-Labs/ai-fabric-framework/releases/tag/ai-fabric-framework-v0.3.1`.
- Private product services now consume the released framework through `io.github.loom-ai-labs:ai-fabric-bom:0.3.1`.
- Private Dockerfiles and Platform regression workflow pin public framework tag `ai-fabric-framework-v0.3.1` for reproducible builds.
- Product integration verification against `0.3.1` passed locally for private runtime/generic connector and product vectorization/embedding workers.
- Boot 4/Jackson transition note: private product services still using Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper` must explicitly declare Jackson 2 where used and provide a compatibility mapper bean until those services are intentionally migrated to Boot 4's Jackson 3 mapper APIs.

Completed:

- Public framework repo exists at `/Users/mahmoudashraf/Downloads/Projects/ai-fabric-framework` and `https://github.com/Loom-AI-Labs/ai-fabric-framework`.
- Framework source and example apps were moved to the public repo and pushed.
- Public framework artifacts were installed locally as `0.1.0-preview`.
- Private repo no longer keeps reusable framework library source or `Real_Apps`.
- Private repo keeps deployable product services under `ai-infrastructure-module/ai-fabric-runtime` and `ai-infrastructure-module/ai-infrastructure-generic-rest-connector`.
- Private repo root README now documents the product/framework boundary.
- Private `ai-fabric-product` consumes framework artifacts with `ai-fabric.version=0.1.0-preview`.
- Private CI and embedding-worker Dockerfiles install the public framework repo into local Maven before private product builds.
- Framework-only private workflows/actions/scripts were removed from the private repo.
- Local private compile checks passed for `ai-fabric-product`, Platform backend, Shopify Bridge, and MCP execution gateway.
- Public framework repo no longer contains or publishes the deployable runtime/generic REST connector service modules.

Still required before public release:

- fix license conflicts
- rewrite public README/docs
- run exposure/secret scans on the new public repo
- publish release from the public repo, not from the private product repo
- decide whether preview distribution uses GitHub Packages only or also Maven Central later

Still required before deployment-source cleanup is complete:

- add a clean per-service source strategy or published-image/package strategy for generated deployments.
- deployable runtime/connector source belongs to this private repo.
- private product workers still belong to this private repo.
- do not reintroduce copied framework source into the private product repo to work around source wiring.
