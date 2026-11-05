import assert from 'node:assert/strict'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { isAbsolute, join } from 'node:path'

const [privateRoot, output] = process.argv.slice(2)
assert.ok(isAbsolute(privateRoot ?? '') && isAbsolute(output ?? ''), 'Pass explicit private evidence and public output directories.')
const read = name => JSON.parse(readFileSync(join(privateRoot, 'receipts', name + '.private.json')))
const final = read('final-qualified'), outage = read('observer-outage'), indexing = read('staging-bounded-indexing')
assert.equal(final.status, 'PASSED'); assert.equal(outage.status, 'PASSED'); assert.equal(outage.timerRestored, true)
assert.equal(indexing.status, 'PASSED'); assert.equal(indexing.afterRemoval.source.status, 'DELETED')
const suites = ['baseline', 'tuned'].flatMap(label => ['staging', 'production'].map(environment => {
  const result = read(`${environment}-${label}-benchmark`)
  assert.equal(result.failures.length, 0)
  return { environment, label, queryCount: result.results.length, durationsMs: result.results.map(r => r.elapsedMs) }
}))
const handover = ['staging', 'production'].map(environment => {
  const result = read(`${environment}-support-actions-handover`); assert.equal(result.status, 'PASSED')
  return { environment, persistedConversationReadback: true, cancelledWithoutWrite: true, confirmedReceipt: true,
    humanReplyVisible: true, aiPausedDuringHumanOwnership: true, explicitAiReturn: true }
})
const footprint = ['staging', 'production'].map(environment => {
  const before = read(`${environment}-${environment === 'staging' ? 'baseline-v2' : 'baseline'}-sample`)
  const after = read(`${environment}-final-warm-sample`)
  const sum = sample => sample.resources.filter(r => r.state === 'running' && ['runtime', 'connector'].includes(r.role)
    && !r.name.includes('postgres')).reduce((bytes, r) => bytes + r.memoryWorkingSetBytes, 0)
  return { environment, beforeWorkingSetBytes: sum(before), afterWorkingSetBytes: sum(after),
    interpretation: 'Short observed snapshots, not a controlled long-duration capacity or latency guarantee.' }
})
const result = {
  observedAt: final.at, status: 'PASSED', applications: final.apps,
  hosts: final.hosts.map(h => ({ controlPlane: h.controlPlane, remainingRunningSourceBuilds: h.remainingRunningSourceBuilds, timers: h.timers })),
  customerMonitoringAccess: final.customerMonitoringAccess, quota: final.quota,
  suites, handover, footprint, telemetryOverhead: read('telemetry-overhead'),
  telemetryOutage: { productQueries: outage.productQueries, observed: outage.observed, timerRestored: outage.timerRestored },
  indexing: { bytes: 251, evidenceCount: indexing.proof.evidenceCount, removedFromIndex: true, productionSelected: false },
  limits: ['No high-load or maximum-customer capacity claim.', 'No smaller hard memory/CPU ceiling was qualified.',
    'Uncertain provider mutations still require explicit readback/review, never automatic replay.'],
}
mkdirSync(output, { recursive: true })
writeFileSync(join(output, 'report.json'), JSON.stringify(result, null, 2) + '\n', { flag: 'wx' })
console.log(JSON.stringify({ status: result.status, applications: result.applications.length, output }))
