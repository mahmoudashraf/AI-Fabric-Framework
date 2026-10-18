#!/usr/bin/env node
import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {constants} from 'node:fs';
import {open, lstat} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {promisify} from 'node:util';

const execute = promisify(execFile);
const positive = value => Number.isSafeInteger(value) && value > 0;
const nonnegative = value => Number.isSafeInteger(value) && value >= 0;

export function configuration(argv) {
  const values = {};
  const accepted = ['environment', 'host', 'user', 'identity', 'samples', 'interval', 'output'];
  for (let i = 0; i < argv.length; i += 2) {
    const name = argv[i]?.slice(2);
    assert.ok(argv[i]?.startsWith('--') && accepted.includes(name) && !Object.hasOwn(values, name), 'Invalid argument.');
    assert.ok(argv[i + 1] && !argv[i + 1].startsWith('--'), 'Missing argument value.');
    values[name] = argv[i + 1];
  }
  assert.ok(['staging', 'production'].includes(values.environment), 'An explicit environment is required.');
  // Only a plain hostname/IP and Unix account may enter SSH destination parsing.
  assert.match(values.host ?? '', /^(?!-)[A-Za-z0-9][A-Za-z0-9.-]{0,252}$/);
  assert.match(values.user ?? '', /^[a-z_][a-z0-9_-]{0,31}$/);
  assert.ok(values.identity && values.output, 'Explicit identity and private output paths are required.');
  const samples = Number(values.samples ?? 25);
  const interval = Number(values.interval ?? 5);
  assert.ok(positive(samples) && samples >= 3 && samples <= 721, 'Sample count must be 3..721.');
  assert.ok(positive(interval) && interval <= 60 && (samples - 1) * interval <= 3600, 'Sampling must be bounded to one hour.');
  return {...values, samples, interval, identity: resolve(values.identity), output: resolve(values.output)};
}

export function remoteCommand({samples, interval}) {
  assert.ok(positive(samples) && samples >= 3 && samples <= 721 && positive(interval) && interval <= 60
    && (samples - 1) * interval <= 3600, 'Invalid sample bounds.');
  return `set -eu
export LC_ALL=C
i=0
while [ "$i" -lt ${samples} ]; do
  printf '{"epochSeconds":'
  date +%s | tr -d '\\n'
  awk '/^cpu / {printf ",\\"cpuTicks\\":["; for (i=2;i<=9;i++) printf "%s%s", (i==2 ? "" : ","), $i; printf "]"} /^cpu[0-9]+ / {n++} END {printf ",\\"cores\\":%d", n}' /proc/stat
  awk 'BEGIN {printf ",\\"memoryKiB\\":{"} /^(MemTotal|MemAvailable|SwapTotal|SwapFree):/ {gsub(":", "", $1); printf "%s\\"%s\\":%s", (n++ ? "," : ""), $1, $2} END {printf "}"}' /proc/meminfo
  printf ',"dockerFilesystem":'
  stat -f -c '{"blockSize":%S,"blocks":%b,"freeBlocks":%f,"availableBlocks":%a}' /var/lib/docker | tr -d '\\n'
  printf '}\\n'
  i=$((i+1))
  if [ "$i" -lt ${samples} ]; then sleep ${interval}; fi
done`;
}

function validate(row) {
  assert.ok(positive(row.epochSeconds) && positive(row.cores), 'Invalid host/time observation.');
  assert.ok(Array.isArray(row.cpuTicks) && row.cpuTicks.length === 8 && row.cpuTicks.every(nonnegative), 'Invalid CPU counters.');
  const memory = row.memoryKiB;
  assert.ok(memory && positive(memory.MemTotal) && nonnegative(memory.MemAvailable)
    && nonnegative(memory.SwapTotal) && nonnegative(memory.SwapFree)
    && memory.MemAvailable <= memory.MemTotal && memory.SwapFree <= memory.SwapTotal, 'Invalid memory counters.');
  const disk = row.dockerFilesystem;
  assert.ok(disk && positive(disk.blockSize) && positive(disk.blocks) && nonnegative(disk.freeBlocks)
    && nonnegative(disk.availableBlocks) && disk.availableBlocks <= disk.freeBlocks && disk.freeBlocks <= disk.blocks
    && Number.isSafeInteger(disk.blockSize * disk.blocks), 'Invalid filesystem counters.');
}

export function summarize(rows, config) {
  assert.equal(rows.length, config.samples, 'Incomplete observation window.');
  rows.forEach(validate);
  const first = rows[0];
  const cpu = [];
  const ioWait = [];
  const steal = [];
  for (let i = 1; i < rows.length; i++) {
    const previous = rows[i - 1];
    const row = rows[i];
    assert.equal(row.cores, first.cores, 'CPU topology changed during measurement.');
    assert.equal(row.memoryKiB.MemTotal, first.memoryKiB.MemTotal, 'Memory topology changed during measurement.');
    assert.equal(row.dockerFilesystem.blockSize * row.dockerFilesystem.blocks,
      first.dockerFilesystem.blockSize * first.dockerFilesystem.blocks, 'Filesystem capacity changed during measurement.');
    assert.ok(row.epochSeconds > previous.epochSeconds
      && row.epochSeconds - previous.epochSeconds >= config.interval
      && row.epochSeconds - previous.epochSeconds <= config.interval + 5, 'Invalid or interrupted measurement cadence.');
    const deltas = row.cpuTicks.map((value, index) => value - previous.cpuTicks[index]);
    assert.ok(deltas.every(nonnegative), 'CPU counter reset/reboot detected.');
    const total = deltas.reduce((a, b) => a + b, 0);
    assert.ok(positive(total), 'CPU observation has no elapsed ticks.');
    cpu.push((total - deltas[3] - deltas[4] - deltas[7]) / total * row.cores * 1000);
    ioWait.push(deltas[4] / total * 100);
    steal.push(deltas[7] / total * 100);
  }
  cpu.sort((a, b) => a - b);
  const percentile = p => cpu[Math.ceil(p * cpu.length) - 1];
  return {
    schemaVersion: 'loomai-host-baseline-evidence-v2', environment: config.environment, host: config.host,
    startedAt: new Date(first.epochSeconds * 1000).toISOString(),
    endedAt: new Date(rows.at(-1).epochSeconds * 1000).toISOString(),
    samples: rows.length, intervals: cpu.length, sampleSeconds: config.interval, cores: first.cores,
    cpuBusyMilliCpus: {min: cpu[0], p50: percentile(0.5), p95: percentile(0.95), max: cpu.at(-1)},
    maxIoWaitPercent: Math.max(...ioWait), maxStealPercent: Math.max(...steal),
    memoryBytes: {total: first.memoryKiB.MemTotal * 1024,
      minAvailableObserved: Math.min(...rows.map(row => row.memoryKiB.MemAvailable)) * 1024,
      maxSwapUsedObserved: Math.max(...rows.map(row => row.memoryKiB.SwapTotal - row.memoryKiB.SwapFree)) * 1024},
    dockerFilesystemBytes: {total: first.dockerFilesystem.blocks * first.dockerFilesystem.blockSize,
      minAvailableObserved: Math.min(...rows.map(row => row.dockerFilesystem.availableBlocks * row.dockerFilesystem.blockSize))},
    readOnly: true, providerCalls: 0, candidateWorkloadIncluded: false, policyApproval: false,
    swapExcludedFromCapacity: true,
    limitations: ['Idle/current-fleet baseline only; no candidate, startup, concurrency or promotion-overlap proof.',
      'Interval samples can miss between-sample peaks; observed CPU busy excludes iowait and steal, reported separately.',
      'Only the /var/lib/docker filesystem is measured; external volumes, vector quota and database capacity are not certified.',
      'This tool never creates or approves admission policies, customer grants or reservations.'],
  };
}

export async function measure(config, run = execute) {
  const identity = await lstat(config.identity);
  assert.ok(identity.isFile() && !identity.isSymbolicLink() && !(identity.mode & 0o077), 'SSH identity must be a private regular file.');
  // Exclusive output reservation prevents overwriting prior evidence or following a symlink.
  const output = await open(config.output, constants.O_CREAT | constants.O_EXCL | constants.O_WRONLY, 0o600);
  try {
    const {stdout} = await run('ssh', ['-T', '-i', config.identity, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
      '-o', 'ConnectTimeout=15', '-o', 'ConnectionAttempts=1', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=2',
      `${config.user}@${config.host}`, remoteCommand(config)],
    {encoding: 'utf8', timeout: (config.samples * config.interval + 60) * 1000, maxBuffer: 2_000_000});
    const rows = stdout.trim().split('\n').map(line => JSON.parse(line));
    const report = summarize(rows, config);
    await output.writeFile(JSON.stringify({status: 'PASS', ...report, observations: rows}, null, 2));
    return report;
  } catch {
    await output.writeFile(JSON.stringify({status: 'FAIL', observedAt: new Date().toISOString(),
      errorCode: 'HOST_BASELINE_MEASUREMENT_FAILED', policyApproval: false}));
    throw new Error('HOST_BASELINE_MEASUREMENT_FAILED');
  } finally { await output.close(); }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const config = configuration(process.argv.slice(2));
    const report = await measure(config);
    console.log(JSON.stringify({...report, output: config.output}));
  } catch {
    console.error('HOST_BASELINE_MEASUREMENT_FAILED: verify explicit arguments, private paths and SSH access.');
    process.exitCode = 1;
  }
}
