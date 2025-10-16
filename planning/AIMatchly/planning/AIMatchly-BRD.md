Business Requirements Document (BRD): AI Agencies & Teams Platform
1) Purpose
Build a platform where AI automation agencies maintain standardized, evidence-first profiles; buyers submit briefs; an AI layer normalizes each brief and generates a “Project Pack”; the platform matches buyers to agencies, enables consultation booking, and optionally publishes anonymized projects for structured applications. The product emphasizes comparability, provenance, and speed, not heavy SLAs or dispute resolution.

2) Scope
In Scope (MVP)
Evidence-first Agency Profiles with standardized schema and “Proof Pack” badge
Unstructured Ingestion (URL/PDF/image) into draft profiles with human review
Buyer Brief Intake → LLM Normalization → Embeddings → Matching with explainable reasons
Project Pack generation from normalized briefs (non-binding SOW-like bundle)
Consultations via embedded scheduler or native request (optional prepaid)
Publish Projects: invite-only by default; controlled, capped applications as an option; buyer identity protection
Credits System for costly or spam-prone actions; Pro Plan for advanced features
Featured placements, concierge intro fee, basic admin and moderation
Out of Scope (MVP)
Escrow, SLAs, dispute resolution, in-app messaging, task/PM features, per-agency forums

3) Roles
Visitor: browse, submit brief, request consultation
Buyer: manages briefs, Project Packs, postings, matches, bookings
Agency Owner: creates/claims profile, manages tiles, support packages, scheduler, applies to projects
Agency Contributor: edits profile content with Owner approval
Admin: curation, verification, moderation, featured placement, duplicates, metrics
Verifier (Ops): spot-check artifacts, flip Proof Pack status
System Services: Ingestion, Normalizer/LLM, Matching Engine, Notification, Billing, Credits

4) Core Entities
Agency: basics, summary, logo, proof_pack_complete, scheduler_url
StackTag with agency-specific depth (1–3)
UseCase: title, problem, intervention, outcome_metric, industries[], stacks[], loom_url, hours_low/high, verified, embedding
SupportPackage: tier (Basic/Plus/Pro), price, inclusions (platform template)
Brief: raw_text, normalized_json, risk_flags[], embedding, status
Project: generated pack (objectives, scope in/out, milestones, acceptance tests, bands, constraints)
ProjectPosting: publication settings (mode, caps, anonymity)
Application: structured agency application, referenced use-cases, bands, risks, team, scores
MatchResult: brief_id, agency_id, score, reasons[]
Consultation: buyer↔agency requests, method, status, payment_status
IngestionJob and SourceLog (provenance)
ClaimVerification (email/domain)
CreditAccount and CreditTxn
Payment (featured, credit packs, concierge)
AuditLog, RateLimit

5) Key Features & Requirements
5.1 Agency Profile Builder
Required to publish: basics, stacks with depth, ≥2 UseCase tiles
UseCase tile: Loom demo, one outcome metric with timeframe, stacks, industries, hours band
Support packages: choose Basic/Plus/Pro templates; agency sets prices
Proof Pack badge: Complete vs Self-reported, per verifier checklist
Claiming: email/domain; unclaimed profiles show minimal excerpts and no logos until claimed
5.2 Unstructured Ingestion
Sources: URL (render + parse + JSON-LD), PDF (text + OCR fallback), image (OCR + optional vision layout)
Extraction to strict JSON with field-level citations; confidence scoring
Review & Accept UI for Ops; accept/edit/reject values before publish
5.3 Brief Intake → Normalization → Matching
Intake ≤8 questions: company size, industry, tools/data sources, desired outcome, constraints (PII/region/compliance), budget band, urgency, optional preferred stack
Normalization via LLM to schema with risk_flags; buyer confirms a short sanity summary
Embeddings for brief and use-cases
Matching score (0–100)
Stack fit 0–40 (Jaccard weighted by depth)
Use-case similarity 0–30 (cosine vs agency top-3 tiles)
Domain fit 0–20 (industry + size + verified)
Proof signal 0–10 (badge + verified tiles)
Explainable reasons: similar tiles, stack overlap, domain notes, proof status
5.4 Project Pack (from Brief)
Auto-generated, non-binding: objectives, success metrics, scope in/out, milestones with acceptance tests, integrations, data constraints, risks/assumptions, support tier, price/timeline bands, discovery questions
Shareable link and PDF/Notion export
5.5 Publishing Projects for Applications
Modes
Invite-only (default): auto-invite top 5–7 matched agencies, free to apply
Controlled publish: visible to eligible verified agencies; cap apps (e.g., 7–10)
Anonymity
Hide buyer identity by default: show industry, size band, region, constraints, stacks; optional budget band
Identity reveals upon shortlist (or NDA acceptance if enabled)
Applications are structured (no essays): approach bullets, referenced use-cases, milestone tweaks, risks, bands, team, compliance, limited questions
Scoring
Fit 0–40, Evidence 0–40, Clarity 0–20; transparent to buyer
Auto-close on cap or deadline; shortlist up to 3 for consultations
5.6 Consultations
Embedded scheduler (Cal.com/Calendly) with webhooks for analytics
Native request: proposed slots, ICS emails, optional prepaid consult via Stripe
Carry brief/project context into invites
5.7 Credits & Pricing
Free core: profile with 2 tiles, appear in search, receive invites, scheduler link
Credits (usage-based; never affect match rank)
Apply to published project: 5 credits, refund 4 if shortlisted; invited apps free
Ingestion run (URL/PDF/image): 2–5 credits
Extra use-case tiles beyond 2: 3 credits each
Category boost (7 days, visual highlight only): 20 credits
Concierge intro fee: pay cash or 100 credits
Earn credits: Proof Pack completion (+10), fast response to consults (+2), shortlisted (+5), hired (+20), referrals (+15). Caps apply.
Pro Plan (optional): monthly $49 or annual $420 initially; higher tile cap, advanced analytics, auto-ingest, application fee waivers, Proof fast-track, priority gallery placement
No “3 months free then $500” at launch; move to annual after proven throughput

6) Non-Functional Requirements
Security/Privacy: PII scrubbing before LLM; RBAC; audit logs; encryption in transit and at rest
Compliance: GDPR basics (export/delete, DPA, EU storage option)
Performance: p95 page load < 2.5s; p95 matching < 800 ms at 1k agencies
Availability: 99.5% target; graceful degradation if LLM unavailable
Observability: structured logs, error tracking, analytics events
Data provenance: SourceLog required for extracted fields
Rate limiting on ingestion and brief endpoints

7) User Stories (selected)
Buyer
Submit a brief and confirm the normalized summary
Generate a Project Pack and share with matched agencies
Publish a project (invite-only or controlled); review ranked applications; shortlist and book
Book a consultation and receive ICS invites
Agency
Claim/create profile; add stacks and ≥2 use-cases; select support packages
Complete Proof Pack and earn credits
Apply to eligible postings using structured forms; reference existing tiles
Receive invites/consultations and view “why we matched”
Admin/Verifier
Review ingested drafts with citations; accept/edit/reject; flip Proof Pack
Merge duplicates; unpublish low-quality profiles; manage featured slots
Refund credits per rules; export metrics
System
Normalize briefs to schema with risk flags
Compute matching scores and persist human-readable reasons
Enforce anonymity/redaction until shortlist; cap applications

8) Functional Flows
A) Brief → Project Pack → Matches → Consult
Buyer completes intake → LLM normalization → confirm summary
Generate Project Pack → show matches with reasons
Invite shortlist or publish controlled posting
Shortlist finalists → book consultations → send ICS with context
B) Profile Creation / Claim
Claim via email/domain → complete basics, stacks, ≥2 tiles → pick support tiers
Optional: run URL/PDF ingestion → Ops review → publish
C) Controlled Posting Applications
Posting opens (anon) to eligible verified agencies; cost 5 credits to apply
Structured applications submitted; system scores; buyer reviews ranked list
Shortlist triggers identity reveal and scheduling; auto-refund 4 credits

9) LLM & Matching Specifications
Normalization model: GPT-4o-mini, temp 0.2, JSON Schema enforced; if ambiguous, set null + risk flag
Embeddings: text-embedding-3-large for use-cases and briefs
Project Pack: same model, strict schema, milestones from curated library, acceptance tests from library
Redaction: deterministic scrubbing + NER pass; human preview before publish

10) Data Model (summary, PostgreSQL + pgvector)
agency(id, name, website, location, employee_band, summary, logo_url, proof_pack_complete, scheduler_url, created_at)
stack_tag(id, name); agency_stack(agency_id, stack_id, depth)
use_case(id, agency_id, title, problem, intervention, outcome_metric, industries[], stacks[], loom_url, hours_low, hours_high, verified, embedding vector)
support_package(id, agency_id, tier, price, inclusions jsonb)
brief(id, raw_text, normalized_json jsonb, risk_flags[], embedding vector, status, created_at)
project(id, brief_id, title, objectives[], success_metrics jsonb, scope_in[], scope_out[], integrations[], data_constraints jsonb, risks[], assumptions[], support_tier, price_band, timeline_weeks, status, share_token)
project_milestone(id, project_id, name, description, weeks_from_start, acceptance_test jsonb)
project_posting(id, project_id, mode, visible_to, cap_apps, anon, budget_blind, status)
application(id, project_posting_id, agency_id, approach_bullets[], referenced_usecases[], milestone_flags jsonb, risks jsonb, price_band, timeline_weeks, team jsonb, compliance jsonb, questions[], scores jsonb, status)
match_result(brief_id, agency_id, score, reasons[], created_at)
consultation(id, buyer_id, agency_id, brief_id, method, status, payment_status, created_at)
ingestion_job(id, source, url_or_file, text, confidence, profile_id, created_at)
source_log(id, entity, field, source_items jsonb[])
claim_verification(id, agency_id, method, status, token)
credit_account(id, owner_type, owner_id, balance, updated_at)
credit_txn(id, account_id, type, amount, reason, ref_type, ref_id, idempotency_key, created_at)
payment(id, owner_id, type, amount, status, provider_ref, created_at)

11) API Surface (illustrative)
POST   /api/ingest/url|/pdf|/image
GET    /api/profiles/:id/draft
POST   /api/profiles/:id/publish
POST   /api/profiles/:id/claim

POST   /api/agencies
GET    /api/agencies/:id
POST   /api/agencies/:id/use-cases
POST   /api/agencies/:id/support-packages
POST   /api/agencies/:id/proof/complete

POST   /api/briefs
POST   /api/briefs/:id/confirm
POST   /api/match/:briefId
GET    /api/match/:briefId

POST   /api/projects/from-brief/:briefId
GET    /api/projects/:id
PATCH  /api/projects/:id
POST   /api/projects/:id/share
POST   /api/projects/:id/publish
GET    /p/:shareToken

GET    /api/postings/:id/eligibility
POST   /api/postings/:id/applications
GET    /api/postings/:id/applications
POST   /api/applications/:id/shortlist
POST   /api/postings/:id/close

POST   /api/consultations
POST   /api/consultations/:id/accept
POST   /api/integrations/scheduler/webhook

GET    /api/credits/balance
POST   /api/credits/spend
POST   /api/credits/grant
POST   /api/billing/credit-packs/checkout
POST   /api/billing/credit-packs/webhook

GET    /api/admin/metrics


12) Pricing & Credits Rules
Profiles: free to publish with 2 tiles; extra tiles cost credits
Applications: 5 credits; invited apps free; 4-credit refund on shortlist
Ingestion: 2–5 credits/run; auto-refund on failure
Boosts: 20 credits/7 days, cosmetic only
Concierge intro: $ or credits
Pro Plan: monthly/annual, no effect on match rank
Credits non-transferable, expire in 12 months; clear balance/cost UI

13) KPIs (MVP targets)
≥70% briefs parse valid on first pass
≥40% brief views click a booking or publish CTA
≥60% shortlist-to-consult conversion
≥40% agencies with Proof Pack within 14 days of onboarding
Median time to 5 qualified applications ≤ 5 days for published projects
App spam rate < 15% (fails eligibility or low evidence)
Positive unit economics on ingestion credits vs compute cost

14) Risks & Mitigations
Hallucinations: strict schemas, citations, human review
Spam/low quality: eligibility filter, caps, credits to apply, Proof Pack requirement
Pay-to-play perception: credits never affect match rank; invites free
Doxing buyers: default anonymity, NER redaction, human preview
Legal: non-binding packs, clear Terms, no escrow/SLAs in MVP

15) Roadmap
Phase 1 (Weeks 1–3)
Profiles, tiles, gallery/search, manual seeding; brief intake + normalization; matching; embedded scheduling; featured placements
Phase 2 (Weeks 4–6)
Project Pack generation; invite-only postings; native consultation; credits for applications; Proof Pack workflow
Phase 3 (Weeks 7–9)
Controlled publish with anonymity, caps, structured applications and scoring; ingestion (URL/PDF) with review & citations; credit purchases
Phase 4 (Later)
Image ingestion; advanced analytics; Pro features (auto-ingest, fee waivers); public benchmark reports and pricing band insights

16) Acceptance Criteria (MVP)
Buyer can submit a brief, confirm normalization, generate a Project Pack, and see 5 explainable matches within 60 seconds end-to-end
Agency can publish a profile with ≥2 use-cases and a visible Proof Pack status
Buyer can create invite-only posting and shortlist; or publish controlled posting with anonymity and caps; agencies apply with structured forms; shortlist leads to consultation booking
Credits are debited/refunded per rules with idempotency and visible balances
Ingestion turns a public URL into a draft profile with field-level citations and human review before publish
This BRD consolidates the full feature set we aligned on into a focused, defensible MVP with clear extensions.



