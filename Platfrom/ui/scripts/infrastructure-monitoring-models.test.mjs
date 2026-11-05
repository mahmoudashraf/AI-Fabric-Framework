import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/infrastructureMonitoring/monitoringModels.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
const models = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`)
const now = Date.parse('2026-11-05T12:00:00Z')
const server = { status: 'FRESH', observedAt: new Date(now - 60_000).toISOString() }

test('unknown metrics never become zero; real zero and unbounded limits stay distinct', () => {
  for (const value of [null, undefined, NaN, Infinity, '0']) {
    assert.equal(models.bytes(value), 'Unknown')
    assert.equal(models.percent(value), 'Unknown')
    assert.equal(models.cores(value), 'Unknown')
    assert.equal(models.rate(value), 'Unknown')
  }
  assert.equal(models.bytes(0), '0 B')
  assert.equal(models.percent(0), '0%')
  assert.equal(models.limit(0), 'No hard limit')
  assert.equal(models.limit(-1), 'Unlimited')
})

test('freshness ages last observations and rejects invalid/future timestamps as fresh', () => {
  assert.equal(models.freshness(server, now), 'FRESH')
  assert.equal(models.freshness(server, now + 180_000), 'STALE')
  assert.equal(models.freshness({ ...server, status: 'STALE' }, now), 'STALE')
  for (const observedAt of ['invalid', new Date(now + 60_000).toISOString()]) assert.equal(models.freshness({ ...server, observedAt }, now), 'STALE')
  assert.equal(models.freshness({ ...server, observedAt: null }, now), 'NEVER_RECEIVED')
})

test('ranking leaves missing CPU last and filters role, image and trusted deployment labels', () => {
  const resources = [
    { resourceId: 'unknown', role: 'runtime', name: 'alpha', cpuCoresUsed: null },
    { resourceId: 'zero', role: 'connector', name: 'beta', cpuCoresUsed: 0, image: 'registry/connector:immutable' },
    { resourceId: 'busy', role: 'runtime', name: 'gamma', cpuCoresUsed: 2.1, deploymentName: 'Known deployment' },
  ]
  const fleet = [{ displayName: 'Host', resources }]
  assert.deepEqual(models.resourceRows(fleet, '', '', 'cpuCoresUsed').map(row => row.resource.resourceId), ['busy', 'zero', 'unknown'])
  assert.deepEqual(models.resourceRows(fleet, 'runtime', 'known deployment', 'cpuCoresUsed').map(row => row.resource.resourceId), ['busy'])
  assert.equal(models.resourceRows(fleet, '', 'IMMUTABLE', 'cpuCoresUsed').length, 1)
  assert.equal(models.resourceRows(fleet, 'connector', 'known', 'cpuCoresUsed').length, 0)
})

test('deployment links require Platform mapping and encode the exact deployment/target', () => {
  assert.equal(models.deploymentPath({ providerResourceId: 'not-ownership', name: 'customer-name' }), null)
  const url = new URL(models.deploymentPath({ deploymentId: 'dep/a&b', targetProfileId: 'target/c&d' }), 'https://console.test')
  assert.equal(url.pathname, '/overview')
  assert.equal(url.searchParams.get('deploymentId'), 'dep/a&b')
  assert.equal(url.searchParams.get('targetProfileId'), 'target/c&d')
})

test('history preserves explicit nulls, missing intervals and all actual zero values', () => {
  const point = (offset, value, max = value) => ({ observedAt: new Date(now - offset * 60_000).toISOString(), values: { cpu: value }, maxValues: { cpu: max } })
  const result = models.historySeries([point(10, 1, 2), point(9, 0, 1), point(8, null), point(7, 3, 5), point(4, 4, 6)], 'cpu', 60, '1h', now)
  assert.equal((result.meanPath.match(/M/g) || []).length, 3)
  assert.equal((result.meanPath.match(/L/g) || []).length, 1)
  assert.equal(result.count, 4)
  assert.equal(result.expected, 60)
  assert.equal(result.mean, 2)
  assert.equal(result.peak, 6)
  assert.equal(result.samples.length, 5)
  assert.equal(result.start, now - 3_600_000)
})

test('empty, unsupported, invalid and out-of-window history never invent a measured mean', () => {
  const points = [{ observedAt: 'invalid', values: { cpu: 0 }, maxValues: {} },
    { observedAt: new Date(now - 4_000_000).toISOString(), values: { cpu: 0 }, maxValues: {} },
    { observedAt: new Date(now).toISOString(), values: { cpu: null }, maxValues: { cpu: null } }]
  const result = models.historySeries(points, 'cpu', 60, '1h', now)
  assert.equal(result.mean, null); assert.equal(result.peak, null); assert.equal(result.count, 0)
  assert.equal(result.meanPath.trim(), '')
})

test('maximum-only buckets retain actual coverage without inventing their missing mean', () => {
  const result = models.historySeries([{ observedAt: new Date(now).toISOString(), values: { cpu: null }, maxValues: { cpu: 5 } }], 'cpu', 60, '1h', now)
  assert.equal(result.count, 1)
  assert.equal(result.mean, null)
  assert.equal(result.peak, 5)
  assert.equal(result.meanPath.trim(), '')
  assert.match(result.maxPath, /^M/)
})
