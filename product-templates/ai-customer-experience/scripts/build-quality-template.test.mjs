import assert from 'node:assert/strict'
import { test } from 'node:test'
import { spawnSync } from 'node:child_process'
import { readFileSync, mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildCustomerExperienceQualityTemplate as build } from './build-quality-template.mjs'

const catalogue = JSON.parse(readFileSync(new URL('../../dealership-experience/fixtures/behaviour-catalogue.test.json', import.meta.url)))
const defaults = JSON.parse(readFileSync(new URL('../fixtures/quality-defaults.json', import.meta.url)))
const bootstrap = new URL('../../../Platfrom/backend/src/main/resources/bootstrap/', import.meta.url)
const policy = JSON.parse(readFileSync(new URL('brain-quality-defaults.json', bootstrap)))
const prompts = JSON.parse(readFileSync(new URL('prompts/default-prompt-config.json', bootstrap)))
const intentPrompt = `${prompts.intentExtractionPrompt}\n\n${policy.intentGuidance}`
const profile = (key, id) => ({ presetId: id, revision: 1, instructions: catalogue.groups.find(g => g.key === key).options.find(o => o.id === id).instructions })
const expectedProfiles = () => ({ schemaVersion: catalogue.schemaVersion, revision: catalogue.revision,
  ...Object.fromEntries(Object.entries(policy.behaviourSelections).map(([key, id]) => [key, profile(key, id)])) })
const manifest = () => ({ schemaVersion: 1, pluginType: 'TEMPLATE', pluginId: 'mkp-template-document-knowledge-assistant', version: '1.4.0',
  displayName: 'Synthetic generic workspace', permissions: { contributesTemplate: true }, compatibility: { supportedDeploymentTargets: ['original-target'] },
  contributions: { template: { templateId: 'original-template', deploymentBehavior: { type: 'CONVERSATIONAL', contractVersion: 1, defaultExecutionExtensions: [] },
    requiredPluginRefs: ['generic-source@1.0.0'], requiredPluginInstallDefaults: { 'generic-source@1.0.0': { config: { publicContentApprovalConfirmed: false }, secretRefs: { key: 'EXISTING_REF' } } },
    security: { authzMode: 'ALLOW_VERIFIED', originalAudience: 'original-audience' }, shell: { greeting: { title: 'Original workspace' } },
    knowledgeSources: [{ sourceId: 'original-source', vectorSpace: 'original-space', approvalRequired: true }],
    storageConfig: { vectorStrategy: 'EXTERNAL_EXISTING', runtimeState: 'BYO', bucket: 'original-bucket' },
    routingConfig: { allowedSourceIds: ['original-source'], allowedActions: ['original-write'] },
    actionDefinitions: [{ actionId: 'original-write', accessMode: 'WRITE', requiresConfirmation: true }],
    providerConfig: { openaiModel: 'original-model', openaiMaxTokens: 2048, vectorStrategy: 'original-vector', originalProvider: { retained: true } },
  } } })

test('fixture changes only the reviewed generic template family', () => {
  assert.equal(defaults.revision, 3)
  assert.equal(defaults.status, 'OWNER_APPROVED_SELECTIVE_DEFAULTS_20261114')
  assert.deepEqual(defaults.template, { pluginId: 'mkp-template-document-knowledge-assistant', currentVersion: '1.4.0', candidateVersion: '1.5.0' })
  assert.deepEqual(defaults.aiBehaviour, { schemaVersion: 'loomai-ai-behaviour-v1', revision: 1 })
  assert.deepEqual(Object.keys(defaults).sort(), ['schemaVersion', 'revision', 'status', 'template', 'aiBehaviour'].sort())
})

test('quality defaults and version change; source, security and explicit model are preserved', () => {
  const source = manifest(), before = structuredClone(source)
  const output = build(source, catalogue)
  const expected = structuredClone(before)
  expected.version = '1.5.0'
  expected.contributions.template.promptConfig = {
    intentExtractionPrompt: intentPrompt, ...policy.promptBudgets, aiBehaviour: expectedProfiles(),
  }
  expected.contributions.template.providerConfig.orchestrationModeOverrides = { executor: {
    knowledgeBaseOverviewEnabled: policy.knowledgeBaseOverviewEnabled, readActionResolution: policy.readActionResolution,
  } }
  assert.deepEqual(output, expected)
  assert.deepEqual(source, before)
  assert.equal(Object.hasOwn(output.contributions.template.promptConfig, 'ragSimilarityThreshold'), false)
  assert.equal(Object.hasOwn(output.contributions.template.promptConfig, 'answerGenerationPrompt'), false)
  assert.equal(Object.hasOwn(output.contributions.template.providerConfig.orchestrationModeOverrides.executor, 'semanticRecovery'), false)
})

test('existing breadth, threshold, prompt, model, other modes and explicit source policy are unchanged', () => {
  const source = manifest(), t = source.contributions.template
  t.promptConfig = { ragSimilarityThreshold: 0.61, intentExtractionPrompt: 'Original custom intent instructions.', answerGenerationPrompt: 'Original custom answer instructions.',
    systemPrompt: 'Original system instructions.', actionSelectionPrompt: 'Original action instructions.',
    clarificationPrompt: 'Original clarification instructions.', retrievalPrompt: 'Original retrieval instructions.', assistantUiPrompt: 'Original UI instructions.',
    customPrompt: { instructions: 'Original approved prompt' }, aiBehaviour: expectedProfiles() }
  t.promptConfig.aiBehaviour.answerBreadth = { presetId: 'custom', revision: 1, instructions: 'Original custom breadth.' }
  t.providerConfig.orchestrationModeOverrides = { navigator: { marker: true }, executor: { knowledgeBaseOverviewEnabled: false, rag: { fanoutEnabled: false, maxSpaces: 1 }, semanticRecovery: { missingVectorSpace: 'DENY' }, readActionResolution: { enabled: true, unrelated: { preserved: true } } } }
  const output = build(source, catalogue).contributions.template
  assert.deepEqual(output.promptConfig.aiBehaviour.answerBreadth, t.promptConfig.aiBehaviour.answerBreadth)
  assert.equal(output.promptConfig.ragSimilarityThreshold, 0.61)
  assert.equal(output.promptConfig.intentExtractionPrompt, 'Original custom intent instructions.')
  assert.equal(output.promptConfig.answerGenerationPrompt, 'Original custom answer instructions.')
  for (const key of ['systemPrompt', 'actionSelectionPrompt', 'clarificationPrompt', 'retrievalPrompt', 'assistantUiPrompt', 'customPrompt']) {
    assert.deepEqual(output.promptConfig[key], t.promptConfig[key], key)
  }
  assert.equal(output.providerConfig.openaiModel, 'original-model')
  assert.equal(output.providerConfig.orchestrationModeOverrides.executor.knowledgeBaseOverviewEnabled, false)
  assert.deepEqual(output.providerConfig.orchestrationModeOverrides.navigator, { marker: true })
  assert.deepEqual(output.providerConfig.orchestrationModeOverrides.executor.rag, t.providerConfig.orchestrationModeOverrides.executor.rag)
  assert.deepEqual(output.providerConfig.orchestrationModeOverrides.executor.semanticRecovery, { missingVectorSpace: 'DENY' })
  output.promptConfig.customPrompt.instructions = 'Changed in output only'
  output.providerConfig.orchestrationModeOverrides.executor.readActionResolution.unrelated.preserved = false
  assert.equal(t.promptConfig.customPrompt.instructions, 'Original approved prompt')
  assert.equal(t.providerConfig.orchestrationModeOverrides.executor.readActionResolution.unrelated.preserved, true)
})

test('explicit breadth and custom profile instructions are preserved exactly', () => {
  for (const breadth of [profile('answerBreadth', 'advisory'), { presetId: 'custom', revision: 1, instructions: 'Custom breadth.' }]) {
    const source = manifest(), t = source.contributions.template
    t.promptConfig = { aiBehaviour: expectedProfiles() }
    t.promptConfig.aiBehaviour.answerBreadth = breadth
    for (const key of ['tone', 'answerLength', 'nextSteps']) {
      t.promptConfig.aiBehaviour[key] = { presetId: 'custom', revision: 1, instructions: `Custom ${key} instructions.` }
    }
    const before = structuredClone(source)
    const output = build(source, catalogue).contributions.template.promptConfig.aiBehaviour
    assert.deepEqual(output, t.promptConfig.aiBehaviour)
    output.tone.instructions = 'OUTPUT ONLY'
    assert.deepEqual(source, before)
  }
})

test('explicit provider choices and output-token limits are not replaced or clamped', () => {
  for (const limit of [256, 2048, 4096]) {
    const source = manifest(), t = source.contributions.template
    Object.assign(t.providerConfig, {
      llmProvider: 'anthropic', openaiMaxTokens: limit, anthropicModel: 'original-anthropic-model',
      embeddingProvider: 'original-embedding-provider', embeddingModel: 'original-embedding-model',
      llmApiKeyRef: 'ORIGINAL_LLM_KEY_REF', llmPurposeOverrides: { intentExtraction: { model: 'original-intent-model' } },
    })
    const output = build(source, catalogue).contributions.template.providerConfig
    const { orchestrationModeOverrides, ...unchanged } = output
    assert.deepEqual(unchanged, t.providerConfig)
    assert.equal(orchestrationModeOverrides.executor.readActionResolution.maxIterations, 2)
  }
})

test('missing provider and recall settings receive domain-neutral defaults without new permissions or fanout', () => {
  const source = manifest()
  delete source.contributions.template.providerConfig.openaiModel
  delete source.contributions.template.providerConfig.openaiMaxTokens
  const output = build(source, catalogue).contributions.template
  assert.equal(output.providerConfig.openaiModel, policy.openaiModel)
  assert.equal(output.providerConfig.openaiMaxTokens, policy.openaiMaxTokens)
  assert.equal(Object.hasOwn(output.promptConfig, 'ragSimilarityThreshold'), false)
  assert.equal(Object.hasOwn(output.promptConfig, 'answerGenerationPrompt'), false)
  assert.equal(output.providerConfig.orchestrationModeOverrides.executor.knowledgeBaseOverviewEnabled, policy.knowledgeBaseOverviewEnabled)
  assert.equal(Object.hasOwn(output.providerConfig.orchestrationModeOverrides.executor, 'rag'), false)
  assert.equal(Object.hasOwn(output.providerConfig.orchestrationModeOverrides.executor, 'semanticRecovery'), false)
  assert.equal(output.promptConfig.intentExtractionPrompt, intentPrompt)
  assert.doesNotMatch(output.promptConfig.intentExtractionPrompt, /Harbor|dealer|furniture|stockId/)
  assert.deepEqual(output.security, source.contributions.template.security)
  assert.deepEqual(output.actionDefinitions, source.contributions.template.actionDefinitions)
  assert.deepEqual(output.knowledgeSources, source.contributions.template.knowledgeSources)
  assert.deepEqual(output.routingConfig, source.contributions.template.routingConfig)
  assert.deepEqual(output.storageConfig, source.contributions.template.storageConfig)
})

test('absent configuration blocks gain only the approved default fields, without new sources or providers', () => {
  const source = manifest(), t = source.contributions.template
  delete t.providerConfig
  delete t.knowledgeSources
  const output = build(source, catalogue).contributions.template
  assert.deepEqual(output.providerConfig, {
    openaiModel: policy.openaiModel, openaiMaxTokens: policy.openaiMaxTokens,
    orchestrationModeOverrides: { executor: {
      knowledgeBaseOverviewEnabled: policy.knowledgeBaseOverviewEnabled, readActionResolution: policy.readActionResolution,
    } },
  })
  assert.deepEqual(Object.keys(output.promptConfig).sort(), ['intentExtractionPrompt', ...Object.keys(policy.promptBudgets), 'aiBehaviour'].sort())
  assert.equal(Object.hasOwn(output, 'knowledgeSources'), false)
  assert.deepEqual(output.requiredPluginRefs, t.requiredPluginRefs)
  assert.deepEqual(output.requiredPluginInstallDefaults, t.requiredPluginInstallDefaults)
  assert.deepEqual(output.actionDefinitions, t.actionDefinitions)
  assert.deepEqual(build(source, catalogue).permissions, source.permissions)
})

test('read changes are limited to minimum evidence budgets and bounded iterative cooperation', () => {
  const source = manifest(), t = source.contributions.template
  t.promptConfig = { ragMaxDocumentsUsedForContext: 2, ragMaxContextChars: 2000 }
  const previous = {
    enabled: false, planningMode: 'ONE_SHOT', maxIterations: 7, ragCooperationMode: 'original-mode',
    maxPlannerContextChars: 3000, maxActionEvidenceCharsPerAction: 1000, maxActionEvidenceItems: 2,
    maxTotalActions: 1, allowedActions: ['original-read'], allowedSourceIds: ['original-source'],
    timeoutMs: 1234, requiresConfirmation: true,
  }
  t.providerConfig.orchestrationModeOverrides = { executor: { readActionResolution: previous } }
  const output = build(source, catalogue).contributions.template
  assert.deepEqual(output.providerConfig.orchestrationModeOverrides.executor.readActionResolution, { ...previous, ...policy.readActionResolution })
  for (const [key, value] of Object.entries(policy.promptBudgets)) assert.equal(output.promptConfig[key], value)
  assert.deepEqual(t.providerConfig.orchestrationModeOverrides.executor.readActionResolution, previous)
})

test('already larger evidence limits are not reduced', () => {
  const source = manifest(), t = source.contributions.template
  t.promptConfig = { ragMaxDocumentsUsedForContext: 12, ragMaxContextChars: 16000 }
  t.providerConfig.orchestrationModeOverrides = { executor: { readActionResolution: { maxPlannerContextChars: 24000, maxActionEvidenceCharsPerAction: 12000, maxActionEvidenceItems: 15 } } }
  const output = build(source, catalogue).contributions.template
  assert.equal(output.promptConfig.ragMaxDocumentsUsedForContext, 12)
  assert.equal(output.promptConfig.ragMaxContextChars, 16000)
  assert.equal(output.providerConfig.orchestrationModeOverrides.executor.readActionResolution.maxPlannerContextChars, 24000)
  assert.equal(output.providerConfig.orchestrationModeOverrides.executor.readActionResolution.maxActionEvidenceCharsPerAction, 12000)
  assert.equal(output.providerConfig.orchestrationModeOverrides.executor.readActionResolution.maxActionEvidenceItems, 15)
})

test('canonical selections use actual catalogue instructions, not dealership or catalogue default selections', () => {
  const selected = structuredClone(catalogue)
  selected.defaults.answerBreadth = 'advisory'
  selected.groups.find(g => g.key === 'tone').options.find(o => o.id === 'consultative').instructions = 'SYNTHETIC CHANGED CATALOGUE INSTRUCTION'
  const output = build(manifest(), selected).contributions.template.promptConfig.aiBehaviour
  assert.equal(output.answerBreadth.presetId, policy.behaviourSelections.answerBreadth)
  assert.equal(output.answerBreadth.instructions, profile('answerBreadth', 'grounded').instructions)
  assert.equal(output.tone.instructions, 'SYNTHETIC CHANGED CATALOGUE INSTRUCTION')
})

test('ambiguous, missing or unsupported catalogue identities are rejected without inference', () => {
  for (const edit of [c => { c.revision = 2 }, c => { c.groups.push(structuredClone(c.groups[0])) },
    c => { c.groups[0].options.push(structuredClone(c.groups[0].options[0])) }, c => { c.groups.splice(1, 1) },
    c => { c.groups[0].options[0].instructions = '' }, c => { c.groups[0].options[0].revision = 2 }]) {
    const selected = structuredClone(catalogue); edit(selected)
    assert.throws(() => build(manifest(), selected))
  }
})

test('wrong template, baseline, type and execution contract are not silently transformed', () => {
  for (const edit of [m => { m.pluginId = 'another-template' }, m => { m.version = '1.2.0' }, m => { m.pluginType = 'DATA' },
    m => { m.schemaVersion = 2 }, m => { m.contributions.template.deploymentBehavior.type = 'AGENTIC' }]) {
    const source = manifest(); edit(source); assert.throws(() => build(source, catalogue))
  }
})

test('malformed existing blocks and budgets are surfaced, not discarded', () => {
  for (const edit of [t => { t.promptConfig = [] }, t => { t.providerConfig = null },
    t => { t.promptConfig = { ragMaxContextChars: '8000' } }, t => { t.promptConfig = { ragMaxDocumentsUsedForContext: 0 } },
    t => { t.promptConfig = { aiBehaviour: { ...expectedProfiles(), revision: 2 } } },
    ...[0, -1, 1.5, '12000', null].map(value => t => {
      t.providerConfig.orchestrationModeOverrides = { executor: { readActionResolution: { maxPlannerContextChars: value } } }
    }),
    t => { t.providerConfig.orchestrationModeOverrides = { executor: { readActionResolution: [] } } }]) {
    const source = manifest(); edit(source.contributions.template); assert.throws(() => build(source, catalogue))
  }
})

test('CLI accepts a raw manifest or matched published capture without leaking wrapper fields', t => {
  const root = mkdtempSync(join(tmpdir(), 'customer-quality-')); t.after(() => rmSync(root, { recursive: true, force: true }))
  const input = join(root, 'input.json'), cataloguePath = join(root, 'catalogue.json')
  writeFileSync(cataloguePath, JSON.stringify(catalogue))
  const cli = fileURLToPath(new URL('./build-quality-template.mjs', import.meta.url))
  const run = value => { writeFileSync(input, JSON.stringify(value)); return spawnSync(process.execPath, [cli, input, cataloguePath], { encoding: 'utf8' }) }
  for (const value of [manifest(), { version: { status: 'PUBLISHED', pluginId: manifest().pluginId, version: '1.4.0', manifest: manifest() }, unrelatedSecret: 'DO_NOT_COPY' }]) {
    const result = run(value); assert.equal(result.status, 0, result.stderr); assert.deepEqual(JSON.parse(result.stdout), build(manifest(), catalogue)); assert.equal(result.stdout.includes('DO_NOT_COPY'), false)
  }
  assert.notEqual(run({ version: { status: 'DRAFT', pluginId: manifest().pluginId, version: '1.4.0', manifest: manifest() } }).status, 0)
})
