import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { randomUUID } from 'node:crypto';

process.umask(0o077);
const [environment, output, adapterPath, sessionSelection, scenariosPath] = process.argv.slice(2);
assert.ok(['staging', 'production'].includes(environment));
assert.ok(path.isAbsolute(output ?? '') && path.isAbsolute(adapterPath ?? ''),
  'Usage: node run-quality.mjs staging|production /new/output /private/adapter.mjs');
const { request, selectedResources, attest } = await import(pathToFileURL(adapterPath));
assert.equal(typeof request, 'function');
if (scenariosPath) assert.ok(path.isAbsolute(scenariosPath), 'Additional scenario files must use an absolute path.');
const scenarios = JSON.parse(await fs.readFile(scenariosPath ?? new URL('./scenarios.json', import.meta.url)));
assert.ok(Array.isArray(scenarios.sessions) && scenarios.sessions.length > 0);
for (const s of scenarios.sessions) assert.ok(typeof s.id === 'string' && Array.isArray(s.queries)
  && s.queries.length > 0 && s.queries.every(q => typeof q === 'string' && q.trim())
  && typeof s.diagnosticConsent === 'boolean');
const selectedSessions = sessionSelection?.split(',');
if (selectedSessions) assert.ok(selectedSessions.length && selectedSessions.every(id => scenarios.sessions.some(s => s.id === id)));
await fs.mkdir(output, { mode: 0o700 });
const report = { startedAt: new Date().toISOString(), environment,
  semanticReview: 'REQUIRED_MANUAL_REVIEW', selectedSessions: selectedSessions ?? 'ALL', cases: [], turns: [] };
const save = () => fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2), { mode: 0o600 });
report.attestation = await attest(environment);
await save();

for (const scenario of scenarios.sessions.filter(s => !selectedSessions || selectedSessions.includes(s.id))) {
  const item = { id: scenario.id, key: `support-quality-${randomUUID()}`, createIntentAt: new Date().toISOString() };
  report.cases.push(item);
  await save();
  const created = await request(environment, '/api/support/cases', {
    method: 'POST', operationKey: item.key,
    body: { summary: `Support quality qualification: ${scenario.id}`,
      details: scenario.queries[0], topic: scenario.topic,
      resources: scenario.diagnosticConsent ? selectedResources(environment) : [],
      diagnosticConsent: scenario.diagnosticConsent },
  });
  item.caseId = created.id;
  await save();
  try {
    const base = `/api/support/cases/${created.id}`;
    for (const [index, query] of scenario.queries.entries()) {
      const turn = { scenario: scenario.id, index, query,
        intentAt: new Date().toISOString(), key: `support-quality-${randomUUID()}` };
      report.turns.push(turn);
      await save();
      const began = Date.now();
      let response;
      if (index === 0) {
        const started = await request(environment, `${base}/start`, { method: 'POST' });
        assert.equal(started.runtimeStatus, 200, 'Do not replay an uncertain initial message.');
        response = started.response;
        item.conversationId = started.supportCase.episodes.at(-1).conversationId;
      } else {
        response = await request(environment, `${base}/runtime/api/chat/me/conversations/${item.conversationId}/messages`, {
          method: 'POST', operationKey: turn.key,
          body: { query, mode: 'executor', position: 'search' },
        });
      }
      turn.elapsedMs = Date.now() - began;
      turn.response = response;
      turn.observedAt = new Date().toISOString();
      await save();
      console.log(JSON.stringify({ scenario: turn.scenario, index, elapsedMs: turn.elapsedMs,
        success: response.success, type: response.type, answer: response.answer,
        sources: response.sources?.length, actions: response.actions?.map(a => ({
          action: a.action ?? a.name, success: a.actionResult?.success, error: a.actionResult?.errorCode,
        })), fallbackReason: response.fallbackReason }));
      assert.ok(!String(response.fallbackReason ?? '').startsWith('PROVIDER_BUDGET'), 'Stop on provider interruption.');
      const control = await request(environment, `${base}/runtime/api/chat/me/conversations/${item.conversationId}/control`);
      turn.controlState = control.controlState ?? control.conversation?.controlState;
      await save();
      assert.ok(!['HUMAN_PENDING', 'HUMAN_ACTIVE'].includes(turn.controlState), 'Unexpected handover; inspect before proceeding.');
    }
  } finally {
    const current = await request(environment, `/api/support/cases/${created.id}`);
    if (current.status === 'OPEN') {
      item.resolved = await request(environment, `/api/support/cases/${created.id}/resolve`, {
        method: 'POST', body: { expectedRevision: current.revision },
      });
      assert.equal(item.resolved.consent.granted, false, 'Resolution must revoke diagnostic consent.');
    }
    await save();
  }
}
report.finishedAt = new Date().toISOString();
await save();
console.log(JSON.stringify({ completed: report.turns.length, output, semanticReview: report.semanticReview }));
