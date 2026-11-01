import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const root = new URL('../', import.meta.url);
const read = name => JSON.parse(readFileSync(new URL(name, root), 'utf8'));
const profile = read('fixtures/balanced-support-quality.json');
const reusable = read('manifests/ai-support-workspace.1.0.2.manifest.json');
const internal = read('manifests/loomai-support-workspace.1.0.1.manifest.json');

test('both new support templates embed the same bounded quality defaults', () => {
  for (const manifest of [reusable, internal]) {
    const template = manifest.contributions.template;
    assert.deepEqual(template.providerConfig, profile.providerConfig);
    assert.deepEqual(template.promptConfig, profile.promptConfig);
    const executor = template.providerConfig.orchestrationModeOverrides.executor;
    assert.equal(executor.readActionResolution.planningMode, 'ITERATIVE');
    assert.equal(executor.readActionResolution.maxIterations, 2);
    assert.equal(executor.readActionResolution.ragCooperationMode, 'RAG_IF_ACTIONS_INSUFFICIENT');
    assert.equal(executor.readActionResolution.maxPlannerContextChars, 12000);
    assert.equal(executor.readActionResolution.maxActionEvidenceCharsPerAction, 8000);
    assert.equal(executor.readActionResolution.maxActionEvidenceItems, 10);
    assert.equal(executor.semanticRecovery.missingVectorSpace, 'BOUNDED_ALLOWED_FAN_OUT');
    assert.equal(executor.rag.maxSpaces, 1);
    assert.equal(template.promptConfig.ragMaxDocumentsUsedForContext, 8);
    assert.equal(template.promptConfig.ragMaxContextChars, 8000);
    assert.equal(template.promptConfig.ragSimilarityThreshold, undefined);
  }
});

test('reusable defaults do not add internal diagnostics, company reads or silent assistance', () => {
  const template = reusable.contributions.template;
  assert.deepEqual(template.requiredPluginRefs, ['mkp-data-ai-support-documents@1.0.0']);
  assert.equal(template.deploymentBehavior.specialistAssistance.enabled, false);
  assert.deepEqual(template.deploymentBehavior.defaultExecutionExtensions, []);
  for (const area of template.deploymentBehavior.specialistAssistance.areas)
    assert.deepEqual(area.readActions, []);
  assert.equal(template.security, undefined);
  assert.equal(template.routingConfig, undefined);
  assert.equal(template.providerConfig.weaviateHost, undefined);
  assert.equal(template.providerConfig.openaiApiKey, undefined);
});

test('internal support keeps its separately authorized integration and deferred activation', () => {
  const template = internal.contributions.template;
  assert.ok(template.requiredPluginRefs.includes('mkp-action-loomai-support-diagnostics@1.0.3'));
  assert.equal(template.deploymentBehavior.specialistAssistance.enabled, false);
  assert.deepEqual(template.deploymentBehavior.defaultExecutionExtensions, []);
  assert.deepEqual(template.deploymentBehavior.specialistAssistance.areas[0].readActions,
    ['support_get_case_summary', 'support_get_engage_readiness']);
  assert.deepEqual(template.deploymentBehavior.specialistAssistance.areas[1].readActions,
    ['support_get_deployment_status', 'support_get_connection_sync_status']);
  assert.equal(template.security, undefined);
});

test('historical published templates remain separate and unchanged in meaning', () => {
  const prior = read('manifests/ai-support-workspace.1.0.1.manifest.json');
  assert.equal(prior.version, '1.0.1');
  assert.equal(prior.contributions.template.providerConfig, undefined);
  assert.equal(prior.contributions.template.promptConfig, undefined);
  assert.equal(read('manifests/loomai-support-workspace.manifest.json').version, '1.0.0');
});
