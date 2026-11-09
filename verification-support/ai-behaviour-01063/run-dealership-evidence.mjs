import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { parseArgs } from 'node:util'

const { values: args } = parseArgs({ options: {
  scope: { type: 'string' }, root: { type: 'string' }, only: { type: 'string' },
  scenarios: { type: 'string' },
} })
assert.ok(args.scope && args.root, 'Provide the current installation scope and a new private output directory')
process.umask(0o077)
const root = resolve(args.root)
assert.ok(!existsSync(root), 'Keep each observed run separate')
mkdirSync(root, { recursive: true, mode: 0o700 })
const save = (name, value) => writeFileSync(`${root}/${name}.private.json`, JSON.stringify(value, null, 2), { mode: 0o600 })
const scope = JSON.parse(readFileSync(args.scope, 'utf8'))
const scenarios = JSON.parse(readFileSync(args.scenarios || new URL('./dealership-evidence.scenarios.json', import.meta.url)))
const selected = args.only?.split(',')
const queries = selected ? scenarios.filter(row => selected.includes(row.id)) : scenarios
assert.ok(queries.length && queries.length <= 8)
if (selected) assert.equal(queries.length, selected.length)
const origin = new URL(scope.appUrl).origin
assert.equal(new URL(origin).protocol, 'https:')
const manifestResponse = await fetch(`https://api.loomai.pro/api/public/ai-workspace/installations/${scope.installationId}/manifest`, {
  headers: { Origin: origin }, signal: AbortSignal.timeout(30000),
})
assert.equal(manifestResponse.status, 200)
const manifest = await manifestResponse.json()
assert.equal(manifest.connection.mode, 'public-runtime-anonymous')
assert.equal(manifest.connection.runtimeBaseUrl, scope.runtimeBaseUrl)
assert.equal(manifest.assignmentRevision, scope.assignmentRevision)
save('manifest', manifest)
const connection = manifest.connection
const results = []
const writes = body => [...(body.actions ?? []), ...(body.results ?? []).flatMap(writes)]
  .filter(action => action.actionResult?.success === true && ![
    'dealership_search_inventory', 'dealership_get_vehicle', 'dealership_compare_vehicles',
  ].includes(action.action))
for (const session of new Set(queries.map(row => row.session))) {
  const bootstrap = await fetch(new URL(connection.anonymousBootstrap.url, connection.runtimeBaseUrl), {
    method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: '{}',
    signal: AbortSignal.timeout(30000),
  })
  assert.equal(bootstrap.status, 200)
  const identity = await bootstrap.json()
  assert.ok(identity.token)
  const conversationId = `chat-${randomUUID()}`
  for (const scenario of queries.filter(row => row.session === session)) {
    const request = { query: scenario.prompt, conversationId, mode: 'executor', position: 'landing',
      context: { workspaceInstallationId: manifest.installationId, assignmentRevision: manifest.assignmentRevision } }
    const route = connection.routes.conversationMessageUrlTemplate.replace('{conversationId}', conversationId)
    const started = Date.now()
    const response = await fetch(new URL(route, connection.runtimeBaseUrl), {
      method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json',
        Authorization: `Bearer ${identity.token}`, 'Idempotency-Key': `evidence-${randomUUID()}` },
      body: JSON.stringify(request), signal: AbortSignal.timeout(180000),
    })
    const body = await response.json()
    const result = { id: scenario.id, session, at: new Date().toISOString(), request, status: response.status,
      durationMs: Date.now() - started, response: body, semanticVerdict: 'PENDING_MANUAL_REVIEW' }
    results.push(result)
    save(scenario.id, result)
    assert.equal(response.status, 200)
    assert.equal(body.conversationId, conversationId)
    assert.equal(writes(body).length, 0, 'Stop if any non-READ action succeeds')
    assert.notEqual(body.fallbackReason, 'PROVIDER_BUDGET_INTERRUPTED')
    console.log(JSON.stringify({ id: scenario.id, success: body.success, type: body.type,
      answer: body.answer, seconds: result.durationMs / 1000 }))
  }
}
save('summary', { observedAt: new Date().toISOString(), scope, count: results.length,
  acceptance: 'TRANSPORT_ONLY_PENDING_MANUAL_EVIDENCE_REVIEW', uiAcceptanceClaimed: false,
  productSettingsChanged: false, results })
