#!/usr/bin/env node

import {pathToFileURL} from 'node:url';
import {isIP} from 'node:net';

const STOCK_SOURCE = 'dealership-external-stock-source';
const ACTION_PATH = '/api/internal/actions/execute';
const SIMULATOR_FIXTURE = 'external-vehicle-provider-v2-media';
const SOURCE_FIELDS = ['sourceCount', 'normalizedCount', 'indexedCount', 'deletedCount',
  'acceptedWorkCount', 'completedWorkCount', 'failedWorkCount'];

class PreflightFailure extends Error {
  constructor(code, httpStatus) { super(code); this.code = code; this.httpStatus = httpStatus; }
}

function requireThat(condition, code, httpStatus) {
  if (!condition) throw new PreflightFailure(code, httpStatus);
}

function identifier(value, code) {
  requireThat(typeof value === 'string' && /^[A-Za-z0-9._:-]{1,160}$/.test(value), code);
  return value;
}

function origin(value, approvedHost) {
  let url;
  try { url = new URL(value); } catch { throw new PreflightFailure('REVIEWED_HTTPS_ORIGIN_REQUIRED'); }
  requireThat(typeof approvedHost === 'string' && /^[a-z0-9][a-z0-9.-]*[a-z0-9]$/.test(approvedHost)
    && !isIP(approvedHost) && approvedHost !== 'localhost'
    && url.protocol === 'https:' && url.hostname === approvedHost && !url.port && !url.username
    && !url.password && !url.search && !url.hash && url.pathname === '/', 'REVIEWED_HTTPS_ORIGIN_REQUIRED');
  return url.origin;
}

function secret(value) {
  requireThat(typeof value === 'string' && value.length > 0 && value.length <= 16384
    && !/[\r\n\u0000]/.test(value), 'REQUIRED_CREDENTIAL_MISSING_OR_INVALID');
  return value;
}

function timestamp(value) {
  return typeof value === 'string' && /^\d{4}-\d\d-\d\dT/.test(value)
    && Number.isFinite(Date.parse(value)) ? Date.parse(value) : NaN;
}

async function boundedJson(response, limit) {
  requireThat(response.body, 'JSON_RESPONSE_REQUIRED', response.status);
  const chunks = [];
  let bytes = 0;
  for await (const part of response.body) {
    bytes += part.byteLength;
    requireThat(bytes <= limit, 'RESPONSE_SIZE_LIMIT_EXCEEDED', response.status);
    chunks.push(Buffer.from(part));
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new PreflightFailure('JSON_RESPONSE_REQUIRED', response.status); }
}

/**
 * Independent external-fixture preflight only. Secrets enter in memory, never the report.
 * Its sole POST is the reviewed stock /authenticate exchange. No business action,
 * reconciliation, notification, model, provider-control mutation or provisioning call.
 */
export async function preflightExternalFixtures(config, {fetchImpl = fetch, now = () => new Date()} = {}) {
  const observedAt = now();
  requireThat(observedAt instanceof Date && Number.isFinite(observedAt.valueOf()), 'VALID_CLOCK_REQUIRED');
  const scope = Object.fromEntries(['customerId', 'tenantId', 'deploymentId'].map(key =>
    [key, identifier(config?.scope?.[key], 'EXACT_CUSTOMER_TENANT_DEPLOYMENT_REQUIRED')]));
  const stock = config.stock, receiver = config.receiver;
  requireThat(stock && receiver, 'STOCK_AND_RECEIVER_CONFIGURATION_REQUIRED');
  const stockOrigin = origin(stock.providerBaseUrl, stock.providerHost);
  identifier(stock.advertiserId, 'EXPLICIT_ADVERTISER_REQUIRED');
  const key = secret(stock.providerKey), providerSecret = secret(stock.providerSecret);
  const receiverKey = secret(receiver.receiverApiKey);
  let actionUrl;
  try { actionUrl = new URL(receiver.receiverActionUrl); }
  catch { throw new PreflightFailure('REVIEWED_ACTION_ENDPOINT_REQUIRED'); }
  const receiverOrigin = origin(actionUrl.origin, receiver.receiverHost);
  requireThat(!actionUrl.username && !actionUrl.password && !actionUrl.port && !actionUrl.search
    && !actionUrl.hash && actionUrl.pathname === ACTION_PATH, 'REVIEWED_ACTION_ENDPOINT_REQUIRED');
  requireThat(/^[a-f0-9]{40}$/.test(receiver.expectedCommit ?? ''), 'REVIEWED_RECEIVER_COMMIT_REQUIRED');
  const pageSize = config.pageSize ?? 200, maxPages = config.maxPages ?? 100;
  requireThat(Number.isInteger(pageSize) && pageSize >= 1 && pageSize <= 200
    && Number.isInteger(maxPages) && maxPages >= 1 && maxPages <= 100, 'BOUNDED_PAGINATION_REQUIRED');

  // Review source/index scope before making any external request.
  let indexOrigin;
  if (config.index) {
    const index = config.index;
    indexOrigin = origin(index.baseUrl, index.host);
    requireThat(index.sourceId === STOCK_SOURCE
      && Object.entries(scope).every(([key, value]) => index.scope?.[key] === value), 'EXACT_INDEX_SCOPE_REQUIRED');
    for (const key of ['connectionProfileRef', 'protectedResourceBindingRef', 'sourceVersion']) {
      identifier(index[key], 'EXACT_INDEX_PROVENANCE_REQUIRED');
    }
    requireThat(index.headers && Object.keys(index.headers).length > 0, 'INDEX_READ_AUTH_REQUIRED');
    for (const [name, value] of Object.entries(index.headers)) {
      requireThat(/^[A-Za-z0-9-]+$/.test(name)
        && !['host', 'cookie', 'content-type'].includes(name.toLowerCase()), 'INDEX_HEADER_NOT_ALLOWED');
      secret(value);
    }
  }

  const report = {schemaVersion: 'loomai-external-fixture-preflight-v1', checkedAt: observedAt.toISOString(),
    status: 'RUNNING', scope, claim: 'READ_ONLY_EXTERNAL_FIXTURE_PREFLIGHT_ONLY',
    stock: {providerBaseUrl: stockOrigin, providerHost: stock.providerHost, advertiserId: stock.advertiserId,
      sourceId: STOCK_SOURCE, totalResults: null, indexedCount: null},
    receiver: {receiverActionUrl: actionUrl.href, receiverHost: receiver.receiverHost,
      expectedCommit: receiver.expectedCommit, actionExecuted: false},
    requiredBindings: [
      {slot: 'vehicle-stock', profileId: 'dealership-external-stock-v1',
        credentialFields: ['providerKey', 'providerSecret', 'notificationSecret']},
      {slot: 'business-actions', profileId: 'dealership-external-engagement-v1', credentialFields: ['receiverApiKey']},
    ],
    index: {status: 'NOT_OBSERVED', sourceCount: null, indexedCount: null, lastSuccessAt: null},
    checks: [], requests: [], businessWrites: 0, notificationsSent: 0, reconciliationCalls: 0,
    modelCalls: 0, resourcesCreated: 0,
    limitations: ['BUSINESS_ACTION_EXECUTION_NOT_TESTED', 'SIGNED_NOTIFICATION_DELIVERY_NOT_TESTED',
      'REAL_AUTOTRADER_ACCESS_NOT_PROVEN', 'INSPECTED_CONFIGURATION_IS_NOT_REMOTE_AUTHORIZATION_PROOF']};

  async function request(url, options = {}) {
    const target = new URL(url);
    const method = options.method ?? 'GET';
    const allowedGet = (target.origin === stockOrigin && ['/actuator/health', '/actuator/info', '/stock'].includes(target.pathname))
      || (target.origin === receiverOrigin && ['/actuator/health', '/api/public/status', ACTION_PATH].includes(target.pathname))
      || (indexOrigin && target.origin === indexOrigin
        && target.pathname === `/api/admin/connector/integrations/sources/${STOCK_SOURCE}`);
    requireThat((method === 'GET' && allowedGet)
      || (method === 'POST' && target.href === `${stockOrigin}/authenticate`), 'REQUEST_OUTSIDE_READ_ONLY_PREFLIGHT');
    report.requests.push({method, url: target.href});
    let response;
    try {
      response = await fetchImpl(target.href, {method, redirect: 'error', credentials: 'omit',
        headers: {Accept: 'application/json', ...options.headers}, body: options.body,
        signal: AbortSignal.timeout(20000)});
    } catch { throw new PreflightFailure('TRANSPORT_OR_REDIRECT_FAILURE'); }
    return response;
  }

  async function check(name, action) {
    try { await action(); report.checks.push({name, status: 'PASS'}); return true; }
    catch (error) {
      const failure = error instanceof PreflightFailure ? error : new PreflightFailure('UNEXPECTED_PREFLIGHT_FAILURE');
      report.checks.push({name, status: 'FAIL', code: failure.code,
        ...(Number.isInteger(failure.httpStatus) ? {httpStatus: failure.httpStatus} : {})});
      return false;
    }
  }

  for (const [label, base] of [['STOCK', stockOrigin], ['RECEIVER', receiverOrigin]]) {
    await check(`${label}_HEALTH`, async () => {
      const response = await request(`${base}/actuator/health`);
      requireThat(response.status === 200, 'HEALTH_HTTP_FAILURE', response.status);
      requireThat((await boundedJson(response, 65536)).status === 'UP', 'HEALTH_NOT_UP');
    });
  }

  await check('SIMULATOR_FIXTURE_VERSION', async () => {
    const response = await request(`${stockOrigin}/actuator/info`);
    requireThat(response.status === 200, 'SIMULATOR_INFO_HTTP_FAILURE', response.status);
    const body = await boundedJson(response, 65536);
    requireThat(body.app?.name === 'external-vehicle-provider-simulator'
      && body.app?.['fixture-version'] === SIMULATOR_FIXTURE, 'REVIEWED_SIMULATOR_FIXTURE_REQUIRED');
    report.stock.fixtureVersion = SIMULATOR_FIXTURE;
  });
  await check('NOTIFICATION_SECRET_PRESENT_NOT_EXERCISED', () => { secret(stock.notificationSecret); });
  let token;
  const authenticated = await check('STOCK_FORM_TOKEN_EXCHANGE', async () => {
    const response = await request(`${stockOrigin}/authenticate`, {method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'},
      body: new URLSearchParams({key, secret: providerSecret}).toString()});
    requireThat(response.status === 200, 'STOCK_AUTH_HTTP_FAILURE', response.status);
    const body = await boundedJson(response, 65536);
    token = secret(body.access_token);
    const expires = timestamp(body.expires_at);
    requireThat(expires > observedAt.valueOf() + 30000, 'VALID_UNEXPIRED_STOCK_TOKEN_REQUIRED');
    report.stock.tokenExpiresAt = new Date(expires).toISOString();
  });
  if (authenticated) await check('SCOPED_STOCK_PAGINATION_AND_MEDIA', async () => {
    const ids = new Set(), dates = [];
    let total, withMedia = 0;
    for (let page = 1; page <= maxPages; page++) {
      const query = new URLSearchParams({advertiserId: stock.advertiserId,
        lifecycleState: 'FORECOURT', page: String(page), pageSize: String(pageSize)});
      const response = await request(`${stockOrigin}/stock?${query}`, {headers: {Authorization: `Bearer ${token}`}});
      requireThat(response.status === 200, 'COMPLETE_STOCK_HTTP_REQUIRED', response.status);
      const body = await boundedJson(response, 10485760);
      requireThat(Array.isArray(body.results) && Number.isSafeInteger(body.totalResults)
        && body.totalResults >= 1 && body.totalResults <= pageSize * maxPages, 'BOUNDED_STOCK_ENVELOPE_REQUIRED');
      total ??= body.totalResults;
      requireThat(total === body.totalResults && body.results.length === Math.min(pageSize, total - ids.size),
        'STOCK_PAGINATION_INCOMPLETE_OR_CHANGED');
      for (const row of body.results) {
        const id = identifier(row.metadata?.stockId, 'CANONICAL_STOCK_ID_REQUIRED');
        requireThat(!ids.has(id) && row.advertiser?.advertiserId === stock.advertiserId
          && row.metadata.lifecycleState === 'FORECOURT'
          && row.adverts?.retailAdverts?.advertiserAdvert?.status === 'PUBLISHED', 'STOCK_SCOPE_OR_STATE_MISMATCH');
        const updated = timestamp(row.metadata.lastUpdated);
        requireThat(Number.isFinite(updated) && updated <= observedAt.valueOf() + 5000, 'VALID_STOCK_UPDATE_TIME_REQUIRED');
        requireThat(typeof row.vehicle?.make === 'string' && row.vehicle.make.length > 0
          && typeof row.vehicle?.model === 'string' && row.vehicle.model.length > 0, 'STOCK_VEHICLE_SHAPE_REQUIRED');
        requireThat(Array.isArray(row.media?.images), 'STOCK_MEDIA_SHAPE_REQUIRED');
        for (const image of row.media.images) {
          identifier(image.imageId, 'CANONICAL_MEDIA_ID_REQUIRED');
          let href;
          try { href = new URL(image.href); } catch { throw new PreflightFailure('REVIEWED_STOCK_MEDIA_URL_REQUIRED'); }
          requireThat(href.protocol === 'https:' && href.origin === stockOrigin && !href.username
            && !href.password && !href.search && !href.hash && /^\/media\/[^/]+\/[^/]+\.webp$/.test(href.pathname),
          'REVIEWED_STOCK_MEDIA_URL_REQUIRED');
        }
        if (row.media.images.length) withMedia++;
        ids.add(id); dates.push(updated);
      }
      if (ids.size === total) break;
    }
    requireThat(ids.size === total, 'STOCK_PAGE_LIMIT_REACHED');
    Object.assign(report.stock, {totalResults: total, publishedCount: ids.size, recordsWithMedia: withMedia,
      oldestRecordUpdatedAt: new Date(Math.min(...dates)).toISOString(),
      newestRecordUpdatedAt: new Date(Math.max(...dates)).toISOString(), observedAt: observedAt.toISOString()});
  });

  await check('RECEIVER_REPORTED_VERSION', async () => {
    const response = await request(`${receiverOrigin}/api/public/status`);
    requireThat(response.status === 200, 'RECEIVER_STATUS_HTTP_FAILURE', response.status);
    const body = await boundedJson(response, 65536);
    requireThat(body.service === 'loomai-dealership-demo-backend' && body.status === 'UP', 'RECEIVER_STATUS_SHAPE_REQUIRED');
    report.receiver.reportedCommit = /^[a-f0-9]{40}$/.test(body.commit ?? '') ? body.commit : 'unknown';
    if (Number.isSafeInteger(body.inventoryCount) && body.inventoryCount >= 0) {
      report.receiver.independentWebsiteInventoryCount = body.inventoryCount;
    }
    if (Number.isFinite(timestamp(body.inventoryRefreshedAt))) {
      report.receiver.independentWebsiteInventoryRefreshedAt = new Date(timestamp(body.inventoryRefreshedAt)).toISOString();
    }
    requireThat(body.commit === receiver.expectedCommit, 'INDEPENDENT_RECEIVER_UPGRADE_REQUIRED');
  });

  await check('RECEIVER_UNAUTHENTICATED_ROUTE_DENIED', async () => {
    const response = await request(actionUrl.href);
    requireThat([401, 403].includes(response.status), 'RECEIVER_ROUTE_NOT_AUTH_PROTECTED', response.status);
  });
  await check('RECEIVER_AUTHENTICATED_METHOD_CONTRACT', async () => {
    const response = await request(actionUrl.href, {headers: {'X-DEALERSHIP-INTERNAL-KEY': receiverKey}});
    const methods = (response.headers.get('allow') ?? '').split(',').map(value => value.trim()).filter(Boolean);
    const postOnly = methods.length === 1 && methods[0] === 'POST';
    report.receiver.methodProbe = {httpStatus: response.status, expectedHttpStatus: 405, postOnlyAdvertised: postOnly,
      classification: response.status === 405 && postOnly ? 'POST_ONLY_DECLARED_WITH_405'
        : response.status === 500 ? 'UNSUPPORTED_METHOD_SERVER_ERROR_500' : 'METHOD_CONTRACT_UNVERIFIED',
      postExecutionTested: false};
    await response.body?.cancel();
    requireThat(response.status !== 500, 'RECEIVER_UNSUPPORTED_METHOD_RETURNED_500', response.status);
    requireThat(response.status === 405 && postOnly, 'ACTION_ROUTE_METHOD_CONTRACT_UNVERIFIED', response.status);
  });
  await check('RECEIVER_INSPECTED_EXTERNAL_STOCK_CONFIGURATION', () => {
    const observed = receiver.observedConfiguration;
    requireThat(observed, 'RECEIVER_CONFIGURATION_EVIDENCE_REQUIRED');
    requireThat(observed.providerBaseUrl === stockOrigin && observed.advertiserId === stock.advertiserId
      && observed.providerKeyPresent === true && observed.providerSecretPresent === true,
    'RECEIVER_EXTERNAL_PROVIDER_READ_CONFIGURATION_REQUIRED');
  });
  await check('RECEIVER_INSPECTED_AUTHORITY_SCOPE', () => {
    const observed = receiver.observedConfiguration;
    requireThat(observed && Object.entries(scope).every(([key, value]) => observed[key] === value),
      'RECEIVER_CUSTOMER_TENANT_DEPLOYMENT_AUTHORITY_MISMATCH');
  });

  if (config.index) await check('CURRENT_SCOPED_INDEX_EVIDENCE', async () => {
    const response = await request(`${indexOrigin}/api/admin/connector/integrations/sources/${STOCK_SOURCE}`,
      {headers: config.index.headers});
    requireThat(response.status === 200, 'INDEX_STATUS_HTTP_FAILURE', response.status);
    const body = await boundedJson(response, 1048576), state = body.state, counts = state?.counts;
    requireThat(body.sourceId === STOCK_SOURCE && state?.sourceId === STOCK_SOURCE
      && body.connectionProfileRef === config.index.connectionProfileRef
      && body.protectedResourceBindingRef === config.index.protectedResourceBindingRef
      && state.sourceVersion === config.index.sourceVersion && body.vectorSpace === 'dealer-vehicle', 'INDEX_PROVENANCE_MISMATCH');
    const projected = Object.fromEntries(SOURCE_FIELDS.map(field => [field, counts?.[field]]));
    requireThat(Object.values(projected).every(value => Number.isSafeInteger(value) && value >= 0), 'INDEX_COUNTS_REQUIRED');
    const lastSuccessAt = timestamp(state.lastSuccessAt), startedAt = timestamp(state.lastStartedAt);
    const updatedAt = timestamp(state.updatedAt);
    Object.assign(report.index, projected, {lastSuccessAt: Number.isFinite(lastSuccessAt)
      ? new Date(lastSuccessAt).toISOString() : null});
    requireThat(body.enabled === true && body.preflightState === 'READY' && body.freshnessState === 'CURRENT'
      && state.status === 'COMPLETED' && !state.errorClass && !state.errorMessage
      && lastSuccessAt >= startedAt && updatedAt >= lastSuccessAt && updatedAt <= observedAt.valueOf() + 5000
      && lastSuccessAt >= observedAt.valueOf() - 1800000
      && (!state.lastErrorAt || timestamp(state.lastErrorAt) <= lastSuccessAt), 'INDEX_NOT_FRESH_OR_COMPLETE');
    requireThat(Array.isArray(body.work) && body.work.every(work => work.sourceId === STOCK_SOURCE
      && work.status === 'COMPLETED' && !work.errorCode && Number.isFinite(timestamp(work.updatedAt))), 'INDEX_WORK_NOT_COMPLETE');
    requireThat(projected.failedWorkCount === 0 && projected.sourceCount === report.stock.totalResults
      && projected.normalizedCount === projected.sourceCount && projected.indexedCount === projected.normalizedCount
      && projected.acceptedWorkCount === projected.completedWorkCount
      && projected.completedWorkCount === projected.indexedCount + projected.deletedCount, 'INDEX_CONVERGENCE_UNVERIFIED');
    report.index.status = 'OBSERVED_CURRENT'; report.stock.indexedCount = projected.indexedCount;
  });
  report.status = report.checks.every(item => item.status === 'PASS') ? 'PASS' : 'FAIL';
  if (!config.index) report.limitations.push('AI_CANARY_INDEX_NOT_OBSERVED');
  return report;
}

// Pass private JSON on stdin, not argv or a committed fixture. This runner never writes files.
async function main() {
  if (process.argv.length !== 3 || process.argv[2] !== '--live') {
    process.stderr.write('Usage: node external-fixture-preflight.mjs --live < private-configuration.json\n');
    process.exitCode = 2; return;
  }
  try {
    const chunks = []; let size = 0;
    for await (const chunk of process.stdin) {
      size += chunk.length; requireThat(size <= 1048576, 'CONFIGURATION_SIZE_LIMIT_EXCEEDED'); chunks.push(chunk);
    }
    let config;
    try { config = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
    catch { throw new PreflightFailure('PRIVATE_JSON_CONFIGURATION_REQUIRED'); }
    const report = await preflightExternalFixtures(config);
    process.stdout.write(JSON.stringify(report, null, 2) + '\n');
    if (report.status !== 'PASS') process.exitCode = 1;
  } catch (error) {
    process.stderr.write((error instanceof PreflightFailure ? error.code : 'EXTERNAL_FIXTURE_PREFLIGHT_FAILED') + '\n');
    process.exitCode = 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
