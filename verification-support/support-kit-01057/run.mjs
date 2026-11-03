import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { createRequesterAdapter, redact, runLifecycle, runQuality } from './requester-adapter.mjs';

process.umask(0o077);
const [mode, environment, output, adapterPath, sessionSelection, scenariosPath] = process.argv.slice(2);
assert.ok(['preflight', 'lifecycle', 'quality'].includes(mode));
assert.ok(['staging', 'production'].includes(environment));
assert.ok(path.isAbsolute(output ?? '') && path.isAbsolute(adapterPath ?? ''),
  'Usage: node run.mjs preflight|lifecycle|quality staging|production /new/private/output /private/adapter.mjs [sessionIDs] [scenarios.json]');
if (mode !== 'preflight' && environment === 'production') assert.equal(process.env.SUPPORT_KIT_ALLOW_PRODUCTION_WRITES, 'true',
  'Explicit SUPPORT_KIT_ALLOW_PRODUCTION_WRITES=true is required for production writes.');
if (scenariosPath) assert.ok(path.isAbsolute(scenariosPath));
await fs.mkdir(output, { mode: 0o700 }); // EEXIST prevents overwriting previous evidence.
const report = { environment, mode, startedAt: new Date().toISOString(), semanticReview: 'REQUIRED_MANUAL_REVIEW' };
const save = async value => {
  if (value) report.result = value;
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(redact(report), null, 2), { mode: 0o600 });
};
await save();
try {
  const port = await import(pathToFileURL(adapterPath));
  if (mode !== 'preflight') assert.equal(typeof port.attest, 'function', 'Writes require trusted release/account attestation.');
  report.attestation = await port.attest?.(environment); await save();
  const client = createRequesterAdapter({ ...port, environment,
    record: event => fs.appendFile(path.join(output, 'attempts.jsonl'), `${JSON.stringify(event)}\n`, { mode: 0o600 }) });
  report.session = await client.session(); await save();
  assert.equal(report.session.authenticated, true);
  if (report.session.space?.humanHandoverEnabled) { report.team = await client.team(); await save(); }
  report.list = await client.list(); await save();
  if (mode === 'lifecycle') await runLifecycle(client, { save });
  if (mode === 'quality') {
    const scenarios = JSON.parse(await fs.readFile(scenariosPath ?? new URL('../support-quality-01054/scenarios.json', import.meta.url)));
    assert.equal(scenarios.contractVersion, 'loomai-support-quality-v1');
    assert.ok(Array.isArray(scenarios.sessions) && scenarios.sessions.length);
    for (const session of scenarios.sessions) assert.ok(typeof session.id === 'string' && typeof session.topic === 'string'
      && typeof session.diagnosticConsent === 'boolean' && Array.isArray(session.queries) && session.queries.length
      && session.queries.every(query => typeof query === 'string' && query.trim()));
    const selected = sessionSelection?.split(',');
    assert.ok(!selected || selected.every(id => scenarios.sessions.some(s => s.id === id)), 'Unknown scenario ID.');
    await runQuality(client, scenarios.sessions.filter(s => !selected || selected.includes(s.id)), {
      save, prepareContext: port.prepareContext ? (ticket, scenario, native) => port.prepareContext(environment, ticket, scenario, native)
        : port.selectedResources && clientContext,
    });
    async function clientContext(ticket, scenario, native) {
      assert.equal(port.apiBase ?? '/api/support', '/api/support', 'Non-Console diagnostics need a host prepareContext callback.');
      const base = `/api/support/requests/${encodeURIComponent(ticket.id)}/context`;
      let context = await native.call(base);
      context = await native.call(base, { method: 'PUT', body: { resources: scenario.diagnosticConsent ? await port.selectedResources(environment) : [],
        expectedRevision: context.revision } });
      await native.call(`${base}/consent`, { method: 'PUT', body: { granted: scenario.diagnosticConsent, expectedVersion: context.consent.version } });
    }
  }
  report.finishedAt = new Date().toISOString(); await save();
  console.log(JSON.stringify({ mode, environment, output, completed: true, semanticReview: report.semanticReview }));
} catch (error) {
  report.failure = { name: error.name, message: error.message, status: error.status, code: error.code };
  await save();
  console.error(JSON.stringify({ mode, environment, output, completed: false, failure: redact(report.failure), replayed: false }));
  process.exitCode = 1;
}
