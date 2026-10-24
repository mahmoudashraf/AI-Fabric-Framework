/**
 * Owned, target-scoped managed dependency lifecycle (010.36 R5).
 *
 * <h2>Parent integration</h2>
 * Publish an active reviewed ManagedDependencySpecEntity with schema managed-dependencies/v1,
 * validator.fingerprint(spec), immutable source artifact/image provenance and explicit capacity.
 * No template, capacity or credential rows are seeded by V162. The examples in
 * ManagedDependencyFixtures are test-only images/budgets, not admission evidence.
 *
 * Approved bootstrap/apply calls lifecycle.submit(deploymentId, targetProfileId,
 * new Request(specRef, candidateVersionId, frozenTargetBindingFingerprint, operationKey)).
 * It then calls provision(deploymentId, targetProfileId, anotherOperationKey) and waits for PROVISIONED.
 * Consume provisionedBindings with exact spec/binding fingerprints to create the candidate runtime.
 * Once explicit candidate runtime callbacks are administrable, call reconcile with a new operation
 * key and wait for READY. This two-stage contract avoids callback bootstrap/readiness deadlocks;
 * PROVISIONED is not activation or canary evidence. A pending initialization can retain a guarded
 * projection, but only if every resource was provisioned at the exact configuration revision.
 * Owner enrollment may precede reconciliation. Missing Basic Inbox operators blocks the whole
 * composition before any provider creation. There is deliberately no public provision/reconcile
 * endpoint. Readiness is READY only after all services/databases and initialization canaries pass.
 *
 * Main wiring consumes lifecycle.bindings(deploymentId, targetProfileId, specFingerprint,
 * bindingFingerprint): exact slot, HTTPS endpoint, generated account/binding identities and
 * managed secret references. Only resolve these into reviewed endpoint/secret slots. Do not
 * mutate semantic template composition or replace slot:document-storage with a physical ref.
 * New candidate bindings use bindCandidate with expectedGraphRevision; the dependency spec and
 * resource/account identities remain unchanged. Reconcile is not a fixture-wide reset.
 * Changed dependency topology/images require a separately reviewed lifecycle, not a hidden overlay.
 *
 * <h2>Required adapter</h2>
 * Provide exactly one ManagedDependencyPrerequisites bean integrating core release authorization.
 * New releases prove approved candidate/owner entitlement, immutable configuration/spec/source and
 * reviewed target identity without an R7 capacity reservation. The returned core authorization
 * reference is distinct from a historical reservation and makes no capacity assertion. Existing
 * R7-backed graphs retain their historical ledger path. The adapter returns reviewed
 * target values/secret refs, TLS origins and named callbacks from server-owned target bindings.
 * These are never browser-supplied URLs or environment overlays. The legacy-named validateReservation
 * rechecks core ownership, entitlement, reviewed placement and the frozen binding before side effects;
 * it does not require optional placement/capacity lease freshness for a core-authorized release.
 * validateArtifact must prove source artifact membership, registry availability, exact service
 * and database digests, supported architecture/provider digest semantics and mounted data paths.
 *
 * initializeAndVerify must use existing connector/integration/runtime management APIs to register
 * callback hosts/signature refs, idempotently seed only the generated account, reconcile stock/media,
 * sync and run target-scoped canaries. For Inbox it verifies binding membership, callback signatures,
 * human reply and current operator key acceptance/rejection; installed plugin rows are not proof.
 * Required named callback slots are declared by CALLBACK_URL environment values in the spec.
 * Runtime URLs, private token audiences/issuers and target secrets use TARGET_VALUE/TARGET_SECRET.
 * Cross-service secrets use SERVICE_SECRET(reference=purpose, serviceSlot=declared dependency).
 * No runtime source or public framework changes are made here.
 *
 * <h2>Concrete environment contracts</h2>
 * Simulator: persistent /app/data, SIMULATOR_PUBLIC_BASE_URL=PUBLIC_URL;
 * SIMULATOR_ALLOWED_WEBHOOK_HOSTS=CALLBACK_HOSTS; generated account identity for
 * SIMULATOR_AUTOTRADER_ADVERTISER_ID/INTEGRATION_ID and configured profile account IDs;
 * distinct generated control, signing, API and notification secrets. Declare all configured
 * simulator profiles explicitly; never reuse Northfield's account/fixture IDs or keys.
 * Receiver: DATABASE_URL=DATABASE_JDBC, DATABASE_USERNAME=DATABASE_USERNAME,
 * DATABASE_PASSWORD=SECRET; generated DEALERSHIP_ID=ACCOUNT_ID; approved CORS_ALLOWED_ORIGINS=ORIGINS;
 * internal API key=SECRET; staff username=ACCOUNT_ID, password hash=SECRET_BCRYPT_BASE64;
 * PII key=SECRET_STANDARD_BASE64 (32 bytes); production guards/cookie settings must be explicit.
 * Review its public media, simulator, integration source, runtime and private assertion bindings
 * using TARGET_VALUE, TARGET_SECRET, SERVICE_URL and SERVICE_SECRET. No manual environment repair.
 * Basic Inbox: BASIC_INBOX_BINDING_ID=BINDING_ID, TENANT_ID=TENANT_ID, DEPLOYMENT_ID=DEPLOYMENT_ID;
 * BASIC_INBOX_CONNECTOR_CALLBACK_URL=CALLBACK_URL; distinct SECRET purposes for provider key,
 * callback signing and data encryption; BASIC_INBOX_OPERATORS=OPERATORS. PostgreSQL JDBC/user/
 * password or an explicitly reviewed H2 file path and persistent volume is mandatory.
 * Runtime binding maps the returned provider/callback secret refs, never an operator key.
 *
 * <h2>Operator and retirement contracts</h2>
 * Only the persisted CUSTOMER_ADMIN owner enrolls operators. No platform roles are granted.
 * API prefix: /api/deployments/{deploymentId}/targets/{targetProfileId}/managed-dependencies.
 * GET status never returns keys or secret refs. POST operators enrolls; POST
 * operators/{id}/rotate or revoke requires expectedCredentialRevision and operationKey.
 * POST operators/{id}/credential-delivery requires authorized TLS, is no-store and delivers
 * once per key revision. A lost delivery response requires rotation, not replay.
 * Mutation is pending until redeployment/readback; effectiveAt and effectiveConfigurationRevision
 * report actual completion, not an immediate revocation claim. Failed redeploy can leave the old
 * key accepted. Revoking the last operator invokes disableAndVerifyBinding and stops/readbacks
 * the owned Inbox without deleting its database/volume. Selected disabled Inbox blocks readiness.
 *
 * R3 calls retirement.plan then retire/compensateUnused with expectedRevision, plan fingerprint
 * and operation key. It must hold its activation/retirement admission barrier throughout execution.
 * requireUnused rechecks assignments, generations, drained human work and retention before each
 * destructive step. verifyReclaimed must prove physical database/volume reclamation before capacity
 * release; absent application metadata alone is insufficient. Retry the same retirement command
 * after uncertain responses. History and IDs are retained. No deployment-wide deletion is invoked.
 * recoverInterrupted requires proof the old worker process terminated; stale heartbeat alone is
 * insufficient. Creation-intent uncertainty reconciles exact ownership markers or blocks, never
 * allocates a replacement. No automatic lease steal can race an in-flight provider creation.
 *
 * <h2>Outstanding hosted prerequisites</h2>
 * Reviewed artifact/spec publication, core release authorization, approved callback/
 * runtime initialization canaries and physical volume readback are required before actual use.
 * Owner S3 canary remains a parent gate. Actual staging placement is only 46.224.145.148 and
 * production only 46.225.162.106; dtp-coolify-prod-staging is not accepted based on its name.
 * Provider ownership/digest/healthy status evidence must match exact current API shapes; missing
 * evidence is a conflict. Nothing here certifies P0/P1/P3/P5/P7 or changes framework 0.8.35.
 */
package com.ai.fabric.platform.backend.deployment.dependency;
