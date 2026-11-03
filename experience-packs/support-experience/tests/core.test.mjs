import assert from 'node:assert/strict'
import test from 'node:test'
import { build } from 'vite'
import { fileURLToPath } from 'node:url'
import { validateConfig, validateIntake, supportExperiencePreviewFixtures, createRequesterApi, SupportApiError } from '../dist/support-experience.esm.js'

const bundled = await build({ configFile: false, publicDir: false, logLevel: 'error', build: { write: false, minify: false,
  lib: { entry: fileURLToPath(new URL('../src/store.ts', import.meta.url)), formats: ['es'] } } })
const source = (Array.isArray(bundled) ? bundled[0] : bundled).output.find(item => item.type === 'chunk').code
const { SupportStore } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
const config = { schemaVersion: 'loomai-support-experience-config-v1', app: { assistantLabel: 'Example Support' },
  support: { spaceId: 'space-1', ticketProviderBindingId: 'binding-1', requesterOrigin: 'https://desk.example.test', topics: [{ code: 'guidance', label: 'Guidance' }] },
  capabilities: { tickets: true, currentPage: false, handover: false } }
const capabilities = ['CREATE', 'LIST', 'GET', 'RESOLVE', 'CANCEL', 'REOPEN', 'SEEN', 'LINK_CONVERSATION']
const session = { authenticated: true, requesterId: 'requester-1', space: { spaceId: 'space-1', providerBindingId: 'binding-1', humanHandoverEnabled: false, capabilities }, capabilities }
const ticket = { id: 'request-1', reference: 'EX-1', subject: 'An issue', details: 'Issue details', providerBindingId: 'binding-1', status: 'OPEN', version: 'W/"opaque:etag-1"',
  openingMessageRequired: true, conversation: { episodeId: 'episode-1', conversationId: 'conversation-1', delivery: 'PENDING' } }
const api = (overrides = {}) => ({ session: async () => session, team: async () => [], list: async () => ({ items: [ticket], nextCursor: null }),
  get: async () => ticket, seen: async () => ticket, create: async () => ticket, start: async () => ticket, transition: async () => ticket, ...overrides })

test('active validation requires execution references, not unbound draft previews', () => {
  assert.equal(validateConfig(config), config)
  assert.throws(() => validateConfig({ ...config, support: {} }), /support space/)
  assert.doesNotThrow(() => validateConfig({ ...config, support: {}, capabilities: { tickets: false } }))
  assert.equal(supportExperiencePreviewFixtures({ ...config, support: {} }).fixture, true)
  assert.throws(() => validateConfig({ ...config, support: { ...config.support, requesterOrigin: 'https://user:secret@desk.example.test/private' } }), /origin/)
  assert.throws(() => validateConfig({ ...config, schemaVersion: 'unsupported' }), /schema/)
})
test('intake validates required bounds and exact declared topic without semantic inference', () => {
  assert.doesNotThrow(() => validateIntake({ subject: 'Cancel punctuation? No semantic routing!', details: 'Details', topic: 'guidance' }, config))
  assert.throws(() => validateIntake({ subject: 'x'.repeat(241), details: 'Details' }, config), /240/)
  assert.throws(() => validateIntake({ subject: 'Issue', details: 'x'.repeat(12001) }, config), /12,000/)
  assert.throws(() => validateIntake({ subject: 'Issue', details: 'Details', topic: 'other' }, config), /configured topic/)
})
test('fixtures respect explicit disabled capabilities and empty copy', () => {
  const sample = supportExperiencePreviewFixtures({ ...config, capabilities: { tickets: false, attachments: false, handover: false }, copy: { welcomeMessage: '', starterSuggestions: [] } })
  assert.deepEqual(sample.support.requests, [])
  assert.deepEqual(sample.support.session.capabilities, [])
  assert.equal(sample.attachment, null)
  assert.equal(sample.receipt, null)
  assert.equal(sample.answer, '')
  assert.deepEqual(sample.support.presentation.starterSuggestions, [])
})
test('handover-only sessions load assigned roster without ticket capabilities or operations', async () => {
  const handoverConfig = { ...config, support: { ...config.support, teamVisible: true }, capabilities: { tickets: false, handover: true } }
  const calls = []
  const disabledTickets = { ...session, capabilities: [], space: { ...session.space, enabled: false, humanHandoverEnabled: true, capabilities: [] } }
  const member = { profileRef: 'assigned-profile', displayName: 'Assigned support person', photoAvailable: false }
  const store = new SupportStore(handoverConfig, api({ session: async () => { calls.push('session'); return disabledTickets },
    team: async () => { calls.push('team'); return [member] }, list: async () => { calls.push('list'); return { items: [] } } }))
  await store.loadSession()
  assert.deepEqual(calls, ['session', 'team'])
  assert.deepEqual(store.getState().team, [member])
  assert.deepEqual(store.getState().session.capabilities, [])
  await assert.rejects(store.list(), /not available/)
  assert.deepEqual(calls, ['session', 'team'])
  for (const [teamVisible, humanHandoverEnabled] of [[false, true], [true, false]]) {
    const hidden = new SupportStore({ ...handoverConfig, support: { ...handoverConfig.support, teamVisible } }, api({
      session: async () => ({ ...disabledTickets, space: { ...disabledTickets.space, humanHandoverEnabled } }),
      team: async () => { throw new Error('A disabled roster must not be fetched') },
    }))
    await hidden.loadSession()
    assert.deepEqual(hidden.getState().team, [])
  }
  const preview = supportExperiencePreviewFixtures(handoverConfig)
  assert.equal(preview.support.team.length, 1)
  assert.deepEqual(preview.support.requests, [])
  assert.equal(preview.receipt, null)
})
test('wire client uses the injected BFF root and opaque version unchanged', async () => {
  globalThis.document = { baseURI: 'https://console.example.test/' }
  const calls = []
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return new Response(JSON.stringify(ticket)) }
  const client = createRequesterApi({ configuration: config, credentials: { getIdentityAccessToken: () => 'console-token' } },
    { apiBaseUrl: '/api/support', runtimeCredentialMode: 'host-mediated' })
  await client.transition(ticket, 'resolve', 'resolve-key')
  assert.equal(calls[0].url, 'https://console.example.test/api/support/requests/request-1/resolve')
  assert.deepEqual(JSON.parse(calls[0].options.body), { expectedVersion: ticket.version })
  assert.equal(calls[0].options.headers['Idempotency-Key'], 'resolve-key')
  assert.equal(calls[0].options.headers.Authorization, 'Bearer console-token')
  assert.equal(calls[0].options.credentials, 'omit')
  assert.equal(calls[0].options.cache, 'no-store')
  await client.start(ticket, 'start-key')
  assert.equal(calls[1].options.headers['X-LoomAI-Runtime-Authorization'], undefined)
})
test('direct linking and start require the runtime callback but list and detail do not', async () => {
  let runtimeCalls = 0
  const calls = []
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return new Response(JSON.stringify(ticket)) }
  const client = createRequesterApi({ configuration: config, credentials: { getIdentityAccessToken: () => 'identity-token', getRuntimeAccessToken: () => { runtimeCalls++; return 'runtime-token' } } })
  await client.get(ticket.id)
  assert.equal(runtimeCalls, 0)
  await client.start(ticket, 'key')
  assert.equal(runtimeCalls, 1)
  assert.equal(calls[1].options.headers['X-LoomAI-Runtime-Authorization'], 'Bearer runtime-token')
  assert.deepEqual(JSON.parse(calls[1].options.body), { expectedVersion: ticket.version })
  assert.equal(calls[0].url, 'https://desk.example.test/api/desk/requester/v1/spaces/space-1/requests/request-1')
})
test('seen sends its retained idempotency key, bounded marker and returns the new opaque version', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return new Response(JSON.stringify({ ...ticket, version: 'seen-etag-next', unread: false })) }
  const client = createRequesterApi({ configuration: config, credentials: { getIdentityAccessToken: () => 'identity-token' } })
  const result = await client.seen({ ...ticket, updatedAt: '2026-01-01T10:00:00Z' }, 'seen-operation-key')
  assert.equal(calls[0].options.headers['Idempotency-Key'], 'seen-operation-key')
  assert.deepEqual(JSON.parse(calls[0].options.body), { expectedVersion: ticket.version, seenThrough: '2026-01-01T10:00:00Z' })
  assert.equal(result.version, 'seen-etag-next')
})
test('start has a separate 130-second deadline and ordinary reads retain 15 seconds', async () => {
  const realSetTimeout = globalThis.setTimeout, realClearTimeout = globalThis.clearTimeout
  const deadlines = []
  globalThis.setTimeout = (_callback, delay) => { deadlines.push(delay); return 1 }
  globalThis.clearTimeout = () => {}
  globalThis.fetch = async () => new Response(JSON.stringify(ticket))
  try {
    const client = createRequesterApi({ configuration: config, credentials: { getIdentityAccessToken: () => 'token', getRuntimeAccessToken: () => 'runtime' } })
    await client.get(ticket.id); await client.start(ticket, 'start-key')
    assert.deepEqual(deadlines, [15000, 130000])
  } finally { globalThis.setTimeout = realSetTimeout; globalThis.clearTimeout = realClearTimeout }
})
test('requester credential absence and in-flight account change fail before returning data', async () => {
  let identity = { status: 'authenticated', subjectKey: 'owner-a' }, calls = 0
  const context = { configuration: config, credentials: { getIdentity: () => identity, getIdentityAccessToken: () => 'token' } }
  const client = createRequesterApi(context)
  globalThis.fetch = async () => { calls++; identity = { status: 'authenticated', subjectKey: 'owner-b' }; return new Response(JSON.stringify(ticket)) }
  await assert.rejects(client.get(ticket.id), error => error instanceof SupportApiError && error.code === 'IDENTITY_CHANGED')
  await assert.rejects(client.get(ticket.id), /Sign in again/)
  assert.equal(calls, 1)
  const unauthenticated = createRequesterApi({ configuration: config, credentials: { getIdentityAccessToken: () => null } })
  await assert.rejects(unauthenticated.get(ticket.id), /Sign in/)
  assert.equal(calls, 1)
})
test('unconfirmed create retries the same key and locks changed input', async () => {
  const keys = []; let failed = true
  const store = new SupportStore(config, api({ create: async (input, key) => { keys.push(key); if (failed) { failed = false; throw new Error('Lost response') }; return ticket } }))
  const input = { subject: 'Issue', details: 'Details' }
  await assert.rejects(store.create(input), /Lost response/)
  await assert.rejects(store.create({ ...input, details: 'Changed' }), /unconfirmed/)
  await store.create(input)
  assert.equal(keys.length, 2)
  assert.equal(keys[0], keys[1])
  assert.equal(store.getState().request.reference, 'EX-1')
})
test('linked-only receipts never start another opening message', async () => {
  let starts = 0
  const store = new SupportStore(config, api({ start: async () => { starts++; return ticket } }))
  await store.start({ ...ticket, openingMessageRequired: false })
  await store.start({ ...ticket, conversation: { ...ticket.conversation, delivery: 'NOT_REQUIRED' } })
  assert.equal(starts, 0)
})
test('unsupported transitions and cross-space sessions never reach mutation transport', async () => {
  let transitions = 0
  const store = new SupportStore(config, api({ session: async () => ({ ...session, capabilities: ['CREATE', 'LIST', 'GET'] }), transition: async () => { transitions++; return ticket } }))
  await store.detail(ticket.id)
  await assert.rejects(store.transition('cancel'), /not available/)
  assert.equal(transitions, 0)
  const foreign = new SupportStore(config, api({ session: async () => ({ ...session, space: { ...session.space, spaceId: 'foreign' } }) }))
  await assert.rejects(foreign.loadSession(), /provider changed/)
})
test('delivery uncertainty keeps a saved receipt and stable command key', async () => {
  const keys = []; let failed = true
  const store = new SupportStore(config, api({ start: async (value, key) => { keys.push(key); if (failed) { failed = false; throw new Error('Timeout') }; return { ...value, conversation: { ...value.conversation, delivery: 'UNKNOWN' } } } }))
  await store.create({ subject: 'Issue', details: 'Details' })
  await assert.rejects(store.start(ticket), /Timeout/)
  assert.equal(store.getState().request.id, ticket.id)
  await store.start(ticket)
  assert.equal(keys[0], keys[1])
  assert.match(store.getState().notice, /unconfirmed/)
})
test('state snapshots are detached and destroy drops late responses', async () => {
  let complete
  const store = new SupportStore(config, api({ list: () => new Promise(resolve => { complete = resolve }) }))
  await store.loadSession()
  const list = store.list()
  store.destroy()
  complete({ items: [ticket], nextCursor: null })
  await list
  assert.deepEqual(store.getState().requests, [])
  const state = store.getState(); state.requests.push(ticket)
  assert.deepEqual(store.getState().requests, [])
})
test('seen retry retains its command key and refreshes the version before later status commands', async () => {
  let failed = true
  const keys = [], versions = []
  const unread = { ...ticket, unread: true, updatedAt: '2026-01-01T10:00:00Z' }
  const store = new SupportStore(config, api({ get: async () => unread,
    seen: async (request, key) => { keys.push(key); if (failed) { failed = false; throw new Error('Lost seen receipt') }; return { ...request, version: 'seen-next-version', unread: false } },
    transition: async request => { versions.push(request.version); return { ...request, status: 'RESOLVED' } } }))
  await store.detail(ticket.id)
  assert.equal(keys.length, 0, 'Reading request metadata is not proof its conversation is visible')
  await assert.rejects(store.markSeen(unread), /Lost seen receipt/)
  await store.markSeen(unread)
  assert.equal(keys[0], keys[1])
  await store.transition('resolve')
  assert.deepEqual(versions, ['seen-next-version'])
})
test('continuation and activity use scoped runtime credentials, opaque versions and command keys', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return new Response(JSON.stringify(ticket)) }
  const client = createRequesterApi({ configuration: config, credentials: { getIdentityAccessToken: () => 'identity', getRuntimeAccessToken: () => 'runtime' } })
  await client.newEpisode(ticket, 'episode-key')
  await client.activity(ticket, 'activity-key')
  assert.deepEqual(calls.map(call => call.url.split('/').at(-1)), ['episodes', 'activity'])
  for (const [index, call] of calls.entries()) {
    assert.equal(call.options.method, 'POST')
    assert.deepEqual(JSON.parse(call.options.body), { expectedVersion: ticket.version })
    assert.equal(call.options.headers['Idempotency-Key'], index ? 'activity-key' : 'episode-key')
    assert.equal(call.options.headers['X-LoomAI-Runtime-Authorization'], 'Bearer runtime')
  }
})
test('only the actual current terminal episode of an open request can explicitly continue without start', async () => {
  let episodes = 0, starts = 0
  const continued = { ...ticket, version: 'episode:next', openingMessageRequired: false,
    conversation: { episodeId: 'episode-2', conversationId: 'conversation-2', delivery: 'NOT_REQUIRED', openingMessageRequired: false } }
  const store = new SupportStore(config, api({ newEpisode: async (request, key) => {
    episodes++; assert.equal(request.version, ticket.version); assert.match(key, /^support-episode-/); return continued
  }, start: async () => { starts++; return continued } }))
  await store.detail(ticket.id)
  const chat = { ready: true, busy: false, conversationId: ticket.conversation.conversationId, controlState: 'CLOSED' }
  assert.equal(store.canContinue({ ...chat, controlState: 'AI_ACTIVE' }), false)
  assert.equal(store.canContinue({ ...chat, conversationId: 'older-episode' }), false)
  assert.equal(store.canContinue({ ...chat, busy: true }), false)
  store.update({ request: { ...ticket, status: 'RESOLVED' } })
  assert.equal(store.canContinue(chat), false)
  store.update({ request: ticket })
  await store.newEpisode(chat)
  assert.equal(episodes, 1)
  assert.equal(starts, 0)
  assert.equal(store.getState().request.conversation.conversationId, 'conversation-2')
  assert.equal(store.canContinue(chat), false)
  const unsupported = new SupportStore(config, api())
  await unsupported.detail(ticket.id)
  assert.equal(unsupported.canContinue(chat), false)
})
test('canonical activity coalesces updates, does not auto-seen hidden history, and stops on destroy', async () => {
  const bundle = await build({ configFile: false, publicDir: false, logLevel: 'error', build: { write: false, minify: false,
    lib: { entry: fileURLToPath(new URL('../src/activity.ts', import.meta.url)), formats: ['es'] } } })
  const code = (Array.isArray(bundle) ? bundle[0] : bundle).output.find(item => item.type === 'chunk').code
  const { ConversationActivityObserver } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
  let activities = 0, seen = 0, visible = false
  const store = new SupportStore(config, api({ session: async () => ({ ...session, capabilities: [...capabilities, 'OBSERVE_ACTIVITY'] }),
    activity: async request => { activities++; return { ...request, unread: true, updatedAt: '2026-01-01T10:01:00Z', version: `activity-${activities}` } },
    seen: async request => { seen++; return { ...request, unread: false, version: 'seen-next' } } }))
  await store.detail(ticket.id)
  let chat = { conversationId: ticket.conversation.conversationId, canonicalRevision: 1, busy: false, error: null }
  const observer = new ConversationActivityObserver(store, () => chat, () => visible, 20, 5)
  const wait = () => new Promise(resolve => setTimeout(resolve, 45))
  try {
    for (let i = 0; i < 20; i++) observer.changed({ ...chat, isOpen: !!(i % 2) })
    await wait()
    assert.equal(activities, 1)
    assert.equal(seen, 0)
    assert.equal(store.getState().request.unread, true)
    visible = true; chat = { ...chat, canonicalRevision: 2 }
    observer.changed(chat); await wait()
    assert.equal(activities, 2); assert.equal(seen, 1)
    assert.equal(store.getState().request.version, 'seen-next')
    observer.changed(chat); await wait()
    assert.equal(activities, 2)
    chat = { ...chat, canonicalRevision: 3, busy: true }; observer.changed(chat); await wait()
    assert.equal(activities, 2)
    chat.busy = false; await wait()
    assert.equal(activities, 3)
    chat = { ...chat, canonicalRevision: 4 }; observer.changed(chat); observer.destroy(); await wait()
    assert.equal(activities, 3)
  } finally { observer.destroy(); store.destroy() }
})
test('generic team photos use only scoped bearer endpoints and dispose blobs on account change or destroy', async () => {
  let owner = { status: 'authenticated', subjectKey: 'owner' }
  const calls = [], revoked = []
  const originalRevoke = URL.revokeObjectURL
  URL.revokeObjectURL = url => { revoked.push(url); originalRevoke(url) }
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    return url.endsWith('/team') ? new Response(JSON.stringify([{ profileRef: 'opaque-profile', displayName: 'Assigned person', photoAvailable: true }]))
      : new Response(new Blob(['synthetic'], { type: 'image/jpeg' }), { headers: { 'Content-Type': 'image/jpeg' } })
  }
  try {
    const client = createRequesterApi({ configuration: config, credentials: { getIdentity: () => owner, getIdentityAccessToken: () => 'identity-token' } })
    const [agent] = await client.team()
    assert.match(agent.photoUrl, /^blob:/)
    assert.equal(calls[1].url, 'https://desk.example.test/api/desk/requester/v1/spaces/space-1/team/opaque-profile/photo')
    assert.equal(calls[1].options.headers.Authorization, 'Bearer identity-token')
    assert.equal(calls[1].options.headers['X-LoomAI-Runtime-Authorization'], undefined)
    client.destroy()
    assert.deepEqual(revoked, [agent.photoUrl])
    await assert.rejects(client.team(), /Sign in again/)
    const next = createRequesterApi({ configuration: config, credentials: { getIdentity: () => owner, getIdentityAccessToken: () => 'identity-token' } })
    const [second] = await next.team()
    owner = { status: 'authenticated', subjectKey: 'other' }
    await assert.rejects(next.get(ticket.id), /Sign in again/)
    assert.ok(revoked.includes(second.photoUrl))
  } finally { URL.revokeObjectURL = originalRevoke }
})
