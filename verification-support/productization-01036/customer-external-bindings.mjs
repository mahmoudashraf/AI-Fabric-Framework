#!/usr/bin/env node

/**
 * Attended, control-plane-only external DATA/ACTION binding client.
 *
 * CLI (private JSON on stdin only; no auth/credential argv or environment reads):
 *   node customer-external-bindings.mjs prepare --live < private-input.json
 *   node customer-external-bindings.mjs execute --live --plan reviewed-plan.json \
 *     --reviewed-digest <digest> --checkpoint-dir /private/tmp/private-checkpoints < private-input.json
 *   node customer-external-bindings.mjs replay --live --plan reviewed-plan.json \
 *     --reviewed-digest <digest> --checkpoint-dir /private/tmp/private-checkpoints < private-input.json
 *   node customer-external-bindings.mjs reconcile --live --plan reviewed-plan.json \
 *     --checkpoint-dir /private/tmp/private-checkpoints < private-session-input.json
 *
 * Input: action APPROVE or BIND; explicit origin, scope (customerId, ownerUserId,
 * offerId, deploymentId, tenantId, versionId, targetProfileId, targetIntent,
 * expectedEntitlementRevision); staffActorId; staffAuthenticationMode (SESSION
 * or API_KEY); review {profile, config,
 * accountScope, reviewReference}; operationKey; customerSessionCookie; private
 * credentialFenceKey (32 random bytes as 64 hex characters, retained privately).
 * APPROVE needs exactly staffSessionCookie (SESSION) or staffApiKey (API_KEY),
 * never both and never fallback. API_KEY also requires an explicit configured
 * staffApiKeyHeaderName in APPROVE and BIND. The selected mode/header are public
 * review fences, not credentials. BIND needs approvalCheckpoint
 * (successful APPROVE checkpoint path) and credentials with the exact profile
 * secretFields. profile is the FULL separately reviewed offer profile, not an
 * inferred product/host/account default. Prepare derives and compares the live
 * offer profile and exact immutable marketplace manifest before returning it.
 * Reconcile needs only origin, customerSessionCookie and credentialFenceKey.
 * ACTION accountScope is explicit staff review evidence, not remote ACL proof.
 * This helper is fenced to the deployment's bootstrap target/authorization
 * revision; it does not infer a promotion target or refresh placement evidence.
 *
 * Customer auth is always SESSION. Session actorId is an email, not a user ID:
 * /api/platform/users must prove its exact ACTIVE user/role/customer mapping to
 * ownerUserId. Staff SESSION uses the same directory proof; staff API_KEY uses
 * only the normal server PLATFORM_ADMIN principal and exact reviewed audit actor.
 *
 * Read routes: /api/platform/auth/session; /api/platform/users;
 * /api/customer-self-service/catalogue/{offerId};
 * /api/customer-self-service/deployments;
 * /api/deployments/{deploymentId}/versions;
 * /api/marketplace/plugins/{pluginId};
 * /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/external-integrations
 * (query: versionId, targetProfileId, bindingSlotId, selectedProfileId).
 * POST approval: /api/customer-self-service/staff/offers/{offerId}/deployments/{deploymentId}/external-integrations/approvals
 * POST binding: /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/external-integrations/bind
 *
 * stdout contains only a sanitized prepared plan or report. Checkpoints contain
 * public plans, keyed commitments and strict receipts, NEVER credential bytes,
 * cookies or the fence key. Use a private, owned, canonical 0700 checkpoint dir.
 * Keep the same dir and key across invocations. One plan performs one command;
 * prepare/review APPROVE, execute it, then separately prepare/review/execute BIND.
 * v2 rejects old plans/checkpoints without identity and staff auth-mode fences.
 * No login, placement probe, apply, provisioning, business request or revoke.
 *
 * Backend qualification: the summary READ authorization correction is fixed in
 * source; verify its deployment for the selected origin. Expect a qualified build
 * that authorizes READ at deployment/target scope and separately enforces the
 * exact external slot/profile, published version and manifest. Older builds may
 * return BINDING_SLOT_FORBIDDEN; preparation still stops on that exact denial.
 * Source completion is not hosted rollout or readback evidence.
 * No read-only approval/command lookup exists. Unknown approvals stay unresolved;
 * an unknown bind's summary cannot prove command identity/credential revisions.
 */

import {createHash, createHmac, randomUUID, timingSafeEqual} from 'node:crypto';
import {constants} from 'node:fs';
import {lstat, open, readFile, realpath, rename, unlink} from 'node:fs/promises';
import {dirname, join, resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

const PLAN_SCHEMA = 'loomai-attended-external-binding-plan/v2';
const CHECKPOINT_SCHEMA = 'loomai-attended-external-binding-checkpoint/v2';
const PROFILE_SCHEMA = 'loomai-external-integration-profile/v1';
const PROFILE_FIELDS = ['schemaVersion', 'id', 'bindingSlotId', 'pluginId', 'pluginVersionId', 'manifestSha256',
  'targetProfileIds', 'configFields', 'secretFields', 'endpointFields', 'accountScopeFields', 'allowedHosts'];
const SCOPE_FIELDS = ['customerId', 'ownerUserId', 'offerId', 'deploymentId', 'tenantId', 'versionId',
  'targetProfileId', 'targetIntent', 'expectedEntitlementRevision'];
const SUMMARY_FIELDS = ['id', 'approvalId', 'deploymentId', 'versionId', 'targetProfileId', 'bindingSlotId',
  'selectedProfileId', 'status', 'revision'];
const USER_FIELDS = ['id', 'email', 'displayName', 'role', 'customerId', 'customerName', 'customerSlug',
  'status', 'lastLoginAt', 'createdAt', 'updatedAt'];
const MAX_BYTES = 1024 * 1024;
const STATES = ['INTENT', 'UNKNOWN', 'READBACK_PENDING', 'SUCCEEDED', 'REPLAY_INTENT', 'REPLAY_UNKNOWN'];

export class ExternalBindingError extends Error {
  constructor(code) { super(code); this.name = 'ExternalBindingError'; this.code = code; }
}
function check(condition, code) { if (!condition) throw new ExternalBindingError(code); }
function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value)
  && [Object.prototype, null].includes(Object.getPrototypeOf(value)); }
function exact(value, fields, code) {
  check(object(value) && equal(Object.keys(value).sort(), [...fields].sort()), code);
}
function literal(value, max = 2048) {
  return typeof value === 'string' && value.length > 0 && value.length <= max && value.trim() === value
    && !/[\u0000-\u001f\u007f-\u009f]/.test(value) && !value.includes('${') && !value.includes('#{');
}
function identifier(value) { return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(value); }
function revision(value) { return Number.isSafeInteger(value) && value >= 0; }
function hash(value) { return createHash('sha256').update(canonical(value)).digest('hex'); }
function mac(key, value) { return createHmac('sha256', Buffer.from(key, 'hex')).update(canonical(value)).digest('hex'); }
function canonical(value) {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (object(value)) return '{' + Object.keys(value).sort().map(key => JSON.stringify(key) + ':' + canonical(value[key])).join(',') + '}';
  check(value === null || ['string', 'boolean'].includes(typeof value)
    || (typeof value === 'number' && Number.isFinite(value)), 'JSON_VALUE_INVALID');
  return JSON.stringify(value);
}
function equal(a, b) { return canonical(a) === canonical(b); }
function sameMac(a, b) { return typeof a === 'string' && /^[a-f0-9]{64}$/.test(a)
  && typeof b === 'string' && /^[a-f0-9]{64}$/.test(b)
  && timingSafeEqual(Buffer.from(a, 'hex'), Buffer.from(b, 'hex')); }
function strings(value, min, max, predicate = identifier) {
  check(Array.isArray(value) && value.length >= min && value.length <= max
    && new Set(value).size === value.length && value.every(predicate), 'PROFILE_SET_INVALID');
  return [...value].sort();
}
function host(value) {
  return typeof value === 'string' && value.length <= 253
    && /^[a-z][a-z0-9-]*(?:\.[a-z0-9][a-z0-9-]*)*\.[a-z]{2,63}$/.test(value)
    && !['localhost', 'local', 'internal', 'lan', 'home', 'invalid'].includes(value.split('.').at(-1));
}
function endpoint(value, allowedHosts) {
  check(literal(value) && /^https:\/\//.test(value), 'ENDPOINT_NOT_REVIEWED');
  let url;
  try { url = new URL(value); } catch { throw new ExternalBindingError('ENDPOINT_NOT_REVIEWED'); }
  const raw = value.slice('https://'.length), slash = raw.indexOf('/');
  const authority = slash === -1 ? raw : raw.slice(0, slash), path = slash === -1 ? '' : raw.slice(slash);
  check(host(url.hostname) && allowedHosts.includes(url.hostname) && !url.username && !url.password
    && !url.search && !url.hash && (!url.port || url.port === '443')
    && (authority === url.hostname || authority === `${url.hostname}:443`)
    && /^[A-Za-z0-9/_~.-]*$/.test(path) && !path.includes('//')
    && !path.split('/').some(part => part === '.' || part === '..'), 'ENDPOINT_NOT_REVIEWED');
  return url;
}
function origin(value) {
  check(typeof value === 'string', 'EXPLICIT_PLATFORM_ORIGIN_REQUIRED');
  let url;
  try { url = new URL(value); } catch { throw new ExternalBindingError('EXPLICIT_PLATFORM_ORIGIN_REQUIRED'); }
  check(host(url.hostname) && value === url.origin && url.protocol === 'https:' && !url.username && !url.password,
    'EXPLICIT_PLATFORM_ORIGIN_REQUIRED');
  return value;
}
function profile(value) {
  exact(value, PROFILE_FIELDS, 'PROFILE_CONTRACT_INVALID');
  check(value.schemaVersion === PROFILE_SCHEMA && ['id', 'bindingSlotId', 'pluginId', 'pluginVersionId'].every(key => identifier(value[key]))
    && /^[a-f0-9]{64}$/.test(value.manifestSha256), 'PROFILE_CONTRACT_INVALID');
  const result = {...value, targetProfileIds: strings(value.targetProfileIds, 1, 16),
    configFields: strings(value.configFields, 1, 32), secretFields: strings(value.secretFields, 1, 16),
    endpointFields: strings(value.endpointFields, 1, 32), accountScopeFields: strings(value.accountScopeFields, 0, 8),
    allowedHosts: strings(value.allowedHosts, 1, 16, host)};
  check([...result.endpointFields, ...result.accountScopeFields].every(field => result.configFields.includes(field))
    && !result.secretFields.some(field => result.configFields.includes(field)), 'PROFILE_CONTRACT_INVALID');
  return result;
}
function textMap(value, fields, max, code) {
  exact(value, fields, code);
  check(Object.values(value).every(value => literal(value, max)), code);
  return structuredClone(value);
}
function privateValues(input) {
  const cookies = [input?.customerSessionCookie, input?.staffSessionCookie].filter(value => typeof value === 'string');
  return [...cookies, ...cookies.map(value => value.slice(value.indexOf('=') + 1)), input?.staffApiKey, input?.credentialFenceKey,
    ...Object.values(object(input?.credentials) ? input.credentials : {})].filter(value => typeof value === 'string' && value.length > 0);
}
function noSecrets(value, secrets) {
  const visit = item => {
    if (typeof item === 'string') check(!secrets.some(secret => item.includes(secret)), 'SECRET_IN_PUBLIC_DATA');
    else if (Array.isArray(item)) item.forEach(visit);
    else if (object(item)) Object.entries(item).forEach(([key, entry]) => { visit(key); visit(entry); });
  };
  visit(value);
  return value;
}
function sessionCookie(value) {
  check(typeof value === 'string' && /^[A-Za-z0-9_-]+=[A-Za-z0-9._~%+/-]+$/.test(value)
    && value.length <= 16384, 'PRIVATE_SESSION_COOKIE_REQUIRED');
  return value;
}
function apiKeyHeader(value) {
  return typeof value === 'string' && /^X-[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$/.test(value) && value.length <= 128
    && !/^x-(?:forwarded(?:-|$)|http-method(?:-|$)|real-ip$|original-(?:url|host)$|rewrite-url$)/i.test(value);
}
function normalizeInput(raw) {
  check(object(raw) && ['APPROVE', 'BIND'].includes(raw.action), 'EXPLICIT_ACTION_REQUIRED');
  check(['SESSION', 'API_KEY'].includes(raw.staffAuthenticationMode), 'EXPLICIT_STAFF_AUTH_MODE_REQUIRED');
  const apiKey = raw.staffAuthenticationMode === 'API_KEY';
  const fields = ['action', 'origin', 'scope', 'staffActorId', 'staffAuthenticationMode', 'review', 'operationKey',
    'customerSessionCookie', 'credentialFenceKey', ...(apiKey ? ['staffApiKeyHeaderName'] : []),
    ...(raw.action === 'APPROVE' ? [apiKey ? 'staffApiKey' : 'staffSessionCookie'] : ['credentials', 'approvalCheckpoint'])];
  exact(raw, fields, 'PRIVATE_INPUT_FIELDS_INVALID');
  exact(raw.scope, SCOPE_FIELDS, 'EXACT_SCOPE_REQUIRED');
  check(SCOPE_FIELDS.filter(field => !['targetIntent', 'expectedEntitlementRevision'].includes(field))
    .every(field => identifier(raw.scope[field])) && revision(raw.scope.expectedEntitlementRevision)
    && ['CUSTOMER_STAGING', 'CUSTOMER_PRODUCTION'].includes(raw.scope.targetIntent), 'EXACT_SCOPE_REQUIRED');
  check(literal(raw.staffActorId, 255) && raw.staffActorId !== raw.scope.ownerUserId && literal(raw.operationKey, 128)
    && /^[a-f0-9]{64}$/.test(raw.credentialFenceKey), 'PRIVATE_INPUT_FIELDS_INVALID');
  if (apiKey) check(apiKeyHeader(raw.staffApiKeyHeaderName), 'EXPLICIT_API_KEY_HEADER_REQUIRED');
  exact(raw.review, ['profile', 'config', 'accountScope', 'reviewReference'], 'REVIEW_FIELDS_INVALID');
  const reviewedProfile = profile(raw.review.profile);
  check(reviewedProfile.targetProfileIds.includes(raw.scope.targetProfileId), 'PROFILE_TARGET_MISMATCH');
  const config = textMap(raw.review.config, reviewedProfile.configFields, 2048, 'CONFIG_FIELDS_INVALID');
  check(object(raw.review.accountScope) && Object.keys(raw.review.accountScope).length >= 1
    && Object.keys(raw.review.accountScope).length <= 8 && Object.keys(raw.review.accountScope).every(identifier), 'EXPLICIT_ACCOUNT_SCOPE_REQUIRED');
  const accountScope = textMap(raw.review.accountScope, Object.keys(raw.review.accountScope), 2048, 'ACCOUNT_SCOPE_INVALID');
  check(!Object.keys(accountScope).some(field => reviewedProfile.secretFields.includes(field)), 'ACCOUNT_SCOPE_SECRET_FIELD_FORBIDDEN');
  check(reviewedProfile.accountScopeFields.every(field => accountScope[field] === config[field]), 'ACCOUNT_SCOPE_MISMATCH');
  check(literal(raw.review.reviewReference), 'EXPLICIT_REVIEW_REFERENCE_REQUIRED');
  reviewedProfile.endpointFields.forEach(field => endpoint(config[field], reviewedProfile.allowedHosts));
  const input = {...raw, origin: origin(raw.origin), scope: structuredClone(raw.scope),
    review: {profile: reviewedProfile, config, accountScope, reviewReference: raw.review.reviewReference},
    customerSessionCookie: sessionCookie(raw.customerSessionCookie)};
  if (raw.action === 'APPROVE') {
    if (apiKey) check(literal(raw.staffApiKey, 16384) && /^[\x21-\x7e]+$/.test(raw.staffApiKey), 'PRIVATE_STAFF_API_KEY_REQUIRED');
    else {
      input.staffSessionCookie = sessionCookie(raw.staffSessionCookie);
      check(input.staffSessionCookie !== input.customerSessionCookie, 'DISTINCT_STAFF_AND_CUSTOMER_SESSIONS_REQUIRED');
    }
  } else {
    input.credentials = textMap(raw.credentials, reviewedProfile.secretFields, 16384, 'CREDENTIAL_FIELDS_INVALID');
    check(typeof raw.approvalCheckpoint === 'string' && raw.approvalCheckpoint.length > 0, 'APPROVAL_CHECKPOINT_REQUIRED');
  }
  noSecrets({origin: input.origin, scope: input.scope, staffActorId: input.staffActorId,
    staffAuthenticationMode: input.staffAuthenticationMode, staffApiKeyHeaderName: input.staffApiKeyHeaderName ?? null, review: input.review,
    operationKey: input.operationKey}, privateValues(input));
  return input;
}

async function boundedJson(response) {
  check(response.body && typeof response.body.getReader === 'function', 'INVALID_JSON_RESPONSE');
  const reader = response.body.getReader(), chunks = [];
  let size = 0;
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      size += value.byteLength;
      check(size <= MAX_BYTES, 'RESPONSE_TOO_LARGE');
      chunks.push(Buffer.from(value));
    }
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch (error) {
    try { await reader.cancel(); } catch { /* No response content leaves this boundary. */ }
    throw error instanceof ExternalBindingError ? error : new ExternalBindingError('INVALID_JSON_RESPONSE');
  } finally { reader.releaseLock(); }
}
function transport(input, options) {
  const fetchImpl = options.fetchImpl ?? globalThis.fetch;
  return async (path, {staff = false, body} = {}) => {
    let response;
    try {
      response = await fetchImpl(input.origin + path, {method: body ? 'POST' : 'GET',
        headers: {Accept: 'application/json', ...(staff && input.staffAuthenticationMode === 'API_KEY'
          ? {[input.staffApiKeyHeaderName]: input.staffApiKey} : {Cookie: staff ? input.staffSessionCookie : input.customerSessionCookie}),
          ...(body ? {'Content-Type': 'application/json'} : {})},
        ...(body ? {body: JSON.stringify(body)} : {}), redirect: 'error', credentials: 'omit',
        signal: AbortSignal.timeout(30000)});
    } catch { throw new ExternalBindingError('TRANSPORT_OUTCOME_UNCERTAIN'); }
    return {status: response.status, payload: await boundedJson(response)};
  };
}
async function get(request, path) {
  const response = await request(path);
  check(response.status === 200, 'SCOPE_READBACK_DENIED');
  return response.payload;
}
function bindingPath(plan, staff = false) {
  const {offerId, deploymentId} = plan.scope;
  return `/api/customer-self-service/${staff ? 'staff/offers' : 'catalogue'}/${encodeURIComponent(offerId)}`
    + `/deployments/${encodeURIComponent(deploymentId)}/external-integrations`;
}
async function readBinding(request, plan) {
  const query = new URLSearchParams({versionId: plan.scope.versionId, targetProfileId: plan.scope.targetProfileId,
    bindingSlotId: plan.profile.bindingSlotId, selectedProfileId: plan.profile.id});
  const response = await request(bindingPath(plan) + '?' + query);
  if (response.status === 409 && response.payload?.success === false) {
    if (response.payload.errorCode === 'CONFLICT' && response.payload.message === 'EXTERNAL_INTEGRATION_BINDING_REQUIRED') return null;
  }
  // The customer exception handler uses 403 for BINDING_SLOT_FORBIDDEN.
  if (response.payload?.success === false && response.payload.errorCode === 'BINDING_SLOT_FORBIDDEN'
    && response.payload.message === 'BINDING_SLOT_FORBIDDEN' && [403, 409].includes(response.status)) {
    throw new ExternalBindingError('SERVER_READ_SLOT_AUTHORIZATION_GAP');
  }
  check(response.status === 200, 'BINDING_READBACK_DENIED');
  return summary(response.payload, plan, 'READ');
}
function summary(value, plan, kind) {
  exact(value, SUMMARY_FIELDS, 'UNSAFE_OR_INVALID_RECEIPT');
  check(identifier(value.id) && identifier(value.approvalId) && revision(value.revision)
    && value.deploymentId === plan.scope.deploymentId && value.versionId === plan.scope.versionId
    && value.targetProfileId === plan.scope.targetProfileId && value.bindingSlotId === plan.profile.bindingSlotId
    && value.selectedProfileId === plan.profile.id, 'RECEIPT_SCOPE_MISMATCH');
  if (kind === 'APPROVE') check(value.status === 'APPROVED_CONFIG_ONLY' && value.id === value.approvalId
    && value.revision === 0, 'APPROVAL_RECEIPT_INVALID');
  else if (kind === 'BIND') check(value.status === 'CONFIGURED_LOCAL_CREDENTIALS' && value.revision === 0
    && value.approvalId === plan.approvalId, 'BINDING_RECEIPT_INVALID');
  else check(['CONFIGURED', 'REVOKED'].includes(value.status), 'BINDING_STATUS_UNKNOWN');
  return structuredClone(value);
}
function requireConfigured(observed, receipt) {
  check(observed && observed.status === 'CONFIGURED' && receipt
    && equal({...observed, status: receipt.status}, receipt), 'BINDING_READBACK_MISMATCH');
}
function validateManifest(detail, selected, config) {
  check(object(detail?.plugin) && detail.plugin.id === selected.pluginId && detail.plugin.status === 'ACTIVE'
    && ['DATA', 'ACTION'].includes(detail.plugin.pluginType) && Array.isArray(detail.versions), 'PLUGIN_CONTRACT_MISMATCH');
  const versions = detail.versions.filter(value => value?.id === selected.pluginVersionId);
  check(versions.length === 1, 'EXACT_PLUGIN_VERSION_REQUIRED');
  const version = versions[0], manifest = version.manifest;
  check(version.status === 'PUBLISHED' && version.pluginId === selected.pluginId && object(manifest)
    && manifest.pluginId === selected.pluginId && manifest.version === version.version
    && hash(manifest) === selected.manifestSha256, 'MANIFEST_PIN_MISMATCH');
  check(Array.isArray(manifest.installForm), 'MANIFEST_FIELDS_INVALID');
  const fields = manifest.installForm;
  check(fields.every(field => object(field) && identifier(field.id) && field.required === true
    && !Object.hasOwn(field, 'defaultValue') && ['text', 'url', 'secretRef'].includes(field.type))
    && new Set(fields.map(field => field.id)).size === fields.length, 'MANIFEST_FIELDS_INVALID');
  check(equal(fields.filter(field => field.type !== 'secretRef').map(field => field.id).sort(), selected.configFields)
    && equal(fields.filter(field => field.type === 'secretRef').map(field => field.id).sort(), selected.secretFields)
    && equal(fields.filter(field => field.type === 'url').map(field => field.id).sort(), selected.endpointFields), 'MANIFEST_FIELDS_MISMATCH');
  check(object(manifest.contributions), 'EXECUTION_CONTRACT_INVALID');
  const {actions = [], datasets = []} = manifest.contributions;
  check(Array.isArray(actions) && Array.isArray(datasets), 'EXECUTION_CONTRACT_INVALID');
  check(detail.plugin.pluginType === 'DATA' ? datasets.length > 0 : actions.length > 0, 'EXECUTION_CONTRACT_INVALID');
  for (const action of actions) {
    const route = action?.route;
    check(object(route), 'EXECUTION_CONTRACT_INVALID');
    if (Object.hasOwn(route, 'sourceProjection')) continue;
    check(action.adapterType === 'connector-http' && !['url', 'urlTemplate', 'headers'].some(field => Object.hasOwn(route, field))
      && selected.endpointFields.includes(route.managedUrlField), 'UNREVIEWED_EXECUTION_ENDPOINT');
    const headers = route.managedHeaderSecretRefFields ?? {};
    check(object(headers) && Object.values(headers).every(field => selected.secretFields.includes(field)), 'UNREVIEWED_EXECUTION_CREDENTIAL');
  }
  for (const dataset of datasets) {
    const connection = dataset?.syncConnector?.connectionProfile, auth = connection?.auth;
    check(dataset?.ingestionMode === 'EXTERNAL_SYNC_HTTP' && object(connection) && object(auth)
      && !['baseUrl', 'allowedHosts'].some(field => Object.hasOwn(connection, field))
      && selected.endpointFields.includes(connection.managedBaseUrlField)
      && selected.configFields.includes(connection.managedAllowedHostField), 'UNREVIEWED_EXECUTION_ENDPOINT');
    check(endpoint(config[connection.managedBaseUrlField], selected.allowedHosts).hostname
      === config[connection.managedAllowedHostField], 'ENDPOINT_HOST_SCOPE_MISMATCH');
    check(!['tokenBaseUrl', 'apiKeyValue', 'token'].some(field => Object.hasOwn(auth, field))
      && (!Object.hasOwn(auth, 'managedTokenBaseUrlField') || selected.endpointFields.includes(auth.managedTokenBaseUrlField)),
    'UNREVIEWED_EXECUTION_ENDPOINT');
    const credentialFields = auth.credentialFields ?? [];
    check(Array.isArray(credentialFields) && credentialFields.every(field => object(field)
      && selected.secretFields.includes(field.secretRefField) && !Object.hasOwn(field, 'value'))
      && (!Object.hasOwn(auth, 'apiKeySecretRefField') || selected.secretFields.includes(auth.apiKeySecretRefField)),
    'UNREVIEWED_EXECUTION_CREDENTIAL');
    const webhookSecret = dataset.syncConnector.webhook?.verification?.secretRefField;
    check(webhookSecret === undefined || selected.secretFields.includes(webhookSecret), 'UNREVIEWED_EXECUTION_CREDENTIAL');
    const accountField = dataset.syncConnector.protectedResource?.resourceIdField;
    check(accountField === undefined || selected.accountScopeFields.includes(accountField), 'ACCOUNT_FIELD_NOT_REVIEWED');
  }
}
async function session(request, input, staff = false) {
  const {scope} = input, mode = staff ? input.staffAuthenticationMode : 'SESSION';
  const response = await request('/api/platform/auth/session', {staff});
  const value = response.payload;
  check(response.status === 200 && value?.enabled === true && value.authenticated === true
    && value.authenticationMode === mode && literal(value.actorId, 255)
    && (mode === 'SESSION' ? value.sessionAuthEnabled === true
      : value.apiKeyAuthEnabled === true && value.headerName === input.staffApiKeyHeaderName)
    && value.role === (staff ? 'PLATFORM_ADMIN' : 'CUSTOMER_ADMIN')
    && (!staff || value.actorId === input.staffActorId)
    && (staff ? value.customerId === null : value.customerId === scope.customerId), 'EXACT_SESSION_ACTOR_REQUIRED');
  if (mode === 'API_KEY') return;
  const directory = await request('/api/platform/users', {staff});
  check(directory.status === 200 && Array.isArray(directory.payload), 'SESSION_USER_DIRECTORY_REQUIRED');
  const users = directory.payload;
  users.forEach(user => exact(user, USER_FIELDS, 'SESSION_USER_DIRECTORY_INVALID'));
  check(users.every(user => identifier(user.id) && literal(user.email, 255)
    && (staff || user.customerId === scope.customerId))
    && new Set(users.map(user => user.id)).size === users.length, 'SESSION_USER_DIRECTORY_INVALID');
  const matches = users.filter(user => user.email === value.actorId);
  check(matches.length === 1, 'EXACT_SESSION_USER_REQUIRED');
  const user = matches[0];
  check(user.role === value.role && user.status === 'ACTIVE'
    && (staff ? user.customerId === null : user.id === scope.ownerUserId && user.customerId === scope.customerId),
  'EXACT_SESSION_USER_REQUIRED');
  return hash({actorId: value.actorId, userId: user.id, role: user.role, status: user.status, customerId: user.customerId});
}
async function authority(input, request, options) {
  const {scope, review} = input;
  const customerIdentitySha256 = await session(request, input);
  if (input.action === 'APPROVE') await session(request, input, true);
  const offer = await get(request, `/api/customer-self-service/catalogue/${encodeURIComponent(scope.offerId)}`);
  const entitlement = offer.entitlement;
  check(offer.id === scope.offerId && /^[a-f0-9]{64}$/.test(offer.fingerprint)
    && entitlement?.customerId === scope.customerId && entitlement.offerId === scope.offerId
    && entitlement.ownerUserId === scope.ownerUserId && entitlement.status === 'ACTIVE'
    && identifier(entitlement.id) && entitlement.revision === scope.expectedEntitlementRevision
    && revision(entitlement.revision) && literal(entitlement.expiresAt)
    && Number.isFinite(Date.parse(entitlement.expiresAt)) && Date.parse(entitlement.expiresAt) > (options.now?.() ?? new Date()).getTime(),
  'ENTITLEMENT_SCOPE_OR_REVISION_MISMATCH');
  check(Array.isArray(offer.targets) && Array.isArray(offer.permissions) && Array.isArray(offer.bindingSlots)
    && Array.isArray(offer.externalIntegrationProfiles), 'OFFER_CONTRACT_INVALID');
  check(new Set(offer.externalIntegrationProfiles.map(value => value?.id)).size === offer.externalIntegrationProfiles.length
    && new Set(offer.externalIntegrationProfiles.map(value => value?.bindingSlotId)).size === offer.externalIntegrationProfiles.length,
  'AMBIGUOUS_OFFER_PROFILES');
  const matches = offer.externalIntegrationProfiles.filter(value => value?.id === review.profile.id
    && value.bindingSlotId === review.profile.bindingSlotId);
  check(matches.length === 1, 'EXACT_OFFER_PROFILE_REQUIRED');
  const selected = profile(matches[0]);
  check(equal(selected, review.profile), 'REVIEWED_PROFILE_CHANGED');
  const slots = offer.bindingSlots.filter(slot => slot?.id === selected.bindingSlotId);
  check(slots.length === 1 && slots[0].kind === 'EXTERNAL_SERVICE' && slots[0].pluginId === selected.pluginId
    && slots[0].installConfigField === null && equal(slots[0].supportedProfileIds, [selected.id]), 'SLOT_CONTRACT_MISMATCH');
  const targets = offer.targets.filter(target => target?.id === scope.targetProfileId);
  check(targets.length === 1 && targets[0].intent === scope.targetIntent
    && targets[0].readiness === 'PLACEMENT_VERIFIED_ADMISSION_REQUIRED' && Array.isArray(targets[0].capabilities)
    && ['READ', 'BINDING_REPLACE'].every(op => targets[0].capabilities.includes(op)), 'REVIEWED_TARGET_NOT_READY');
  for (const op of ['READ', 'BINDING_REPLACE']) {
    const permissions = offer.permissions.filter(permission => permission?.operation === op);
    check(permissions.length === 1 && permissions[0].targetIntents?.includes(scope.targetIntent)
      && permissions[0].admissionEvidenceRequired === false
      && permissions[0].ownedBindingRequired === (op === 'BINDING_REPLACE')
      && permissions[0].staffAccess === (op === 'BINDING_REPLACE' ? 'ADMIN' : 'VIEWER'), 'REVIEWED_PERMISSION_REQUIRED');
  }
  const deployments = await get(request, '/api/customer-self-service/deployments');
  check(Array.isArray(deployments), 'DEPLOYMENT_PORTFOLIO_INVALID');
  const owned = deployments.filter(value => value?.deploymentId === scope.deploymentId);
  check(owned.length === 1, 'EXACT_CUSTOMER_DEPLOYMENT_REQUIRED');
  const deployment = owned[0];
  check(deployment.tenantId === scope.tenantId && deployment.offerId === scope.offerId
    && deployment.bootstrapTargetProfileId === scope.targetProfileId && deployment.offerFingerprint === offer.fingerprint
    && deployment.authorizationEntitlementRevision === scope.expectedEntitlementRevision
    && !['ARCHIVED', 'DELETED'].includes(deployment.status), 'DEPLOYMENT_SCOPE_OR_REVISION_MISMATCH');
  const versions = await get(request, `/api/deployments/${encodeURIComponent(scope.deploymentId)}/versions`);
  check(Array.isArray(versions), 'VERSION_READBACK_INVALID');
  const published = versions.filter(version => version?.id === scope.versionId);
  check(published.length === 1 && published[0].deploymentId === scope.deploymentId && published[0].status === 'PUBLISHED'
    && /^[a-f0-9]{64}$/.test(published[0].configHash), 'PUBLISHED_VERSION_SCOPE_MISMATCH');
  const detail = await get(request, `/api/marketplace/plugins/${encodeURIComponent(selected.pluginId)}`);
  validateManifest(detail, selected, review.config);
  return {profile: selected, authority: {customerIdentitySha256, offerFingerprint: offer.fingerprint, entitlementId: entitlement.id,
    entitlementExpiresAt: entitlement.expiresAt, versionConfigHash: published[0].configHash,
    authorizationEntitlementRevision: deployment.authorizationEntitlementRevision}};
}

function validatePrepared(prepared) {
  exact(prepared, ['plan', 'digest'], 'PREPARED_PLAN_INVALID');
  const plan = prepared.plan;
  exact(plan, ['schemaVersion', 'action', 'origin', 'scope', 'staffActorId', 'staffAuthenticationMode', 'staffApiKeyHeaderName',
    'profile', 'config', 'accountScope',
    'reviewReference', 'operationKey', 'authority', 'fenceKeyId', 'credentialCommitment', 'approvalId', 'approvalDigest'], 'PREPARED_PLAN_INVALID');
  check(plan.schemaVersion === PLAN_SCHEMA && ['APPROVE', 'BIND'].includes(plan.action)
    && literal(plan.staffActorId, 255) && ['SESSION', 'API_KEY'].includes(plan.staffAuthenticationMode)
    && (plan.staffAuthenticationMode === 'SESSION' ? plan.staffApiKeyHeaderName === null : apiKeyHeader(plan.staffApiKeyHeaderName))
    && sameMac(prepared.digest, hash(plan)), 'PREPARED_DIGEST_MISMATCH');
  return prepared;
}
function approvalBasis(plan) {
  return {origin: plan.origin, scope: plan.scope, staffActorId: plan.staffActorId,
    staffAuthenticationMode: plan.staffAuthenticationMode, staffApiKeyHeaderName: plan.staffApiKeyHeaderName, profile: plan.profile,
    config: plan.config, accountScope: plan.accountScope, reviewReference: plan.reviewReference,
    authority: plan.authority, fenceKeyId: plan.fenceKeyId};
}
async function buildPlan(input, options, expectedBinding = null, allowExistingApproval = false) {
  const request = transport(input, options), readback = await authority(input, request, options);
  const plan = {schemaVersion: PLAN_SCHEMA, action: input.action, origin: input.origin, scope: input.scope,
    staffActorId: input.staffActorId, staffAuthenticationMode: input.staffAuthenticationMode,
    staffApiKeyHeaderName: input.staffApiKeyHeaderName ?? null, profile: readback.profile, config: input.review.config,
    accountScope: input.review.accountScope, reviewReference: input.review.reviewReference, operationKey: input.operationKey,
    authority: readback.authority, fenceKeyId: hash(input.credentialFenceKey), credentialCommitment: null, approvalId: null, approvalDigest: null};
  if (input.action === 'BIND') {
    const approved = await readCheckpoint(input.approvalCheckpoint, input.credentialFenceKey);
    check(approved.state === 'SUCCEEDED' && approved.plan.action === 'APPROVE'
      && equal(approvalBasis(approved.plan), approvalBasis(plan)), 'EXACT_SUCCESSFUL_APPROVAL_CHECKPOINT_REQUIRED');
    summary(approved.receipt, approved.plan, 'APPROVE');
    check(approved.plan.operationKey !== input.operationKey, 'DISTINCT_OPERATION_KEYS_REQUIRED');
    plan.approvalId = approved.receipt.approvalId; plan.approvalDigest = approved.digest;
    plan.credentialCommitment = mac(input.credentialFenceKey, {purpose: 'external-binding-credentials/v1',
      scope: plan.scope, profile: plan.profile, config: plan.config, accountScope: plan.accountScope,
      approvalId: plan.approvalId, credentials: input.credentials});
  }
  const observed = await readBinding(request, plan);
  if (expectedBinding) requireConfigured(observed, expectedBinding);
  else if (allowExistingApproval && observed) check(observed.approvalId === allowExistingApproval
    && observed.status === 'CONFIGURED', 'BINDING_READBACK_MISMATCH');
  else check(observed === null, 'IMMUTABLE_BINDING_ALREADY_EXISTS');
  return noSecrets({plan, digest: hash(plan)}, privateValues(input));
}
async function guarded(callback) {
  try { return await callback(); }
  catch (error) { throw error instanceof ExternalBindingError ? error : new ExternalBindingError('LOCAL_OPERATION_FAILED'); }
}

/** GETs only. An existing binding, an ambiguous read or a server authorization gap blocks preparation. */
export async function prepareExternalBinding(raw, options = {}) {
  return guarded(async () => buildPlan(normalizeInput(raw), options));
}

async function privateDirectory(path) {
  check(typeof path === 'string' && path.length > 0 && resolve(path) === path, 'PRIVATE_CHECKPOINT_DIRECTORY_REQUIRED');
  const stat = await lstat(path);
  check(stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o077) === 0
    && stat.uid === process.getuid?.() && await realpath(path) === path, 'PRIVATE_CHECKPOINT_DIRECTORY_REQUIRED');
  return path;
}
async function privateRead(path) {
  check(typeof path === 'string' && resolve(path) === path, 'PRIVATE_CHECKPOINT_FILE_REQUIRED');
  await privateDirectory(dirname(path));
  const handle = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const stat = await handle.stat();
    check(stat.isFile() && stat.nlink === 1 && (stat.mode & 0o077) === 0 && stat.uid === process.getuid?.()
      && stat.size <= MAX_BYTES, 'PRIVATE_CHECKPOINT_FILE_REQUIRED');
    return JSON.parse(await handle.readFile('utf8'));
  } finally { await handle.close(); }
}
async function syncDirectory(path) {
  const handle = await open(path, constants.O_RDONLY);
  try { await handle.sync(); } finally { await handle.close(); }
}
async function durableWrite(path, value, exclusive = false) {
  await privateDirectory(dirname(path));
  const temporary = exclusive ? path : path + '.' + randomUUID() + '.tmp';
  let handle;
  try {
    handle = await open(temporary, constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
    await handle.writeFile(JSON.stringify(value, null, 2) + '\n');
    await handle.sync(); await handle.close(); handle = undefined;
    if (!exclusive) await rename(temporary, path);
    await syncDirectory(dirname(path));
  } finally {
    if (handle) await handle.close();
    if (!exclusive) { try { await unlink(temporary); } catch (error) { if (error.code !== 'ENOENT') throw error; } }
  }
}
async function readCheckpoint(path, key) {
  const value = await privateRead(path);
  exact(value, ['schemaVersion', 'plan', 'digest', 'state', 'receipt', 'failureCode', 'seal'], 'CHECKPOINT_INVALID');
  const {seal, ...body} = value;
  check(value.schemaVersion === CHECKPOINT_SCHEMA && STATES.includes(value.state) && sameMac(seal, mac(key, body)),
    'CHECKPOINT_INTEGRITY_INVALID');
  validatePrepared({plan: value.plan, digest: value.digest});
  check(value.plan.fenceKeyId === hash(key), 'CHECKPOINT_KEY_MISMATCH');
  if (value.receipt !== null) summary(value.receipt, value.plan, value.plan.action);
  check(!['SUCCEEDED', 'READBACK_PENDING'].includes(value.state) || value.receipt !== null, 'CHECKPOINT_INVALID');
  return value;
}
function checkpoint(prepared, state, receipt = null, failureCode = null) {
  return {schemaVersion: CHECKPOINT_SCHEMA, ...prepared, state, receipt, failureCode};
}
async function saveCheckpoint(path, value, input, exclusive = false) {
  const sealed = {...value, seal: mac(input.credentialFenceKey, value)};
  noSecrets(sealed, privateValues(input));
  await durableWrite(path, sealed, exclusive);
}
async function saveAfterDispatch(path, value, input) {
  try { await saveCheckpoint(path, value, input); }
  catch { throw new ExternalBindingError('CHECKPOINT_FAILURE_AFTER_DISPATCH_RECONCILIATION_ONLY'); }
}
function command(plan, input) {
  const scope = {versionId: plan.scope.versionId, targetProfileId: plan.scope.targetProfileId,
    bindingSlotId: plan.profile.bindingSlotId, selectedProfileId: plan.profile.id};
  return plan.action === 'APPROVE'
    ? {...scope, config: plan.config, accountScope: plan.accountScope, reviewReference: plan.reviewReference, operationKey: plan.operationKey}
    : {...scope, approvalId: plan.approvalId, expectedEntitlementRevision: plan.scope.expectedEntitlementRevision,
      operationKey: plan.operationKey, credentials: input.credentials};
}
function report(prepared, status, receipt = null, extra = {}) {
  return {schemaVersion: 'loomai-attended-external-binding-report/v1', digest: prepared.digest, action: prepared.plan.action,
    status, receipt, providerHealthProven: false, credentialRevisionProven: false, businessActionsExecuted: false,
    ...extra};
}
async function locked(path, callback) {
  let lock;
  try { lock = await open(path + '.lock', constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600); }
  catch { throw new ExternalBindingError('CHECKPOINT_LOCKED_OR_UNAVAILABLE'); }
  let result, failure;
  try { await lock.sync(); await syncDirectory(dirname(path)); result = await callback(); }
  catch (error) { failure = error; }
  try { await lock.close(); await unlink(path + '.lock'); await syncDirectory(dirname(path)); }
  catch { failure ??= new ExternalBindingError('CHECKPOINT_LOCK_CLEANUP_FAILED_RECONCILIATION_ONLY'); }
  if (failure) throw failure;
  return result;
}

/** Exactly one POST. Replay is explicit, identical and allowed only from a durable known-success receipt. */
export async function executeExternalBinding(raw, prepared, options = {}) {
  return guarded(async () => {
    const input = normalizeInput(raw);
    prepared = structuredClone(validatePrepared(prepared));
    check(options.reviewedDigest === prepared.digest, 'SEPARATELY_REVIEWED_DIGEST_REQUIRED');
    check(options.replay === undefined || typeof options.replay === 'boolean', 'EXPLICIT_REPLAY_BOOLEAN_REQUIRED');
    const directory = await privateDirectory(options.checkpointDirectory), path = join(directory, prepared.digest + '.json');
    return locked(path, async () => {
      let previous = null;
      try { previous = await readCheckpoint(path, input.credentialFenceKey); }
      catch (error) { if (error.code !== 'ENOENT') throw error; }
      if (options.replay) check(previous?.state === 'SUCCEEDED', 'KNOWN_SUCCESS_REQUIRED_FOR_REPLAY');
      else check(previous === null, 'EXISTING_INTENT_RECONCILIATION_ONLY');
      if (previous) check(equal(previous.plan, prepared.plan), 'CHECKPOINT_PLAN_MISMATCH');
      const fresh = await buildPlan(input, options, options.replay && prepared.plan.action === 'BIND' ? previous.receipt : null,
        options.replay && prepared.plan.action === 'APPROVE' ? previous.receipt.approvalId : false);
      check(equal(fresh, prepared), 'REVIEWED_PLAN_CHANGED');
      const replay = options.replay === true;
      await saveCheckpoint(path, checkpoint(prepared, replay ? 'REPLAY_INTENT' : 'INTENT', previous?.receipt ?? null), input, !replay);
      const request = transport(input, options);
      let receipt = null;
      try {
        const response = await request(bindingPath(prepared.plan, input.action === 'APPROVE')
          + (input.action === 'APPROVE' ? '/approvals' : '/bind'), {staff: input.action === 'APPROVE', body: command(prepared.plan, input)});
        check(response.status === 200, 'MUTATION_RESPONSE_NOT_CONFIRMED');
        receipt = summary(response.payload, prepared.plan, input.action);
        noSecrets(receipt, privateValues(input));
        if (replay) check(equal(receipt, previous.receipt), 'REPLAY_RECEIPT_MISMATCH');
      } catch (error) {
        const code = error instanceof ExternalBindingError ? error.code : 'MUTATION_OUTCOME_UNCERTAIN';
        await saveAfterDispatch(path, checkpoint(prepared, replay ? 'REPLAY_UNKNOWN' : 'UNKNOWN', previous?.receipt ?? null, code), input);
        throw new ExternalBindingError('MUTATION_OUTCOME_UNKNOWN_RECONCILIATION_ONLY');
      }
      if (input.action === 'BIND') {
        await saveAfterDispatch(path, checkpoint(prepared, 'READBACK_PENDING', receipt), input);
        try { requireConfigured(await readBinding(request, prepared.plan), receipt); }
        catch {
          throw new ExternalBindingError('CONFIRMED_RECEIPT_READBACK_PENDING_RECONCILIATION_ONLY');
        }
      }
      await saveAfterDispatch(path, checkpoint(prepared, 'SUCCEEDED', receipt), input);
      return noSecrets(report(prepared, 'SUCCEEDED', receipt, {replayVerified: replay}), privateValues(input));
    });
  });
}

/** GETs only; no retries, invented command lookup, credential reads or unknown-to-success inference. */
export async function reconcileExternalBinding(raw, prepared, options = {}) {
  return guarded(async () => {
    exact(raw, ['origin', 'customerSessionCookie', 'credentialFenceKey'], 'PRIVATE_RECONCILIATION_INPUT_INVALID');
    prepared = structuredClone(validatePrepared(prepared));
    const input = {origin: origin(raw.origin), customerSessionCookie: sessionCookie(raw.customerSessionCookie),
      credentialFenceKey: raw.credentialFenceKey};
    check(input.origin === prepared.plan.origin && /^[a-f0-9]{64}$/.test(input.credentialFenceKey), 'RECONCILIATION_SCOPE_MISMATCH');
    const directory = await privateDirectory(options.checkpointDirectory), path = join(directory, prepared.digest + '.json');
    const recorded = await readCheckpoint(path, input.credentialFenceKey);
    check(equal(recorded.plan, prepared.plan), 'CHECKPOINT_PLAN_MISMATCH');
    const request = transport(input, options);
    const current = await authority({...input, action: 'BIND', scope: prepared.plan.scope,
      staffActorId: prepared.plan.staffActorId, staffAuthenticationMode: prepared.plan.staffAuthenticationMode,
      staffApiKeyHeaderName: prepared.plan.staffApiKeyHeaderName,
      review: {profile: prepared.plan.profile, config: prepared.plan.config}}, request, options);
    check(equal(current, {profile: prepared.plan.profile, authority: prepared.plan.authority}), 'RECONCILIATION_AUTHORITY_CHANGED');
    if (prepared.plan.action === 'APPROVE') return noSecrets(report(prepared,
      recorded.state === 'SUCCEEDED' ? 'RECORDED_APPROVAL_SUCCESS' : 'UNRESOLVED_APPROVAL', recorded.receipt,
      {mutationAllowed: false, code: 'READ_ONLY_APPROVAL_AND_COMMAND_LOOKUP_UNAVAILABLE'}), privateValues(input));
    const observed = await readBinding(request, prepared.plan);
    if (recorded.state === 'SUCCEEDED' || recorded.state === 'READBACK_PENDING') {
      requireConfigured(observed, recorded.receipt);
      // Readback can complete an already confirmed POST, never an ambiguous POST.
      if (recorded.state === 'READBACK_PENDING') await locked(path, async () => {
        const latest = await readCheckpoint(path, input.credentialFenceKey);
        check(equal(latest, recorded), 'CHECKPOINT_CHANGED_DURING_RECONCILIATION');
        await saveCheckpoint(path, checkpoint(prepared, 'SUCCEEDED', recorded.receipt), input);
      });
      return noSecrets(report(prepared, 'CONFIRMED_COMMAND_AND_LOCAL_BINDING', observed,
        {mutationAllowed: false, checkpointState: 'SUCCEEDED'}), privateValues(input));
    }
    if (observed) check(observed.approvalId === prepared.plan.approvalId, 'RECONCILIATION_APPROVAL_MISMATCH');
    return noSecrets(report(prepared, observed ? 'BINDING_PRESENT_COMMAND_UNPROVEN' : 'BINDING_ABSENT_OUTCOME_UNRESOLVED', observed,
      {mutationAllowed: false, code: 'READ_ONLY_COMMAND_AND_CREDENTIAL_REVISION_LOOKUP_UNAVAILABLE'}), privateValues(input));
  });
}

async function main() {
  const args = process.argv.slice(2), mode = args.shift();
  check(['prepare', 'execute', 'replay', 'reconcile'].includes(mode), 'EXPLICIT_CLI_MODE_REQUIRED');
  const options = {};
  for (let index = 0; index < args.length; index++) {
    const name = args[index];
    check(['--live', '--plan', '--reviewed-digest', '--checkpoint-dir'].includes(name) && !Object.hasOwn(options, name), 'CLI_ARGUMENT_INVALID');
    options[name] = name === '--live' ? true : args[++index];
    check(options[name] && !String(options[name]).startsWith('--'), 'CLI_ARGUMENT_INVALID');
  }
  const required = mode === 'prepare' ? ['--live'] : mode === 'reconcile'
    ? ['--live', '--plan', '--checkpoint-dir'] : ['--live', '--plan', '--checkpoint-dir', '--reviewed-digest'];
  check(equal(Object.keys(options).sort(), required.sort()), 'EXPLICIT_LIVE_OPTIONS_REQUIRED');
  check(!process.stdin.isTTY, 'PRIVATE_JSON_STDIN_REQUIRED');
  let contents = '';
  for await (const chunk of process.stdin) { contents += chunk; check(Buffer.byteLength(contents) <= MAX_BYTES, 'PRIVATE_INPUT_TOO_LARGE'); }
  let input;
  try { input = JSON.parse(contents); } catch { throw new ExternalBindingError('PRIVATE_JSON_STDIN_REQUIRED'); }
  const prepared = mode === 'prepare' ? null : JSON.parse(await readFile(resolve(options['--plan']), 'utf8'));
  const runOptions = {reviewedDigest: options['--reviewed-digest'], checkpointDirectory: options['--checkpoint-dir'], replay: mode === 'replay'};
  const result = mode === 'prepare' ? await prepareExternalBinding(input)
    : mode === 'reconcile' ? await reconcileExternalBinding(input, prepared, runOptions)
    : await executeExternalBinding(input, prepared, runOptions);
  process.stdout.write(JSON.stringify(noSecrets(result, privateValues(input)), null, 2) + '\n');
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(error => { process.stderr.write((error instanceof ExternalBindingError ? error.code : 'LOCAL_OPERATION_FAILED') + '\n'); process.exitCode = 1; });
}
