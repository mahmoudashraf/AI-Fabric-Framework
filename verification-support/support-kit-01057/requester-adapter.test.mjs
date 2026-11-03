import assert from 'node:assert/strict';
import test from 'node:test';
import { createRequesterAdapter, firstCompletedResponse, redact, runLifecycle, runQuality } from './requester-adapter.mjs';

const ticket = (extra = {}) => ({ id: 'req-1', reference: 'REQ-1', providerBindingId: 'binding', subject: 'Question',
  status: 'OPEN', version: 'opaque:v1/not-a-number', updatedAt: '2026-11-03T10:00:00Z', ...extra });
const history = () => ({ participantMessages: [
  { messageId: 'u1', role: 'USER', content: 'opening' },
  { messageId: 'a1', role: 'AI', content: 'Canonical first answer' },
], completedTurns: [{ userMessageId: 'u1', aiMessageId: 'a1', response: { success: true, answer: 'stale presentation', sources: [] } }] });

test('native commands preserve opaque versions, stable supplied keys and separate start deadline', async () => {
  const calls = [];
  const client = createRequesterAdapter({ environment: 'staging', request: async (env, route, options) => {
    calls.push({ env, route, ...options }); return ticket();
  } });
  await client.start(ticket(), 'retained-attempt-key');
  await client.seen(ticket(), 'seen-key');
  await client.transition(ticket(), 'reopen', 'reopen-key');
  await client.activity(ticket()); await client.newEpisode(ticket());
  assert.equal(calls[0].route, '/api/support/requests/req-1/start');
  assert.equal(calls[0].timeoutMs, 130000);
  assert.equal(calls[0].headers['Idempotency-Key'], 'retained-attempt-key');
  assert.deepEqual(calls[0].body, { expectedVersion: ticket().version });
  assert.equal(calls[1].method, 'PUT');
  assert.deepEqual(calls[1].body, { expectedVersion: ticket().version, seenThrough: ticket().updatedAt });
  assert.equal(calls[1].timeoutMs, 15000);
  assert.ok(calls.every(call => call.operationKey && call.signal instanceof AbortSignal && !call.route.includes('/cases')));
  await assert.rejects(client.start(ticket({ version: 1 })), /opaque version/);
  assert.equal(calls.length, 5);
});

test('direct requester runtime credential is callback-only and external runtimes require a separate trusted port', async () => {
  const calls = [];
  const options = { environment: 'staging', apiBase: '/api/desk/requester/v1/spaces/space-1', runtimeCredentialMode: 'direct',
    getRuntimeAccessToken: async () => 'private-runtime-token',
    request: async (_, route, options) => { calls.push({ route, ...options }); return ticket(); },
    fetchWorkspace: async () => ({ conversationId: 'c1', apiBaseUrl: 'https://runtime.example/api' }) };
  const client = createRequesterAdapter(options);
  await client.create({ subject: 'Linked', existingConversationId: 'c1' });
  assert.equal(calls[0].headers['X-LoomAI-Runtime-Authorization'], 'Bearer private-runtime-token');
  assert.equal(calls[0].route, '/api/desk/requester/v1/spaces/space-1/requests');
  await assert.rejects(client.workspace(), /explicit trusted runtimeRequest/);
  const direct = createRequesterAdapter({ ...options, runtimeRequest: async (_, route) => ({ route }) });
  const bootstrap = await direct.workspace();
  assert.equal((await direct.canonical(bootstrap, '/control')).route, 'https://runtime.example/api/chat/me/conversations/c1/control');
});

test('first non-2xx wire body is journaled before failure, never retried', async () => {
  const events = []; let attempts = 0;
  const client = createRequesterAdapter({ environment: 'staging', record: async event => events.push(event),
    request: async () => { attempts++; return new Response('{"code":"PROVIDER_TIMEOUT","detail":"first failure"}', { status: 504 }); } });
  await assert.rejects(client.start(ticket()), /HTTP 504/);
  assert.equal(attempts, 1);
  assert.deepEqual(events.map(e => e.type), ['intent', 'response', 'failure']);
  assert.equal(events[1].status, 504);
  assert.equal(events[1].rawBody, '{"code":"PROVIDER_TIMEOUT","detail":"first failure"}');
});

test('uncertain start stops quality run with saved receipt and no replay or cleanup write', async () => {
  const calls = []; let saved;
  const client = createRequesterAdapter({ environment: 'staging', request: async (_, route, options) => {
    calls.push({ route, ...options });
    if (route.endsWith('/start')) throw Object.assign(new Error('Uncertain provider timeout'), { code: 'ETIMEDOUT' });
    return ticket();
  } });
  await assert.rejects(runQuality(client, [{ id: 'one', topic: 'General', diagnosticConsent: false, queries: ['Question'] }],
    { save: async report => { saved = structuredClone(report); } }), /Uncertain/);
  assert.equal(saved.requests[0].ticket.id, 'req-1');
  assert.deepEqual(calls.map(c => c.route), ['/api/support/requests', '/api/support/requests/req-1/start']);
});

test('quality reads the first canonical answer by message IDs, sends only follow-ups, and resolves only success', async () => {
  const calls = [];
  const client = createRequesterAdapter({ environment: 'staging', request: async (_, route, options) => {
    calls.push({ route, ...options });
    if (route.endsWith('/workspace')) return { conversationId: 'c1', apiBaseUrl: '/api/support/requests/req-1/runtime/api' };
    if (route.endsWith('/control')) return { controlState: 'AI_ACTIVE' };
    if (route.endsWith('/messages')) return { success: true, answer: 'Follow-up' };
    if (route.endsWith('/conversations/c1')) return history();
    return ticket({ conversation: { conversationId: 'c1', delivery: 'DELIVERED' } });
  } });
  const report = await runQuality(client, [{ id: 'two', topic: 'General', diagnosticConsent: false, queries: ['First', 'Second'] }]);
  assert.equal(report.semanticReview, 'REQUIRED_MANUAL_REVIEW');
  assert.equal(report.turns[0].response.answer, 'Canonical first answer');
  assert.equal(calls.filter(c => c.route.endsWith('/start')).length, 1);
  assert.deepEqual(calls.filter(c => c.route.endsWith('/messages')).map(c => c.body.query), ['Second']);
  assert.equal(calls.at(-1).route, '/api/support/requests/req-1/resolve');
  assert.throws(() => firstCompletedResponse({ ...history(), completedTurns: [] }), /not confirmed/);
});

test('failed first AI response is retained and prevents follow-up or resolution', async () => {
  const writes = []; let saved;
  const failed = history(); failed.completedTurns[0].response.success = false;
  const client = createRequesterAdapter({ environment: 'staging', request: async (_, route, options) => {
    if (options.method !== 'GET') writes.push(route);
    if (route.endsWith('/workspace')) return { conversationId: 'c1', apiBaseUrl: '/api/support/requests/req-1/runtime/api' };
    if (route.endsWith('/conversations/c1')) return failed;
    return ticket({ conversation: { conversationId: 'c1', delivery: 'DELIVERED' } });
  } });
  await assert.rejects(runQuality(client, [{ id: 'failure', topic: 'General', diagnosticConsent: false, queries: ['First', 'Second'] }],
    { save: async report => { saved = structuredClone(report); } }), /First response failed/);
  assert.equal(saved.turns[0].response.success, false);
  assert.equal(saved.turns.length, 1);
  assert.deepEqual(writes, ['/api/support/requests', '/api/support/requests/req-1/start']);
});

test('lifecycle initializes empty control, links without start, and preserves existing history', async () => {
  let initialized = false, casualSent = false; const calls = []; const tickets = new Map();
  const client = createRequesterAdapter({ environment: 'staging', request: async (_, route, options) => {
    calls.push({ route, ...options });
    if (route.endsWith('/requests?limit=50')) return { items: [...tickets.values()] };
    if (route === '/api/support/requests') {
      const value = ticket({ id: tickets.size ? 'req-2' : 'req-1', openingMessageRequired: !options.body.existingConversationId,
        conversation: { conversationId: options.body.existingConversationId ?? 'c1', delivery: options.body.existingConversationId ? 'NOT_REQUIRED' : 'PENDING' } });
      tickets.set(value.id, value); return value;
    }
    if (route.endsWith('/workspace')) return { conversationId: route.includes('/req-1/') ? 'c1' : 'casual',
      apiBaseUrl: route === '/api/support/workspace' ? '/api/support/conversations/casual/runtime/api'
        : route.includes('/req-1/') ? '/api/support/requests/req-1/runtime/api' : '/api/support/requests/req-2/runtime/api' };
    if (route.endsWith('/control')) { initialized = true; return { controlState: 'AI_ACTIVE' }; }
    if (route.endsWith('/messages')) { casualSent = true; return { success: true, answer: 'Casual answer' }; }
    if (route.includes('/conversations/')) {
      if (route.endsWith('/casual')) { assert.ok(initialized, 'Unknown items return 404 before authorized control initialization.'); return casualSent ? history() : { participantMessages: [] }; }
      return history();
    }
    const [, id, operation] = route.match(/\/requests\/([^/]+)(?:\/(\w+))?$/) ?? [];
    let value = tickets.get(id); assert.ok(value);
    if (operation === 'start') value = { ...value, conversation: { ...value.conversation, delivery: 'DELIVERED' } };
    if (operation === 'resolve') value = { ...value, status: 'RESOLVED' };
    if (operation === 'reopen') value = { ...value, status: 'OPEN' };
    if (operation === 'cancel') value = { ...value, status: 'CANCELLED' };
    tickets.set(id, value); return value;
  } });
  const report = await runLifecycle(client);
  assert.equal(report.requests.at(-1).status, 'CANCELLED');
  assert.equal(calls.filter(c => c.route.endsWith('/start')).length, 1);
  assert.equal(calls.filter(c => c.route.endsWith('/messages')).length, 1);
  assert.ok(calls.findIndex(c => c.route.endsWith('/casual/control')) < calls.findIndex(c => c.route.endsWith('/conversations/casual')));
});

test('private artifacts redact credential fields; consented scenarios need host integration before writes', async () => {
  assert.deepEqual(redact({ nested: { access_token: 'secret', Authorization: 'Bearer secret', query: 'Keep this' } }),
    { nested: { access_token: '[REDACTED]', Authorization: '[REDACTED]', query: 'Keep this' } });
  let writes = 0;
  const client = createRequesterAdapter({ environment: 'staging', request: async () => { writes++; } });
  await assert.rejects(runQuality(client, [{ diagnosticConsent: true }]), /prepareContext/);
  assert.equal(writes, 0);
});
