import assert from 'node:assert/strict';
import test from 'node:test';
import {configuration, verifyCustomerConfiguration} from '../verify-customer-configuration.mjs';

const values = {
  ENGAGE_CANARY_PLATFORM_URL: 'https://platform.example.test',
  ENGAGE_CANARY_EMAIL: 'owner@example.test', ENGAGE_CANARY_PASSWORD: 'private-owner-password',
  ENGAGE_CANARY_CUSTOMER_ID: 'customer-one', ENGAGE_CANARY_OFFER_ID: 'offer-one',
  ENGAGE_CANARY_TARGET_PROFILE_ID: 'staging-one', ENGAGE_CANARY_OPERATION_PREFIX: 'metadata-canary',
  ENGAGE_CANARY_AI_STATE_STORAGE_MODE: 'MANAGED_POSTGRES',
  ENGAGE_CANARY_RUN_ID: 'run-one', ENGAGE_CANARY_DEPLOYMENT_NAME: 'Metadata Canary',
  ENGAGE_CANARY_CONSUMER_ID: 'canary-consumer', ENGAGE_CANARY_APP_NAME: 'Canary App',
  ENGAGE_CANARY_APP_SLUG: 'canary-app', ENGAGE_CANARY_FOREIGN_DEPLOYMENT_ID: 'foreign-deployment',
  ENGAGE_CANARY_FOREIGN_CUSTOMER_ID: 'foreign-customer',
  S3_CANARY_ENDPOINT: 'https://s3.eu-north-1.amazonaws.com', S3_CANARY_REGION: 'eu-north-1',
  S3_CANARY_BUCKET: 'private-bucket', S3_CANARY_STAGING_PREFIX: 'staging/',
  S3_CANARY_STAGING_ACCESS_KEY_ID: 'private-staging-access', S3_CANARY_STAGING_SECRET_ACCESS_KEY: 'private-staging-secret',
  S3_CANARY_VERSIONING_ENABLED: 'false',
};

function fixture(options = {}) {
  const calls = [];
  let appCreates = 0;
  let versions = 0;
  let bindings = 0;
  const interaction = {packCode: 'generic', packVersion: '1.0.0', connectionMode: 'PUBLIC_RUNTIME_ANONYMOUS',
    connectionProfileCode: 'runtime-anonymous-direct', connectionProfileVersion: '1.0.0',
    connectionConfiguration: {}, configuration: {title: 'Canary'}, deliveryMode: 'STANDALONE'};
  const profile = {id: 'reviewed-one', label: 'Reviewed', interaction, theme: null,
    authentication: {profileCode: 'supabase', profileVersion: '1.0.0',
      interactionPolicy: 'PUBLIC_WITH_PROTECTED_CAPABILITIES', configuration: {projectUrl: 'https://auth.example.test', publishableKey: 'public-key'}}};
  const deploymentPath = '/api/customer-self-service/catalogue/offer-one/deployments';
  const root = `${deploymentPath}/own-deployment`;
  async function fetchImpl(url, request) {
    const {pathname, search} = new URL(url);
    const path = pathname + search;
    const body = request.body ? JSON.parse(request.body) : undefined;
    const method = request.method;
    calls.push({path, method, body, headers: request.headers});
    assert.equal(new URL(url).origin, 'https://platform.example.test');
    assert.equal(request.redirect, 'error');
    if (path !== '/api/platform/auth/login') assert.equal(request.headers.Cookie, 'session=private-cookie');
    let status = 200;
    let payload;
    if (path === '/api/platform/auth/login') {
      assert.deepEqual(body, {email: values.ENGAGE_CANARY_EMAIL, password: values.ENGAGE_CANARY_PASSWORD});
      payload = {role: 'CUSTOMER_ADMIN', authenticated: true, authenticationMode: 'SESSION',
        customerId: options.wrongSessionCustomer ? 'other-customer' : 'customer-one'};
    } else if (path === '/api/customer-self-service/catalogue/offer-one') {
      payload = {id: 'offer-one', entitlement: {offerId: 'offer-one',
        customerId: options.wrongEntitlementCustomer ? 'other-customer' : 'customer-one',
        status: options.inactive ? 'REVOKED' : 'ACTIVE', revision: 3},
        targets: [{id: 'staging-one', intent: options.production ? 'CUSTOMER_PRODUCTION' : 'CUSTOMER_STAGING'}],
        settings: [{setting: 'AI_STATE_STORAGE_MODE', type: 'CHOICE',
          allowedValues: options.unreviewedStorage ? [] : ['MANAGED_POSTGRES', 'CUSTOMER_POSTGRES']}],
        workspaceProfiles: [profile]};
    } else if (path.endsWith('/placement-evidence')) payload = {id: 'placement-one'};
    else if (path === deploymentPath) payload = {deploymentId: 'own-deployment', targetProfileId: 'staging-one',
      aiStateStorageMode: options.wrongStorageChoice ? 'H2' : body.aiStateStorageMode};
    else if (path === '/api/deployments/own-deployment/releases') payload = [];
    else if (path === `${root}/staging-consumer-assignments`) payload = {consumerId: body.consumerId, boundReleaseId: null, boundTargetProfileId: 'staging-one'};
    else if (path === '/api/platform/customers/customer-one/ai-workspace-installations') {
      appCreates++;
      assert.deepEqual(body.connectionConfiguration, interaction.connectionConfiguration);
      assert.equal(body.configuration.theme, null);
      assert.equal(body.authConfiguration.publishableKey, 'public-key');
      assert.equal(request.headers['Idempotency-Key'], 'metadata-canary-create-app-run-one');
      status = 201;
      payload = {installationId: options.badAppReplay && appCreates === 2 ? 'other-installation' : 'installation-one',
        status: 'DRAFT', ready: false, releaseId: null, appUrl: 'https://canary-app.apps.example.test'};
    } else if (path.endsWith('/installation-one/activate')) {
      status = 409; payload = {errorCode: 'CONFLICT'};
    } else if (path === '/api/deployments/own-deployment/draft') payload = {
      providerConfig: {aiStateStorageMode: options.wrongPersistedStorage ? 'H2' : options.storageMode ?? 'MANAGED_POSTGRES'},
      securityConfig: {corsAllowedOrigins: ['https://canary-app.apps.example.test']}};
    else if (path === `${root}/document-storage-binding`) {
      bindings++;
      payload = {status: 'CONFIGURED', deploymentId: 'own-deployment', targetProfileId: 'staging-one',
        binding: {bindingRef: 'binding-one', credentialsPresent: true,
          safeMetadata: {readOnly: !(options.unsafeBindingReplay && bindings === 2), storageOwnership: 'CUSTOMER_MANAGED', deleteSourceOnRemoval: false,
            ...(options.leakSecret || options.leakReplaySecret && bindings === 2
              ? {secret: values.S3_CANARY_STAGING_SECRET_ACCESS_KEY} : {})}}};
    }
    else if (path === `${root}/versions`) {
      versions++;
      payload = options.emptyVersion ? {} : {versionId: options.badVersionReplay && versions === 2 ? 'other-version' : 'version-one',
        configHash: (options.badVersionHash && versions === 2 ? 'b' : 'a').repeat(64),
        deploymentId: 'own-deployment', targetProfileId: options.wrongVersionTarget ? 'other-target' : 'staging-one',
        entitlementRevision: 3, versionLabel: 'v1'};
    } else if (path.includes('foreign-deployment') || path.includes('/foreign-customer/')) {
      status = options.foreignAllowed ? 200 : 403; payload = {errorCode: 'FORBIDDEN'};
    } else if (path === `${root}/admission-reservations?targetProfileId=staging-one`) payload = options.existingReservation ? [{id: 'existing-reservation'}] : [];
    else if (path === `${root}/admission-reservations`) {
      assert.equal(body.operation, 'APPLY');
      if (options.admissionSucceeds) payload = {id: 'unexpected-reservation'};
      else {
        status = options.wrongDenial ? 403 : 412;
        payload = {errorCode: options.wrongDenial ? 'FORBIDDEN' : 'PRECONDITION_FAILED',
          message: options.wrongDenial ? 'RESOURCE_SCOPE_MISMATCH' : 'MEASURED_POLICY_REQUIRED'};
      }
    } else throw new Error(`Unexpected route ${method} ${path}`);
    const response = new Response(JSON.stringify(payload), {status, headers: {'Content-Type': 'application/json'}});
    if (path === '/api/platform/auth/login') response.headers.append('Set-Cookie', 'session=private-cookie; HttpOnly; Secure');
    return response;
  }
  return {fetchImpl, calls};
}

test('requires explicit metadata writes and a clean HTTPS origin', () => {
  assert.throws(() => configuration(values), /METADATA_WRITE_APPROVAL_REQUIRED/);
  for (const url of ['http://platform.example.test', 'https://user:secret@platform.example.test', 'https://platform.example.test/api']) {
    assert.throws(() => configuration({...values, ENGAGE_CANARY_PLATFORM_URL: url}, true), /HTTPS_PLATFORM_ORIGIN_REQUIRED/);
  }
  assert.throws(() => configuration({...values, S3_CANARY_VERSIONING_ENABLED: 'true'}, true), /UNVERSIONED_CANARY_REQUIRED/);
  assert.throws(() => configuration({...values, ENGAGE_CANARY_FOREIGN_CUSTOMER_ID: 'customer-one'}, true), /FOREIGN_CUSTOMER_REQUIRED/);
  assert.throws(() => configuration({...values, ENGAGE_CANARY_AI_STATE_STORAGE_MODE: ''}, true), /CONFIGURATION_REQUIRED:ENGAGE_CANARY_AI_STATE_STORAGE_MODE/);
  assert.throws(() => configuration({...values, ENGAGE_CANARY_AI_STATE_STORAGE_MODE: 'H2'}, true), /EXPLICIT_AI_STATE_STORAGE_MODE_REQUIRED/);
});

test('customer SQL selection remains explicit metadata and never provisions SQL', async () => {
  const {fetchImpl, calls} = fixture({storageMode: 'CUSTOMER_POSTGRES'});
  const result = await verifyCustomerConfiguration(configuration({...values, ENGAGE_CANARY_AI_STATE_STORAGE_MODE: 'CUSTOMER_POSTGRES'}, true), {fetchImpl});
  assert.equal(result.aiStateStorageMode, 'CUSTOMER_POSTGRES');
  assert.ok(calls.filter(call => call.path.endsWith('/deployments')).every(call => call.body.aiStateStorageMode === 'CUSTOMER_POSTGRES'));
  assert.ok(!calls.some(call => /ai-state-storage-binding|staging-releases|provision/.test(call.path)));
});

test('verifies exact owner/profile/replays/redaction/foreign denial and missing-policy admission without apply', async () => {
  const {fetchImpl, calls} = fixture();
  const result = await verifyCustomerConfiguration(configuration(values, true), {fetchImpl, verifyAdmissionDenial: true});
  assert.equal(result.status, 'PASS');
  assert.equal(result.checks.length, 9);
  assert.equal(result.runtimeApplyCalled, false);
  assert.equal(result.sourceObjectsModified, false);
  assert.ok(result.checks.includes('MEASURED_POLICY_FAIL_CLOSED_NO_RESERVATIONS'));
  for (const secret of [values.ENGAGE_CANARY_PASSWORD, values.S3_CANARY_STAGING_ACCESS_KEY_ID,
    values.S3_CANARY_STAGING_SECRET_ACCESS_KEY, 'private-cookie', 'public-key']) assert.ok(!JSON.stringify(result).includes(secret));
  assert.ok(!calls.some(call => /staging-releases|promotion|provision|revoke|\/staff\//.test(call.path)));
  assert.equal(calls.filter(call => call.path.endsWith('/admission-reservations') && call.method === 'POST').length, 1);
});

test('admission-denial probe is optional and not silently implied', async () => {
  const {fetchImpl, calls} = fixture();
  const result = await verifyCustomerConfiguration(configuration(values, true), {fetchImpl});
  assert.equal(result.status, 'PASS');
  assert.ok(!result.checks.includes('MEASURED_POLICY_FAIL_CLOSED_NO_RESERVATIONS'));
  assert.ok(!calls.some(call => call.method === 'POST' && call.path.endsWith('/admission-reservations')));
});

for (const [name, options, code] of [
  ['inactive owner entitlement', {inactive: true}, 'ACTIVE_ENTITLEMENT_REQUIRED'],
  ['different customer session', {wrongSessionCustomer: true}, 'CONFIGURED_CUSTOMER_SESSION_REQUIRED'],
  ['different entitlement customer', {wrongEntitlementCustomer: true}, 'CONFIGURED_CUSTOMER_ENTITLEMENT_REQUIRED'],
  ['unreviewed AI state selection', {unreviewedStorage: true}, 'REVIEWED_AI_STATE_STORAGE_CHOICE_REQUIRED'],
  ['changed AI state receipt', {wrongStorageChoice: true}, 'AI_STATE_STORAGE_CHOICE_MISMATCH'],
  ['changed persisted AI state selection', {wrongPersistedStorage: true}, 'PERSISTED_AI_STATE_STORAGE_CHOICE_MISMATCH'],
  ['production target supplied to staging canary', {production: true}, 'REVIEWED_CUSTOMER_STAGING_REQUIRED'],
  ['app replay drift', {badAppReplay: true}, 'APP_REPLAY_MISMATCH'],
  ['secret exposed by a binding response', {leakSecret: true}, 'SECRET_IN_BINDING_RESPONSE'],
  ['secret exposed only in binding replay', {leakReplaySecret: true}, 'SECRET_IN_BINDING_RESPONSE'],
  ['binding replay loses its read-only boundary', {unsafeBindingReplay: true}, 'READ_ONLY_CUSTOMER_STORAGE_REQUIRED'],
  ['immutable version replay drift', {badVersionReplay: true}, 'VERSION_REPLAY_MISMATCH'],
  ['empty immutable-version receipt', {emptyVersion: true}, 'SCOPED_IMMUTABLE_VERSION_REQUIRED'],
  ['same version ID with a different replay hash', {badVersionHash: true}, 'VERSION_REPLAY_MISMATCH'],
  ['foreign immutable-version target', {wrongVersionTarget: true}, 'SCOPED_IMMUTABLE_VERSION_REQUIRED'],
  ['foreign resource access', {foreignAllowed: true}, 'FOREIGN_RESOURCE_ACCESS_NOT_DENIED'],
  ['different admission denial', {wrongDenial: true}, 'EXACT_MEASURED_POLICY_DENIAL_REQUIRED'],
  ['preexisting admission reservation', {existingReservation: true}, 'EXISTING_RESERVATION_REQUIRES_INSPECTION'],
]) {
  test(`fails closed on ${name}`, async () => {
    const {fetchImpl, calls} = fixture(options);
    let evidence;
    await assert.rejects(verifyCustomerConfiguration(configuration(values, true), {fetchImpl, verifyAdmissionDenial: true,
      checkpoint: async value => {evidence = structuredClone(value);}}), new RegExp(code));
    assert.equal(evidence.status, 'FAIL');
    assert.ok(!calls.some(call => call.path.endsWith('/staging-releases')));
    if (options.inactive || options.production || options.unreviewedStorage) assert.ok(!calls.some(call => call.path.endsWith('/deployments')));
    if (options.wrongSessionCustomer || options.wrongEntitlementCustomer) {
      assert.ok(!calls.some(call => call.path.endsWith('/deployments') || call.path.endsWith('/placement-evidence')));
    }
    if (options.existingReservation) assert.ok(!calls.some(call => call.method === 'POST' && call.path.endsWith('/admission-reservations')));
  });
}

test('records unexpected reservation for inspection and never calls apply or cleanup', async () => {
  const {fetchImpl, calls} = fixture({admissionSucceeds: true});
  let evidence;
  await assert.rejects(verifyCustomerConfiguration(configuration(values, true), {fetchImpl, verifyAdmissionDenial: true,
    checkpoint: async value => {evidence = structuredClone(value);}}), /UNEXPECTED_RESERVATION_REQUIRES_INSPECTION_NO_APPLY_CALLED/);
  assert.equal(evidence.unexpectedReservationId, 'unexpected-reservation');
  assert.equal(evidence.status, 'FAIL');
  assert.ok(!calls.some(call => call.path.endsWith('/staging-releases') || call.path.includes('/retire')));
});

test('does not expose an unexpected transport error or request secrets in evidence', async () => {
  let evidence;
  await assert.rejects(verifyCustomerConfiguration(configuration(values, true), {
    fetchImpl: async () => {throw new Error(values.ENGAGE_CANARY_PASSWORD);},
    checkpoint: async value => {evidence = structuredClone(value);},
  }), /UNEXPECTED_LOCAL_OR_TRANSPORT_ERROR/);
  assert.ok(!JSON.stringify(evidence).includes(values.ENGAGE_CANARY_PASSWORD));
});
