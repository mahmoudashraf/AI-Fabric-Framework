import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { readFileSync, mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'
import { buildSupportQualityTemplate as build } from './build-quality-template.mjs'
import { supportOfferHash as hash } from './build-support-offer.mjs'
import { readConversationalQualityDefaults } from '../../shared/conversational-quality-defaults.mjs'

const read = name => JSON.parse(readFileSync(new URL(`../${name}`, import.meta.url), 'utf8'))
const fixture = read('fixtures/published-support-quality-baseline.1.2.0.json')
const catalogue = fixture.behaviourCatalogue
const quality = readConversationalQualityDefaults()
const baseline = () => structuredClone(fixture.templates[0].manifest)
const profiles = () => ({ schemaVersion: catalogue.schemaVersion, revision: catalogue.revision,
  ...Object.fromEntries(Object.entries(quality.behaviourSelections).map(([key, presetId]) => [key, {
    presetId, revision: catalogue.revision,
    instructions: catalogue.groups.find(group => group.key === key).options.find(option => option.id === presetId).instructions,
  }])) })

test('captured published 1.2.0 identities and hashes are stable in both environments', () => {
  assert.deepEqual(fixture.publishedEnvironments, ['staging', 'production'])
  assert.equal(fixture.candidatePublicationStatus, 'PENDING_PUBLICATION')
  assert.deepEqual(fixture.templates.map(item => item.pluginId),
    ['mkp-template-ai-support-workspace', 'mkp-template-loomai-support-workspace'])
  for (const item of fixture.templates) {
    assert.equal(item.status, 'PUBLISHED')
    assert.equal(item.version, '1.2.0')
    assert.equal(item.manifest.pluginId, item.pluginId)
    assert.equal(item.manifest.version, item.version)
    assert.equal(hash(item.manifest), item.manifestSha256)
  }
})

test('both committed candidates reproduce from live captures with exactly the approved leaf changes', () => {
  for (const item of fixture.templates) {
    const before = structuredClone(item.manifest)
    const expected = structuredClone(before)
    expected.version = '1.3.0'
    expected.contributions.template.providerConfig.openaiModel = quality.openaiModel
    expected.contributions.template.providerConfig.openaiMaxTokens = quality.openaiMaxTokens
    expected.contributions.template.promptConfig.aiBehaviour = profiles()
    assert.deepEqual(build(item.manifest, catalogue), expected)
    assert.deepEqual(read(item.candidateManifest), expected)
    assert.deepEqual(item.manifest, before)
  }
})

test('public and internal bootstrap pins, managed references and conversation authority remain distinct', () => {
  const [publicTemplate, internal] = fixture.templates.map(item => read(item.candidateManifest).contributions.template)
  assert.deepEqual(publicTemplate.requiredPluginRefs,
    ['mkp-data-ai-support-documents@1.1.0', 'mkp-action-support-tickets@1.0.0'])
  assert.deepEqual(internal.requiredPluginRefs, [...publicTemplate.requiredPluginRefs,
    'mkp-action-loomai-support-diagnostics@1.0.3', 'mkp-integration-loomai-desk@1.0.1', 'mkp-action-conversation-handover@1.1.0'])
  assert.deepEqual(publicTemplate.deploymentBehavior.defaultExecutionExtensions, [])
  assert.deepEqual(internal.deploymentBehavior.defaultExecutionExtensions, ['CONVERSATION_HANDOVER'])
  assert.equal(publicTemplate.deploymentBehavior.specialistAssistance.enabled, false)
  assert.equal(internal.deploymentBehavior.specialistAssistance.enabled, false)
  assert.equal(internal.requiredPluginInstallDefaults['mkp-action-support-tickets@1.0.0'], undefined)
  for (const item of fixture.templates) {
    const candidate = read(item.candidateManifest)
    assert.doesNotMatch(JSON.stringify(candidate), /service_role|sb_secret_|sk-proj-|BEGIN PRIVATE KEY|api\.loomai\.pro|s3:\/\//)
    assert.deepEqual(candidate.permissions, item.manifest.permissions)
  }
})

test('all custom prompts, calibrated thresholds and unrelated contribution fields survive exactly', () => {
  const source = baseline(), template = source.contributions.template
  Object.assign(template.promptConfig, { ragSimilarityThreshold: 0.37, customPrompt: { instructions: 'Original customer instructions.' } })
  source.contributions.otherContribution = { retained: ['original'] }
  template.security = { authzMode: 'ORIGINAL', audience: 'original' }
  template.routingConfig = { allowedSourceIds: ['original-source'], allowedActions: ['original-write'] }
  template.knowledgeSources = [{ sourceId: 'original-source', approvalRequired: true }]
  template.actionDefinitions = [{ actionId: 'original-write', requiresConfirmation: true }]
  template.storageConfig = { vectorStrategy: 'original-storage' }
  const before = structuredClone(source), expected = structuredClone(source)
  expected.version = '1.3.0'
  expected.contributions.template.providerConfig.openaiModel = quality.openaiModel
  expected.contributions.template.providerConfig.openaiMaxTokens = quality.openaiMaxTokens
  expected.contributions.template.promptConfig.aiBehaviour = profiles()
  const output = build(source, catalogue)
  assert.deepEqual(output, expected)
  output.contributions.template.promptConfig.customPrompt.instructions = 'Output only'
  assert.deepEqual(source, before)
})

test('absent settings gain only canonical intent, profiles and finite READ/evidence defaults', () => {
  const source = baseline(), template = source.contributions.template
  delete template.promptConfig
  delete template.providerConfig
  const output = build(source, catalogue).contributions.template
  assert.deepEqual(output.promptConfig, { intentExtractionPrompt: quality.intentExtractionPrompt,
    ...quality.promptBudgets, aiBehaviour: profiles() })
  assert.deepEqual(output.providerConfig, { openaiModel: quality.openaiModel, openaiMaxTokens: quality.openaiMaxTokens,
    orchestrationModeOverrides: { executor: {
      knowledgeBaseOverviewEnabled: quality.knowledgeBaseOverviewEnabled, readActionResolution: quality.readActionResolution,
    } } })
  assert.equal(Object.hasOwn(output.promptConfig, 'answerGenerationPrompt'), false)
  assert.equal(Object.hasOwn(output.promptConfig, 'ragSimilarityThreshold'), false)
  assert.equal(Object.hasOwn(output.providerConfig.orchestrationModeOverrides.executor, 'semanticRecovery'), false)
  assert.equal(Object.hasOwn(output.providerConfig.orchestrationModeOverrides.executor, 'rag'), false)
})

test('mini alias is pinned, missing model defaults, and explicit other models and token caps are preserved', () => {
  for (const model of [undefined, 'gpt-5.4-mini', quality.openaiModel, 'original-custom-model']) {
    for (const tokens of [undefined, 256, 2048, 4096]) {
      const source = baseline(), provider = source.contributions.template.providerConfig
      if (model === undefined) delete provider.openaiModel
      else provider.openaiModel = model
      if (tokens !== undefined) provider.openaiMaxTokens = tokens
      provider.llmPurposeOverrides = { intentExtraction: { model: 'original-intent-model' } }
      provider.embeddingModel = 'original-embedding-model'
      const output = build(source, catalogue).contributions.template.providerConfig
      assert.equal(output.openaiModel, model === undefined || model === 'gpt-5.4-mini' ? quality.openaiModel : model)
      assert.equal(output.openaiMaxTokens, tokens ?? quality.openaiMaxTokens)
      assert.deepEqual(output.llmPurposeOverrides, provider.llmPurposeOverrides)
      assert.equal(output.embeddingModel, provider.embeddingModel)
    }
  }
})

test('explicit overview, other modes, fanout, recovery and READ authority ceilings are unchanged', () => {
  const source = baseline(), provider = source.contributions.template.providerConfig
  provider.orchestrationModeOverrides = { navigator: { original: true }, executor: {
    knowledgeBaseOverviewEnabled: false, rag: { fanoutEnabled: false, maxSpaces: 1 },
    semanticRecovery: { missingVectorSpace: 'DENY' },
    readActionResolution: { enabled: false, maxTotalActions: 1, allowedActions: ['original-read'],
      allowedSourceIds: ['original-source'], timeoutMs: 1234, requiresConfirmation: true },
  } }
  const output = build(source, catalogue).contributions.template.providerConfig.orchestrationModeOverrides
  const expected = structuredClone(provider.orchestrationModeOverrides)
  Object.assign(expected.executor.readActionResolution, quality.readActionResolution)
  assert.deepEqual(output, expected)
})

test('small evidence budgets reach canonical minima and larger budgets are never reduced', () => {
  for (const multiplier of [0.5, 2]) {
    const source = baseline(), template = source.contributions.template
    for (const [key, value] of Object.entries(quality.promptBudgets)) template.promptConfig[key] = value * multiplier
    const reads = template.providerConfig.orchestrationModeOverrides.executor.readActionResolution
    const keys = ['maxPlannerContextChars', 'maxActionEvidenceCharsPerAction', 'maxActionEvidenceItems']
    for (const key of keys) reads[key] = quality.readActionResolution[key] * multiplier
    const output = build(source, catalogue).contributions.template
    for (const [key, value] of Object.entries(quality.promptBudgets)) assert.equal(output.promptConfig[key], Math.max(value, value * multiplier))
    for (const key of keys) assert.equal(output.providerConfig.orchestrationModeOverrides.executor.readActionResolution[key],
      Math.max(quality.readActionResolution[key], quality.readActionResolution[key] * multiplier))
  }
})

test('managed profile instructions come from the supplied catalogue, not catalogue default selections', () => {
  const selected = structuredClone(catalogue)
  selected.defaults.answerBreadth = 'advisory'
  selected.groups.find(group => group.key === 'tone').options.find(option => option.id === 'consultative').instructions = 'Changed catalogue instruction.'
  const output = build(baseline(), selected).contributions.template.promptConfig.aiBehaviour
  assert.equal(output.tone.instructions, 'Changed catalogue instruction.')
  assert.deepEqual(Object.fromEntries(Object.entries(quality.behaviourSelections).map(([key]) => [key, output[key].presetId])),
    { tone: 'consultative', answerBreadth: 'grounded', answerLength: 'balanced', nextSteps: 'contextual' })
})

test('custom profile instructions, explicit breadth and unrelated managed metadata remain intact', () => {
  const source = baseline(), template = source.contributions.template
  template.promptConfig.aiBehaviour = { ...profiles(), originalMetadata: { retained: true } }
  for (const key of ['tone', 'answerLength', 'nextSteps']) {
    template.promptConfig.aiBehaviour[key] = { presetId: 'custom', revision: 1, instructions: `Custom ${key}.` }
  }
  template.promptConfig.aiBehaviour.answerBreadth = { presetId: 'advisory', revision: 1, instructions: 'Explicit breadth.' }
  const before = structuredClone(source)
  const output = build(source, catalogue).contributions.template.promptConfig.aiBehaviour
  assert.deepEqual(output, template.promptConfig.aiBehaviour)
  output.originalMetadata.retained = false
  assert.deepEqual(source, before)
  delete template.promptConfig.aiBehaviour.nextSteps
  assert.deepEqual(build(source, catalogue).contributions.template.promptConfig.aiBehaviour.nextSteps, profiles().nextSteps)
})

test('unreviewed template identity, version and execution contracts are rejected before transformation', () => {
  for (const change of [m => { m.pluginId = 'another-template' }, m => { m.version = '1.1.0' },
    m => { m.pluginType = 'DATA' }, m => { m.schemaVersion = 2 },
    m => { m.contributions.template.deploymentBehavior.type = 'AGENT' },
    m => { m.contributions.template.deploymentBehavior.contractVersion = 2 }]) {
    const source = baseline(); change(source); assert.throws(() => build(source, catalogue))
  }
})

test('malformed source blocks and finite evidence budgets fail visibly without discarding configuration', () => {
  for (const change of [t => { t.promptConfig = [] }, t => { t.providerConfig = null },
    t => { t.promptConfig.aiBehaviour = { ...profiles(), revision: 2 } },
    t => { t.providerConfig.orchestrationModeOverrides = [] },
    t => { t.providerConfig.orchestrationModeOverrides.executor.readActionResolution = [] },
    ...[0, -1, 1.5, '8000', null].flatMap(value => [
      t => { t.promptConfig.ragMaxContextChars = value },
      t => { t.providerConfig.orchestrationModeOverrides.executor.readActionResolution.maxPlannerContextChars = value },
    ])]) {
    const source = baseline(); change(source.contributions.template); assert.throws(() => build(source, catalogue))
  }
})

test('missing, ambiguous and unsupported catalogue identities are not inferred or silently replaced', () => {
  for (const change of [c => { c.revision = 2 }, c => { c.groups.push(structuredClone(c.groups[0])) },
    c => { c.groups.splice(1, 1) }, c => { c.groups[0].options.push(structuredClone(c.groups[0].options.find(o => o.id === 'consultative'))) },
    c => { c.groups[0].options.find(o => o.id === 'consultative').instructions = '' }]) {
    const selected = structuredClone(catalogue); change(selected); assert.throws(() => build(baseline(), selected))
  }
})

test('current catalogue candidates are explicitly pending publication and do not relabel historical snapshots', () => {
  const catalog = read('catalog.json')
  for (const item of fixture.templates) {
    const entry = catalog.entries.find(entry => entry.currentSourceCandidate?.pluginId === item.pluginId)
    assert.ok(entry)
    assert.equal(entry.currentSourceCandidate.manifest, item.candidateManifest)
    assert.equal(entry.currentSourceCandidate.version, '1.3.0')
    assert.equal(entry.currentSourceCandidate.publicationScope, 'SOURCE_ONLY')
    assert.equal(entry.currentSourceCandidate.publicationStatus, 'PENDING_PUBLICATION')
    assert.equal(entry.currentSourceCandidate.productionDefaultAccepted, false)
    assert.equal(entry.currentSourceCandidate.publishedBaselineVersion, item.version)
    assert.equal(entry.currentSourceCandidate.publishedBaselineVersionId, item.versionId)
    assert.notEqual(entry.manifest, item.candidateManifest)
  }
  assert.ok(catalog.qualityCandidates.every(candidate => candidate.productionDefaultAccepted === false))
})

test('CLI accepts raw and matched published captures but never copies authentication or wrapper fields', t => {
  const root = mkdtempSync(join(tmpdir(), 'support-quality-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  const input = join(root, 'input.json'), catalogPath = join(root, 'catalogue.json')
  writeFileSync(catalogPath, JSON.stringify(catalogue))
  const cli = fileURLToPath(new URL('./build-quality-template.mjs', import.meta.url))
  const run = value => {
    writeFileSync(input, JSON.stringify(value))
    return spawnSync(process.execPath, [cli, input, catalogPath], { encoding: 'utf8' })
  }
  const source = baseline()
  const wrapper = { version: { pluginId: source.pluginId, version: source.version, status: 'PUBLISHED', manifest: source },
    authentication: 'DO_NOT_COPY' }
  for (const value of [source, wrapper]) {
    const result = run(value)
    assert.equal(result.status, 0, result.stderr)
    assert.deepEqual(JSON.parse(result.stdout), build(source, catalogue))
    assert.equal(result.stdout.includes('DO_NOT_COPY'), false)
  }
  for (const field of ['status', 'pluginId', 'version']) {
    const bad = structuredClone(wrapper); bad.version[field] = 'mismatch'
    assert.notEqual(run(bad).status, 0)
  }
})
