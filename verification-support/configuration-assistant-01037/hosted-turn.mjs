import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

// Explicit operator qualification against a real customer's disposable draft.
const args = process.argv.slice(2)
const option = name => { const i = args.indexOf(name); return i < 0 ? undefined : args[i + 1] }
const environment = option('--environment')
const name = option('--case')
const commandPath = option('--command')
const state = process.env.AUTHORING_PRIVATE_STATE
assert.ok(['staging', 'production'].includes(environment), 'Choose the exact hosted environment')
assert.ok(state && commandPath && /^[a-z0-9-]+$/.test(name ?? ''), 'Provide private state, a case name and command JSON')
const fixturePath = resolve(state, `${environment}-fixture.private.json`)
const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'))
const identity = JSON.parse(readFileSync(resolve(state, 'customer-session.private.json'), 'utf8'))
assert.ok(identity.tokens?.access_token, 'Provide a fresh normal customer sign-in session; completed qualification sessions are revoked')
const command = JSON.parse(readFileSync(commandPath, 'utf8'))
const output = resolve(state, 'hosted-results')
mkdirSync(output, { recursive: true, mode: 0o700 })
const resultPath = resolve(output, `${environment}-${name}.private.json`)
assert.equal(existsSync(resultPath), false, 'A recorded case is not retried or overwritten automatically')
let token = identity.tokens.access_token
async function request(path, { method = 'GET', body, key, statuses = [200] } = {}) {
  const response = await fetch(fixture.base + path, {
    method, headers: { Authorization: `Bearer ${token}`, Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000),
  })
  const raw = await response.text()
  let value; try { value = raw ? JSON.parse(raw) : null } catch { value = { nonJsonResponse: true } }
  assert.ok(statuses.includes(response.status), `${method} ${path}: ${response.status} ${JSON.stringify(value)}`)
  return value
}
const ready = await request('/api/configuration-assistant/status')
assert.equal(ready.ready, true, 'Enable only after dedicated runtime readiness has been verified')
const resourceId = command.resourceId ?? (command.resourceKind === 'PRIVATE_DATA_PLUGIN' ? (command.pluginFixture === 'http' ? fixture.httpPlugin.id : fixture.plugin.id)
  : command.resourceKind === 'ENGAGE_APP' ? fixture.app.installationId : fixture.deployment.id)
const resourceKind = command.resourceKind ?? 'DEPLOYMENT_DRAFT'
let session = command.sessionId ? await request(`/api/configuration-assistant/sessions/${command.sessionId}`)
  : await request('/api/configuration-assistant/sessions', { method: 'POST',
    body: { resourceKind, resourceId, task: command.task } })
fixture.sessions ??= []
if (!fixture.sessions.includes(session.id)) fixture.sessions.push(session.id)
writeFileSync(fixturePath, JSON.stringify(fixture, null, 2) + '\n', { mode: 0o600 })
for (const source of command.sources ?? []) {
  const { contentFile, ...input } = source
  if (contentFile) input.content = readFileSync(resolve(dirname(commandPath), contentFile), 'utf8')
  session = await request(`/api/configuration-assistant/sessions/${session.id}/sources`, { method: 'POST', body: input })
}
const sourceIds = command.sourceIds ?? session.sources.map(source => source.id)
const operationIds = command.operationIds ?? []
const turnInput = { query: command.query, previewOnly: Boolean(command.previewOnly), sourceIds, operationIds,
  ...(command.validationFeedback ? { validationFeedback: command.validationFeedback } : {}) }
const key = randomUUID()
const startedAt = new Date().toISOString()
session = await request(`/api/configuration-assistant/sessions/${session.id}/turns`, { method: 'POST', body: turnInput, key })
const turnId = session.activeTurnId ?? session.turns.at(-1)?.id
assert.ok(turnId)
if (command.proveIdempotency) {
  const repeated = await request(`/api/configuration-assistant/sessions/${session.id}/turns`, { method: 'POST', body: turnInput, key })
  assert.equal(repeated.turns.filter(turn => turn.id === turnId).length, 1)
  assert.equal(repeated.turns.length, session.turns.length)
}
if (command.cancel) session = await request(`/api/configuration-assistant/sessions/${session.id}/cancel`, { method: 'POST', body: { turnId } })
const deadline = Date.now() + 155000
while (session.activeTurnId && Date.now() < deadline) {
  await new Promise(resolve => setTimeout(resolve, 1200))
  session = await request(`/api/configuration-assistant/sessions/${session.id}`)
}
assert.equal(session.activeTurnId, null, 'A timed-out case must be inspected, not retried')
let turn = session.turns.find(item => item.id === turnId)
assert.ok(turn)
const beforeProposal = structuredClone(turn)
if (command.proposal) {
  assert.ok(['check', 'apply', 'undo'].includes(command.proposal))
  session = await request(`/api/configuration-assistant/sessions/${session.id}/proposals/${turnId}/${command.proposal}`, { method: 'POST' })
  turn = session.turns.find(item => item.id === turnId)
}
const draftRaw = resourceKind === 'DEPLOYMENT_DRAFT'
  ? await (await fetch(fixture.base + `/api/deployments/${resourceId}/draft`, { headers: { Authorization: `Bearer ${token}` } })).text() : null
const evidence = { environment, name, startedAt, finishedAt: new Date().toISOString(),
  resourceKind, resourceId, sessionId: session.id, turnId, command,
  beforeProposal, turn, draftRaw, idempotencyKey: key, hiddenRetries: 0 }
writeFileSync(resultPath, JSON.stringify(evidence, null, 2) + '\n', { mode: 0o600 })
console.log(JSON.stringify({ environment, name, sessionId: session.id, turnId, status: turn.status,
  message: turn.message, changes: turn.changes, questions: turn.questions, findings: turn.findings,
  receipt: turn.receipt ?? turn.undoReceipt, runtime: turn.runtime, diagnostics: turn.diagnostics,
  validationFeedback: turn.validationFeedback, feedbackStopReason: turn.feedbackStopReason, attempts: turn.attempts,
  evidence: resultPath }, null, 2))
