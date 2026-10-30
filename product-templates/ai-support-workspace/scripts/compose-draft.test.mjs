import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const directory = dirname(fileURLToPath(import.meta.url));
const compose = (draft, variant) => {
  const temporary = mkdtempSync(join(tmpdir(), 'support-draft-fixture-'));
  try {
    const input = join(temporary, 'draft.json');
    const original = JSON.stringify(draft);
    writeFileSync(input, original);
    const result = spawnSync(process.execPath, [join(directory, 'compose-draft.mjs'), input, variant], { encoding: 'utf8' });
    assert.equal(readFileSync(input, 'utf8'), original);
    return result;
  } finally {
    rmSync(temporary, { recursive: true, force: true });
  }
};

test('composes normal draft sections while preserving existing handover and provider choices', () => {
  const handover = { contractVersion: 'fixture', enabled: true, channelBindingId: 'owned-binding' };
  const result = compose({
    behaviorConfig: { type: 'CONVERSATIONAL', executionExtensions: ['CONVERSATION_HANDOVER'], conversationHandover: handover },
    providerConfig: { llmProvider: 'openai', orchestrationModel: 'owner-selected-model' },
    securityConfig: { fixtureOnlyMustNotBeExported: true },
  }, 'loomai');
  assert.equal(result.status, 0, result.stderr);
  const body = JSON.parse(result.stdout);
  assert.deepEqual(Object.keys(body).sort(), ['behaviorConfig', 'providerConfig', 'shellConfig']);
  assert.deepEqual(body.behaviorConfig.conversationHandover, handover);
  assert.deepEqual(body.behaviorConfig.executionExtensions, ['CONVERSATION_HANDOVER', 'SPECIALIST_ASSISTANCE']);
  assert.equal(body.behaviorConfig.specialistAssistance.compiled, undefined);
  assert.equal(body.providerConfig.orchestrationModel, 'owner-selected-model');
  assert.equal(body.providerConfig.orchestrationModeOverrides.executor.readActionResolution.planningMode, 'ITERATIVE');
  assert.equal(body.providerConfig.orchestrationModeOverrides.executor.readActionResolution.maxIterations, 2);
  assert.equal(body.providerConfig.orchestrationModeOverrides.executor.readActionResolution.ragCooperationMode, 'RAG_IF_ACTIONS_INSUFFICIENT');
  assert.equal(body.shellConfig.defaultConversationMode, 'executor');
  assert.equal(result.stdout.includes('fixtureOnlyMustNotBeExported'), false);
});

test('Acme overlay uses its own tools and never inherits an internal compiled catalogue', () => {
  const result = compose({ providerConfig: {}, behaviorConfig: {
    type: 'CONVERSATIONAL', executionExtensions: ['SPECIALIST_ASSISTANCE'],
    specialistAssistance: { compiled: { invocations: [{ id: 'internal-only' }] } },
  } }, 'acme-cloud');
  assert.equal(result.status, 0, result.stderr);
  const body = JSON.parse(result.stdout);
  assert.equal(body.behaviorConfig.specialistAssistance.compiled, undefined);
  assert.deepEqual(body.behaviorConfig.specialistAssistance.areas[0].readActions, ['acme_get_workspace_status']);
  assert.equal(result.stdout.includes('support_get_'), false);
  assert.equal(result.stdout.includes('internal-only'), false);
});

test('generic variant keeps read slots empty and needs no case provider', () => {
  const result = compose({ providerConfig: {}, behaviorConfig: { type: 'CONVERSATIONAL' } }, 'generic');
  assert.equal(result.status, 0, result.stderr);
  const body = JSON.parse(result.stdout);
  assert.ok(body.behaviorConfig.specialistAssistance.areas.every(area => area.readActions.length === 0));
  assert.equal(body.providerConfig.orchestrationModeOverrides.executor.readActionResolution.maxIterations, 2);
  assert.equal(result.stdout.includes('support_get_'), false);
});

test('rejects another behavior and unknown variant instead of inventing an alternate flow', () => {
  assert.notEqual(compose({ behaviorConfig: { type: 'SMART_BRAIN' } }, 'loomai').status, 0);
  assert.notEqual(compose({ behaviorConfig: { type: 'CONVERSATIONAL' } }, '../outside').status, 0);
  assert.notEqual(compose({ behaviorConfig: { type: 'CONVERSATIONAL' } }, 'generic').status, 0);
});
