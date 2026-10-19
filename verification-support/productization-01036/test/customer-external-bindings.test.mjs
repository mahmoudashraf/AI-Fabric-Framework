import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdtemp, chmod, link, lstat, readFile, readdir, realpath, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {test} from 'node:test';
import {ExternalBindingError, prepareExternalBinding, executeExternalBinding, reconcileExternalBinding} from '../customer-external-bindings.mjs';

const NOW = '2026-10-19T12:00:00.000Z';
const ORIGIN = 'https://control.example.test';
const STOCK_HOST = 'external-vehicle-provider-simulator.46.224.145.148.sslip.io';
const RECEIVER_HOST = 'loomai-dealership-demo-api.46.224.145.148.sslip.io';
const SCOPE = {customerId: 'cus-19a32c9b', ownerUserId: 'usr-7238e886',
  offerId: 'offer-loomai-engage-external-dealership-attended-v1', deploymentId: 'dep-70b3d2c1', tenantId: 'ten-ad10424d',
  versionId: 'ver-df8d2843', targetProfileId: 'dtp-coolify-staging-behavior', targetIntent: 'CUSTOMER_STAGING', expectedEntitlementRevision: 2};
const KEY = 'b7'.repeat(32);
const CUSTOMER_COOKIE = 'PLATFORM_SESSION=synthetic-customer-session';
const STAFF_COOKIE = 'PLATFORM_SESSION=synthetic-staff-session';
const CREDENTIALS = {stock: {providerKey: 'synthetic-provider-key-sentinel', providerSecret: 'synthetic-provider-secret-sentinel',
  notificationSecret: 'synthetic-notification-secret-sentinel'}, receiver: {receiverApiKey: 'synthetic-receiver-key-sentinel'}};
const SECRETS = [KEY, CUSTOMER_COOKIE, STAFF_COOKIE, CUSTOMER_COOKIE.split('=')[1], STAFF_COOKIE.split('=')[1],
  ...Object.values(CREDENTIALS.stock), ...Object.values(CREDENTIALS.receiver)];
const SUMMARY_FIELDS = ['id', 'approvalId', 'deploymentId', 'versionId', 'targetProfileId', 'bindingSlotId', 'selectedProfileId', 'status', 'revision'];
const JAVA = new URL('../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/', import.meta.url);
const copy = value => structuredClone(value);
function canonical(value) {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (value && typeof value === 'object') return '{' + Object.keys(value).sort()
    .map(key => JSON.stringify(key) + ':' + canonical(value[key])).join(',') + '}';
  return JSON.stringify(value);
}
const hash = value => createHash('sha256').update(canonical(value)).digest('hex');
function safe(value) { for (const secret of SECRETS) assert.ok(!JSON.stringify(value).includes(secret), 'private input must not escape'); }
const json = (value, status = 200) => new Response(JSON.stringify(value), {status, headers: {'Content-Type': 'application/json'}});
const denial = (code, status = 409, errorCode = 'CONFLICT') => json({timestamp: NOW, success: false, errorCode, message: code}, status);
const now = () => new Date(NOW);

// These are the actual V174/V196 DATA/ACTION protocol field shapes, not invented client endpoints.
function manifest(kind) {
  const stock = kind === 'stock';
  return {schemaVersion: 1, pluginId: stock ? 'mkp-data-dealership-external-stock-v1' : 'mkp-action-dealership-external-engagement-v1',
    version: '1.0.0', pluginType: stock ? 'DATA' : 'ACTION', pricing: {pricingModel: 'FREE'},
    installForm: stock ? [
      {id: 'providerBaseUrl', type: 'url', required: true}, {id: 'providerHost', type: 'text', required: true},
      {id: 'providerKey', type: 'secretRef', required: true}, {id: 'providerSecret', type: 'secretRef', required: true},
      {id: 'notificationSecret', type: 'secretRef', required: true}, {id: 'advertiserId', type: 'text', required: true},
    ] : [{id: 'receiverActionUrl', type: 'url', required: true}, {id: 'receiverApiKey', type: 'secretRef', required: true}],
    contributions: stock ? {datasets: [{datasetId: 'dealership-external-stock', ingestionMode: 'EXTERNAL_SYNC_HTTP',
      syncConnector: {connectionProfile: {profileId: 'external-stock-provider', managedBaseUrlField: 'providerBaseUrl',
        managedAllowedHostField: 'providerHost', auth: {strategy: 'FORM_TOKEN_EXCHANGE', managedTokenBaseUrlField: 'providerBaseUrl',
          credentialFields: [{name: 'key', secretRefField: 'providerKey'}, {name: 'secret', secretRefField: 'providerSecret'}]}},
      protectedResource: {bindingId: 'external-dealership-account', resourceIdField: 'advertiserId'},
      webhook: {sourceId: 'external-dealership-stock-events', verification: {secretRefField: 'notificationSecret'}}}}]}
      : {actions: [{actionId: 'dealership_request_callback', adapterType: 'connector-http', requiresConfirmation: true,
        route: {managedUrlField: 'receiverActionUrl', managedHeaderSecretRefFields: {'X-DEALERSHIP-INTERNAL-KEY': 'receiverApiKey'},
          method: 'POST', idempotencyHeader: 'Idempotency-Key'}}]}};
}
function reviewedProfile(kind) {
  const m = manifest(kind), stock = kind === 'stock';
  return {schemaVersion: 'loomai-external-integration-profile/v1', id: stock ? 'dealership-external-stock-v1' : 'dealership-external-engagement-v1',
    bindingSlotId: stock ? 'vehicle-stock' : 'business-actions', pluginId: m.pluginId,
    pluginVersionId: stock ? 'mkv-data-dealership-external-stock-v1-0' : 'mkv-action-dealership-external-engagement-v1-0',
    manifestSha256: hash(m), targetProfileIds: [SCOPE.targetProfileId, 'dtp-reviewed-production'],
    configFields: m.installForm.filter(field => field.type !== 'secretRef').map(field => field.id).sort(),
    secretFields: m.installForm.filter(field => field.type === 'secretRef').map(field => field.id).sort(),
    endpointFields: m.installForm.filter(field => field.type === 'url').map(field => field.id),
    accountScopeFields: stock ? ['advertiserId'] : [], allowedHosts: [stock ? STOCK_HOST : RECEIVER_HOST]};
}
function input(kind = 'stock', action = 'APPROVE', approvalCheckpoint) {
  const stock = kind === 'stock';
  return {action, origin: ORIGIN, scope: copy(SCOPE), staffActorId: 'usr-reviewed-staff-admin',
    review: {profile: reviewedProfile(kind), config: stock ? {providerBaseUrl: `https://${STOCK_HOST}`, providerHost: STOCK_HOST,
      advertiserId: 'advertiser990001'} : {receiverActionUrl: `https://${RECEIVER_HOST}/api/internal/actions/execute`},
    accountScope: stock ? {advertiserId: 'advertiser990001'} : {receiverAccountId: 'explicitly-reviewed-business-account'},
    reviewReference: 'owner-reviewed-external-fixtures-01044'}, operationKey: `${action.toLowerCase()}-${kind}-attended-001`,
    customerSessionCookie: CUSTOMER_COOKIE, credentialFenceKey: KEY,
    ...(action === 'APPROVE' ? {staffSessionCookie: STAFF_COOKIE} : {credentials: copy(CREDENTIALS[kind]), approvalCheckpoint})};
}
function auth(staff = false) {
  return {enabled: true, headerName: 'X-PLATFORM-API-KEY', authenticated: true,
    actorId: staff ? 'usr-reviewed-staff-admin' : SCOPE.ownerUserId, displayName: 'Synthetic Session',
    role: staff ? 'PLATFORM_ADMIN' : 'CUSTOMER_ADMIN', authenticationMode: 'SESSION', sessionAuthEnabled: true,
    apiKeyAuthEnabled: true, canManageUsers: staff, canManageUserDirectory: true, canManageCustomers: true,
    canCreateCustomers: staff, canManageSecrets: staff, canOperateDeployments: true,
    customerId: staff ? null : SCOPE.customerId, customerName: staff ? null : 'Fixture', customerSlug: staff ? null : 'fixture'};
}
function offer() {
  const profiles = ['stock', 'receiver'].map(reviewedProfile);
  return {id: SCOPE.offerId, name: 'Reviewed External Systems', templateId: 'mkp-template-dealership-external-systems-v1',
    templateVersionId: 'mkv-template-dealership-external-systems-v1-0', fingerprint: 'a'.repeat(64),
    entitlement: {id: 'ent-reviewed-customer', offerId: SCOPE.offerId, customerId: SCOPE.customerId,
      ownerUserId: SCOPE.ownerUserId, status: 'ACTIVE', revision: 2, expiresAt: '2026-10-20T12:00:00Z'},
    targets: [{id: SCOPE.targetProfileId, intent: 'CUSTOMER_STAGING', readiness: 'PLACEMENT_VERIFIED_ADMISSION_REQUIRED',
      capabilities: ['READ', 'BINDING_REPLACE'], limits: {}, blockers: ['PREREQUISITE_EVIDENCE_REQUIRED']}],
    permissions: ['READ', 'BINDING_REPLACE'].map(operation => ({operation, staffAccess: operation === 'READ' ? 'VIEWER' : 'ADMIN',
      targetIntents: ['CUSTOMER_STAGING', 'CUSTOMER_PRODUCTION'], settings: [], ownedBindingRequired: operation !== 'READ', admissionEvidenceRequired: false})),
    settings: [], bindingSlots: profiles.map(p => ({id: p.bindingSlotId, kind: 'EXTERNAL_SERVICE', supportedProfileIds: [p.id],
      pluginId: p.pluginId, installConfigField: null})), documentAudiences: ['PUBLIC'], blockers: ['PREREQUISITE_EVIDENCE_REQUIRED'],
    workspaceProfiles: [], externalIntegrationProfiles: profiles};
}
function portfolio() {
  return [{deploymentId: SCOPE.deploymentId, name: 'Configuration Only', environmentName: 'staging', status: 'DRAFT', tenantId: SCOPE.tenantId,
    offerId: SCOPE.offerId, bootstrapTargetProfileId: SCOPE.targetProfileId, offerFingerprint: 'a'.repeat(64),
    authorizationEntitlementRevision: 2, activeVersionId: null, createdAt: NOW}];
}
function version() {
  return {id: SCOPE.versionId, deploymentId: SCOPE.deploymentId, sourceDraftId: 'draft-reviewed', versionLabel: 'v1', status: 'PUBLISHED',
    configHash: 'c'.repeat(64), reindexRequired: false, publishedAt: NOW, entityConfigContractVersion: '1', aiFabricFrameworkVersion: '0.8.36',
    behaviorType: 'reviewed', behaviorContractVersion: '1', sourceCapabilityManifestRequired: true};
}
function detail(kind) {
  const m = manifest(kind), p = reviewedProfile(kind);
  return {plugin: {id: p.pluginId, slug: p.pluginId, displayName: 'Fixture Plugin', pluginType: m.pluginType, publisherSlug: 'loom',
    publisherDisplayName: 'Loom AI', shortDescription: 'Fixture', status: 'ACTIVE', latestVersion: '9.9.9', pricing: {}, categories: [],
    contributions: {}, updatedAt: NOW}, versions: [{id: 'mkv-unreviewed-latest', pluginId: p.pluginId, version: '9.9.9', status: 'PUBLISHED', manifest: {}},
    {id: p.pluginVersionId, pluginId: p.pluginId, version: '1.0.0', releaseChannel: 'EARLY_ACCESS', status: 'PUBLISHED', manifest: m,
      pricing: {}, compatibility: {}, installForm: m.installForm, permissions: {}, contributions: m.contributions, recommendedPluginIds: [], publishedAt: NOW}]};
}
function receipt(body, action, id = action === 'APPROVE' ? 'eia-reviewed-001' : 'eib-reviewed-001') {
  return {id, approvalId: action === 'APPROVE' ? id : body.approvalId, deploymentId: SCOPE.deploymentId,
    versionId: body.versionId, targetProfileId: body.targetProfileId, bindingSlotId: body.bindingSlotId,
    selectedProfileId: body.selectedProfileId, status: action === 'APPROVE' ? 'APPROVED_CONFIG_ONLY' : 'CONFIGURED_LOCAL_CREDENTIALS', revision: 0};
}
function fixture() {
  const api = {requests: [], postCount: 0, binding: null, offer: offer(), portfolio: portfolio(), versions: [version()],
    customerSession: auth(), staffSession: auth(true), details: {stock: detail('stock'), receiver: detail('receiver')},
    override: null, beforePost: null, afterPost: null, approvals: new Map(), bindings: new Map()};
  api.fetchImpl = async (url, request) => {
    const target = new URL(url);
    assert.equal(target.origin, ORIGIN, 'only the explicit control plane may be called');
    assert.equal(request.redirect, 'error'); assert.equal(request.credentials, 'omit');
    assert.ok(request.signal instanceof AbortSignal);
    assert.equal(request.headers.Authorization, undefined); assert.equal(request.headers['Idempotency-Key'], undefined);
    const body = request.body ? JSON.parse(request.body) : null;
    api.requests.push({url, path: target.pathname, method: request.method, body, cookie: request.headers.Cookie});
    if (api.override) { const result = await api.override(target, request, body); if (result !== undefined) return result; }
    if (request.method === 'GET') {
      assert.equal(body, null);
      if (target.pathname === '/api/platform/auth/session') return json(request.headers.Cookie === STAFF_COOKIE ? api.staffSession : api.customerSession);
      assert.equal(request.headers.Cookie, CUSTOMER_COOKIE);
      if (target.pathname === `/api/customer-self-service/catalogue/${SCOPE.offerId}`) return json(api.offer);
      if (target.pathname === '/api/customer-self-service/deployments') return json(api.portfolio);
      if (target.pathname === `/api/deployments/${SCOPE.deploymentId}/versions`) return json(api.versions);
      for (const kind of ['stock', 'receiver']) if (target.pathname === `/api/marketplace/plugins/${reviewedProfile(kind).pluginId}`) return json(api.details[kind]);
      if (target.pathname === `/api/customer-self-service/catalogue/${SCOPE.offerId}/deployments/${SCOPE.deploymentId}/external-integrations`) {
        assert.deepEqual(Object.keys(Object.fromEntries(target.searchParams)).sort(), ['bindingSlotId', 'selectedProfileId', 'targetProfileId', 'versionId']);
        assert.equal(target.searchParams.get('versionId'), SCOPE.versionId);
        assert.equal(target.searchParams.get('targetProfileId'), SCOPE.targetProfileId);
        return api.binding ? json(api.binding) : denial('EXTERNAL_INTEGRATION_BINDING_REQUIRED');
      }
    } else {
      api.postCount++;
      if (api.beforePost) await api.beforePost(target, body);
      const approvalPath = `/api/customer-self-service/staff/offers/${SCOPE.offerId}/deployments/${SCOPE.deploymentId}/external-integrations/approvals`;
      const bindPath = `/api/customer-self-service/catalogue/${SCOPE.offerId}/deployments/${SCOPE.deploymentId}/external-integrations/bind`;
      const approval = target.pathname === approvalPath;
      assert.ok(approval || target.pathname === bindPath, 'no other mutation route is allowed');
      assert.equal(request.headers.Cookie, approval ? STAFF_COOKIE : CUSTOMER_COOKIE);
      const store = approval ? api.approvals : api.bindings, key = body.operationKey;
      const old = store.get(key);
      if (old && !equalBody(old.body, body)) return denial('EXTERNAL_INTEGRATION_OPERATION_KEY_CONFLICT');
      const result = old?.receipt ?? receipt(body, approval ? 'APPROVE' : 'BIND');
      store.set(key, {body, receipt: result});
      if (!approval) api.binding = {...result, status: 'CONFIGURED'};
      if (api.afterPost) { const overridden = await api.afterPost(target, body, result); if (overridden !== undefined) return overridden; }
      return json(result);
    }
    throw new Error('Unrecognized synthetic route');
  };
  return api;
}
function equalBody(a, b) { return canonical(a) === canonical(b); }
async function directory(t) {
  const path = await mkdtemp(join(await realpath(tmpdir()), 'customer-external-bindings-'));
  await chmod(path, 0o700); t.after(() => rm(path, {recursive: true, force: true})); return path;
}
function options(api, dir, prepared, extra = {}) {
  return {fetchImpl: api.fetchImpl, now, checkpointDirectory: dir, reviewedDigest: prepared?.digest, ...extra};
}
async function checkpointFile(dir, prepared) { return JSON.parse(await readFile(join(dir, prepared.digest + '.json'), 'utf8')); }
async function approve(t, kind = 'stock', api = fixture(), dir) {
  dir ??= await directory(t);
  const raw = input(kind), prepared = await prepareExternalBinding(raw, options(api));
  const result = await executeExternalBinding(raw, prepared, options(api, dir, prepared));
  safe(result); return {api, dir, raw, prepared, result, approvalCheckpoint: join(dir, prepared.digest + '.json')};
}
async function bindPreparation(t, kind = 'stock') {
  const context = await approve(t, kind);
  const raw = input(kind, 'BIND', context.approvalCheckpoint);
  const prepared = await prepareExternalBinding(raw, options(context.api));
  return {...context, raw, prepared};
}
function reconciliationInput() { return {origin: ORIGIN, customerSessionCookie: CUSTOMER_COOKIE, credentialFenceKey: KEY}; }
function code(value) { return error => error instanceof ExternalBindingError && error.code === value; }

test('fixtures and outgoing bodies match actual Java records and route declarations', async () => {
  const contracts = await readFile(new URL('externalintegration/ExternalIntegrationContracts.java', JAVA), 'utf8');
  function fields(source, name) {
    const match = source.match(new RegExp(`public record ${name}\\(([\\s\\S]*?)\\)\\s*\\{`));
    assert.ok(match, name);
    return match[1].split(',').map(field => field.trim().match(/([A-Za-z][A-Za-z0-9]*)$/)[1]).sort();
  }
  assert.deepEqual(fields(contracts, 'Summary'), [...SUMMARY_FIELDS].sort());
  assert.deepEqual(fields(contracts, 'Profile'), Object.keys(reviewedProfile('stock')).sort());
  assert.deepEqual(fields(contracts, 'ApprovalRequest'), ['versionId', 'targetProfileId', 'bindingSlotId', 'selectedProfileId',
    'config', 'accountScope', 'reviewReference', 'operationKey'].sort());
  assert.deepEqual(fields(contracts, 'BindRequest'), ['versionId', 'targetProfileId', 'bindingSlotId', 'selectedProfileId',
    'approvalId', 'expectedEntitlementRevision', 'operationKey', 'credentials'].sort());
  const customer = await readFile(new URL('customerselfservice/model/CustomerOfferContracts.java', JAVA), 'utf8');
  assert.deepEqual(fields(customer, 'OfferSummary'), Object.keys(offer()).sort());
  assert.deepEqual(fields(customer, 'CustomerDeploymentSummary'), Object.keys(portfolio()[0]).sort());
  assert.deepEqual(fields(customer, 'EntitlementSummary'), Object.keys(offer().entitlement).sort());
  const authentication = await readFile(new URL('security/model/PlatformAuthSessionSummary.java', JAVA), 'utf8');
  assert.deepEqual(fields(authentication, 'PlatformAuthSessionSummary'), Object.keys(auth()).sort());
  const versions = await readFile(new URL('deployment/model/DeploymentVersionSummary.java', JAVA), 'utf8');
  assert.deepEqual(fields(versions, 'DeploymentVersionSummary'), Object.keys(version()).sort());
  const controller = await readFile(new URL('externalintegration/ExternalIntegrationController.java', JAVA), 'utf8');
  for (const route of ['/staff/offers/{offerId}/deployments/{deploymentId}/external-integrations/approvals',
    '/catalogue/{offerId}/deployments/{deploymentId}/external-integrations/bind', '/catalogue/{offerId}/deployments/{deploymentId}/external-integrations']) {
    assert.ok(controller.includes(`"${route}"`));
  }
  const migration = await readFile(new URL('../../../Platfrom/backend/src/main/resources/db/migration/V196__external_dealership_ai_composition.sql', import.meta.url), 'utf8');
  const installForms = [...migration.matchAll(/'installForm',\s*'(\[[\s\S]*?\])'::jsonb/g)].map(match => JSON.parse(match[1]));
  for (const kind of ['stock', 'receiver']) assert.ok(installForms.some(form => equalBody(form.map(({id, type, required}) => ({id, type, required})), manifest(kind).installForm)));
});

test('prepare uses real CUSTOMER_ADMIN/staff sessions and GETs only, with explicit profile/version/account pins', async () => {
  const api = fixture(), raw = input(), prepared = await prepareExternalBinding(raw, options(api));
  safe(prepared); assert.equal(api.postCount, 0); assert.ok(api.requests.every(request => request.method === 'GET'));
  assert.equal(prepared.plan.scope.expectedEntitlementRevision, 2);
  assert.equal(prepared.plan.profile.pluginVersionId, raw.review.profile.pluginVersionId);
  assert.equal(prepared.plan.authority.versionConfigHash, 'c'.repeat(64));
  assert.equal(prepared.plan.config.advertiserId, 'advertiser990001');
  assert.equal(prepared.plan.credentialCommitment, null); assert.equal(prepared.plan.approvalId, null);
  assert.equal(prepared.digest, hash(prepared.plan));
  assert.equal(prepared.plan.profile.targetProfileIds.includes('dtp-reviewed-production'), true);
  assert.equal(api.requests.some(request => request.path.includes('placement-evidence')), false);
});

test('both exact DATA and ACTION approvals/bindings persist intent before one POST and verify explicit replay', async t => {
  for (const kind of ['stock', 'receiver']) {
    const api = fixture(), dir = await directory(t), raw = input(kind);
    const prepared = await prepareExternalBinding(raw, options(api));
    let checkedIntents = 0;
    api.beforePost = async () => {
      const names = await readdir(dir), files = names.filter(name => name.endsWith('.json'));
      const rows = await Promise.all(files.map(name => readFile(join(dir, name), 'utf8').then(JSON.parse)));
      const intent = rows.find(row => ['INTENT', 'REPLAY_INTENT'].includes(row.state));
      assert.ok(intent); assert.equal(intent.digest, hash(intent.plan)); assert.match(intent.seal, /^[a-f0-9]{64}$/);
      assert.equal((await lstat(join(dir, intent.digest + '.json'))).mode & 0o077, 0);
      rows.forEach(safe); checkedIntents++;
    };
    const approved = await executeExternalBinding(raw, prepared, options(api, dir, prepared));
    assert.equal(approved.status, 'SUCCEEDED'); assert.equal(api.postCount, 1);
    const approvalReplay = await executeExternalBinding(raw, prepared, options(api, dir, prepared, {replay: true}));
    assert.deepEqual(approvalReplay.receipt, approved.receipt); assert.equal(approvalReplay.replayVerified, true);
    const bindInput = input(kind, 'BIND', join(dir, prepared.digest + '.json'));
    const bindPlan = await prepareExternalBinding(bindInput, options(api));
    safe(bindPlan); assert.match(bindPlan.plan.credentialCommitment, /^[a-f0-9]{64}$/);
    assert.notEqual(bindPlan.plan.credentialCommitment, hash(bindInput.credentials));
    assert.equal(bindPlan.plan.approvalId, approved.receipt.id);
    const bound = await executeExternalBinding(bindInput, bindPlan, options(api, dir, bindPlan));
    assert.equal(bound.receipt.status, 'CONFIGURED_LOCAL_CREDENTIALS'); assert.equal(bound.providerHealthProven, false);
    const replayed = await executeExternalBinding(bindInput, bindPlan, options(api, dir, bindPlan, {replay: true}));
    assert.equal(replayed.replayVerified, true); assert.deepEqual(bound.receipt, replayed.receipt);
    assert.equal(checkedIntents, 4); assert.equal(api.postCount, 4);
    const posts = api.requests.filter(request => request.method === 'POST');
    assert.deepEqual(posts[0].body, posts[1].body); assert.deepEqual(posts[2].body, posts[3].body);
    assert.deepEqual(Object.keys(posts[2].body).sort(), ['versionId', 'targetProfileId', 'bindingSlotId', 'selectedProfileId',
      'approvalId', 'expectedEntitlementRevision', 'operationKey', 'credentials'].sort());
    assert.deepEqual(Object.keys(posts[2].body.credentials).sort(), bindInput.review.profile.secretFields);
    assert.equal(Object.hasOwn(posts[2].body, 'config'), false);
    const recorded = await checkpointFile(dir, bindPlan); assert.equal(recorded.state, 'SUCCEEDED'); safe(recorded);
    const reconciliation = await reconcileExternalBinding(reconciliationInput(), bindPlan, options(api, dir));
    assert.equal(reconciliation.status, 'CONFIRMED_COMMAND_AND_LOCAL_BINDING'); assert.equal(api.postCount, 4); safe(reconciliation);
  }
});

test('scope, exact customer/session actor, entitlement, immutable version and slot/profile revisions fail closed', async () => {
  const changes = [api => api.customerSession.role = 'PLATFORM_ADMIN', api => api.customerSession.actorId = 'usr-foreign',
    api => api.customerSession.customerId = 'cus-foreign', api => api.customerSession.authenticationMode = 'API_KEY',
    api => api.customerSession.enabled = false, api => api.staffSession.role = 'PLATFORM_OPERATOR', api => api.staffSession.actorId = 'usr-other-staff',
    api => api.offer.entitlement.ownerUserId = 'usr-foreign', api => api.offer.entitlement.customerId = 'cus-foreign',
    api => api.offer.entitlement.offerId = 'offer-foreign', api => api.offer.entitlement.status = 'INVITED',
    api => api.offer.entitlement.expiresAt = NOW, api => api.offer.entitlement.revision = 3,
    api => api.offer.entitlement.revision = '2', api => api.offer.fingerprint = 'not-a-hash',
    api => api.portfolio[0].tenantId = 'ten-foreign', api => api.portfolio[0].offerId = 'offer-foreign',
    api => api.portfolio[0].bootstrapTargetProfileId = 'dtp-foreign', api => api.portfolio[0].offerFingerprint = 'f'.repeat(64),
    api => api.portfolio[0].authorizationEntitlementRevision = 1, api => api.portfolio.push(copy(api.portfolio[0])),
    api => api.versions[0].deploymentId = 'dep-foreign', api => api.versions[0].status = 'DRAFT',
    api => api.versions[0].id = 'ver-other', api => api.versions.push(copy(api.versions[0])),
    api => api.offer.targets[0].intent = 'CUSTOMER_PRODUCTION', api => api.offer.targets[0].readiness = 'PLACEMENT_RECHECK_REQUIRED',
    api => api.offer.targets[0].capabilities = ['READ'], api => api.offer.permissions[1].ownedBindingRequired = false,
    api => api.offer.bindingSlots[0].kind = 'CUSTOMER_S3', api => api.offer.bindingSlots[0].installConfigField = 'providerUrl',
    api => api.offer.bindingSlots[0].supportedProfileIds.push('ambiguous-profile'),
    api => api.offer.externalIntegrationProfiles.push(copy(api.offer.externalIntegrationProfiles[0])),
    api => api.offer.externalIntegrationProfiles[0].manifestSha256 = 'd'.repeat(64),
    api => api.offer.externalIntegrationProfiles[0].targetProfileIds = ['dtp-other']];
  for (const change of changes) {
    const api = fixture(); change(api);
    await assert.rejects(prepareExternalBinding(input(), options(api)), ExternalBindingError); assert.equal(api.postCount, 0);
  }
});

test('no semantic profile fallback, host default or account inference; malformed private inputs fail before I/O', async () => {
  const changes = [raw => delete raw.scope.tenantId, raw => raw.scope.expectedEntitlementRevision = '2',
    raw => raw.scope.expectedEntitlementRevision = -1, raw => raw.scope.expectedEntitlementRevision = 2.5,
    raw => raw.scope.expectedEntitlementRevision = Number.MAX_SAFE_INTEGER + 1,
    raw => raw.scope.customerId = '../foreign', raw => raw.origin = 'http://control.example.test',
    raw => raw.origin += '/api', raw => raw.origin = 'https://user:secret@control.example.test',
    raw => raw.customerSessionCookie += '; OTHER=private', raw => raw.staffSessionCookie = raw.customerSessionCookie,
    raw => delete raw.review.profile, raw => raw.review.profile.id = 'external-stock-looking-like-a-match',
    raw => raw.review.profile.allowedHosts = [], raw => raw.review.profile.allowedHosts = ['*.sslip.io'],
    raw => raw.review.profile.allowedHosts = ['127.0.0.1'], raw => raw.review.profile.allowedHosts = ['service.internal'],
    raw => raw.review.profile.allowedHosts.push(STOCK_HOST), raw => raw.review.profile.configFields.push('providerKey'),
    raw => raw.review.accountScope = {}, raw => raw.review.accountScope.advertiserId = 'foreign-account',
    raw => raw.review.config.advertiserId = 990001, raw => raw.review.config.unreviewed = 'extra',
    raw => raw.review.config.providerKey = CREDENTIALS.stock.providerKey,
    raw => raw.review.reviewReference = '${UNREVIEWED}', raw => raw.credentialFenceKey = 'short',
    raw => raw.unreviewed = true];
  for (const change of changes) {
    const api = fixture(), raw = input(); change(raw);
    await assert.rejects(prepareExternalBinding(raw, options(api)), ExternalBindingError); assert.equal(api.postCount, 0);
  }
});

test('endpoints require exact reviewed hosts and unambiguous HTTPS paths, never suffix or keyword matching', async () => {
  const endpoints = [`http://${STOCK_HOST}`, `https://${STOCK_HOST}.evil.example.test`, `https://user:secret@${STOCK_HOST}`,
    `https://${STOCK_HOST}:444/`, `https://${STOCK_HOST}/?key=private`, `https://${STOCK_HOST}/#fragment`,
    `https://${STOCK_HOST}/a/../stock`, `https://${STOCK_HOST}/a/./stock`, `https://${STOCK_HOST}//stock`,
    `https://${STOCK_HOST}/%2e%2e/stock`, `https://${STOCK_HOST}/stock%3fkey`, `https://${STOCK_HOST.toUpperCase()}/`,
    'https://127.0.0.1/stock', `https://${STOCK_HOST}\\@evil.example.test/stock`];
  for (const value of endpoints) {
    const api = fixture(), raw = input(); raw.review.config.providerBaseUrl = value;
    await assert.rejects(prepareExternalBinding(raw, options(api)), ExternalBindingError); assert.equal(api.postCount, 0);
  }
  const api = fixture(), raw = input(); raw.review.config.providerHost = RECEIVER_HOST;
  await assert.rejects(prepareExternalBinding(raw, options(api)), code('ENDPOINT_HOST_SCOPE_MISMATCH'));
  const receiver = input('receiver'); receiver.review.accountScope = {};
  await assert.rejects(prepareExternalBinding(receiver, options(fixture())), code('EXPLICIT_ACCOUNT_SCOPE_REQUIRED'));
  receiver.review.accountScope = {receiverApiKey: CREDENTIALS.receiver.receiverApiKey};
  await assert.rejects(prepareExternalBinding(receiver, options(fixture())), code('ACCOUNT_SCOPE_SECRET_FIELD_FORBIDDEN'));
});

test('exact manifest pin/type/form/managed endpoints and credential/account mappings are revalidated', async () => {
  const changes = [d => d.plugin.pluginType = 'TEMPLATE', d => d.plugin.status = 'RETIRED',
    d => d.versions[1].id = 'mkv-other', d => d.versions.push(copy(d.versions[1])),
    d => d.versions[1].status = 'DRAFT', d => d.versions[1].manifest.pluginId = 'mkp-foreign',
    d => d.versions[1].manifest.installForm[0].type = 'text', d => d.versions[1].manifest.installForm[0].defaultValue = 'https://unreviewed.example.test',
    d => d.versions[1].manifest.installForm.push(copy(d.versions[1].manifest.installForm[0])),
    d => d.versions[1].manifest.contributions.datasets[0].syncConnector.connectionProfile.baseUrl = `https://${STOCK_HOST}`,
    d => d.versions[1].manifest.contributions.datasets[0].syncConnector.connectionProfile.auth.token = CREDENTIALS.stock.providerKey,
    d => d.versions[1].manifest.contributions.datasets[0].syncConnector.connectionProfile.auth.credentialFields[0].secretRefField = 'otherSecret',
    d => d.versions[1].manifest.contributions.datasets[0].syncConnector.protectedResource.resourceIdField = 'unknownAccount'];
  for (const change of changes) {
    const api = fixture(); change(api.details.stock);
    await assert.rejects(prepareExternalBinding(input(), options(api)), ExternalBindingError); assert.equal(api.postCount, 0);
  }
  // Even a separately supplied matching hash cannot authorize an unmanaged route or field default.
  for (const mutate of [m => m.installForm[0].defaultValue = `https://${RECEIVER_HOST}`,
    m => m.contributions.actions[0].route.url = `https://${RECEIVER_HOST}/api/internal/actions/execute`,
    m => m.contributions.actions[0].route.managedHeaderSecretRefFields = {'X-KEY': 'unreviewedSecret'}]) {
    const api = fixture(), raw = input('receiver'), m = api.details.receiver.versions[1].manifest; mutate(m);
    raw.review.profile.manifestSha256 = hash(m); api.offer.externalIntegrationProfiles[1].manifestSha256 = hash(m);
    await assert.rejects(prepareExternalBinding(raw, options(api)), ExternalBindingError); assert.equal(api.postCount, 0);
  }
});

test('actual server READ slot authorization gap blocks prepare; generic 404/409/500 is not absence', async () => {
  for (const [status, payload, expectedCode] of [[403, {success: false, errorCode: 'BINDING_SLOT_FORBIDDEN', message: 'BINDING_SLOT_FORBIDDEN'}, 'SERVER_READ_SLOT_AUTHORIZATION_GAP'],
    [404, {message: 'EXTERNAL_INTEGRATION_BINDING_REQUIRED'}, 'BINDING_READBACK_DENIED'],
    [409, {success: false, errorCode: 'CONFLICT', message: 'EXTERNAL_INTEGRATION_APPROVAL_STALE_OR_FOREIGN'}, 'BINDING_READBACK_DENIED'],
    [500, {success: false, errorCode: 'CONFLICT', message: 'EXTERNAL_INTEGRATION_BINDING_REQUIRED'}, 'BINDING_READBACK_DENIED']]) {
    const api = fixture(); api.override = target => target.pathname.endsWith('/external-integrations') ? json(payload, status) : undefined;
    await assert.rejects(prepareExternalBinding(input(), options(api)), code(expectedCode)); assert.equal(api.postCount, 0);
  }
});

test('separate digest and private synced checkpoint are mandatory; stale reviewed plans never POST', async t => {
  const api = fixture(), dir = await directory(t), raw = input(), prepared = await prepareExternalBinding(raw, options(api));
  for (const reviewedDigest of [undefined, 'd'.repeat(64)]) {
    await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared, {reviewedDigest})), code('SEPARATELY_REVIEWED_DIGEST_REQUIRED'));
  }
  await chmod(dir, 0o755);
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)), code('PRIVATE_CHECKPOINT_DIRECTORY_REQUIRED'));
  await chmod(dir, 0o700);
  api.versions[0].configHash = 'd'.repeat(64);
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)), code('REVIEWED_PLAN_CHANGED'));
  assert.equal(api.postCount, 0); assert.deepEqual(await readdir(dir), []);
  const tampered = copy(prepared); tampered.plan.config.advertiserId = 'changed';
  await assert.rejects(executeExternalBinding(raw, tampered, options(api, dir, tampered)), code('PREPARED_DIGEST_MISMATCH'));
});

test('lost approval response is durable UNKNOWN, cannot reexecute/replay, and has no invented GET lookup', async t => {
  const api = fixture(), dir = await directory(t), raw = input(), prepared = await prepareExternalBinding(raw, options(api));
  api.afterPost = () => { throw new Error(SECRETS.join(' ')); };
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)), code('MUTATION_OUTCOME_UNKNOWN_RECONCILIATION_ONLY'));
  assert.equal(api.postCount, 1); assert.equal((await checkpointFile(dir, prepared)).state, 'UNKNOWN');
  safe(await checkpointFile(dir, prepared));
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)), code('EXISTING_INTENT_RECONCILIATION_ONLY'));
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared, {replay: true})), code('KNOWN_SUCCESS_REQUIRED_FOR_REPLAY'));
  const before = api.requests.length, result = await reconcileExternalBinding(reconciliationInput(), prepared, options(api, dir));
  assert.equal(result.status, 'UNRESOLVED_APPROVAL'); assert.equal(result.mutationAllowed, false); safe(result);
  assert.ok(api.requests.slice(before).every(request => request.method === 'GET' && !request.path.endsWith('/approvals')));
  assert.equal(api.postCount, 1);
  const next = input('stock', 'BIND', join(dir, prepared.digest + '.json'));
  await assert.rejects(prepareExternalBinding(next, options(api)), code('EXACT_SUCCESSFUL_APPROVAL_CHECKPOINT_REQUIRED'));
});

test('unknown bind with or without a visible binding remains command/credential-unproven and GET-only', async t => {
  for (const present of [true, false]) {
    const {api, dir, raw, prepared} = await bindPreparation(t);
    api.afterPost = () => { if (!present) api.binding = null; throw Error(CREDENTIALS.stock.providerKey); };
    await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)), code('MUTATION_OUTCOME_UNKNOWN_RECONCILIATION_ONLY'));
    assert.equal(api.postCount, 2); safe(await checkpointFile(dir, prepared));
    const result = await reconcileExternalBinding(reconciliationInput(), prepared, options(api, dir));
    assert.equal(result.status, present ? 'BINDING_PRESENT_COMMAND_UNPROVEN' : 'BINDING_ABSENT_OUTCOME_UNRESOLVED');
    assert.equal(result.credentialRevisionProven, false); assert.equal(result.mutationAllowed, false);
    await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared, {replay: true})), code('KNOWN_SUCCESS_REQUIRED_FOR_REPLAY'));
    assert.equal(api.postCount, 2); assert.equal((await checkpointFile(dir, prepared)).state, 'UNKNOWN'); safe(result);
  }
});

test('a confirmed bind receipt with interrupted GET can be reconciled read-only, then explicitly replayed', async t => {
  const {api, dir, raw, prepared} = await bindPreparation(t);
  api.override = target => target.pathname.endsWith('/external-integrations') && api.binding ? json({private: CREDENTIALS.stock.providerKey}, 503) : undefined;
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)), code('CONFIRMED_RECEIPT_READBACK_PENDING_RECONCILIATION_ONLY'));
  assert.equal((await checkpointFile(dir, prepared)).state, 'READBACK_PENDING');
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared, {replay: true})), code('KNOWN_SUCCESS_REQUIRED_FOR_REPLAY'));
  api.override = null;
  const result = await reconcileExternalBinding(reconciliationInput(), prepared, options(api, dir));
  assert.equal(result.status, 'CONFIRMED_COMMAND_AND_LOCAL_BINDING'); assert.equal(api.postCount, 2);
  assert.equal((await checkpointFile(dir, prepared)).state, 'SUCCEEDED');
  await executeExternalBinding(raw, prepared, options(api, dir, prepared, {replay: true})); assert.equal(api.postCount, 3);
});

test('credentials, key, account/endpoint review or operation identity cannot change on execute/replay', async t => {
  const {api, dir, raw, prepared} = await bindPreparation(t);
  for (const mutate of [r => r.credentials.providerKey = 'synthetic-changed-key', r => r.credentialFenceKey = 'c8'.repeat(32),
    r => r.operationKey += '-changed', r => r.review.config.advertiserId = 'foreign',
    r => r.review.accountScope.advertiserId = 'foreign', r => r.credentials.extra = 'unreviewed',
    r => delete r.credentials.providerSecret, r => r.credentials.providerSecret = 'line\nbreak',
    r => r.credentials.providerSecret = '${EXISTING_SECRET_REF}', r => r.credentials.providerSecret = 123,
    r => r.credentials.providerSecret = {secretRef: 'existing'}, r => r.credentials.providerSecret = 'x'.repeat(16385)]) {
    const changed = copy(raw); mutate(changed);
    await assert.rejects(executeExternalBinding(changed, prepared, options(api, dir, prepared)), ExternalBindingError);
    assert.equal(api.postCount, 1);
  }
  await executeExternalBinding(raw, prepared, options(api, dir, prepared));
  const changed = copy(raw); changed.credentials.providerKey = 'synthetic-changed-key';
  await assert.rejects(executeExternalBinding(changed, prepared, options(api, dir, prepared, {replay: true})), code('REVIEWED_PLAN_CHANGED'));
  assert.equal(api.postCount, 2);
});

test('approval checkpoint is authenticated, successful and exact in deployment/tenant/version/target/account/contract', async t => {
  const {api, dir, approvalCheckpoint} = await approve(t);
  const original = await readFile(approvalCheckpoint, 'utf8');
  const raw = input('stock', 'BIND', approvalCheckpoint);
  for (const mutate of [row => row.receipt.approvalId = 'eia-forged', row => row.state = 'UNKNOWN',
    row => row.plan.scope.tenantId = 'ten-foreign', row => row.plan.config.advertiserId = 'foreign', row => row.seal = 'd'.repeat(64)]) {
    const row = JSON.parse(original); mutate(row); await writeFile(approvalCheckpoint, JSON.stringify(row), {mode: 0o600});
    await assert.rejects(prepareExternalBinding(raw, options(api)), ExternalBindingError); assert.equal(api.postCount, 1);
  }
  await writeFile(approvalCheckpoint, original, {mode: 0o600});
  const changed = copy(raw); changed.review.accountScope.reviewedBusiness = 'another-scope';
  await assert.rejects(prepareExternalBinding(changed, options(api)), code('EXACT_SUCCESSFUL_APPROVAL_CHECKPOINT_REQUIRED'));
  await chmod(approvalCheckpoint, 0o644);
  await assert.rejects(prepareExternalBinding(raw, options(api)), code('PRIVATE_CHECKPOINT_FILE_REQUIRED'));
  await chmod(approvalCheckpoint, 0o600);
  const symlinkPath = join(dir, 'approval-link.json'); await symlink(approvalCheckpoint, symlinkPath);
  await assert.rejects(prepareExternalBinding({...raw, approvalCheckpoint: symlinkPath}, options(api)), ExternalBindingError);
  const hardlinkPath = join(dir, 'approval-hardlink.json'); await link(approvalCheckpoint, hardlinkPath);
  await assert.rejects(prepareExternalBinding({...raw, approvalCheckpoint: hardlinkPath}, options(api)), code('PRIVATE_CHECKPOINT_FILE_REQUIRED'));
});

test('success is never reported for foreign/stale/expanded/malformed/secret-echo mutation receipts', async t => {
  for (const mutate of [r => r.deploymentId = 'dep-foreign', r => r.versionId = 'ver-foreign', r => r.targetProfileId = 'dtp-other',
    r => r.selectedProfileId = 'other-profile', r => r.revision = '0', r => r.status = 'HEALTHY',
    r => r.credentials = CREDENTIALS.stock, r => r.id = CUSTOMER_COOKIE, r => r.approvalId = 'eia-foreign',
    r => { r.id = CUSTOMER_COOKIE.split('=')[1]; r.approvalId = r.id; }]) {
    const api = fixture(), dir = await directory(t), raw = input(), prepared = await prepareExternalBinding(raw, options(api));
    api.afterPost = (_target, _body, result) => { const changed = copy(result); mutate(changed); return json(changed); };
    await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)), code('MUTATION_OUTCOME_UNKNOWN_RECONCILIATION_ONLY'));
    assert.equal(api.postCount, 1); const row = await checkpointFile(dir, prepared); assert.equal(row.state, 'UNKNOWN'); safe(row);
  }
});

test('unknown HTTP results, malformed/oversized JSON and untrusted errors never echo or trigger another POST', async t => {
  for (const response of [() => json({message: CREDENTIALS.stock.providerKey}, 409), () => json({message: STAFF_COOKIE}, 503),
    () => new Response('not-json-' + CREDENTIALS.stock.providerSecret), () => new Response('x'.repeat(1024 * 1024 + 1)),
    () => { throw new ExternalBindingError(CREDENTIALS.stock.notificationSecret); }]) {
    const api = fixture(), dir = await directory(t), raw = input(), prepared = await prepareExternalBinding(raw, options(api));
    api.afterPost = response;
    try { await executeExternalBinding(raw, prepared, options(api, dir, prepared)); assert.fail('must fail'); }
    catch (error) { assert.equal(error.code, 'MUTATION_OUTCOME_UNKNOWN_RECONCILIATION_ONLY'); safe({message: error.message, code: error.code}); }
    assert.equal(api.postCount, 1); safe(await checkpointFile(dir, prepared));
    await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)), code('EXISTING_INTENT_RECONCILIATION_ONLY'));
    assert.equal(api.postCount, 1);
  }
});

test('replay cannot accept a different receipt, revoked binding, or altered credential revision summary', async t => {
  const {api, dir, raw, prepared} = await bindPreparation(t);
  await executeExternalBinding(raw, prepared, options(api, dir, prepared));
  api.binding.status = 'REVOKED'; api.binding.revision = 1;
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared, {replay: true})), code('BINDING_READBACK_MISMATCH'));
  assert.equal(api.postCount, 2);
  api.binding.status = 'CONFIGURED'; api.binding.revision = 0;
  api.afterPost = (_target, _body, result) => json({...result, id: 'eib-different-replay'});
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared, {replay: true})), code('MUTATION_OUTCOME_UNKNOWN_RECONCILIATION_ONLY'));
  assert.equal(api.postCount, 3); assert.equal((await checkpointFile(dir, prepared)).state, 'REPLAY_UNKNOWN');
  const result = await reconcileExternalBinding(reconciliationInput(), prepared, options(api, dir));
  assert.equal(result.status, 'BINDING_PRESENT_COMMAND_UNPROVEN');
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared, {replay: true})), code('KNOWN_SUCCESS_REQUIRED_FOR_REPLAY'));
});

test('parallel execution and leftover/crashed intent cannot dispatch a second mutation', async t => {
  const api = fixture(), dir = await directory(t), raw = input(), prepared = await prepareExternalBinding(raw, options(api));
  let entered, release;
  const inside = new Promise(resolve => { entered = resolve; }), resume = new Promise(resolve => { release = resolve; });
  api.beforePost = async () => { entered(); await resume; };
  const first = executeExternalBinding(raw, prepared, options(api, dir, prepared));
  await inside;
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)), code('CHECKPOINT_LOCKED_OR_UNAVAILABLE'));
  const row = await checkpointFile(dir, prepared); assert.equal(row.state, 'INTENT');
  release(); await first; assert.equal(api.postCount, 1);
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)), code('EXISTING_INTENT_RECONCILIATION_ONLY'));
});

test('checkpoint failure after dispatch leaves the original intent reconciliation-only without retry', async t => {
  const api = fixture(), dir = await directory(t), raw = input(), prepared = await prepareExternalBinding(raw, options(api));
  api.afterPost = async () => { await chmod(dir, 0o755); };
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)),
    code('CHECKPOINT_FAILURE_AFTER_DISPATCH_RECONCILIATION_ONLY'));
  assert.equal(api.postCount, 1);
  await chmod(dir, 0o700);
  const recorded = await checkpointFile(dir, prepared); assert.equal(recorded.state, 'INTENT'); safe(recorded);
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared)), code('EXISTING_INTENT_RECONCILIATION_ONLY'));
  await assert.rejects(executeExternalBinding(raw, prepared, options(api, dir, prepared, {replay: true})), code('KNOWN_SUCCESS_REQUIRED_FOR_REPLAY'));
  const result = await reconcileExternalBinding(reconciliationInput(), prepared, options(api, dir));
  assert.equal(result.status, 'UNRESOLVED_APPROVAL'); assert.equal(api.postCount, 1);
});

test('the reviewed plan is snapshotted before asynchronous dispatch and checkpoint writes', async t => {
  const api = fixture(), dir = await directory(t), raw = input(), prepared = await prepareExternalBinding(raw, options(api));
  const original = copy(prepared);
  api.beforePost = () => { prepared.plan.config.advertiserId = 'caller-mutated'; prepared.digest = 'd'.repeat(64); };
  const result = await executeExternalBinding(raw, prepared, options(api, dir, prepared));
  assert.equal(result.digest, original.digest); assert.equal(result.status, 'SUCCEEDED');
  const recorded = await checkpointFile(dir, original); assert.deepEqual(recorded.plan, original.plan); safe(recorded);
  assert.equal(api.requests.find(request => request.method === 'POST').body.config.advertiserId, 'advertiser990001');
});

test('reconciliation rechecks exact current entitlement/tenant/version/manifest authority and cannot bless drift', async t => {
  const {api, dir, raw, prepared} = await bindPreparation(t);
  await executeExternalBinding(raw, prepared, options(api, dir, prepared));
  for (const mutate of [a => a.offer.entitlement.revision = 3, a => a.portfolio[0].tenantId = 'ten-foreign',
    a => a.versions[0].configHash = 'd'.repeat(64), a => a.offer.externalIntegrationProfiles[0].manifestSha256 = 'd'.repeat(64)]) {
    const saved = {offer: copy(api.offer), portfolio: copy(api.portfolio), versions: copy(api.versions)}; mutate(api);
    await assert.rejects(reconcileExternalBinding(reconciliationInput(), prepared, options(api, dir)), ExternalBindingError);
    Object.assign(api, saved); assert.equal(api.postCount, 2);
    assert.equal((await checkpointFile(dir, prepared)).state, 'SUCCEEDED');
  }
});

test('public data may not contain known credentials, cookies or the private commitment key', async t => {
  const {api, raw, prepared, dir} = await bindPreparation(t);
  for (const mutate of [r => r.review.reviewReference = 'review-' + CREDENTIALS.stock.providerKey,
    r => r.review.config.advertiserId = CREDENTIALS.stock.providerSecret,
    r => r.review.accountScope.privateReference = CUSTOMER_COOKIE]) {
    const changed = copy(raw); mutate(changed);
    await assert.rejects(executeExternalBinding(changed, prepared, options(api, dir, prepared)), ExternalBindingError);
  }
  assert.equal(api.postCount, 1);
});

test('CLI refuses missing live/digest options and never prints malformed private stdin or process env secrets', () => {
  const runner = new URL('../customer-external-bindings.mjs', import.meta.url).pathname;
  for (const args of [[], ['prepare'], ['prepare', '--live', '--write'], ['execute', '--live'],
    ['replay', '--live', '--plan', 'no-file', '--checkpoint-dir', '/private/tmp/checkpoints'], ['prepare', '--live', '--live']]) {
    const result = spawnSync(process.execPath, [runner, ...args], {input: SECRETS.join(' '), encoding: 'utf8',
      env: {...process.env, CUSTOMER_SESSION_COOKIE: CUSTOMER_COOKIE}});
    assert.equal(result.status, 1); assert.equal(result.stdout, ''); safe(result.stderr);
  }
  const result = spawnSync(process.execPath, [runner, 'prepare', '--live'], {input: SECRETS.join(' '), encoding: 'utf8'});
  assert.equal(result.status, 1); assert.equal(result.stdout, ''); assert.equal(result.stderr, 'PRIVATE_JSON_STDIN_REQUIRED\n');
});
