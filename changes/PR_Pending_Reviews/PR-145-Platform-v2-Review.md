# PR #145 Review: Platform v2

**Branch:** `Platformv-V2` → `main`
**Reviewed:** 2026-03-31
**Files changed:** 255 | **Additions:** +31,637 | **Deletions:** -840 | **Commits:** 50
**CI Status:** In progress at time of review

---

## What's New in V2

Building on V1, this PR adds:
- **Operation approvals** — approval/reject workflow for apply and delete operations
- **POC workspace** — embedded chat, scenario library, dataset import, prompt sessions
- **Prompt management** — prompt config in drafts/versions, revision history, baseline diff, preview overlay
- **Deployment assignments** — user-deployment role mapping with visibility controls
- **Bulk operations** — batch archive/restore/delete deployments
- **Config diff center** — draft vs published vs live comparison per deployment
- **Remediation actions** — rerun verification, redeploy, restart, reset vectors, archive, delete
- **Security governance** — automated checks for credential posture, auth config, secret usage
- **Production readiness scorecards** — area-based readiness assessment
- **Railway live drift readback** — compare Railway env vars against platform source of truth
- **User administration** — create/update/disable users, password reset, preferences
- **Deployment workspace** — unified landing page with context, lifecycle, navigation

---

## Critical Issues

### 1. Prompt Injection in ChatRuntimeController
- `buildActionAwareSuggestionsPrompt()` and `buildFallbackSuggestions()` embed user-supplied content (request attachments `content`, `contentText`, `url`, `hint`) directly into LLM prompts via string concatenation — no sanitization.
- Attack: crafted content can override system instructions.
- Fix: use structured prompt templates; never concatenate untrusted input into prompts.

### 2. Admin Auth Bypass When API Key Not Configured
- `ChatRuntimeController.isAdminAuthorized()`: if `app.admin.api-key` is not set, all requests pass (`return true`). Silently disables auth for prompt preview.
- Fix: deny access when key is not configured; use Spring Security.

### 3. Race Condition in Approval Consumption
- `DeploymentOperationApprovalService.consumeApprovedRequestIfRequired()` checks status then updates to `CONSUMED` without locking. Two concurrent requests can both consume the same approval.
- Fix: add `@Version` optimistic locking on `DeploymentOperationApprovalEntity`.

### 4. Race Condition in Admin Guardrails
- `PlatformUserAdminService.enforceAdminGuardrails()` counts active admins before update — two concurrent requests can both pass and lock out all admins.
- Fix: pessimistic locking or database-level constraint.

---

## High-Severity Issues

### 5. Controller Authorization Is Implicit
- `DeploymentController` has class-level `@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")` but 40+ endpoints delegate authorization entirely to service methods.
- Destructive operations (`deleteDeployment`, `applyVersion`, `publishDraft`) have no method-level `@PreAuthorize`.
- All POC endpoints (lines 277-368) lack explicit deployment-level authorization annotations.
- Fix: add explicit `@PreAuthorize` or access service calls at controller level.

### 6. Weak Password Policy
- `PlatformUserAdminService.normalizePassword()` only requires 10 characters, no complexity checks.
- Fix: minimum 14 chars with complexity requirements.

### 7. Password Reset Audit Gap
- `resetPassword()` records THAT a reset happened but not WHO performed it.
- Fix: include `SecurityContext` principal in audit details.

### 8. Stale Approval Visibility
- `listApprovals()` returns approvals without checking expiration. Expired approvals show as "APPROVED".
- Fix: filter or annotate expired approvals.

### 9. Unvalidated Prompt Overlay Content
- `sanitizePromptPreview()` validates keys but not values — no length limits on prompt content.
- Fix: enforce `MAX_PROMPT_LENGTH` (12,000 chars from `PromptPreviewOverlaySupport`).

---

## Medium-Severity Issues

### 10. Missing Database Indexes
- `platform_users`: no index on `(role, status)`
- `platform_deployment_operation_approvals`: missing `(deployment_id, status)` and `expires_at` indexes
- POC tables (V10-V12): no index on `created_by_actor_id`

### 11. Missing CHECK Constraints
- `role` and `status` columns are `VARCHAR(64)` with no CHECK constraint.

### 12. Exception Messages Leak Internal Details
- `DeploymentPocChatService` and `DeploymentRailwayLiveReadbackService` include `ex.getMessage()` in client-facing errors.

### 13. Secret Name Parsing Is Fragile
- `DeploymentRailwayLiveReadbackService` parses `${secret:KEY_NAME}` via substring slicing without format validation.

### 14. Oversized UI Components
| File | Lines |
|------|-------|
| `PocPage.tsx` | 1,470 |
| `OverviewPage.tsx` | 1,433 |
| `PromptsPage.tsx` | 1,315 |

### 15. File Upload Has No Size Limit
- `PocPage.tsx handleImportFileSelection()` calls `file.text()` without checking `file.size`.

### 16. JSON Import Has No Size Limits
- `parseImportPayload()` accepts unbounded JSON and per-record content strings.

---

## Unresolved Issues from PR #144

- [ ] Directory typo: `Platfrom/` still not renamed to `Platform/`
- [ ] Hardcoded credentials in config files
- [ ] CSRF disabled globally with session auth
- [ ] Session cookie defaults to `secure=false`
- [ ] No rate limiting on auth endpoints
- [ ] API key in localStorage (UI)
- [ ] HTTP client resource leaks in `RailwayGraphqlClient` and `DeploymentReleaseVerificationService`
- [ ] Race conditions in `DeploymentService.applyVersion()` and `PublicProvisioningApiService.createDeployment()`

---

## Summary Checklist

### Must fix before merge:
- [ ] Fix prompt injection in `ChatRuntimeController` suggestion/fallback builders
- [ ] Fix admin auth bypass when API key not configured
- [ ] Add optimistic locking to approval consumption
- [ ] Add locking to admin guardrails count
- [ ] Add explicit authorization checks to controller endpoints
- [ ] Strengthen password policy
- [ ] Include admin actor in password reset audit
- [ ] Filter expired approvals in list endpoint
- [ ] Enforce length limits on prompt overlay values

### Should fix before production:
- [ ] Add missing database indexes
- [ ] Add CHECK constraints on role/status columns
- [ ] Sanitize exception messages in client-facing errors
- [ ] Validate secret name format in Railway readback
- [ ] Add file/payload size limits in POC import UI
- [ ] Split oversized UI components
- [ ] Address all unresolved V1 issues
