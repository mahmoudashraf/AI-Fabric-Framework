import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import vm from 'node:vm'
import ts from 'typescript'

const deferred = () => {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}
const session = { authenticated: true, requesterId: 'owner', capabilities: [],
  space: { spaceId: 'space', providerBindingId: 'provider', humanHandoverEnabled: true, capabilities: [] } }
const bootstrap = { apiBaseUrl: 'https://console.example.test/api/support/runtime/api', principalNamespace: 'support', conversationId: 'chat' }

function fixture() {
  const events = [], errors = [], observers = [], intervals = new Set(), resize = new Set(), modules = new Map()
  const container = { id: 'support', isConnected: true, append(element) { element.parentNode = this } }
  const body = { isConnected: true, append(element) { element.parentNode = this } }
  class Shell {
    element = { parentNode: null, hidden: false, isConnected: true }
    chatSlot = { isConnected: true }
    constructor(_config, _store, callbacks) { this.callbacks = callbacks }
    setMode() { this.callbacks.layout() }
    setTheme() {}
    setExpanded() { this.callbacks.layout() }
    chatVisible() { return this.element.isConnected && !this.element.hidden }
    visible() { return this.chatVisible() }
    render() { this.callbacks.layout() }
    setChatReady() {}
    syncConversation() {}
    destroy() { events.push('shell-destroy'); this.element.isConnected = false }
  }
  const globals = {
    URL, AbortController, DOMException, crypto: globalThis.crypto, setTimeout, clearTimeout,
    document: { baseURI: 'https://console.example.test/', body, documentElement: {}, visibilityState: 'visible', getElementById: () => container },
    console: { error: (...args) => errors.push(args.join(' ')) },
    setInterval(callback) { intervals.add(callback); return callback },
    clearInterval(callback) { intervals.delete(callback) },
    addEventListener(_type, callback) { resize.add(callback) },
    removeEventListener(_type, callback) { resize.delete(callback) },
    MutationObserver: class {
      constructor(callback) { this.callback = callback; this.connected = false; observers.push(this) }
      observe() { this.connected = true }
      disconnect() { events.push('observer-disconnect'); this.connected = false }
    },
  }
  const load = name => {
    if (name === './shell') return { SupportShell: Shell }
    if (modules.has(name)) return modules.get(name)
    const relative = `experience-packs/support-experience/src/${name.replace('./', '')}.ts`
    const source = process.env.SUPPORT_LIFECYCLE_BASELINE && ['./index', './store'].includes(name)
      ? execFileSync('git', ['show', `${process.env.SUPPORT_LIFECYCLE_BASELINE}:${relative}`], { encoding: 'utf8' })
      : readFileSync(new URL(`../src/${name.replace('./', '')}.ts`, import.meta.url), 'utf8')
    const module = { exports: {} }
    modules.set(name, module.exports)
    const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
    vm.runInNewContext(code, { ...globals, require: load, module, exports: module.exports })
    return module.exports
  }
  const pack = load('./index')
  const context = (overrides = {}) => {
    const calls = { init: 0, open: 0, selected: 0, team: 0, fetch: 0, destroy: 0, frameDestroy: 0, unsubscribe: 0 }
    let frameAlive = true
    const maxMode = {
      init() { calls.init++ }, open() {}, close() {}, setTheme() {},
      getState: () => ({ ready: true, busy: false, conversationId: 'chat' }),
      onStateChange() { return () => { calls.unsubscribe++; events.push('chat-unsubscribe') } },
      async openConversation() { calls.open++ },
      destroy() { calls.destroy++ },
    }
    const workspaceFrame = {
      setTarget() { if (!frameAlive) throw new Error('This workspace frame has been destroyed.') },
      setContentClipping() {},
      destroy() { if (frameAlive) { frameAlive = false; calls.frameDestroy++; events.push('frame-destroy'); maxMode.destroy() } },
    }
    const api = { session: async () => session, team: async () => { calls.team++; return [] }, destroy() {} }
    const host = { api, apiBaseUrl: 'https://console.example.test/api/support', runtimeCredentialMode: 'host-mediated',
      fetchWorkspace: async () => { calls.fetch++; return bootstrap }, onRequestSelected() { calls.selected++ } }
    const value = {
      installationId: 'fixture', assignmentRevision: 'revision', deliveryMode: 'embedded', connectionMode: 'backend-mediated-private-runtime',
      configuration: { schemaVersion: 'loomai-support-experience-config-v1', app: { assistantLabel: 'Support' },
        support: { spaceId: 'space', ticketProviderBindingId: 'provider', teamVisible: true }, capabilities: { tickets: true, handover: true } },
      widgetConfig: {}, hostSession: { namespace: 'support', accountKey: 'owner' }, maxMode, workspaceFrame,
      presentation: { mode: 'page', container }, supportHost: host, refreshAssignment: async () => {}, ...overrides,
    }
    return { value, calls, api, host, maxMode, workspaceFrame }
  }
  return { pack, context, events, errors, observers, intervals, resize }
}

test('pre-aborted mounts do not register observers or initialize the engine', async () => {
  const f = fixture(), abort = new AbortController(), c = f.context({ signal: abort.signal })
  abort.abort()
  await assert.rejects(f.pack.mountInstallation(c.value), error => error.name === 'AbortError')
  assert.equal(f.observers.length, 0)
  assert.equal(c.calls.init, 0)
  assert.equal(f.intervals.size, 0)
})

test('abort during session loading disposes before host frame removal and immediately permits a new mount', async () => {
  const f = fixture(), pending = deferred(), abort = new AbortController(), c = f.context({ signal: abort.signal })
  c.api.session = () => pending.promise
  const mounting = f.pack.mountInstallation(c.value)
  const rejected = assert.rejects(mounting, error => error.name === 'AbortError')
  abort.abort()
  assert.equal(f.observers[0].connected, false)
  assert.equal(f.intervals.size, 0)
  assert.equal(f.resize.size, 0)
  assert.equal(c.calls.frameDestroy, 1)
  assert.ok(f.events.indexOf('observer-disconnect') < f.events.indexOf('frame-destroy'))
  c.workspaceFrame.destroy()
  assert.doesNotThrow(() => f.observers[0].callback())
  const next = f.context()
  const controller = await f.pack.mountInstallation(next.value)
  try {
    pending.resolve(session)
    await rejected
    assert.equal(c.calls.team, 0, 'a late session must not start another roster request')
    assert.equal(c.calls.fetch, 0, 'a cancelled mount must not fetch another runtime bootstrap')
    assert.equal(c.calls.init, 0)
    assert.equal(c.calls.frameDestroy, 1)
    assert.equal(next.calls.init, 1)
    assert.doesNotThrow(() => controller.getState())
    assert.deepEqual(f.errors, [])
  } finally { controller.destroy() }
})

test('abort during runtime bootstrap drops the late response without initializing chat', async () => {
  const f = fixture(), pending = deferred(), reached = deferred(), abort = new AbortController(), c = f.context({ signal: abort.signal })
  c.host.fetchWorkspace = () => { reached.resolve(); return pending.promise }
  const mounting = f.pack.mountInstallation(c.value)
  const rejected = assert.rejects(mounting, error => error.name === 'AbortError')
  await reached.promise
  abort.abort()
  pending.resolve(bootstrap)
  await rejected
  assert.equal(c.calls.init, 0)
  assert.equal(c.calls.selected, 0)
  assert.equal(c.calls.frameDestroy, 1)
  assert.equal(f.intervals.size, 0)
  assert.deepEqual(f.errors, [])
})

test('abort during conversation loading unsubscribes before engine disposal and drops host callbacks', async () => {
  const f = fixture(), pending = deferred(), reached = deferred(), abort = new AbortController(), c = f.context({ signal: abort.signal })
  c.maxMode.openConversation = () => { reached.resolve(); return pending.promise }
  const mounting = f.pack.mountInstallation(c.value)
  const rejected = assert.rejects(mounting, error => error.name === 'AbortError')
  await reached.promise
  abort.abort()
  pending.resolve({})
  await rejected
  assert.ok(f.events.indexOf('chat-unsubscribe') < f.events.indexOf('frame-destroy'))
  assert.equal(c.calls.destroy, 1)
  assert.equal(c.calls.selected, 0)
  assert.deepEqual(f.errors, [])
})

test('ready mounts dispose once on abort without silencing invalid controller use', async () => {
  const f = fixture(), abort = new AbortController(), c = f.context({ signal: abort.signal })
  const controller = await f.pack.mountInstallation(c.value)
  abort.abort()
  controller.destroy()
  assert.equal(c.calls.destroy, 1)
  assert.equal(c.calls.frameDestroy, 1)
  assert.equal(f.intervals.size, 0)
  assert.equal(f.resize.size, 0)
  assert.doesNotThrow(() => f.observers[0].callback())
  assert.throws(() => controller.setVisible(true), error => error.name === 'AbortError')
})

test('hosts without a signal retain explicit destroy and disposed-controller errors', async () => {
  const f = fixture(), c = f.context()
  const controller = await f.pack.mountInstallation(c.value)
  controller.destroy()
  controller.destroy()
  assert.equal(c.calls.destroy, 1)
  assert.throws(() => controller.setVisible(true), /destroyed/)
})
