import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const code = ts.transpileModule(await readFile(new URL('./goalPresentation.ts', import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
const exports = {}
new Function('exports', code)(exports)
const { autonomousAvailability, goalOpen, goalRunning, goalStatus, goalNextAction, goalPauseMessage, goalCoverage, savedGoalOperations, defaultExecutionMode, sessionScope, sessionRunning } = exports

test('Autonomous requires the published explicit availability flag; old servers and rollback use Quick edit', () => {
  for (const status of [undefined, {}, { autonomousEnabled: false }, { autonomousEnabled: 'true' }]) assert.equal(autonomousAvailability(status).enabled, false)
  assert.deepEqual(autonomousAvailability({ autonomousEnabled: true }), { enabled: true, reason: '' })
  assert.equal(autonomousAvailability({ autonomousEnabled: false, autonomousUnavailableReason: 'The published goal policy is disabled.' }).reason, 'The published goal policy is disabled.')
  assert.match(autonomousAvailability({}).reason, /not reported/)
})

test('yield and operational pause do not become a failed or completed goal', () => {
  for (const state of ['QUEUED', 'RUNNING', 'YIELDED']) { assert.equal(goalOpen({ state }), true); assert.equal(goalRunning({ state }), true) }
  for (const state of ['PAUSED', 'WAITING_FOR_INPUT', 'BLOCKED']) { assert.equal(goalOpen({ state }), true); assert.equal(goalRunning({ state }), false) }
  for (const state of ['COMPLETE', 'CANCELLED', 'FAILED']) { assert.equal(goalOpen({ state }), false); assert.equal(goalRunning({ state }), false) }
  assert.equal(goalStatus({ state: 'YIELDED', reasonCode: 'WORKFLOW_CORRECTION_LIMIT' }), 'Continuing preparation')
  assert.equal(goalStatus({ state: 'COMPLETE' }), 'Draft preparation complete')
  assert.equal(goalStatus({ state: 'WAITING_FOR_INPUT' }), 'Needs your input')
  assert.equal(goalStatus({ state: 'FUTURE_STATE' }), 'FUTURE_STATE')
})
test('native terminal Blocked and expired Paused work stop polling without pretending completion', () => {
  for (const state of ['BLOCKED', 'PAUSED']) {
    assert.equal(goalOpen({ state, terminal: true }), false)
    assert.equal(goalRunning({ state, terminal: true }), false)
    assert.equal(goalOpen({ state, terminal: false }), true)
    assert.notEqual(goalStatus({ state, terminal: true }), 'Draft preparation complete')
  }
  assert.equal(goalStatus({ state: 'PAUSED', terminal: true }), 'Preparation stopped')
  assert.equal(goalStatus({ state: 'BLOCKED', terminal: true }), 'Remaining work blocked')
})
test('native typed next actions have readable labels without rewriting model messages', () => {
  assert.equal(goalNextAction({ nextAction: 'RESUME_AFTER_REMEDIATION' }), 'Resolve the reported issue, then resume.')
  assert.equal(goalNextAction({ nextAction: 'ANSWER_AND_RESUME' }), 'Answer the outstanding question to continue.')
  assert.equal(goalNextAction({ nextAction: 'VIEW_RESULT' }), 'Review the recorded result and remaining work.')
  assert.equal(goalNextAction({ nextAction: 'A future exact instruction.' }), 'A future exact instruction.')
})
test('a later Quick edit keeps polling even while the earlier completed goal remains in history', () => {
  const session = { goal: { state: 'COMPLETE', turnId: 'root' }, turns: [{ id: 'root', status: 'RUNNING' }] }
  assert.equal(sessionRunning(session), false)
  assert.equal(sessionRunning({ ...session, activeTurnId: 'quick', turns: [...session.turns, { id: 'quick', status: 'RUNNING' }] }), true)
  assert.equal(sessionRunning({ ...session, goal: { state: 'PAUSED', turnId: 'root' }, activeTurnId: 'root' }), false)
  assert.equal(sessionRunning({ ...session, goal: { state: 'YIELDED', turnId: 'root' } }), true)
  assert.equal(sessionRunning({ activeTurnId: 'bounded', status: 'RUNNING', turns: [] }), true)
  assert.equal(sessionRunning(null), false)
})
test('typed budget, authentication and service pauses remain distinct from user input', () => {
  const messages = ['BUDGET_EXHAUSTED', 'AUTHORITY_EXPIRED', 'SERVICE_UNAVAILABLE'].map(reasonCode => goalPauseMessage({ state: 'PAUSED', reasonCode }))
  assert.equal(new Set(messages).size, 3)
  assert.match(messages[0], /Funding changes stay separate/)
  assert.match(messages[1], /Sign in again/)
  assert.match(goalPauseMessage({ state: 'PAUSED', reasonCode: 'AUTHORING_REAUTHENTICATION_REQUIRED' }), /Sign in again/)
  assert.match(goalPauseMessage({ state: 'PAUSED', reasonCode: 'WORKFLOW_PLANNER_UNAVAILABLE' }), /planner is unavailable/)
  assert.match(goalPauseMessage({ state: 'WAITING_FOR_INPUT' }), /outstanding question/)
  assert.equal(goalPauseMessage({ state: 'COMPLETE' }), undefined)
})
test('only server supplied valid coverage can yield a count, never model prose or write count', () => {
  assert.deepEqual(goalCoverage({ progress: { coverage: { completed: 3, total: 5 } } }), { completed: 3, total: 5 })
  for (const progress of [{ summary: 'All 5 complete' }, { workItems: [{ state: 'SAVED' }] }, { coverage: { completed: '3', total: 5 } }, { coverage: { completed: 6, total: 5 } }, { coverage: { completed: -1, total: 5 } }]) assert.equal(goalCoverage({ progress }), null)
})
test('only executed receipts in the same goal count, stable IDs deduplicate across projections', () => {
  const executed = { operationId: 'goal:step:3', status: 'EXECUTED', receipt: { published: false } }
  const session = { goal: { id: 'goal', turnId: 'root', operations: [executed, { operationId: 'unknown', status: 'DISPATCHING' }] }, turns: [
    { id: 'old', operations: [{ ...executed, operationId: 'unrelated' }] },
    { id: 'root', operations: [executed, { operationId: 'failed', status: 'FAILED' }] },
  ] }
  assert.deepEqual(savedGoalOperations(session), [executed])
  assert.deepEqual(savedGoalOperations({ turns: session.turns }), [])
})
test('available configuration assistance defaults to autonomy without task or text classification', () => {
  assert.equal(defaultExecutionMode(), 'AUTONOMOUS')
})
test('pinned editor context comes from original goal turn, not current navigation', () => {
  assert.deepEqual(sessionScope({ resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'original', task: 'ACTIONS', goal: { turnId: 'root' }, turns: [{ id: 'root', editorContext: { section: 'ACTIONS', selectedDefinitionIds: ['original-action'] } }] }), {
    resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'original', task: 'ACTIONS', editorContext: { section: 'ACTIONS', selectedDefinitionIds: ['original-action'] },
  })
})
test('the backend transaction fixture remains running without optional native progress or invented coverage', () => {
  const goal = JSON.parse('{"authorityId":"cag-fixture","goalId":"cag-fixture","sessionId":"cas-fixture","turnId":"cat-fixture","requestRevision":1,"cancellationEpoch":0,"segment":1,"id":"cag-fixture","executionMode":"AUTONOMOUS","state":"RUNNING","reasonCode":null,"workId":"exec-goal-fixture","updatedAt":"2026-11-24T20:00:00Z","originalRequest":"Set the greeting.","resourceKind":"DEPLOYMENT_DRAFT","resourceId":"dep-fixture","task":"BEHAVIOUR","profileId":"responses","releaseId":"release","bundleHash":"sha256:fixture","sourceIds":[],"operationIds":[],"progress":{"workItems":[],"remaining":[]},"canPause":true,"canResume":false,"canCancel":true,"operations":[],"operationCount":0}')
  assert.equal(goalStatus(goal), 'Preparing draft')
  assert.equal(goalCoverage(goal), null)
  assert.equal(goalPauseMessage(goal), undefined)
  assert.deepEqual(savedGoalOperations({ goal, turns: [{ id: goal.turnId, status: 'RUNNING' }] }), [])
  assert.equal(goalStatus({ ...goal, state: 'PAUSED', reasonCode: 'LOGOUT_REAUTHENTICATION_REQUIRED', cancellationEpoch: 1 }), 'Paused')
  assert.match(goalPauseMessage({ ...goal, state: 'PAUSED', reasonCode: 'LOGOUT_REAUTHENTICATION_REQUIRED' }), /Sign in again/)
})
