import assert from 'node:assert/strict';
import {test} from 'node:test';
import {spawnSync} from 'node:child_process';
import {preflightExternalFixtures} from '../external-fixture-preflight.mjs';

const instant = '2026-10-19T08:00:00.000Z';
const commit = 'a'.repeat(40);
const stockOrigin = 'https://stock.example.test', receiverOrigin = 'https://business.example.test';
const scope = {customerId: 'cus-fixture', tenantId: 'ten-fixture', deploymentId: 'dep-fixture'};
const sensitive = ['private-provider-key', 'private-provider-secret', 'private-notification-secret',
  'private-receiver-key', 'private-access-token', 'private-assertion'];
function configuration() {
  return {scope: {...scope}, pageSize: 2, stock: {providerBaseUrl: stockOrigin, providerHost: 'stock.example.test',
    advertiserId: '990001', providerKey: sensitive[0], providerSecret: sensitive[1], notificationSecret: sensitive[2]},
  receiver: {receiverActionUrl: receiverOrigin + '/api/internal/actions/execute', receiverHost: 'business.example.test',
    receiverApiKey: sensitive[3], expectedCommit: commit, observedConfiguration: {...scope,
      providerBaseUrl: stockOrigin, advertiserId: '990001', providerKeyPresent: true, providerSecretPresent: true}}};
}
function record(id) {
  return {metadata: {stockId: id, lifecycleState: 'FORECOURT', lastUpdated: '2026-10-03T08:00:00Z'},
    advertiser: {advertiserId: '990001'}, vehicle: {make: 'Example', model: 'Demo'},
    adverts: {retailAdverts: {advertiserAdvert: {status: 'PUBLISHED'}}},
    media: {images: [{imageId: `image-${id}`, href: `${stockOrigin}/media/w720h540/image-${id}.webp`}]}};
}
function fixture(options = {}) {
  const requests = [], records = Array.from({length: 3}, (_, i) => record(`stock-${i + 1}`));
  function json(body, status = 200, headers = {}) { return new Response(JSON.stringify(body), {status, headers}); }
  async function fetchImpl(url, request) {
    const target = new URL(url); requests.push({url, ...request});
    assert.equal(request.redirect, 'error'); assert.equal(request.credentials, 'omit');
    if (target.pathname === '/actuator/health') return json({status: 'UP'});
    if (target.pathname === '/actuator/info') return json({app: {name: 'external-vehicle-provider-simulator',
      'fixture-version': options.fixtureVersion ?? 'external-vehicle-provider-v2-media'}});
    if (target.pathname === '/authenticate') {
      assert.equal(request.method, 'POST');
      assert.equal(request.headers['Content-Type'], 'application/x-www-form-urlencoded');
      assert.deepEqual(Object.fromEntries(new URLSearchParams(request.body)), {key: sensitive[0], secret: sensitive[1]});
      return json(options.auth ?? {access_token: sensitive[4], expires_at: '2026-10-19T08:15:00Z'}, options.authStatus ?? 200);
    }
    if (target.pathname === '/stock') {
      assert.equal(request.method, 'GET'); assert.equal(request.headers.Authorization, `Bearer ${sensitive[4]}`);
      assert.equal(target.searchParams.get('advertiserId'), '990001');
      assert.equal(target.searchParams.get('lifecycleState'), 'FORECOURT');
      const page = Number(target.searchParams.get('page')), size = Number(target.searchParams.get('pageSize'));
      let body = {results: structuredClone(records.slice((page - 1) * size, page * size)), totalResults: records.length};
      if (options.stock) body = options.stock(body, page);
      return json(body, options.stockStatus ?? 200);
    }
    if (target.pathname === '/api/public/status') return json({service: 'loomai-dealership-demo-backend', status: 'UP',
      commit: options.commit ?? commit, inventoryCount: 12, inventoryRefreshedAt: instant, privateRow: sensitive[1]});
    if (target.pathname === '/api/internal/actions/execute') {
      assert.equal(request.method, 'GET'); assert.equal(request.body, undefined);
      if (!request.headers['X-DEALERSHIP-INTERNAL-KEY']) return json({error: sensitive[0]}, options.noKeyStatus ?? 401);
      assert.equal(request.headers['X-DEALERSHIP-INTERNAL-KEY'], sensitive[3]);
      return json({error: sensitive[0]}, options.methodStatus ?? 405, {Allow: options.allow ?? 'POST'});
    }
    if (target.pathname === '/api/admin/connector/integrations/sources/dealership-external-stock-source') {
      assert.equal(request.method, 'GET'); assert.equal(request.headers.Authorization, `Bearer ${sensitive[5]}`);
      return json(options.index);
    }
    throw new Error(`Unexpected request ${sensitive[0]}`);
  }
  return {fetchImpl, requests};
}
async function run(config = configuration(), options = {}) {
  const adapter = fixture(options);
  const report = await preflightExternalFixtures(config, {...adapter, now: () => new Date(instant)});
  const serialized = JSON.stringify(report);
  for (const value of sensitive) assert.ok(!serialized.includes(value));
  assert.equal(report.businessWrites, 0); assert.equal(report.notificationsSent, 0);
  assert.equal(report.modelCalls, 0); assert.equal(report.resourcesCreated, 0);
  assert.equal(report.reconciliationCalls, 0);
  assert.deepEqual(adapter.requests.filter(request => request.method !== 'GET').map(request => new URL(request.url).pathname), ['/authenticate']);
  return {report, requests: adapter.requests};
}
function failure(report, code) { assert.ok(report.checks.some(check => check.status === 'FAIL' && check.code === code), code); }

test('bounded two-page scoped read reports counts/media, never conflates website or index counts', async () => {
  const {report, requests} = await run();
  assert.equal(report.status, 'PASS'); assert.equal(report.stock.totalResults, 3);
  assert.equal(report.stock.recordsWithMedia, 3); assert.equal(report.stock.publishedCount, 3);
  assert.equal(report.receiver.independentWebsiteInventoryCount, 12);
  assert.deepEqual(report.receiver.methodProbe, {httpStatus: 405, expectedHttpStatus: 405,
    postOnlyAdvertised: true, classification: 'POST_ONLY_DECLARED_WITH_405', postExecutionTested: false});
  assert.equal(report.stock.indexedCount, null); assert.equal(report.index.status, 'NOT_OBSERVED');
  assert.ok(report.limitations.includes('BUSINESS_ACTION_EXECUTION_NOT_TESTED'));
  assert.ok(report.limitations.includes('AI_CANARY_INDEX_NOT_OBSERVED'));
  assert.deepEqual(requests.filter(request => new URL(request.url).pathname === '/stock')
    .map(request => new URL(request.url).searchParams.get('page')), ['1', '2']);
  assert.deepEqual(report.requiredBindings.map(binding => binding.credentialFields),
    [['providerKey', 'providerSecret', 'notificationSecret'], ['receiverApiKey']]);
});

test('configuration validation rejects unsafe endpoints, missing scope/credentials and unbounded reads before I/O', async () => {
  const mutations = [c => c.stock.providerBaseUrl = 'http://stock.example.test',
    c => c.stock.providerBaseUrl = 'https://user:password@stock.example.test',
    c => c.stock.providerBaseUrl += '/internal/control/reset', c => c.stock.providerHost = '*.example.test',
    c => {c.stock.providerBaseUrl = 'https://127.0.0.1'; c.stock.providerHost = '127.0.0.1';},
    c => c.stock.providerHost = 'other.example.test', c => c.stock.providerKey = '',
    c => c.stock.providerSecret = 'injected\nheader', c => c.scope.tenantId = '',
    c => c.stock.advertiserId = '../other', c => c.receiver.expectedCommit = 'HEAD',
    c => c.receiver.receiverActionUrl += '?key=private',
    c => c.receiver.receiverActionUrl = receiverOrigin + '/internal/control/reset',
    c => c.receiver.receiverActionUrl = 'https://user:secret@business.example.test/api/internal/actions/execute',
    c => c.pageSize = 201, c => c.maxPages = 101];
  for (const mutate of mutations) {
    const config = configuration(); mutate(config); let called = false;
    await assert.rejects(preflightExternalFixtures(config, {fetchImpl: () => { called = true; throw Error('not allowed'); }}));
    assert.equal(called, false);
  }
});

test('token rejection or malformed/expired exchange never falls back to unauthenticated stock', async () => {
  for (const options of [{authStatus: 401}, {auth: {}},
    {auth: {access_token: sensitive[4], expires_at: '2026-10-19T07:59:59Z'}},
    {auth: {access_token: sensitive[4] + '\nheader', expires_at: '2026-10-19T08:15:00Z'}}]) {
    const {report, requests} = await run(configuration(), options);
    assert.equal(report.status, 'FAIL'); assert.equal(report.stock.totalResults, null);
    assert.equal(requests.filter(request => new URL(request.url).pathname === '/stock').length, 0);
    assert.equal(requests.filter(request => new URL(request.url).pathname === '/authenticate').length, 1);
  }
});

test('scope, pagination, partial response, lifecycle and unreviewed media failures are not hidden', async () => {
  for (const options of [{stockStatus: 206},
    {stock: body => ({...body, totalResults: 99999})},
    {stock: body => ({...body, results: body.results.slice(0, 1)})},
    {stock: (body, page) => ({...body, totalResults: page === 2 ? 4 : 3})},
    {stock: (body, page) => {if (page === 2) body.results[0].metadata.stockId = 'stock-1'; return body;}},
    {stock: body => {body.results[0].advertiser.advertiserId = 'foreign'; return body;}},
    {stock: body => {body.results[0].metadata.lifecycleState = 'SOLD'; return body;}},
    {stock: body => {body.results[0].adverts.retailAdverts.advertiserAdvert.status = 'NOT_PUBLISHED'; return body;}},
    {stock: body => {body.results[0].metadata.lastUpdated = '2027-01-01T00:00:00Z'; return body;}},
    {stock: body => {body.results[0].media.images[0].href = 'https://unreviewed.example.test/image.webp'; return body;}}]) {
    const {report} = await run(configuration(), options);
    assert.equal(report.status, 'FAIL'); assert.equal(report.stock.totalResults, null);
  }
});

test('old independently hosted receiver and foreign authority are visible blockers without a write probe', async () => {
  const config = configuration();
  config.receiver.observedConfiguration = {...scope, deploymentId: 'dep-old', tenantId: 'ten-old',
    providerBaseUrl: null, advertiserId: null, providerKeyPresent: false, providerSecretPresent: false};
  const {report} = await run(config, {commit: 'b'.repeat(40), methodStatus: 500});
  assert.equal(report.status, 'FAIL');
  failure(report, 'INDEPENDENT_RECEIVER_UPGRADE_REQUIRED');
  failure(report, 'RECEIVER_UNSUPPORTED_METHOD_RETURNED_500');
  assert.deepEqual(report.receiver.methodProbe, {httpStatus: 500, expectedHttpStatus: 405,
    postOnlyAdvertised: true, classification: 'UNSUPPORTED_METHOD_SERVER_ERROR_500', postExecutionTested: false});
  failure(report, 'RECEIVER_EXTERNAL_PROVIDER_READ_CONFIGURATION_REQUIRED');
  failure(report, 'RECEIVER_CUSTOMER_TENANT_DEPLOYMENT_AUTHORITY_MISMATCH');
  assert.equal(report.receiver.actionExecuted, false);
});

test('missing notification credentials or receiver configuration is not inferred from health', async () => {
  const config = configuration(); delete config.stock.notificationSecret; delete config.receiver.observedConfiguration;
  const {report} = await run(config);
  failure(report, 'REQUIRED_CREDENTIAL_MISSING_OR_INVALID');
  failure(report, 'RECEIVER_CONFIGURATION_EVIDENCE_REQUIRED');
  failure(report, 'RECEIVER_CUSTOMER_TENANT_DEPLOYMENT_AUTHORITY_MISMATCH');
});

test('old simulator fixture does not inherit successful health as a current contract proof', async () => {
  const {report} = await run(configuration(), {fixtureVersion: 'external-vehicle-provider-v1'});
  assert.equal(report.status, 'FAIL'); failure(report, 'REVIEWED_SIMULATOR_FIXTURE_REQUIRED');
});

test('401 to valid receiver credentials, an open endpoint, missing or broadened method allowance are failures', async () => {
  for (const options of [{methodStatus: 401}, {noKeyStatus: 200}, {allow: 'GET'}, {allow: ''}, {allow: 'GET, POST'}]) {
    const {report} = await run(configuration(), options); assert.equal(report.status, 'FAIL');
  }
});

test('actual unupgraded method 500 remains a precise failure even when version/configuration otherwise pass', async () => {
  const {report} = await run(configuration(), {methodStatus: 500, allow: ''});
  assert.equal(report.status, 'FAIL'); failure(report, 'RECEIVER_UNSUPPORTED_METHOD_RETURNED_500');
  assert.deepEqual(report.checks.filter(check => check.status === 'FAIL').map(check => check.name),
    ['RECEIVER_AUTHENTICATED_METHOD_CONTRACT']);
  assert.equal(report.receiver.methodProbe.postOnlyAdvertised, false);
  assert.equal(report.receiver.methodProbe.postExecutionTested, false);
});

function indexConfiguration(config) {
  config.index = {baseUrl: 'https://runtime.example.test', host: 'runtime.example.test', scope: {...scope},
    sourceId: 'dealership-external-stock-source', connectionProfileRef: 'external-stock-provider',
    protectedResourceBindingRef: 'external-dealership-account', sourceVersion: 'v1',
    headers: {Authorization: `Bearer ${sensitive[5]}`}};
  return config;
}
function indexEvidence() {
  return {sourceId: 'dealership-external-stock-source', enabled: true, connectionProfileRef: 'external-stock-provider',
    protectedResourceBindingRef: 'external-dealership-account', vectorSpace: 'dealer-vehicle',
    preflightState: 'READY', freshnessState: 'CURRENT', work: [], state: {
      sourceId: 'dealership-external-stock-source', sourceVersion: 'v1', status: 'COMPLETED',
      lastStartedAt: '2026-10-19T07:58:00Z', lastSuccessAt: '2026-10-19T07:59:00Z', updatedAt: '2026-10-19T07:59:00Z',
      counts: {sourceCount: 3, normalizedCount: 3, indexedCount: 3, deletedCount: 0,
        acceptedWorkCount: 3, completedWorkCount: 3, failedWorkCount: 0}}};
}

test('optional exact-source index read reports only fresh complete convergence and sanitized counters', async () => {
  const index = indexEvidence(); index.privateCredential = sensitive[1];
  const {report} = await run(indexConfiguration(configuration()), {index});
  assert.equal(report.status, 'PASS'); assert.equal(report.index.status, 'OBSERVED_CURRENT');
  assert.equal(report.index.indexedCount, 3); assert.equal(report.stock.indexedCount, 3);
});

test('optional index cannot use another deployment/source/headers and fails before I/O', async () => {
  for (const mutate of [c => c.index.scope.tenantId = 'foreign', c => c.index.sourceId = 'old-source',
    c => c.index.headers = {}, c => c.index.headers = {Host: 'other.example.test'},
    c => c.index.sourceVersion = undefined]) {
    const config = indexConfiguration(configuration()); mutate(config); let called = false;
    await assert.rejects(preflightExternalFixtures(config, {fetchImpl: () => {called = true;}}));
    assert.equal(called, false);
  }
});

test('stale, failed, pending, mismatched or incomplete index evidence cannot pass', async () => {
  for (const mutate of [v => v.state.lastSuccessAt = '2026-10-18T00:00:00Z',
    v => v.state.sourceVersion = 'old', v => v.state.counts.failedWorkCount = 1,
    v => v.state.counts.indexedCount = 2, v => v.state.counts.completedWorkCount = 2,
    v => v.state.lastErrorAt = '2026-10-19T07:59:30Z', v => v.state.updatedAt = '2027-01-01T00:00:00Z',
    v => v.state.status = 'RUNNING', v => v.freshnessState = 'STALE',
    v => v.work = [{sourceId: v.sourceId, status: 'PENDING', updatedAt: instant}],
    v => delete v.state.counts.sourceCount]) {
    const index = indexEvidence(); mutate(index);
    const {report} = await run(indexConfiguration(configuration()), {index});
    assert.equal(report.status, 'FAIL'); assert.equal(report.index.status, 'NOT_OBSERVED');
    assert.equal(report.stock.indexedCount, null);
  }
});

test('transport exceptions and oversized or malformed upstream JSON never leak raw payload or credentials', async () => {
  for (const response of [() => {throw Error(sensitive.join(' '));},
    () => new Response(sensitive[0] + '!'.repeat(65537)),
    () => new Response('not-json-' + sensitive[1])]) {
    const report = await preflightExternalFixtures(configuration(), {fetchImpl: response, now: () => new Date(instant)});
    assert.equal(report.status, 'FAIL');
    for (const value of sensitive) assert.ok(!JSON.stringify(report).includes(value));
    assert.equal(report.businessWrites, 0);
  }
});

test('CLI requires explicit live opt-in and does not print invalid private stdin', () => {
  const runner = new URL('../external-fixture-preflight.mjs', import.meta.url);
  for (const args of [[], ['--live', '--write'], ['--unknown']]) {
    const result = spawnSync(process.execPath, [runner.pathname, ...args], {input: sensitive.join(' '), encoding: 'utf8'});
    assert.equal(result.status, 2); assert.equal(result.stdout, '');
    for (const value of sensitive) assert.ok(!result.stderr.includes(value));
  }
  const result = spawnSync(process.execPath, [runner.pathname, '--live'], {input: sensitive.join(' '), encoding: 'utf8'});
  assert.equal(result.status, 1); assert.equal(result.stdout, '');
  assert.equal(result.stderr, 'PRIVATE_JSON_CONFIGURATION_REQUIRED\n');
});
