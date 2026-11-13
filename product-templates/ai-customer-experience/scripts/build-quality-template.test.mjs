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
const profile = (key, id) => ({ presetId: id, revision: 1, instructions: catalogue.groups.find(g => g.key === key).options.find(o => o.id === id).instructions })
const expectedProfiles = () => ({ schemaVersion: catalogue.schemaVersion, revision: 1,
  tone: profile('tone', 'consultative'), answerLength: profile('answerLength', 'balanced'), nextSteps: profile('nextSteps', 'contextual'), answerBreadth: profile('answerBreadth', 'grounded') })
const manifest = () => ({ schemaVersion: 1, pluginType: 'TEMPLATE', pluginId: 'mkp-template-document-knowledge-assistant', version: '1.3.0',
  displayName: 'Synthetic generic workspace', permissions: { contributesTemplate: true }, compatibility: { supportedDeploymentTargets: ['original-target'] },
  contributions: { template: { templateId: 'original-template', deploymentBehavior: { type: 'CONVERSATIONAL', contractVersion: 1, defaultExecutionExtensions: [] },
    requiredPluginRefs: ['generic-source@1.0.0'], requiredPluginInstallDefaults: { 'generic-source@1.0.0': { config: { publicContentApprovalConfirmed: false }, secretRefs: { key: 'EXISTING_REF' } } },
    security: { authzMode: 'ALLOW_VERIFIED', originalAudience: 'original-audience' }, shell: { greeting: { title: 'Original workspace' } },
    actionDefinitions: [{ actionId: 'original-write', accessMode: 'WRITE', requiresConfirmation: true }],
    providerConfig: { openaiModel: 'original-model', openaiMaxTokens: 2048, vectorStrategy: 'original-vector', originalProvider: { retained: true } },
  } } })

test('fixture changes only the reviewed generic template family', () => {
  assert.equal(defaults.status, 'PUBLISHED_BOOTSTRAP_DEFAULTS_20261113')
  assert.deepEqual(defaults.template, { pluginId: 'mkp-template-document-knowledge-assistant', currentVersion: '1.3.0', candidateVersion: '1.4.0' })
  assert.deepEqual(defaults.aiBehaviour.selections, { tone: 'consultative', answerLength: 'balanced', nextSteps: 'contextual' })
  assert.ok(!Object.hasOwn(defaults, 'requiredDataPlugin'))
})

test('only four approved quality areas and version change; source, security and model are preserved', () => {
  const source = manifest(), before = structuredClone(source)
  const output = build(source, catalogue)
  const expected = structuredClone(before)
  expected.version = '1.4.0'
  expected.contributions.template.promptConfig = { ragMaxDocumentsUsedForContext: 8, ragMaxContextChars: 8000, aiBehaviour: expectedProfiles() }
  expected.contributions.template.providerConfig.orchestrationModeOverrides = { executor: { readActionResolution: {
    maxPlannerContextChars: 12000, maxActionEvidenceCharsPerAction: 8000, maxActionEvidenceItems: 10,
    planningMode: 'ITERATIVE', maxIterations: 2, ragCooperationMode: 'RAG_IF_ACTIONS_INSUFFICIENT',
  } } }
  assert.deepEqual(output, expected)
  assert.deepEqual(source, before)
  assert.equal(Object.hasOwn(output.contributions.template.promptConfig, 'ragSimilarityThreshold'), false)
  assert.equal(Object.hasOwn(output.contributions.template.providerConfig.orchestrationModeOverrides.executor, 'semanticRecovery'), false)
})

test('existing breadth, threshold, other modes and unselected executor configuration are unchanged', () => {
  const source = manifest(), t = source.contributions.template
  t.promptConfig = { ragSimilarityThreshold: 0.61, customPrompt: { instructions: 'Original approved prompt' }, aiBehaviour: expectedProfiles() }
  t.promptConfig.aiBehaviour.answerBreadth = { presetId: 'custom', revision: 1, instructions: 'Original custom breadth.' }
  t.providerConfig.orchestrationModeOverrides = { navigator: { marker: true }, executor: { rag: { fanoutEnabled: false, maxSpaces: 1 }, semanticRecovery: { missingVectorSpace: 'DENY' }, readActionResolution: { enabled: true, unrelated: { preserved: true } } } }
  const output = build(source, catalogue).contributions.template
  assert.deepEqual(output.promptConfig.aiBehaviour.answerBreadth, t.promptConfig.aiBehaviour.answerBreadth)
  assert.equal(output.promptConfig.ragSimilarityThreshold, 0.61)
  assert.deepEqual(output.providerConfig.orchestrationModeOverrides.navigator, { marker: true })
  assert.deepEqual(output.providerConfig.orchestrationModeOverrides.executor.rag, t.providerConfig.orchestrationModeOverrides.executor.rag)
  assert.deepEqual(output.providerConfig.orchestrationModeOverrides.executor.semanticRecovery, { missingVectorSpace: 'DENY' })
  output.promptConfig.customPrompt.instructions = 'Changed in output only'
  output.providerConfig.orchestrationModeOverrides.executor.readActionResolution.unrelated.preserved = false
  assert.equal(t.promptConfig.customPrompt.instructions, 'Original approved prompt')
  assert.equal(t.providerConfig.orchestrationModeOverrides.executor.readActionResolution.unrelated.preserved, true)
})

test('already larger evidence limits are not reduced', () => {
  const source = manifest(), t = source.contributions.template
  t.promptConfig = { ragMaxDocumentsUsedForContext: 12, ragMaxContextChars: 16000 }
  t.providerConfig.orchestrationModeOverrides = { executor: { readActionResolution: { maxPlannerContextChars: 24000, maxActionEvidenceCharsPerAction: 12000, maxActionEvidenceItems: 15 } } }
  const output = build(source, catalogue).contributions.template
  assert.equal(output.promptConfig.ragMaxDocumentsUsedForContext, 12)
  assert.equal(output.promptConfig.ragMaxContextChars, 16000)
  assert.equal(output.providerConfig.orchestrationModeOverrides.executor.readActionResolution.maxPlannerContextChars, 24000)
  assert.equal(output.providerConfig.orchestrationModeOverrides.executor.readActionResolution.maxActionEvidenceItems, 15)
})

test('inherited breadth and instructions come from the catalogue, never dealership selections', () => {
  const selected = structuredClone(catalogue)
  selected.defaults.answerBreadth = 'advisory'
  selected.groups.find(g => g.key === 'tone').options.find(o => o.id === 'consultative').instructions = 'SYNTHETIC CHANGED CATALOGUE INSTRUCTION'
  const output = build(manifest(), selected).contributions.template.promptConfig.aiBehaviour
  assert.equal(output.answerBreadth.presetId, 'advisory')
  assert.equal(output.tone.instructions, 'SYNTHETIC CHANGED CATALOGUE INSTRUCTION')
})

test('ambiguous, missing or unsupported catalogue identities are rejected without inference', () => {
  for (const edit of [c => { c.revision = 2 }, c => { c.groups.push(structuredClone(c.groups[0])) },
    c => { c.groups[0].options.push(structuredClone(c.groups[0].options[0])) }, c => { delete c.defaults },
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
  for (const value of [manifest(), { version: { status: 'PUBLISHED', pluginId: manifest().pluginId, version: '1.3.0', manifest: manifest() }, unrelatedSecret: 'DO_NOT_COPY' }]) {
    const result = run(value); assert.equal(result.status, 0, result.stderr); assert.deepEqual(JSON.parse(result.stdout), build(manifest(), catalogue)); assert.equal(result.stdout.includes('DO_NOT_COPY'), false)
  }
  assert.notEqual(run({ version: { status: 'DRAFT', pluginId: manifest().pluginId, version: '1.3.0', manifest: manifest() } }).status, 0)
})
