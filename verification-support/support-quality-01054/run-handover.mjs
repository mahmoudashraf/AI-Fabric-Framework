import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { randomUUID } from 'node:crypto';

process.umask(0o077);
const [environment, output, adapterPath] = process.argv.slice(2);
assert.ok(['staging', 'production'].includes(environment));
assert.ok(path.isAbsolute(output ?? '') && path.isAbsolute(adapterPath ?? ''));
const { request, deskRequest, attest } = await import(pathToFileURL(adapterPath));
await fs.mkdir(output, { mode: 0o700 });
const report = { startedAt: new Date().toISOString(), environment, attestation: await attest(environment) };
const save = () => fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2), { mode: 0o600 });
const session = await deskRequest(environment, '/api/desk/v1/session');
const candidates = session.bindings.filter(b => b.roles.includes('SUPPORT'));
assert.ok(candidates.length, 'Require an existing assigned support agent before creating a handover.');
await save();
const created = await request(environment, '/api/support/cases', { method: 'POST',
  operationKey: `support-quality-handover-${randomUUID()}`, body: {
    summary: 'Support quality handover regression',
    details: 'Explain how I can use a website embed and a standalone Engage app with the same deployment. Do not transfer me yet.',
    topic: 'Engage', resources: [], diagnosticConsent: false,
  } });
report.caseId = created.id;
await save();
const root = `/api/support/cases/${created.id}`;
try {
  report.start = await request(environment, `${root}/start`, { method: 'POST' });
  await save();
  assert.equal(report.start.runtimeStatus, 200);
  const conversation = report.start.supportCase.episodes.at(-1).conversationId;
  const runtime = `${root}/runtime/api/chat/me/conversations/${conversation}`;
  report.handoverIntentAt = new Date().toISOString();
  await save();
  report.handover = await request(environment, `${runtime}/handover-requests`, { method: 'POST',
    operationKey: `support-quality-human-${randomUUID()}`,
    body: { reasonCode: 'USER_REQUESTED', continueAnonymously: true, intake: {}, consentedFields: [] },
  });
  await save();
  const handoverId = report.handover.conversation.handoverId;
  assert.ok(handoverId);
  let deskPath;
  for (let i = 0; i < 20 && !deskPath; i++) {
    for (const binding of candidates) {
      const route = `/api/desk/v1/bindings/${binding.bindingId}/conversations`;
      const conversations = await deskRequest(environment, route);
      if (conversations.some(c => c.handoverId === handoverId)) deskPath = `${route}/${handoverId}`;
    }
    if (!deskPath) await new Promise(resolve => setTimeout(resolve, 1500));
  }
  assert.ok(deskPath, 'Use an existing assigned support agent; never grant a role just for this test.');
  report.deskPath = deskPath;
  report.claim = await deskRequest(environment, `${deskPath}/claim`, { method: 'POST', body: { idempotencyKey: randomUUID() } });
  await save();
  for (let i = 0; i < 20; i++) {
    report.desk = await deskRequest(environment, deskPath);
    if (report.desk.conversation.status === 'ACTIVE') break;
    assert.ok(i < 19, 'Claim confirmation deadline.');
    await new Promise(resolve => setTimeout(resolve, 1500));
  }
  const humanText = 'Support quality qualification: a human agent is now replying. You can keep the website embed and standalone app connected to the same deployment.';
  report.reply = await deskRequest(environment, `${deskPath}/messages`, { method: 'POST',
    body: { text: humanText, idempotencyKey: randomUUID() } });
  await save();
  for (let i = 0; i < 20; i++) {
    report.afterReply = await request(environment, runtime);
    if (report.afterReply.participantMessages.some(m => m.role === 'HUMAN' && m.content === humanText)) break;
    assert.ok(i < 19, 'Asynchronous human-message delivery deadline.');
    await new Promise(resolve => setTimeout(resolve, 1500));
  }
  const before = report.afterReply.completedTurns.length;
  report.userWhileHuman = await request(environment, `${runtime}/messages`, { method: 'POST',
    operationKey: `support-quality-human-message-${randomUUID()}`,
    body: { query: 'Thank you. I will continue with the AI after this handover check.', mode: 'executor', position: 'search' } });
  report.afterHumanUser = await request(environment, runtime);
  assert.equal(report.afterHumanUser.completedTurns.length, before, 'No AI generation while a human owns the conversation.');
  await save();
  report.returnIntentAt = new Date().toISOString();
  await save();
  report.returnToAi = await request(environment, `${runtime}/ai-resume-requests`, { method: 'POST',
    operationKey: `support-quality-return-${randomUUID()}`, body: { reasonCode: 'USER_REQUESTED' } });
  for (let i = 0; i < 20; i++) {
    report.returnControl = await request(environment, `${runtime}/control`);
    if (report.returnControl.controlState === 'AI_ACTIVE') break;
    assert.ok(i < 19, 'Explicit AI-return confirmation deadline.');
    await new Promise(resolve => setTimeout(resolve, 1500));
  }
  report.followup = await request(environment, `${runtime}/messages`, { method: 'POST',
    operationKey: `support-quality-ai-followup-${randomUUID()}`,
    body: { query: 'Now that I am back with AI, remind me what I must not put in the public website installation script.', mode: 'executor', position: 'search' } });
  report.afterReturn = await request(environment, runtime);
  assert.ok(report.afterReturn.completedTurns.length > before, 'AI must resume after the explicit return.');
  report.status = 'PASSED';
  await save();
  console.log(JSON.stringify({ environment, caseId: created.id, handoverId, status: report.status,
    humanReplyVisible: true, aiPausedDuringHuman: true, explicitReturn: true,
    followup: report.followup.answer }));
} finally {
  if (report.handoverIntentAt && report.status !== 'PASSED') {
    const conversation = report.start.supportCase.episodes.at(-1).conversationId;
    const runtime = `${root}/runtime/api/chat/me/conversations/${conversation}`;
    report.cleanupControl = await request(environment, `${runtime}/control`);
    const state = report.cleanupControl.conversation?.controlState ?? report.cleanupControl.controlState;
    if (state === 'HUMAN_PENDING')
      report.cleanupCancel = await request(environment,
        `${runtime}/handover-requests/${report.cleanupControl.handoverId}/cancel`, {
          method: 'POST', operationKey: `support-quality-cleanup-${randomUUID()}`, body: {},
        });
    else if (state === 'HUMAN_ACTIVE')
      report.cleanupReturnToAi = await request(environment, `${runtime}/ai-resume-requests`, {
        method: 'POST', operationKey: `support-quality-cleanup-${randomUUID()}`, body: {},
      });
    await save();
  }
  const current = await request(environment, root);
  if (current.status === 'OPEN') report.resolved = await request(environment, `${root}/resolve`, {
    method: 'POST', body: { expectedRevision: current.revision },
  });
  report.finishedAt = new Date().toISOString();
  await save();
}
