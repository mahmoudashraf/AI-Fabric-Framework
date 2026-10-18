#!/usr/bin/env node

import {lstat, mkdir, readFile, writeFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {isDeepStrictEqual, parseEnv} from 'node:util';
import {pathToFileURL} from 'node:url';

class CanaryFailure extends Error {
  constructor(code) { super(code); this.code = code; }
}

function requireCheck(value, code) {
  if (!value) throw new CanaryFailure(code);
}

function required(values, name) {
  const value = values[name]?.trim();
  requireCheck(value, `CONFIGURATION_REQUIRED:${name}`);
  return value;
}

export function configuration(values, allowMetadataWrites = false) {
  requireCheck(allowMetadataWrites, 'METADATA_WRITE_APPROVAL_REQUIRED');
  const base = new URL(required(values, 'ENGAGE_CANARY_PLATFORM_URL'));
  requireCheck(base.protocol === 'https:' && !base.username && !base.password
    && !base.search && !base.hash && base.pathname === '/', 'HTTPS_PLATFORM_ORIGIN_REQUIRED');
  const config = {base: base.origin, allowMetadataWrites,
    email: required(values, 'ENGAGE_CANARY_EMAIL'), password: required(values, 'ENGAGE_CANARY_PASSWORD'),
    customerId: required(values, 'ENGAGE_CANARY_CUSTOMER_ID'), offerId: required(values, 'ENGAGE_CANARY_OFFER_ID'),
    targetProfileId: required(values, 'ENGAGE_CANARY_TARGET_PROFILE_ID'),
    operationPrefix: required(values, 'ENGAGE_CANARY_OPERATION_PREFIX'), runId: required(values, 'ENGAGE_CANARY_RUN_ID'),
    deploymentName: required(values, 'ENGAGE_CANARY_DEPLOYMENT_NAME'),
    consumerId: required(values, 'ENGAGE_CANARY_CONSUMER_ID'),
    appName: required(values, 'ENGAGE_CANARY_APP_NAME'), appSlug: required(values, 'ENGAGE_CANARY_APP_SLUG'),
    foreignDeploymentId: required(values, 'ENGAGE_CANARY_FOREIGN_DEPLOYMENT_ID'),
    foreignCustomerId: required(values, 'ENGAGE_CANARY_FOREIGN_CUSTOMER_ID'),
    workspaceProfileId: values.ENGAGE_CANARY_WORKSPACE_PROFILE_ID?.trim(),
    storage: {endpoint: required(values, 'S3_CANARY_ENDPOINT'), region: required(values, 'S3_CANARY_REGION'),
      bucket: required(values, 'S3_CANARY_BUCKET'), prefix: required(values, 'S3_CANARY_STAGING_PREFIX'),
      accessKey: required(values, 'S3_CANARY_STAGING_ACCESS_KEY_ID'),
      secretKey: required(values, 'S3_CANARY_STAGING_SECRET_ACCESS_KEY')},
  };
  requireCheck(required(values, 'S3_CANARY_VERSIONING_ENABLED') === 'false', 'UNVERSIONED_CANARY_REQUIRED');
  requireCheck(config.foreignCustomerId !== config.customerId, 'FOREIGN_CUSTOMER_REQUIRED');
  for (const name of ['customerId', 'offerId', 'targetProfileId', 'consumerId', 'foreignDeploymentId', 'foreignCustomerId']) {
    requireCheck(/^[A-Za-z0-9_-]{1,64}$/.test(config[name]), `INVALID_IDENTIFIER:${name}`);
  }
  requireCheck(/^[A-Za-z0-9_-]{1,70}$/.test(config.operationPrefix)
    && /^[A-Za-z0-9_-]{1,24}$/.test(config.runId), 'INVALID_OPERATION_IDENTITY');
  return config;
}

/** Control-plane metadata only: no apply, grant, policy, provider-resource or source-object mutation. */
export async function verifyCustomerConfiguration(config, {fetchImpl = fetch, verifyAdmissionDenial = false,
  checkpoint = async () => {}} = {}) {
  requireCheck(config.allowMetadataWrites, 'METADATA_WRITE_APPROVAL_REQUIRED');
  const evidence = {schemaVersion: 'loomai-configuration-only-canary-v1', checkedAt: new Date().toISOString(),
    status: 'RUNNING', controlPlane: config.base, offerId: config.offerId, customerId: config.customerId,
    targetProfileId: config.targetProfileId, runtimeApplyCalled: false, sourceObjectsModified: false, checks: []};
  let cookie;
  let step = 'login';
  const key = command => `${config.operationPrefix}-${command}-${config.runId}`;
  const secrets = [config.password, config.storage.accessKey, config.storage.secretKey];
  const check = (value, code) => requireCheck(value, `${step}:${code}`);
  async function request(path, {body, method = body ? 'POST' : 'GET', expected = 200, operationKey} = {}) {
    const response = await fetchImpl(config.base + path, {method,
      headers: {Accept: 'application/json', ...(body ? {'Content-Type': 'application/json'} : {}),
        ...(cookie ? {Cookie: cookie} : {}), ...(operationKey ? {'Idempotency-Key': operationKey} : {})},
      body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(130_000), redirect: 'error'});
    let payload;
    try { payload = await response.json(); } catch { throw new CanaryFailure(`${step}:INVALID_JSON_RESPONSE`); }
    if (expected !== null) check(response.status === expected, `UNEXPECTED_HTTP_${response.status}`);
    return {payload, response};
  }
  const pass = name => evidence.checks.push(name);
  try {
    const login = await request('/api/platform/auth/login', {body: {email: config.email, password: config.password}});
    check(login.payload.role === 'CUSTOMER_ADMIN', 'CUSTOMER_OWNER_REQUIRED');
    check(login.payload.authenticated === true && login.payload.authenticationMode === 'SESSION'
      && login.payload.customerId === config.customerId, 'CONFIGURED_CUSTOMER_SESSION_REQUIRED');
    cookie = login.response.headers.getSetCookie()[0]?.split(';', 1)[0];
    check(cookie, 'OWNER_SESSION_REQUIRED');
    secrets.push(cookie);
    pass('REAL_CUSTOMER_OWNER_SESSION');

    step = 'reviewed-offer';
    const offerPath = `/api/customer-self-service/catalogue/${config.offerId}`;
    let offer = (await request(offerPath)).payload;
    check(offer.entitlement?.status === 'ACTIVE', 'ACTIVE_ENTITLEMENT_REQUIRED');
    check(offer.id === config.offerId && offer.entitlement.customerId === config.customerId
      && offer.entitlement.offerId === config.offerId, 'CONFIGURED_CUSTOMER_ENTITLEMENT_REQUIRED');
    await request(`${offerPath}/targets/${config.targetProfileId}/placement-evidence`, {method: 'POST'});
    offer = (await request(offerPath)).payload;
    check(offer.targets.some(target => target.id === config.targetProfileId
      && target.intent === 'CUSTOMER_STAGING'), 'REVIEWED_CUSTOMER_STAGING_REQUIRED');
    const profiles = offer.workspaceProfiles.filter(profile => !config.workspaceProfileId || profile.id === config.workspaceProfileId);
    check(profiles.length === 1, 'EXACT_REVIEWED_WORKSPACE_PROFILE_REQUIRED');
    const profile = profiles[0];
    const revision = offer.entitlement.revision;
    pass('REVIEWED_PROFILE_AND_STAGING_PLACEMENT');

    step = 'configuration-bootstrap';
    const bootstrap = {targetProfileId: config.targetProfileId, name: config.deploymentName,
      expectedEntitlementRevision: revision, operationKey: key('bootstrap')};
    const deploymentsPath = `${offerPath}/deployments`;
    const deployment = (await request(deploymentsPath, {body: bootstrap})).payload;
    evidence.deploymentId = deployment.deploymentId;
    check((await request(deploymentsPath, {body: bootstrap})).payload.deploymentId === deployment.deploymentId, 'BOOTSTRAP_REPLAY_MISMATCH');
    const root = `${deploymentsPath}/${deployment.deploymentId}`;
    const releasesPath = `/api/deployments/${deployment.deploymentId}/releases`;
    check((await request(releasesPath)).payload.length === 0, 'CONFIGURATION_ONLY_DEPLOYMENT_REQUIRED');
    pass('BOOTSTRAP_REPLAY_WITH_NO_RELEASE');
    await checkpoint(evidence);

    step = 'configuration-consumer';
    const consumerRequest = {consumerId: config.consumerId, displayName: config.deploymentName,
      description: 'Configuration-only verification; no runtime admitted', expectedEntitlementRevision: revision,
      operationKey: key('consumer')};
    const consumerPath = `${root}/staging-consumer-assignments`;
    const consumer = (await request(consumerPath, {body: consumerRequest})).payload;
    check(consumer.boundReleaseId == null && consumer.boundTargetProfileId === config.targetProfileId, 'UNACTIVATED_TARGET_BINDING_REQUIRED');
    check((await request(consumerPath, {body: consumerRequest})).payload.consumerId === consumer.consumerId, 'CONSUMER_REPLAY_MISMATCH');
    evidence.consumerId = consumer.consumerId;
    pass('CONFIGURATION_ONLY_CONSUMER_REPLAY');

    step = 'first-app';
    const enumValue = value => value.toLowerCase().replaceAll('_', '-');
    const appRequest = {consumerId: consumer.consumerId, displayName: config.appName, appSlug: config.appSlug,
      experiencePackCode: profile.interaction.packCode, experiencePackVersion: profile.interaction.packVersion,
      connectionMode: enumValue(profile.interaction.connectionMode), connectionProfileCode: profile.interaction.connectionProfileCode,
      connectionProfileVersion: profile.interaction.connectionProfileVersion, connectionConfiguration: profile.interaction.connectionConfiguration,
      configuration: {...profile.interaction.configuration, theme: profile.theme}, allowedOrigins: [], customDomains: [],
      deliveryMode: enumValue(profile.interaction.deliveryMode), interactionPolicy: enumValue(profile.authentication.interactionPolicy),
      authProfileCode: profile.authentication.profileCode, authProfileVersion: profile.authentication.profileVersion,
      authConfiguration: profile.authentication.configuration};
    const appsPath = `/api/platform/customers/${config.customerId}/ai-workspace-installations`;
    const save = {body: appRequest, operationKey: key('create-app'), expected: 201};
    const installation = (await request(appsPath, save)).payload;
    check(installation.status === 'DRAFT' && installation.ready === false && installation.releaseId == null, 'UNACTIVATED_FIRST_APP_REQUIRED');
    evidence.installationId = installation.installationId;
    check((await request(appsPath, save)).payload.installationId === installation.installationId, 'APP_REPLAY_MISMATCH');
    await request(`${appsPath}/${installation.installationId}/activate`, {method: 'POST', operationKey: key('early-activate'), expected: 409});
    const draft = (await request(`/api/deployments/${deployment.deploymentId}/draft`)).payload;
    check(draft.securityConfig.corsAllowedOrigins.includes(new URL(installation.appUrl).origin), 'APP_ORIGIN_NOT_PROJECTED');
    pass('FIRST_APP_REPLAY_ORIGIN_AND_EARLY_ACTIVATION_DENIAL');
    await checkpoint(evidence);

    step = 'staging-storage';
    const storageRequest = {...config.storage, targetProfileId: config.targetProfileId, bindingSlotId: 'document-storage',
      selectedProfileId: 'reviewed-s3-read-only', expectedEntitlementRevision: revision,
      operationKey: key('bind-s3'), pathStyleAccess: false, objectVersioningAvailable: false};
    const binding = (await request(`${root}/document-storage-binding`, {body: storageRequest})).payload;
    const bindingReplay = (await request(`${root}/document-storage-binding`, {body: storageRequest})).payload;
    for (const receipt of [binding, bindingReplay]) {
      check(receipt.status === 'CONFIGURED' && receipt.deploymentId === deployment.deploymentId
        && receipt.targetProfileId === config.targetProfileId && receipt.binding?.credentialsPresent
        && typeof receipt.binding.bindingRef === 'string' && receipt.binding.bindingRef.length > 0
        && receipt.binding.safeMetadata.readOnly === true
        && receipt.binding.safeMetadata.storageOwnership === 'CUSTOMER_MANAGED'
        && receipt.binding.safeMetadata.deleteSourceOnRemoval === false, 'READ_ONLY_CUSTOMER_STORAGE_REQUIRED');
      check(!secrets.some(secret => JSON.stringify(receipt).includes(secret)), 'SECRET_IN_BINDING_RESPONSE');
    }
    check(isDeepStrictEqual(binding, bindingReplay), 'STORAGE_REPLAY_MISMATCH');
    pass('READ_ONLY_STORAGE_REPLAY_AND_REDACTION');

    step = 'immutable-version';
    const publish = {targetProfileId: config.targetProfileId, expectedEntitlementRevision: revision, operationKey: key('publish-version')};
    const version = (await request(`${root}/versions`, {body: publish})).payload;
    check(typeof version.versionId === 'string' && version.versionId.length > 0
      && typeof version.configHash === 'string' && /^[a-f0-9]{64}$/.test(version.configHash)
      && version.deploymentId === deployment.deploymentId && version.targetProfileId === config.targetProfileId
      && version.entitlementRevision === revision, 'SCOPED_IMMUTABLE_VERSION_REQUIRED');
    evidence.versionId = version.versionId; evidence.configHash = version.configHash;
    check(isDeepStrictEqual((await request(`${root}/versions`, {body: publish})).payload, version), 'VERSION_REPLAY_MISMATCH');
    pass('IMMUTABLE_VERSION_REPLAY');
    await checkpoint(evidence);

    step = 'foreign-scope';
    for (const path of [`/api/deployments/${config.foreignDeploymentId}/draft`,
      `${deploymentsPath}/${config.foreignDeploymentId}/admission-reservations?targetProfileId=${config.targetProfileId}`,
      `/api/platform/customers/${config.foreignCustomerId}/ai-workspace-installations`]) {
      const denial = await request(path, {expected: null});
      check([403, 404].includes(denial.response.status), 'FOREIGN_RESOURCE_ACCESS_NOT_DENIED');
    }
    pass('DIRECT_FOREIGN_DEPLOYMENT_ADMISSION_AND_APP_DENIALS');

    if (verifyAdmissionDenial) {
      step = 'measured-policy-denial';
      const admissionPath = `${root}/admission-reservations`;
      const listPath = `${admissionPath}?targetProfileId=${config.targetProfileId}`;
      check((await request(listPath)).payload.length === 0, 'EXISTING_RESERVATION_REQUIRES_INSPECTION');
      const admission = await request(admissionPath, {expected: null, body: {targetProfileId: config.targetProfileId,
        versionId: version.versionId, operation: 'APPLY', operationKey: key('no-capacity')}});
      if (admission.response.ok) {
        evidence.unexpectedReservationId = admission.payload.id;
        throw new CanaryFailure('UNEXPECTED_RESERVATION_REQUIRES_INSPECTION_NO_APPLY_CALLED');
      }
      check(admission.response.status === 412 && admission.payload.message === 'MEASURED_POLICY_REQUIRED', 'EXACT_MEASURED_POLICY_DENIAL_REQUIRED');
      check((await request(listPath)).payload.length === 0, 'RESERVATION_RETAINED_AFTER_DENIAL');
      pass('MEASURED_POLICY_FAIL_CLOSED_NO_RESERVATIONS');
    }
    step = 'final-no-release';
    check((await request(releasesPath)).payload.length === 0, 'UNEXPECTED_RELEASE_REQUIRES_INSPECTION');
    evidence.status = 'PASS';
    await checkpoint(evidence);
    return evidence;
  } catch (error) {
    evidence.status = 'FAIL'; evidence.failedStep = step;
    evidence.failureCode = error instanceof CanaryFailure ? error.code : 'UNEXPECTED_LOCAL_OR_TRANSPORT_ERROR';
    await checkpoint(evidence);
    throw new CanaryFailure(evidence.failureCode);
  }
}

async function privateEnvironment(path) {
  if (!path) return {};
  const info = await lstat(path);
  requireCheck(info.isFile() && !info.isSymbolicLink() && (info.mode & 0o077) === 0, 'PRIVATE_REGULAR_ENV_FILE_REQUIRED');
  return parseEnv(await readFile(path, 'utf8'));
}

async function main() {
  const args = process.argv.slice(2);
  const allowed = new Set(['--allow-metadata-writes', '--verify-admission-denial', '--env-file', '--s3-env-file', '--output']);
  const options = {};
  for (let index = 0; index < args.length; index++) {
    const name = args[index];
    requireCheck(allowed.has(name), 'UNKNOWN_ARGUMENT');
    options[name] = ['--allow-metadata-writes', '--verify-admission-denial'].includes(name) ? true : args[++index];
    requireCheck(options[name] && !String(options[name]).startsWith('--'), 'ARGUMENT_VALUE_REQUIRED');
  }
  const config = configuration({...await privateEnvironment(options['--env-file']),
    ...await privateEnvironment(options['--s3-env-file']), ...process.env}, options['--allow-metadata-writes']);
  const output = resolve(options['--output'] ?? `verification-support/productization-01036/evidence/customer-configuration-${Date.now()}.json`);
  await mkdir(dirname(output), {recursive: true});
  const checkpoint = evidence => writeFile(output, JSON.stringify(evidence, null, 2) + '\n', {mode: 0o600});
  const result = await verifyCustomerConfiguration(config, {checkpoint, verifyAdmissionDenial: options['--verify-admission-denial']});
  console.log(`Customer configuration canary ${result.status}. Sanitized evidence: ${output}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(error => {console.error(error instanceof CanaryFailure ? error.code : 'CANARY_FAILED'); process.exitCode = 1;});
}
