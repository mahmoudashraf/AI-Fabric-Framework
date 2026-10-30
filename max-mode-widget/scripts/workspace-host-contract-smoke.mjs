import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { build } from 'vite'

const result = await build({ configFile: false, logLevel: 'error', publicDir: false,
  build: { write: false, minify: false, lib: {
    entry: fileURLToPath(new URL('../src/workspaceHost.ts', import.meta.url)), formats: ['es'],
  } },
})
const code = (Array.isArray(result) ? result[0] : result).output.find(item => item.type === 'chunk').code
const { createWorkspaceHost, bindWorkspaceHost, updateWorkspaceHost, destroyWorkspaceHost } =
  await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
const api = createWorkspaceHost()
const observations = []
const unsubscribe = api.onStateChange(state => observations.push(state))
const ready = api.whenReady()
const waiting = new Map()
const unbind = bindWorkspaceHost(api, {
  openConversation: id => new Promise(resolve => waiting.set(id, resolve)),
  refreshConversation: async () => ({ conversationId: 'one' }),
})
assert.equal((await ready).ready, true)
const detached = api.getState()
detached.conversationId = 'mutated'
assert.equal(api.getState().conversationId, null)
await assert.rejects(api.openConversation(''), error => error.code === 'INVALID_CONVERSATION_ID')
const first = api.openConversation('one')
await Promise.resolve()
await assert.rejects(api.openConversation('two'), error => error.code === 'WORKSPACE_BUSY')
waiting.get('one')({ conversationId: 'one', controlState: 'AI_ACTIVE', controlVersion: 1 })
assert.equal((await first).conversationId, 'one')
updateWorkspaceHost(api, { controlState: 'HUMAN_ACTIVE', controlVersion: 3 })
updateWorkspaceHost(api, { controlState: 'AI_ACTIVE', controlVersion: 2 })
assert.equal(api.getState().controlState, 'HUMAN_ACTIVE', 'Older control reads cannot regress the observed state')
const oldCount = observations.length
unsubscribe()
updateWorkspaceHost(api, { isOpen: true })
assert.equal(observations.length, oldCount)
const retired = api.openConversation('retired')
await Promise.resolve()
unbind()
assert.equal(api.getState().status, 'destroyed')
await assert.rejects(api.whenReady(), error => error.code === 'WORKSPACE_DESTROYED')
waiting.get('retired')({ conversationId: 'retired' })
await assert.rejects(retired, error => error.code === 'WORKSPACE_DESTROYED')
updateWorkspaceHost(api, { conversationId: 'stale' })
assert.equal(api.getState().conversationId, null)
const unmounted = createWorkspaceHost()
const pendingReady = unmounted.whenReady()
destroyWorkspaceHost(unmounted)
await assert.rejects(pendingReady, error => error.code === 'WORKSPACE_DESTROYED')
console.log('Workspace host contract smoke passed: readiness, bounded command concurrency, detached snapshots, control versions, unsubscribe and unmount rejection.')
