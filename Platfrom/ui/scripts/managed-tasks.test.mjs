import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { withPluginJsonSupport } from './plugin-json-test-support.mjs'

function moduleUrl(source) {
  return `data:text/javascript;base64,${Buffer.from(ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText).toString('base64')}`
}
const actions = moduleUrl(withPluginJsonSupport(await readFile(new URL('../src/actions/actionDefinitionModel.ts', import.meta.url), 'utf8')))
const helper = await readFile(new URL('../src/brain/managedTasks.ts', import.meta.url), 'utf8')
const m = await import(moduleUrl(withPluginJsonSupport(helper).replace("'../actions/actionDefinitionModel'", JSON.stringify(actions))))
const valid = () => ({ ...m.newTask(), code: 'assess-request', name: 'Assess a request', description: 'Help route this request.', instructions: 'Use the supplied input. Explain missing information.' })

test('task defaults are explicit, blank and resource-free', () => {
  const task = m.newTask()
  assert.equal(task.code, ''); assert.equal(task.instructions, '')
  assert.equal(task.deadlineSeconds, 45); assert.equal(task.maxOutputTokens, 1200)
  assert.deepEqual(JSON.parse(task.inputSchemaText), { type: 'object', properties: {} })
  assert.deepEqual(JSON.parse(task.outputSchemaText), { type: 'object', properties: { answer: { type: 'string' } }, required: ['answer'] })
  assert.deepEqual(task.vectorSpaces, []); assert.deepEqual(task.readActions, []); assert.deepEqual(task.writeActions, [])
  assert.equal(task.confirmationMode, 'CALLER')
  assert.deepEqual(m.tasksErrors({ enabled: true, tasks: [valid()] }), [])
  assert.match(m.tasksErrors({ enabled: true, tasks: [] })[0], /at least one/)
})

test('codes are unique lower-kebab identifiers starting with a letter', () => {
  for (const code of ['a', 'assess-request', 'task2', 'a'.repeat(40)]) assert.equal(m.taskCodeValid(code), true)
  for (const code of ['', '4task', 'a--b', '-task', 'task-', 'Task', 'a_b', 'a'.repeat(41)]) assert.equal(m.taskCodeValid(code), false)
  const task = valid(), other = { ...valid(), code: task.code }
  assert.match(m.taskErrors(task, [task, other]).join(' '), /unique/)
})

test('configuration limits match the initial task contract', () => {
  for (const [field, bad] of [['name', 'a'.repeat(121)], ['description', 'a'.repeat(501)], ['instructions', 'a'.repeat(8001)],
    ['description', ''], ['instructions', ''], ['deadlineSeconds', 4], ['deadlineSeconds', 121], ['deadlineSeconds', 5.1],
    ['maxOutputTokens', 127], ['maxOutputTokens', 4097], ['maxOutputTokens', 128.5]]) {
    const task = { ...valid(), [field]: bad }
    assert.ok(m.taskErrors(task, [task]).length, `${field} ${String(bad).slice(0, 12)}`)
  }
  const tasks = Array.from({ length: 17 }, (_, i) => ({ ...valid(), code: `task-${i}` }))
  assert.match(m.tasksErrors({ enabled: false, tasks })[0], /16 tasks/)
})

test('patch owns only managedTasks and omits all client compiled metadata', () => {
  const behavior = { type: 'CONVERSATIONAL', specialistAssistance: { enabled: true, contentHash: 'preserve-other-owner' },
    conversationHandover: { enabled: true }, managedTasks: { enabled: true, tasks: [
      { ...valid(), inputSchema: { type: 'object', properties: {} }, outputSchema: { type: 'object', properties: {} }, compiled: { forbidden: true } },
    ], compiled: { forbidden: true }, contentHash: 'not-client-owned' } }
  const editor = m.readTasksEditor(behavior)
  const next = m.taskBehaviorPatch(behavior, editor)
  assert.deepEqual(next.specialistAssistance, behavior.specialistAssistance)
  assert.deepEqual(next.conversationHandover, behavior.conversationHandover)
  assert.deepEqual(Object.keys(next.managedTasks).sort(), ['enabled', 'tasks'])
  assert.equal('compiled' in next.managedTasks.tasks[0], false)
  assert.equal('key' in next.managedTasks.tasks[0], false)
  assert.equal(m.tasksIdentity(editor), m.tasksIdentity(m.readTasksEditor(next)))
})

test('TEXT ignores the editor output schema; switching does not mutate its buffer', () => {
  const task = { ...valid(), outputMode: 'TEXT', outputSchemaText: '{incomplete' }
  assert.deepEqual(m.taskErrors(task, [task]), [])
  const next = m.taskBehaviorPatch({}, { enabled: true, tasks: [task] })
  assert.equal('outputSchema' in next.managedTasks.tasks[0], false)
  assert.equal(task.outputSchemaText, '{incomplete')
})

test('write selections default empty and unknown resource references round-trip unchanged', () => {
  const task = { ...valid(), vectorSpaces: ['missing-space'], readActions: ['missing-read'], writeActions: ['missing-write'] }
  const config = m.taskBehaviorPatch({}, { enabled: true, tasks: [task] })
  const reloaded = m.readTasksEditor(config).tasks[0]
  for (const field of ['vectorSpaces', 'readActions', 'writeActions']) assert.deepEqual(reloaded[field], task[field])
  delete config.managedTasks.tasks[0].writeActions
  assert.deepEqual(m.readTasksEditor(config).tasks[0].writeActions, [])
  config.managedTasks.tasks[0].writeActions = 'bad'
  assert.throws(() => m.readTasksEditor(config), /writeActions/)
})

test('specialist reference persists only Task identity, deadline and reference, not shadow settings', () => {
  const task = { ...valid(), specialistRef: 'existing-specialist', instructions: '', inputSchemaText: '{invalid',
    outputSchemaText: '{invalid', maxOutputTokens: 0, vectorSpaces: ['shadow'], writeActions: ['shadow-write'] }
  assert.deepEqual(m.taskErrors(task, [task]), [])
  const behavior = { other: { manual: true } }
  const config = m.taskBehaviorPatch(behavior, { enabled: true, tasks: [task] })
  assert.deepEqual(config.managedTasks.tasks[0], { code: task.code, name: task.name, description: task.description,
    deadlineSeconds: 45, specialistRef: 'existing-specialist', confirmationMode: 'CALLER' })
  assert.deepEqual(config.other, behavior.other)
  const editor = m.readTasksEditor(config)
  assert.equal(editor.tasks[0].specialistRef, 'existing-specialist')
  assert.deepEqual(m.taskBehaviorPatch(config, editor), config)
  assert.ok(m.taskErrors({ ...task, deadlineSeconds: 0 }, [task]).length)
  assert.ok(m.taskErrors({ ...task, specialistRef: ' ' }, [task]).length)
  assert.throws(() => m.readTasksEditor({ managedTasks: { enabled: true, tasks: [{ ...config.managedTasks.tasks[0], specialistRef: 1 }] } }), /reference/)
})

test('confirmation handling defaults to caller and remains Task-owned with a specialist reference', () => {
  const task = { ...valid(), specialistRef: 'service-specialist', confirmationMode: 'HUMAN_REVIEW' }
  const config = m.taskBehaviorPatch({}, { enabled: true, tasks: [task] })
  assert.equal(config.managedTasks.tasks[0].confirmationMode, 'HUMAN_REVIEW')
  assert.equal(m.readTasksEditor(config).tasks[0].confirmationMode, 'HUMAN_REVIEW')
  delete config.managedTasks.tasks[0].confirmationMode
  assert.equal(m.readTasksEditor(config).tasks[0].confirmationMode, 'CALLER')
  config.managedTasks.tasks[0].confirmationMode = 'NEW_POLICY'
  assert.throws(() => m.readTasksEditor(config), /confirmation handling/)
})

test('same-revision task edits are detected exactly without rejecting unrelated behavior changes', () => {
  const original = { managedTasks: { enabled: true, tasks: [{ code: 'task', inputSchema: { type: 'object', properties: {} } }] }, other: 1 }
  assert.equal(m.managedTasksConfigIdentity(original), m.managedTasksConfigIdentity({ ...original, other: 2 }))
  assert.equal(m.managedTasksConfigIdentity(original), m.managedTasksConfigIdentity({ managedTasks: { tasks: original.managedTasks.tasks, enabled: true } }))
  assert.notEqual(m.managedTasksConfigIdentity(original), m.managedTasksConfigIdentity({ managedTasks: { enabled: true, tasks: [] } }))
  assert.notEqual(m.managedTasksConfigIdentity({ managedTasks: { value: 1 } }), m.managedTasksConfigIdentity({ managedTasks: { value: '1' } }))
})

test('schema fields retain exact advanced constraints and local pointer references', () => {
  const schema = { type: 'object', properties: { message: { type: 'string', minLength: 2, pattern: '^[A-Z]', description: 'Preserve' },
    detail: { $ref: '#/$defs/detail' }, optional: false }, $defs: { detail: { type: 'object', additionalProperties: false } },
    additionalProperties: false, allOf: [{ required: ['message'] }], required: ['detail'] }
  let result = m.editSchemaField(JSON.stringify(schema), 'message', { required: true })
  assert.deepEqual(JSON.parse(result).properties.message, schema.properties.message)
  assert.deepEqual(JSON.parse(result).$defs, schema.$defs)
  result = m.editSchemaField(result, 'items', { add: true, type: 'array' })
  assert.deepEqual(JSON.parse(result).properties.items, { type: 'array', items: { type: 'string' } })
  result = m.editSchemaField(result, 'detail', { required: false })
  assert.deepEqual(JSON.parse(result).properties.detail, { $ref: '#/$defs/detail' })
  result = m.editSchemaField(result, 'optional', { required: true })
  assert.equal(JSON.parse(result).properties.optional, false)
  result = m.editSchemaField(result, 'message', { remove: true })
  assert.equal('message' in JSON.parse(result).properties, false)
  assert.deepEqual(JSON.parse(result).allOf, schema.allOf)
  assert.throws(() => m.editSchemaField(result, 'detail', { add: true, type: 'string' }), /already exists/)
})

test('schemas preserve numeric precision, support property names matching keywords and safely create __proto__', () => {
  const schema = '{"type":"object","properties":{"$id":{"type":"string"},"value":{"type":"number","multipleOf":0.00000000000000000001}},"default":{"$id":"literal-data"}}'
  const next = m.editSchemaField(schema, '__proto__', { add: true, type: 'object' })
  assert.ok(next.includes('0.00000000000000000001'))
  assert.equal(Object.prototype.hasOwnProperty.call(JSON.parse(next).properties, '__proto__'), true)
  assert.equal({}.polluted, undefined)
})

test('schema object, reference and size limits are visible execution-contract checks', () => {
  for (const schema of [{}, [], { type: 'array' }, { type: 'object', properties: [] }, { type: 'object', required: [1] },
    { type: 'object', $id: 'local' }, { type: 'object', properties: { a: { $ref: 'https://external.test/schema' } } },
    { type: 'object', $defs: { a: { $dynamicRef: '#a' } } }]) assert.throws(() => m.parseTaskSchema(JSON.stringify(schema), 'Input schema'))
  assert.throws(() => m.parseTaskSchema(JSON.stringify({ type: 'object', description: 'x'.repeat(16384) }), 'Input schema'), /16 KiB/)
  assert.doesNotThrow(() => m.parseTaskSchema(JSON.stringify({ type: 'object', properties: { a: { $ref: '#/$defs/a' } }, $defs: { a: { type: 'string' } } }), 'Input schema'))
})

test('malformed saved configuration is not silently replaced with defaults', () => {
  assert.deepEqual(m.readTasksEditor({ other: true }), { enabled: false, tasks: [] })
  for (const config of [[], { managedTasks: true }, { managedTasks: { enabled: 'yes', tasks: [] } },
    { managedTasks: { enabled: true, tasks: [{}] } }]) assert.throws(() => m.readTasksEditor(config))
})

test('testing requires a selected applied task and no local edits, not whole-Brain publication alignment', () => {
  const current = { dirty: false, targetProfileId: 'staging', releaseVersionId: 'v1', hasAppliedTask: true }
  assert.equal(m.taskTestBlock(current), null)
  assert.equal(m.taskTestBlock({ ...current, enabled: false, draftRevision: 4, workspaceRevision: 5,
    savedDraftMatchesLatestPublished: false, latestPublishedVersionId: 'v2' }), null)
  for (const patch of [{ dirty: true }, { targetProfileId: '' }, { releaseVersionId: null }, { hasAppliedTask: false }]) {
    assert.equal(typeof m.taskTestBlock({ ...current, ...patch }), 'string')
  }
})

test('integration examples use private runtime headers, exact endpoint and escaped input', () => {
  const code = m.taskRuntimeExample('https://runtime.test/', 'assess-request', { message: "customer's request" })
  assert.ok(code.includes('/api/tasks/v1/tasks/assess-request/execute'))
  assert.ok(code.includes('X-AIFABRIC-RUNTIME-API-KEY: <private-runtime-api-key>'))
  assert.ok(code.includes('X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer <private-runtime-token>'))
  assert.equal(code.includes("--header 'Authorization:"), false)
  assert.equal(code.includes('\n+'), false)
  assert.ok(code.includes(`'"'"'`))
  assert.ok(m.taskRuntimeExample('https://user:password@runtime.test', 'task', {}).includes('<runtime-base-url>'))
})

const apiSource = await readFile(new URL('../src/api/managedTasksApi.ts', import.meta.url), 'utf8')
const apiStubs = moduleUrl(`
  export const getPlatformApiBaseUrl=()=> 'https://platform.fixture';
  export const getStoredPlatformApiKey=()=> 'fixture-key';
  export const platformCredentials=async()=> ({credentials:'omit',headers:{Authorization:'Bearer fixture-session'}});
  export const platformRequest=(...args)=>globalThis.__managedTaskMetadata(...args);
  export class PlatformApiError extends Error {constructor(status,message,code){super(message);this.status=status;this.errorCode=code}}
`)
const api = await import(moduleUrl(withPluginJsonSupport(apiSource)
  .replace("'./platformApi'", JSON.stringify(apiStubs)).replace("'../actions/actionDefinitionModel'", JSON.stringify(actions))))

test('API metadata is a scoped read and requires no inference request', async () => {
  const previous = globalThis.__managedTaskMetadata
  try {
    globalThis.__managedTaskMetadata = (...args) => args
    const [path] = api.fetchAppliedManagedTasks('brain /', 'target +')
    assert.equal(path, '/api/deployments/brain%20%2F/tasks/applied?targetProfileId=target+%2B')
    assert.equal(api.fetchManagedTaskResources('brain /')[0], '/api/deployments/brain%20%2F/tasks/resources')
  } finally { globalThis.__managedTaskMetadata = previous }
})

test('non-2xx Task receipts retain completed or unknown writes and pending confirmation without retry', async () => {
  const original = globalThis.fetch
  try {
    for (const sideEffectsStatus of ['EXECUTED', 'OUTCOME_UNKNOWN', 'NONE']) {
      let calls = 0
      const response = { runId: 'run', taskCode: 'task', configurationVersion: 'v1', taskConfigurationHash: 'hash',
        status: sideEffectsStatus === 'NONE' ? 'CONFIRMATION_REQUIRED' : 'FAILED', output: null, failure: null,
        sideEffectsExecuted: sideEffectsStatus === 'EXECUTED', sideEffectsStatus, evidence: [],
        actionExecutions: [{ executionId: 'write-1', actionName: 'update', status: sideEffectsStatus, startedAt: 'now' }],
        actionProposal: { receiptId: 'proposal', actionName: 'update', confirmationMessage: 'Update?', status: 'PROPOSED', createdAt: 'now', expiresAt: 'later' } }
      globalThis.fetch = async () => { calls++; return new Response(JSON.stringify(response), { status: 502 }) }
      assert.deepEqual(await api.executeManagedTask('brain', 'task', 'target', { input: {} }, 45), response)
      assert.equal(calls, 1)
    }
  } finally { globalThis.fetch = original }
})

test('review and input pending states retain actual safe context even on non-2xx without inventing writes', async () => {
  const original = globalThis.fetch
  try {
    for (const status of ['REVIEW_REQUIRED', 'INPUT_REQUIRED', 'SUCCEEDED']) {
      const response = { runId: 'run', taskCode: 'task', configurationVersion: 'v1', taskConfigurationHash: 'hash',
        status, output: null, failure: null, sideEffectsExecuted: false, sideEffectsStatus: 'NONE', evidence: [],
        ...(status === 'REVIEW_REQUIRED' ? { review: { taskId: 'review-1', title: 'Review actual write', status: 'PENDING' } }
          : status === 'INPUT_REQUIRED' ? { inputRequirement: { message: 'Which account should be updated?' } } : { actionExecutions: [] }) }
      globalThis.fetch = async () => new Response(JSON.stringify(response), { status: 409 })
      const value = await api.executeManagedTask('brain', 'task', 'target', { input: {} }, 45)
      assert.deepEqual(value, response); assert.equal(value.sideEffectsExecuted, false)
    }
  } finally { globalThis.fetch = original }
})

test('explicit decisions use exact target and receipt and validate response identity', async () => {
  const previous = globalThis.fetch
  const context = { configurationVersion: 'v1', taskConfigurationHash: 'hash', deadlineSeconds: 120 }
  const response = { taskCode: 'task', configurationVersion: 'v1', taskConfigurationHash: 'hash', status: 'REJECTED',
    decision: { receiptId: 'receipt /', status: 'REJECTED' }, actionExecutions: [], failure: null }
  try {
    for (const decision of ['CONFIRM', 'REJECT']) {
      let calls = 0
      globalThis.fetch = async (path, init) => {
        calls++
        assert.equal(path, 'https://platform.fixture/api/deployments/brain/tasks/task/action-proposals/receipt%20%2F/decisions?targetProfileId=target+%2B&expectedVersion=v1')
        assert.equal(init.method, 'POST'); assert.deepEqual(JSON.parse(init.body), { decision })
        assert.equal(init.credentials, 'omit'); assert.equal(init.headers.Authorization, 'Bearer fixture-session')
        assert.equal(init.redirect, 'error')
        return new Response(JSON.stringify(response))
      }
      assert.deepEqual(await api.decideManagedTaskProposal('brain', 'task', 'target +', 'receipt /', decision, context), response)
      assert.equal(calls, 1)
    }
    for (const patch of [{ taskCode: 'foreign' }, { configurationVersion: 'v2' }, { taskConfigurationHash: 'stale' },
      { decision: { receiptId: 'foreign', status: 'SUCCEEDED' } }, { decision: null }, { decision: { receiptId: 'receipt /', status: null } }]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ ...response, ...patch }), { status: 409 })
      await assert.rejects(() => api.decideManagedTaskProposal('brain', 'task', 'target', 'receipt /', 'CONFIRM', context), /did not match.*outcome is unknown/)
    }
    const denied = { ...response, decision: { receiptId: 'receipt /', status: null,
      failure: { reason: 'ACCESS_DENIED', publicMessage: 'Current policy denied this decision.', retryable: false } } }
    globalThis.fetch = async () => new Response(JSON.stringify(denied), { status: 409 })
    assert.deepEqual(await api.decideManagedTaskProposal('brain', 'task', 'target', 'receipt /', 'CONFIRM', context), denied)
    for (const status of [403, 409]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Stale or denied decision.', errorCode: 'STALE_DECISION' }), { status })
      await assert.rejects(() => api.decideManagedTaskProposal('brain', 'task', 'target', 'receipt /', 'CONFIRM', context), error =>
        error.status === status && error.errorCode === 'STALE_DECISION' && /Stale or denied/.test(error.message))
    }
  } finally { globalThis.fetch = previous }
})

test('non-2xx decision envelopes retain actual receipts and explicit null-decision uncertainty', async () => {
  const previous = globalThis.fetch
  const context = { configurationVersion: 'v1', taskConfigurationHash: 'hash', deadlineSeconds: 120 }
  const receipt = { executionId: 'write-1', actionName: 'send', status: 'SUCCEEDED', outcome: { receipt: 'actual-write' },
    startedAt: '2026-11-10T00:00:00Z', completedAt: '2026-11-10T00:00:01Z' }
  try {
    for (const [httpStatus, status, decision, failure] of [
      [409, 'FAILED', { receiptId: 'proposal', status: 'FAILED' }, { code: 'COORDINATOR_FAILED', message: 'Coordinator failed after a write.' }],
      [409, 'FAILED', { receiptId: 'proposal', status: 'SUCCEEDED' }, { code: 'COORDINATOR_FAILED', message: 'Coordinator failed after a completed decision.' }],
      [504, 'OUTCOME_UNKNOWN', null, { code: 'TASK_DEADLINE_EXCEEDED', message: 'Stopped waiting for the decision.' }],
      [504, 'OUTCOME_UNKNOWN', null, null],
      [409, 'FAILED', null, { code: 'COORDINATOR_FAILED', message: 'Decision unavailable.' }],
    ]) {
      let calls = 0
      const response = { taskCode: 'task', configurationVersion: 'v1', taskConfigurationHash: 'hash', status, decision, actionExecutions: [receipt], failure }
      globalThis.fetch = async () => { calls++; return new Response(JSON.stringify(response), { status: httpStatus }) }
      assert.deepEqual(await api.decideManagedTaskProposal('brain', 'task', 'target', 'proposal', 'CONFIRM', context), response)
      assert.equal(calls, 1)
    }
    const unknown = { taskCode: 'task', configurationVersion: 'v1', taskConfigurationHash: 'hash', status: 'OUTCOME_UNKNOWN',
      decision: null, actionExecutions: [receipt], failure: null }
    for (const patch of [{ taskCode: 'foreign' }, { configurationVersion: 'stale' }, { taskConfigurationHash: 'stale' },
      { status: 'REJECTED' }, { status: 'SUCCEEDED' }, { decision: { receiptId: 'foreign', status: 'SUCCEEDED' } }]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ ...unknown, ...patch }), { status: 504 })
      await assert.rejects(() => api.decideManagedTaskProposal('brain', 'task', 'target', 'proposal', 'CONFIRM', context), /did not match.*outcome is unknown/)
    }
  } finally { globalThis.fetch = previous }
})

test('decision wait uses the applied deadline plus overhead and times out without retry', async () => {
  const previous = globalThis.fetch, previousSetTimeout = globalThis.setTimeout, previousClearTimeout = globalThis.clearTimeout
  try {
    let timeout, delay, calls = 0, cleared = 0
    globalThis.setTimeout = (callback, milliseconds) => { timeout = callback; delay = milliseconds; return 123 }
    globalThis.clearTimeout = id => { assert.equal(id, 123); cleared++ }
    globalThis.fetch = async (_url, init) => {
      calls++
      return new Promise((_, reject) => init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true }))
    }
    const waiting = api.decideManagedTaskProposal('brain', 'task', 'target', 'proposal', 'CONFIRM', {
      configurationVersion: 'v1', taskConfigurationHash: 'hash', deadlineSeconds: 120,
    })
    await new Promise(resolve => setImmediate(resolve))
    assert.equal(delay, 135000)
    timeout()
    await assert.rejects(() => waiting, /decision request timed out.*outcome is unknown.*no automatic retry/)
    assert.equal(calls, 1); assert.equal(cleared, 1)
  } finally { globalThis.fetch = previous; globalThis.setTimeout = previousSetTimeout; globalThis.clearTimeout = previousClearTimeout }
})

test('canonical runtime failure remains a typed result while Platform errors throw', async () => {
  const original = globalThis.fetch
  const failed = { runId: 'run-1', taskCode: 'assess-request', status: 'FAILED', configurationVersion: 'v1', taskConfigurationHash: 'hash',
    output: null, failure: { code: 'TASK_OUTPUT_INVALID', message: 'Output did not match the schema.' }, evidence: [], sideEffectsExecuted: false,
    startedAt: '2026-11-10T00:00:00Z', completedAt: '2026-11-10T00:00:01Z' }
  try {
    let calls = 0
    globalThis.fetch = async (url, init) => {
      calls++
      assert.equal(url, 'https://platform.fixture/api/deployments/brain/tasks/assess-request/execute?targetProfileId=target')
      assert.equal(init.headers.Authorization, 'Bearer fixture-session')
      assert.equal(init.redirect, 'error')
      assert.deepEqual(JSON.parse(init.body), { input: { message: 'Help' } })
      return new Response(JSON.stringify(failed), { status: 502 })
    }
    const result = await api.executeManagedTask('brain', 'assess-request', 'target', { input: { message: 'Help' } }, 45)
    assert.deepEqual(result, failed); assert.equal(calls, 1)
    globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Access denied.' }), { status: 403 })
    await assert.rejects(() => api.executeManagedTask('brain', 'assess-request', 'target', { input: {} }, 45), /Access denied/)
    globalThis.fetch = async () => new Response(JSON.stringify({ ...failed, taskCode: 'foreign-task' }), { status: 502 })
    await assert.rejects(() => api.executeManagedTask('brain', 'assess-request', 'target', { input: {} }, 45), /502/)
  } finally { globalThis.fetch = original }
})

test('stopping wait aborts this request without hidden retry or cancellation claim', async () => {
  const original = globalThis.fetch
  try {
    let calls = 0
    globalThis.fetch = async (_url, init) => {
      calls++
      return new Promise((_, reject) => init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true }))
    }
    const controller = new AbortController()
    const execution = api.executeManagedTask('brain', 'assess-request', 'target', { input: {} }, 120, controller.signal)
    await new Promise(resolve => setImmediate(resolve))
    controller.abort()
    await assert.rejects(() => execution, /Stopped waiting.*may still finish.*no automatic retry/)
    assert.equal(calls, 1)
  } finally { globalThis.fetch = original }
})
