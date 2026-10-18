import assert from 'node:assert/strict';
import {test} from 'node:test';
import {mkdtemp, readFile, writeFile, chmod, rm, symlink, stat} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {configuration, measure, remoteCommand, summarize} from '../measure-host-baseline.mjs';

const args = ['--environment', 'staging', '--host', '192.0.2.1', '--user', 'loomops',
  '--identity', '/tmp/private-identity', '--output', '/tmp/never-overwrite.json', '--samples', '3', '--interval', '5'];
const config = () => configuration(args);
const observations = () => Array.from({length: 3}, (_, i) => ({epochSeconds: 1000 + i * 5, cores: 4,
  cpuTicks: [100 + 20 * i, 0, 50 + 10 * i, 1000 + 50 * i, 2 + 10 * i, 0, 0, 3 + 10 * i],
  memoryKiB: {MemTotal: 16000, MemAvailable: 10000 - 100 * i, SwapTotal: 4000, SwapFree: 3000 - 10 * i},
  dockerFilesystem: {blockSize: 4096, blocks: 1000, freeBlocks: 700 - i, availableBlocks: 600 - i}}));

test('strict bounded options reject SSH/command injection and duplicate or unknown flags', () => {
  for (const value of ['-oProxyCommand=bad', 'host;whoami', 'user@host', 'host\nother']) {
    assert.throws(() => configuration(args.map((part, i) => i === 3 ? value : part)));
  }
  for (const [name, value] of [['--samples', '2'], ['--samples', '3.5'], ['--samples', '9999'],
    ['--interval', 'NaN'], ['--interval', '0'], ['--environment', 'preview'], ['--user', 'root;touch']]) {
    const modified = [...args]; modified[modified.indexOf(name) + 1] = value;
    assert.throws(() => configuration(modified));
  }
  assert.throws(() => configuration([...args, '--host', 'other']));
  assert.throws(() => configuration([...args, '--command', 'docker rm']));
  assert.throws(() => remoteCommand({samples: 3, interval: '5;touch bad'}));
});

test('uses interval CPU deltas, excludes iowait/steal, samples memory and never approves capacity', () => {
  const report = summarize(observations(), config());
  assert.equal(report.cpuBusyMilliCpus.p95, 1200);
  assert.equal(report.maxIoWaitPercent, 10);
  assert.equal(report.maxStealPercent, 10);
  assert.equal(report.memoryBytes.minAvailableObserved, 9800 * 1024);
  assert.equal(report.memoryBytes.maxSwapUsedObserved, 1020 * 1024);
  assert.equal(report.dockerFilesystemBytes.minAvailableObserved, 598 * 4096);
  assert.equal(report.candidateWorkloadIncluded, false);
  assert.equal(report.policyApproval, false);
  assert.equal(report.providerCalls, 0);
  assert.equal(report.swapExcludedFromCapacity, true);
});

test('rejects incomplete, reset, stalled, interrupted, changed and malformed observations', () => {
  const mutate = [rows => rows.pop(), rows => rows[1].cpuTicks[0] = 0,
    rows => rows[1].epochSeconds = rows[0].epochSeconds,
    rows => rows[2].epochSeconds += 20, rows => rows[1].cores = 2,
    rows => rows[1].memoryKiB.MemTotal += 1, rows => rows[1].memoryKiB.MemAvailable = -1,
    rows => rows[1].dockerFilesystem.blocks += 1, rows => rows[1].cpuTicks[2] = NaN,
    rows => rows[1].memoryKiB.SwapFree = 9000, rows => rows[1].dockerFilesystem.availableBlocks = 2000];
  for (const change of mutate) { const rows = observations(); change(rows); assert.throws(() => summarize(rows, config())); }
});

test('exclusive private output, private key, read-only SSH and sanitized transport failures', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'host-baseline-test-'));
  try {
    const identity = join(dir, 'key');
    await writeFile(identity, 'fixture-private-key', {mode: 0o600});
    const current = {...config(), identity, output: join(dir, 'evidence.json')};
    let calls = 0;
    const run = async (command, argv) => {
      calls++;
      assert.equal(command, 'ssh');
      assert.ok(argv.includes('StrictHostKeyChecking=yes'));
      assert.equal(argv.at(-2), 'loomops@192.0.2.1');
      assert.ok(argv.at(-1).includes('/proc/stat'));
      assert.ok(!/docker (rm|stop|prune)|\bsudo\b|\bcurl\b/.test(argv.at(-1)));
      return {stdout: observations().map(row => JSON.stringify(row)).join('\n')};
    };
    await measure(current, run);
    assert.equal((await stat(current.output)).mode & 0o077, 0);
    assert.equal(JSON.parse(await readFile(current.output, 'utf8')).status, 'PASS');
    await assert.rejects(measure(current, run));
    assert.equal(calls, 1);
    const failed = {...current, output: join(dir, 'failed.json')};
    await assert.rejects(measure(failed, async () => {throw new Error('private-provider-secret');}), /HOST_BASELINE_MEASUREMENT_FAILED/);
    const evidence = await readFile(failed.output, 'utf8');
    assert.ok(!evidence.includes('private-provider-secret'));
    assert.equal(JSON.parse(evidence).status, 'FAIL');
    const linked = join(dir, 'linked-key'); await symlink(identity, linked);
    await assert.rejects(measure({...current, identity: linked, output: join(dir, 'linked.json')}, run));
    await chmod(identity, 0o644);
    await assert.rejects(measure({...current, output: join(dir, 'public-key.json')}, run));
    assert.equal(calls, 1);
  } finally { await rm(dir, {recursive: true, force: true}); }
});
