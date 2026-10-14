import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const testDirectory = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(testDirectory, '..');
const verifier = path.join(root, 'verify-s3-canary.mjs');
const fakeAws = path.join(testDirectory, 'fixtures', 'fake-aws.mjs');
const cleanProcessEnvironment = Object.fromEntries(
  Object.entries(process.env).filter(([name]) => !name.startsWith('S3_CANARY_')),
);

const fixtureEnvironment = {
  S3_CANARY_BUCKET: 'loomai-dealership-canary-978e0cdb142540e2',
  S3_CANARY_REGION: 'eu-north-1',
  S3_CANARY_ENDPOINT: 'https://s3.eu-north-1.amazonaws.com',
  S3_CANARY_VERSIONING_ENABLED: 'false',
  S3_CANARY_STAGING_PREFIX: 'dealership/staging/',
  S3_CANARY_STAGING_ACCESS_KEY_ID: 'STAGING_FIXTURE_ACCESS_KEY',
  S3_CANARY_STAGING_SECRET_ACCESS_KEY: 'staging-fixture-secret-value',
  S3_CANARY_PRODUCTION_PREFIX: 'dealership/production/',
  S3_CANARY_PRODUCTION_ACCESS_KEY_ID: 'PRODUCTION_FIXTURE_ACCESS_KEY',
  S3_CANARY_PRODUCTION_SECRET_ACCESS_KEY: 'production-fixture-secret-value',
};

function run(mode = 'strict', overrides = {}) {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), '01036-s3-test-'));
  const output = path.join(temporary, 'evidence.json');
  const log = path.join(temporary, 'aws-calls.jsonl');
  const result = spawnSync(process.execPath, [verifier, '--aws-cli', fakeAws, '--output', output], {
    encoding: 'utf8',
    env: { ...cleanProcessEnvironment, ...fixtureEnvironment, ...overrides, FAKE_AWS_MODE: mode, FAKE_AWS_LOG: log },
  });
  return {
    temporary,
    result,
    evidence: fs.existsSync(output) ? JSON.parse(fs.readFileSync(output, 'utf8')) : null,
    calls: fs.existsSync(log) ? fs.readFileSync(log, 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse) : [],
  };
}

function cleanup(runResult) {
  fs.rmSync(runResult.temporary, { recursive: true, force: true });
}

test('passes strict read-only, prefix-isolated identities and emits sanitized evidence', () => {
  const execution = run();
  try {
    assert.equal(execution.result.status, 0, execution.result.stderr);
    assert.equal(execution.evidence.status, 'PASS');
    assert.equal(execution.evidence.versioning.observedStatus, 'UNVERSIONED');
    assert.equal(execution.evidence.scopes.staging.utf8TextObjectCount, 3);
    assert.equal(execution.evidence.scopes.production.utf8TextObjectCount, 3);
    assert.equal(execution.evidence.scopes.staging.mutationDenial.put, 'ACCESS_DENIED');
    assert.equal(execution.evidence.scopes.production.crossPrefix.get, 'ACCESS_DENIED');
    const serialized = JSON.stringify(execution.evidence);
    for (const forbidden of [
      'STAGING_FIXTURE_ACCESS_KEY', 'PRODUCTION_FIXTURE_ACCESS_KEY',
      'staging-fixture-secret-value', 'production-fixture-secret-value',
      'policy-1.txt', 'Policy 1 for', 'arn:aws:iam',
    ]) assert.equal(serialized.includes(forbidden), false, `evidence leaked ${forbidden}`);
    const consoleOutput = `${execution.result.stdout}\n${execution.result.stderr}`;
    for (const forbidden of [
      'STAGING_FIXTURE_ACCESS_KEY', 'PRODUCTION_FIXTURE_ACCESS_KEY',
      'staging-fixture-secret-value', 'production-fixture-secret-value',
      'policy-1.txt', 'Policy 1 for', 'arn:aws:iam',
    ]) assert.equal(consoleOutput.includes(forbidden), false, `console output leaked ${forbidden}`);
    const operations = execution.calls.flatMap(({ args }) => args.filter((value) => [
      'get-caller-identity', 'get-bucket-versioning', 'list-objects-v2', 'head-object',
      'get-object', 'put-object', 'delete-object',
    ].includes(value)));
    assert.ok(operations.includes('list-objects-v2'));
    assert.ok(operations.includes('head-object'));
    assert.ok(operations.includes('get-object'));
    assert.ok(operations.includes('put-object'));
    assert.ok(operations.includes('delete-object'));
    assert.ok(execution.calls.every((call) => call.canaryEnvironmentVariableCount === 0));
  } finally {
    cleanup(execution);
  }
});
test('fails closed when conditional put permission exists without changing fixture data', () => {
  const execution = run('write-allowed');
  try {
    assert.equal(execution.result.status, 1);
    assert.equal(execution.evidence.status, 'FAIL');
    assert.equal(execution.evidence.failure.code, 'WRITE_NOT_DENIED');
    assert.equal(JSON.stringify(execution.evidence).includes('fixture-secret-value'), false);
  } finally {
    cleanup(execution);
  }
});

test('fails closed when conditional delete permission is not explicitly denied', () => {
  const execution = run('delete-allowed');
  try {
    assert.equal(execution.result.status, 1);
    assert.equal(execution.evidence.failure.code, 'DELETE_NOT_DENIED');
  } finally {
    cleanup(execution);
  }
});

test('fails with sanitized evidence when AWS CLI is unavailable', () => {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), '01036-s3-cli-'));
  const output = path.join(temporary, 'evidence.json');
  const result = spawnSync(process.execPath, [
    verifier, '--aws-cli', path.join(temporary, 'missing-aws'), '--output', output,
  ], { encoding: 'utf8', env: { ...cleanProcessEnvironment, ...fixtureEnvironment } });
  try {
    assert.equal(result.status, 1);
    const evidence = JSON.parse(fs.readFileSync(output, 'utf8'));
    assert.equal(evidence.failure.code, 'AWS_CLI_UNAVAILABLE');
    assert.equal(JSON.stringify(evidence).includes('fixture-secret-value'), false);
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true });
  }
});
test('requires AWS CLI v2 with non-mutating conditional probe support', () => {
  const oldCli = run('strict', { FAKE_AWS_VERSION: '1.99.0' });
  const unsafeCli = run('conditional-probe-unsupported');
  try {
    assert.equal(oldCli.result.status, 1);
    assert.equal(oldCli.evidence.failure.code, 'AWS_CLI_VERSION_UNSUPPORTED');
    assert.equal(unsafeCli.result.status, 1);
    assert.equal(unsafeCli.evidence.failure.code, 'AWS_CLI_CONDITIONAL_PROBE_UNAVAILABLE');
    assert.equal(
      unsafeCli.calls.some(({ args }) => args.includes('get-caller-identity')),
      false,
      'live verification must not start without safe conditional probes',
    );
  } finally {
    cleanup(oldCli);
    cleanup(unsafeCli);
  }
});

test('fails closed when cross-prefix objects can be listed or read', () => {
  const listReadable = run('cross-readable');
  const headReadable = run('cross-head-readable');
  const getReadable = run('cross-get-readable');
  try {
    assert.equal(listReadable.result.status, 1);
    assert.equal(listReadable.evidence.failure.code, 'CROSS_PREFIX_LIST_NOT_DENIED');
    assert.equal(headReadable.result.status, 1);
    assert.equal(headReadable.evidence.failure.code, 'CROSS_PREFIX_HEAD_NOT_DENIED');
    assert.equal(getReadable.result.status, 1);
    assert.equal(getReadable.evidence.failure.code, 'CROSS_PREFIX_GET_NOT_DENIED');
  } finally {
    cleanup(listReadable);
    cleanup(headReadable);
    cleanup(getReadable);
  }
});

test('rejects an own-prefix listing that contains an escaped object key', () => {
  const execution = run('listing-escape');
  try {
    assert.equal(execution.result.status, 1);
    assert.equal(execution.evidence.failure.code, 'PREFIX_SCOPE_VIOLATION');
  } finally {
    cleanup(execution);
  }
});

test('requires three non-empty UTF-8 text objects in each exact scope', () => {
  const insufficient = run('insufficient-text');
  const invalidUtf8 = run('invalid-utf8');
  try {
    assert.equal(insufficient.result.status, 1);
    assert.equal(insufficient.evidence.failure.code, 'INSUFFICIENT_TEXT_OBJECTS');
    assert.equal(invalidUtf8.result.status, 1);
    assert.equal(invalidUtf8.evidence.failure.code, 'INVALID_UTF8_DOCUMENT');
  } finally {
    cleanup(insufficient);
    cleanup(invalidUtf8);
  }
});

test('requires staging and production credentials to resolve to separate principals', () => {
  const execution = run('same-principal');
  try {
    assert.equal(execution.result.status, 1);
    assert.equal(execution.evidence.failure.code, 'IDENTITIES_NOT_SEPARATE');
  } finally {
    cleanup(execution);
  }
});

test('requires actual unversioned bucket state and the exact reviewed endpoint contract', () => {
  const versioned = run('versioned');
  const wrongRegion = run('strict', { S3_CANARY_REGION: 'eu-west-2' });
  try {
    assert.equal(versioned.result.status, 1);
    assert.equal(versioned.evidence.failure.code, 'VERSIONING_NOT_DISABLED');
    assert.equal(wrongRegion.result.status, 1);
    assert.equal(wrongRegion.evidence.failure.code, 'CONTRACT_MISMATCH');
  } finally {
    cleanup(versioned);
    cleanup(wrongRegion);
  }
});

test('reads an ignored key-value file while process environment takes precedence', () => {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), '01036-s3-env-'));
  const envFile = path.join(temporary, '.env');
  const output = path.join(temporary, 'evidence.json');
  fs.writeFileSync(envFile, Object.entries({ ...fixtureEnvironment, S3_CANARY_REGION: 'incorrect-file-value' })
    .map(([name, value]) => `${name}=${value}`)
    .join('\n'));
  fs.chmodSync(envFile, 0o600);
  const result = spawnSync(process.execPath, [
    verifier, '--aws-cli', fakeAws, '--env-file', envFile, '--output', output,
  ], {
    encoding: 'utf8',
    env: { ...cleanProcessEnvironment, S3_CANARY_REGION: 'eu-north-1', FAKE_AWS_MODE: 'strict' },
  });
  try {
    assert.equal(result.status, 0, result.stderr);
    assert.equal(JSON.parse(fs.readFileSync(output, 'utf8')).status, 'PASS');
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true });
  }
});

test('rejects a credential file with group or other permissions', { skip: process.platform === 'win32' }, () => {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), '01036-s3-env-mode-'));
  const envFile = path.join(temporary, '.env');
  const output = path.join(temporary, 'evidence.json');
  fs.writeFileSync(envFile, Object.entries(fixtureEnvironment)
    .map(([name, value]) => `${name}=${value}`)
    .join('\n'), { mode: 0o644 });
  fs.chmodSync(envFile, 0o644);
  const result = spawnSync(process.execPath, [
    verifier, '--aws-cli', fakeAws, '--env-file', envFile, '--output', output,
  ], { encoding: 'utf8', env: cleanProcessEnvironment });
  try {
    assert.equal(result.status, 1);
    const evidence = JSON.parse(fs.readFileSync(output, 'utf8'));
    assert.equal(evidence.failure.code, 'ENV_FILE_NOT_PRIVATE');
    assert.equal(JSON.stringify(evidence).includes('fixture-secret-value'), false);
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true });
  }
});
