import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

export function redact(value) {
  if (Array.isArray(value)) return value.map(redact);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) =>
    [key, /authorization|access.?token|refresh.?token|api.?key|password|private.?key|client.?secret/i.test(key) ? '[REDACTED]' : redact(item)]));
  return typeof value === 'string' ? value.replace(/Bearer\s+[^\s"']+/gi, 'Bearer [REDACTED]') : value;
}

export function validateTicket(value) {
  assert.ok(value && typeof value.id === 'string' && value.id && typeof value.version === 'string' && value.version
    && ['OPEN', 'RESOLVED', 'CANCELLED'].includes(value.status), 'Expected a provider-neutral Ticket with an opaque version.');
  return value;
}

// The trusted port owns origin selection and identity. No origin or key comes from a model/scenario.
export function createRequesterAdapter({ environment, request, runtimeRequest, fetchWorkspace,
  getRuntimeAccessToken, runtimeCredentialMode = 'host-mediated', apiBase = '/api/support', record = async () => {} }) {
  assert.equal(typeof request, 'function');
  assert.ok(/^\/api\/[A-Za-z0-9/_-]+$/.test(apiBase), 'Use an explicit requester API path, without query or origin.');
  assert.ok(['direct', 'host-mediated'].includes(runtimeCredentialMode));
  const path = id => `${apiBase}/requests/${encodeURIComponent(id)}`;
  const key = () => `support-kit-${randomUUID()}`;
  const version = ticket => ({ expectedVersion: validateTicket(ticket).version });

  async function call(route, { method = 'GET', body, operationKey, runtime = false, transport = request, timeoutMs = 15000 } = {}) {
    const attempt = { id: randomUUID(), route, method, body, operationKey, timeoutMs, at: new Date().toISOString() };
    await record({ type: 'intent', ...redact(attempt) });
    const controller = new AbortController();
    let timer;
    try {
      const headers = { Accept: 'application/json', ...(operationKey ? { 'Idempotency-Key': operationKey } : {}) };
      if (runtime && runtimeCredentialMode === 'direct') {
        const token = await getRuntimeAccessToken?.(environment);
        assert.ok(token, 'A trusted runtime credential callback is required.');
        headers['X-LoomAI-Runtime-Authorization'] = `Bearer ${token}`;
      }
      const deadline = new Promise((_, reject) => {
        timer = setTimeout(() => { controller.abort(); reject(new Error(`Unconfirmed ${method} after ${timeoutMs}ms; do not replay writes.`)); }, timeoutMs);
      });
      const result = await Promise.race([Promise.resolve().then(() => transport(environment, route,
        { method, body, operationKey, headers, timeoutMs, signal: controller.signal })), deadline]);
      if (result instanceof Response) {
        const rawBody = await result.text();
        // Keep the first wire body before status/JSON validation. Headers never enter the journal.
        await record({ type: 'response', id: attempt.id, status: result.status, rawBody: redact(rawBody), at: new Date().toISOString() });
        if (!result.ok) throw Object.assign(new Error(`HTTP ${result.status} for ${method} ${route}`), { status: result.status });
        return result.status === 204 ? undefined : JSON.parse(rawBody);
      }
      await record({ type: 'response', id: attempt.id, body: redact(result), at: new Date().toISOString() });
      return result;
    } catch (error) {
      await record({ type: 'failure', id: attempt.id, error: redact({ name: error.name, message: error.message,
        status: error.status, code: error.code, body: error.body }), at: new Date().toISOString() });
      throw error;
    } finally { clearTimeout(timer); }
  }

  async function command(ticket, operation, { runtime = false, timeoutMs = 15000, operationKey = key(), method = 'POST', extra } = {}) {
    return validateTicket(await call(`${path(ticket.id)}/${operation}`,
      { method, body: { ...version(ticket), ...extra }, operationKey, runtime, timeoutMs }));
  }
  return {
    call, key,
    session: () => call(`${apiBase}/session`),
    team: () => call(`${apiBase}/team`),
    list: async () => { const page = await call(`${apiBase}/requests?limit=50`); assert.ok(Array.isArray(page?.items)); page.items.forEach(validateTicket); return page; },
    get: async id => validateTicket(await call(path(id))),
    create: async (input, operationKey = key()) => validateTicket(await call(`${apiBase}/requests`,
      { method: 'POST', body: input, operationKey, runtime: !!input.existingConversationId })),
    start: (ticket, operationKey = key()) => command(ticket, 'start', { runtime: true, timeoutMs: 130000, operationKey }),
    transition: (ticket, operation, operationKey = key()) => {
      assert.ok(['resolve', 'cancel', 'reopen'].includes(operation));
      return command(ticket, operation, { operationKey });
    },
    seen: (ticket, operationKey = key()) => command(ticket, 'seen',
      { method: 'PUT', operationKey, extra: { seenThrough: ticket.updatedAt } }),
    activity: (ticket, operationKey = key()) => command(ticket, 'activity', { runtime: true, operationKey }),
    newEpisode: (ticket, operationKey = key()) => command(ticket, 'episodes', { runtime: true, operationKey }),
    async workspace(id) {
      const bootstrap = fetchWorkspace ? await fetchWorkspace(environment, id, { call })
        : await call(id ? `${path(id)}/workspace` : `${apiBase}/workspace`);
      assert.ok(typeof bootstrap?.conversationId === 'string' && bootstrap.conversationId
        && typeof bootstrap.apiBaseUrl === 'string', 'Expected SupportWorkspaceBootstrap.');
      // An external runtime needs its own reviewed transport; never forward Console credentials there.
      if (!runtimeRequest) assert.ok(bootstrap.apiBaseUrl.startsWith(`${apiBase}/`) && !bootstrap.apiBaseUrl.includes('..'),
        'External runtimes require an explicit trusted runtimeRequest callback.');
      assert.ok(!/[?#]/.test(bootstrap.apiBaseUrl) && !bootstrap.apiBaseUrl.endsWith('/'), 'Use the exact runtime API root.');
      return bootstrap;
    },
    canonical: (bootstrap, suffix = '', options = {}) => {
      assert.ok(['', '/control', '/messages'].includes(suffix));
      const route = `${bootstrap.apiBaseUrl}/chat/me/conversations/${encodeURIComponent(bootstrap.conversationId)}${suffix}`;
      return call(route, { ...options, transport: runtimeRequest ?? request });
    },
  };
}

export function firstCompletedResponse(conversation) {
  assert.ok(Array.isArray(conversation?.participantMessages) && Array.isArray(conversation.completedTurns),
    'Canonical participant messages and retained completed turns are required; never regenerate the opening.');
  const users = conversation.participantMessages.filter(message => message.role === 'USER');
  assert.equal(users.length, 1, 'A fresh ticket opening must have exactly one canonical user message.');
  const turns = conversation.completedTurns.filter(turn => turn.userMessageId === users[0].messageId);
  assert.equal(turns.length, 1, 'Opening response is not confirmed; stop and inspect read-only.');
  const ai = conversation.participantMessages.find(message => message.messageId === turns[0].aiMessageId && message.role === 'AI');
  assert.ok(ai && turns[0].response && typeof turns[0].response.success === 'boolean', 'Missing canonical completed opening.');
  return { ...turns[0].response, answer: ai.content };
}

export async function runQuality(client, scenarios, { prepareContext, save = async () => {} } = {}) {
  const report = { semanticReview: 'REQUIRED_MANUAL_REVIEW', requests: [], turns: [] };
  // Context selection/consent is host-specific, not part of the provider-neutral intake.
  assert.ok(prepareContext || scenarios.every(s => !s.diagnosticConsent), 'Consented scenarios require a trusted prepareContext callback.');
  for (const scenario of scenarios) {
    const item = { scenario: scenario.id };
    report.requests.push(item); await save(report);
    item.ticket = await client.create({ subject: `Support quality qualification: ${scenario.id}`,
      details: scenario.queries[0], topic: scenario.topic });
    await save(report);
    if (prepareContext) await prepareContext(item.ticket, scenario, client);
    // Start returns a Ticket, not an answer/StartReceipt. It is invoked exactly once.
    item.ticket = await client.start(item.ticket); await save(report);
    assert.equal(item.ticket.conversation?.delivery, 'DELIVERED', 'Opening is unconfirmed; do not replay start.');
    const workspace = await client.workspace(item.ticket.id);
    assert.equal(workspace.conversationId, item.ticket.conversation.conversationId);
    for (const [index, query] of scenario.queries.entries()) {
      const turn = { scenario: scenario.id, index, query, intentAt: new Date().toISOString() };
      report.turns.push(turn); await save(report);
      const began = Date.now();
      turn.response = index === 0 ? firstCompletedResponse(await client.canonical(workspace))
        : await client.canonical(workspace, '/messages', { method: 'POST', operationKey: client.key(),
          timeoutMs: 130000, body: { query, mode: 'executor', position: 'search' } });
      turn.elapsedMs = Date.now() - began; await save(report);
      assert.equal(turn.response?.success, true, 'First response failed; preserve it and stop without replay.');
      const control = await client.canonical(workspace, '/control');
      turn.controlState = control.controlState ?? control.conversation?.controlState; await save(report);
      assert.equal(turn.controlState, 'AI_ACTIVE', 'Stop on terminal state or unexpected handover.');
    }
    // Only successful runs resolve their own ticket. Failures leave the receipt intact for readback.
    item.ticket = await client.transition(await client.get(item.ticket.id), 'resolve'); await save(report);
  }
  return report;
}

export async function runLifecycle(client, { save = async () => {} } = {}) {
  const report = { requests: [] };
  const keep = async ticket => { report.requests.push(ticket); await save(report); return ticket; };
  let ticket = await keep(await client.create({ subject: 'Support kit lifecycle qualification',
    details: 'Explain how to use embedded and standalone delivery without exposing credentials.', topic: 'General' }));
  ticket = await keep(await client.start(ticket));
  assert.equal(ticket.conversation?.delivery, 'DELIVERED', 'Unconfirmed opening; do not replay.');
  const workspace = await client.workspace(ticket.id);
  report.firstResponse = firstCompletedResponse(await client.canonical(workspace)); await save(report);
  assert.equal(report.firstResponse.success, true, 'Failed first response; stop without replay.');
  ticket = await keep(await client.activity(await client.get(ticket.id)));
  ticket = await keep(await client.seen(ticket));
  for (const operation of ['resolve', 'reopen', 'cancel']) ticket = await keep(await client.transition(ticket, operation));
  assert.equal(ticket.status, 'CANCELLED');
  const listed = await client.list();
  assert.ok(listed.items.some(item => item.id === ticket.id), 'New request must be visible in its requester list.');

  // Workspace allocation itself creates no ticket and no synthetic opening message.
  const before = await client.list();
  const casual = await client.workspace();
  await client.canonical(casual, '/control');
  const empty = await client.canonical(casual);
  assert.ok(!(empty.participantMessages?.length) && !(empty.turns?.length), 'Expected an empty casual scope.');
  const after = await client.list();
  assert.deepEqual(after.items.map(t => t.id), before.items.map(t => t.id), 'Conversation-first bootstrap must not create a ticket.');
  report.casualResponse = await client.canonical(casual, '/messages', { method: 'POST', operationKey: client.key(),
    timeoutMs: 130000, body: { query: 'Explain embedded and standalone delivery. Do not request human handover.', mode: 'executor', position: 'search' } });
  await save(report);
  assert.equal(report.casualResponse?.success, true, 'Failed casual response; stop without replay.');
  const history = await client.canonical(casual);
  let linked = await keep(await client.create({ subject: 'Support kit linked conversation qualification', details: 'Track this existing conversation.',
    topic: 'General', existingConversationId: casual.conversationId }));
  assert.equal(linked.conversation?.conversationId, casual.conversationId);
  assert.equal(linked.conversation?.delivery, 'NOT_REQUIRED');
  assert.equal(linked.openingMessageRequired, false);
  const linkedWorkspace = await client.workspace(linked.id);
  const linkedHistory = await client.canonical(linkedWorkspace);
  assert.deepEqual(linkedHistory.participantMessages, history.participantMessages, 'Linking must not copy/replay the transcript.');
  linked = await keep(await client.transition(await client.get(linked.id), 'cancel'));
  assert.equal(linked.status, 'CANCELLED');
  return report;
}
