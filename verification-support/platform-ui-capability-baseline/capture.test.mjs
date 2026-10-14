import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const directory = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(directory, '../..');
const script = path.join(directory, 'capture.mjs');
const baselinePath = path.join(directory, '2026-10-14-admin-operator-source-baseline.json');
const run = (...args) => JSON.parse(execFileSync(process.execPath, [script, ...args], { cwd: root, encoding: 'utf8' }));

test('frozen baseline includes routes, field/action controls, HTTP contracts and hashes', () => {
  const baseline = JSON.parse(fs.readFileSync(baselinePath, 'utf8'));
  assert.equal(baseline.evidenceLevel, 'SOURCE_ONLY_NOT_LIVE_VERIFIED');
  assert.equal(baseline.routes.length, 37);
  assert.equal(baseline.navigation.length, 34);
  assert.equal(baseline.surfaces.reduce((sum, item) => sum + item.controls.length, 0), 1577);
  assert.equal(baseline.exportedApis.length, 323);
  assert.equal(baseline.exportedApis.filter((item) => item.requests.length > 0).length, 312);
  assert.ok(baseline.files.every((item) => /^[a-f0-9]{64}$/.test(item.sha256)));
});

test('fresh capture is comparable and cannot overwrite an existing snapshot', () => {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'platform-ui-baseline-'));
  try {
    const snapshot = path.join(temporary, 'snapshot.json');
    run('--output', snapshot);
    const before = fs.readFileSync(snapshot, 'utf8');
    assert.equal(run('--compare', snapshot).reviewRequired, false);
    assert.throws(() => execFileSync(process.execPath, [script, '--output', snapshot],
      { cwd: root, encoding: 'utf8', stdio: 'pipe' }));
    assert.equal(fs.readFileSync(snapshot, 'utf8'), before);
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true });
  }
});

test('comparison flags missing routes and API references/contracts for explicit review', () => {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'platform-ui-baseline-'));
  try {
    const snapshot = path.join(temporary, 'snapshot.json');
    run('--output', snapshot);
    const baseline = JSON.parse(fs.readFileSync(snapshot, 'utf8'));
    baseline.routes.push({ path: '/fixture-missing-route', component: 'FixtureOnly' });
    baseline.exportedApis.push({ name: 'fixtureMissingApi', requests: [] });
    baseline.surfaces[0].apiReferences.push({ name: 'fixtureMissingApi', lines: [] });
    baseline.exportedApis.find((item) => item.name === 'fetchDeployments').requests[0].method = 'FIXTURE_ONLY';
    fs.writeFileSync(snapshot, JSON.stringify(baseline));
    const report = run('--compare', snapshot);
    assert.equal(report.reviewRequired, true);
    assert.ok(report.removedRoutes.includes('/fixture-missing-route'));
    assert.ok(report.removedApiExports.includes('fixtureMissingApi'));
    assert.ok(report.missingApiReferences.includes('fixtureMissingApi'));
    assert.ok(report.changedApiContracts.includes('fetchDeployments'));
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true });
  }
});
