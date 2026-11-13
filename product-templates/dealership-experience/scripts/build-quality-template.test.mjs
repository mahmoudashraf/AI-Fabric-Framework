import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { buildDealershipQualityTemplate } from './build-quality-template.mjs'

const read = name => JSON.parse(readFileSync(new URL(`../fixtures/${name}`, import.meta.url), 'utf8'))
const defaults = read('northfield-quality-defaults.json')
const catalogue = read('behaviour-catalogue.test.json')
const versions = [
  ['mkp-template-simulated-dealership-experience-v1', '1.14.0', '1.15.0'],
  ['mkp-template-dealership-external-systems-v1', '1.4.0', '1.5.0'],
  ['mkp-template-dealership-external-systems-managed-inbox-v1', '1.10.0', '1.11.0'],
]
const groups = ['tone', 'answerBreadth', 'answerLength', 'nextSteps']

function manifestFor([pluginId, version] = versions[0]) {
  return {
    schemaVersion: 1, pluginType: 'TEMPLATE', pluginId, version,
    displayName: 'Synthetic dealership template',
    permissions: { contributesTemplate: true, contributesShellPresentation: true },
    compatibility: { requiredCapabilities: ['templates'], supportedDeploymentTargets: ['original-target'] },
    contributions: { template: {
      templateId: 'original-base', curatedModuleId: 'original-module',
      deploymentBehavior: { type: 'CONVERSATIONAL', contractVersion: 1,
        allowedExecutionExtensions: ['CONVERSATION_HANDOVER'], defaultExecutionExtensions: [] },
      security: { authzMode: 'ALLOW_VERIFIED', publicRuntimeAcceptedAudiences: 'original-audience' },
      requiredPluginRefs: ['original-stock@1.0.0', 'original-policy@1.0.0'],
      requiredPluginInstallDefaults: { 'original-policy@1.0.0': {
        config: { publicContentApprovalConfirmed: false }, secretRefs: { sourceKey: 'ORIGINAL_SCOPED_REFERENCE' },
      } },
      shell: { defaultConversationMode: 'executor', greeting: { title: 'Original brand' } },
    }, shell: { modules: [{ id: 'original-module' }] } },
  }
}

function freeze(value) {
  if (value && typeof value === 'object') {
    Object.values(value).forEach(freeze)
    Object.freeze(value)
  }
  return value
}

function expectedBehaviour(input = catalogue) {
  const result = { schemaVersion: 'loomai-ai-behaviour-v1', revision: 1 }
  for (const key of groups) {
    const presetId = defaults.aiBehaviour.selections[key]
    const option = input.groups.find(group => group.key === key).options.find(option => option.id === presetId)
    result[key] = { presetId, revision: 1, instructions: option.instructions }
  }
  return result
}

test('fixture is limited to the three reviewed IDs, versions and candidate status', () => {
  assert.equal(defaults.schemaVersion, 'loomai-dealership-bootstrap-quality-v1')
  assert.equal(defaults.revision, 1)
  assert.equal(defaults.status, 'CANDIDATE_PENDING_MAIN_QUALIFICATION_AND_PUBLICATION')
  assert.deepEqual(defaults.templates.map(item => [item.pluginId, item.currentVersion, item.candidateVersion]), versions)
  assert.deepEqual(defaults.aiBehaviour, { schemaVersion: 'loomai-ai-behaviour-v1', revision: 1,
    selections: { tone: 'consultative', answerBreadth: 'advisory', answerLength: 'balanced', nextSteps: 'contextual' } })
  assert.deepEqual(defaults.providerConfig, { orchestrationModeOverrides: { executor: {
    knowledgeBaseOverviewEnabled: true,
    rag: { fanoutEnabled: true, maxSpaces: 2 },
    semanticRecovery: { missingVectorSpace: 'BOUNDED_ALLOWED_FAN_OUT' },
    readActionResolution: { planningMode: 'ITERATIVE', maxIterations: 2,
      ragCooperationMode: 'RAG_IF_ACTIONS_INSUFFICIENT', maxPlannerContextChars: 12000,
      maxActionEvidenceCharsPerAction: 8000, maxActionEvidenceItems: 10 },
  } } })
  assert.deepEqual(defaults.promptConfig, { ragMaxDocumentsUsedForContext: 8, ragMaxContextChars: 8000 })
})

for (const target of versions) {
  test(`only approved defaults and version change for ${target[0]}`, () => {
    const source = manifestFor(target)
    const historical = structuredClone(source)
    const output = buildDealershipQualityTemplate(freeze(source), freeze(structuredClone(catalogue)))
    const expected = structuredClone(historical)
    expected.version = target[2]
    expected.contributions.template.providerConfig = structuredClone(defaults.providerConfig)
    expected.contributions.template.promptConfig = { ...defaults.promptConfig, aiBehaviour: expectedBehaviour() }
    assert.deepEqual(output, expected)
    assert.deepEqual(source, historical)
    output.contributions.template.security.authzMode = 'CHANGED_ONLY_IN_OUTPUT'
    assert.deepEqual(source, historical)
  })
}

test('deep merge preserves provider choices, references, other modes and unselected executor settings', () => {
  const source = manifestFor(versions[1])
  const provider = {
    llmProvider: 'existing-provider', openaiModel: 'existing-model',
    vectorStrategy: 'existing-vector', existingEndpoint: 'https://example.test/vector',
    llmApiKeyRef: 'EXISTING_KEY_REFERENCE',
    orchestrationModeOverrides: {
      navigator: { rag: { maxSpaces: 4 }, ownSetting: true },
      thinker_deep: { readActionResolution: { maxIterations: 3 } },
      executor: { ownSetting: { retained: true },
        rag: { fanoutEnabled: false, maxSpaces: 1, timeoutMs: 1234 },
        semanticRecovery: { missingVectorSpace: 'DISABLED', otherPolicy: 'UNCHANGED' },
        readActionResolution: { planningMode: 'ONE_SHOT', maxTotalActions: 3, allowedActions: ['original-read'] },
      },
    },
  }
  const prompt = { systemPrompt: 'Original domain instructions.', retrievalPrompt: 'Original retrieval guidance.',
    smartSuggestionsEnabled: false, ragSimilarityThreshold: 0.43, ownSetting: { retained: true } }
  source.contributions.template.providerConfig = structuredClone(provider)
  source.contributions.template.promptConfig = structuredClone(prompt)
  const historical = structuredClone(source)
  const output = buildDealershipQualityTemplate(source, catalogue)
  const expected = structuredClone(historical)
  expected.version = versions[1][2]
  const executor = expected.contributions.template.providerConfig.orchestrationModeOverrides.executor
  executor.knowledgeBaseOverviewEnabled = true
  for (const key of ['rag', 'semanticRecovery', 'readActionResolution']) {
    Object.assign(executor[key], defaults.providerConfig.orchestrationModeOverrides.executor[key])
  }
  Object.assign(expected.contributions.template.promptConfig, defaults.promptConfig, { aiBehaviour: expectedBehaviour() })
  assert.deepEqual(output, expected)
  assert.deepEqual(source, historical)
  output.contributions.template.providerConfig.orchestrationModeOverrides.navigator.ownSetting = false
  assert.deepEqual(source, historical)
})

test('catalogue instructions, not a customer draft or copied prompt strings, determine each profile', () => {
  const input = structuredClone(catalogue)
  input.customerDraft = { providerConfig: { openaiApiKey: 'DO_NOT_COPY_CUSTOMER_CREDENTIAL' },
    promptConfig: { systemPrompt: 'DO_NOT_COPY_CUSTOMER_PROMPT' } }
  for (const group of input.groups) {
    for (const option of group.options) {
      option.instructions = `Exact supplied catalogue text for ${group.key}/${option.id}.\nKeep this text verbatim.`
      option.credentials = { apiKey: 'DO_NOT_COPY_OPTION_CREDENTIAL' }
    }
  }
  const output = buildDealershipQualityTemplate(manifestFor(), input)
  assert.deepEqual(output.contributions.template.promptConfig.aiBehaviour, expectedBehaviour(input))
  for (const sentinel of ['DO_NOT_COPY_CUSTOMER_CREDENTIAL', 'DO_NOT_COPY_CUSTOMER_PROMPT', 'DO_NOT_COPY_OPTION_CREDENTIAL']) {
    assert.equal(JSON.stringify(output).includes(sentinel), false)
  }
  assert.equal(output.contributions.template.providerConfig.openaiModel, undefined)
  assert.equal(output.contributions.template.providerConfig.openaiApiKey, undefined)
})

test('JSON round-trip retains the managed-profile contract and exact catalogue identity', () => {
  const result = buildDealershipQualityTemplate(manifestFor(), catalogue)
  const reopened = JSON.parse(JSON.stringify(result)).contributions.template.promptConfig.aiBehaviour
  assert.deepEqual(reopened, expectedBehaviour())
  assert.deepEqual(Object.keys(reopened), ['schemaVersion', 'revision', ...groups])
  const selections = Object.fromEntries(groups.map(key => {
    const profile = reopened[key]
    assert.deepEqual(Object.keys(profile), ['presetId', 'revision', 'instructions'])
    const option = catalogue.groups.find(group => group.key === key).options.find(option => option.id === profile.presetId)
    assert.equal(profile.revision, catalogue.revision)
    assert.equal(profile.instructions, option.instructions, 'An exact managed preset must not become Custom on reopen')
    return [key, profile.presetId]
  }))
  assert.deepEqual(selections, defaults.aiBehaviour.selections)
})

test('an existing supported managed profile is replaced only inside the approved behaviour area', () => {
  const source = manifestFor()
  source.contributions.template.promptConfig = { brandPrompt: 'Keep this.', aiBehaviour: expectedBehaviour() }
  source.contributions.template.promptConfig.aiBehaviour.tone = { presetId: 'custom', revision: 1, instructions: 'Prior template instructions.' }
  const output = buildDealershipQualityTemplate(source, catalogue)
  assert.equal(output.contributions.template.promptConfig.brandPrompt, 'Keep this.')
  assert.deepEqual(output.contributions.template.promptConfig.aiBehaviour, expectedBehaviour())
  assert.equal(source.contributions.template.promptConfig.aiBehaviour.tone.presetId, 'custom')
})

test('unsupported template IDs, types, manifest revisions and baseline versions are rejected', () => {
  for (const [key, value] of [
    ['pluginId', 'mkp-template-another-industry'], ['pluginType', 'DATA'], ['schemaVersion', 2],
    ['schemaVersion', '1'], ['version', '1.13.0'], ['version', '1.15.0'], ['version', null],
  ]) {
    const source = manifestFor()
    source[key] = value
    assert.throws(() => buildDealershipQualityTemplate(source, catalogue))
  }
  for (const value of [null, [], 'manifest', { behaviorConfig: {}, providerConfig: { apiKey: 'DO_NOT_COPY' } }]) {
    assert.throws(() => buildDealershipQualityTemplate(value, catalogue))
  }
  const unsupported = manifestFor()
  unsupported.contributions.template.deploymentBehavior.contractVersion = 2
  assert.throws(() => buildDealershipQualityTemplate(unsupported, catalogue), /contract revision/)
  unsupported.contributions.template.deploymentBehavior = { type: 'AGENTIC', contractVersion: 1 }
  assert.throws(() => buildDealershipQualityTemplate(unsupported, catalogue), /contract revision/)
})

test('malformed configuration blocks are rejected rather than discarded during merge', () => {
  const paths = [
    ['contributions'], ['contributions', 'template'], ['contributions', 'template', 'deploymentBehavior'],
    ['contributions', 'template', 'providerConfig'], ['contributions', 'template', 'promptConfig'],
    ...['orchestrationModeOverrides', 'orchestrationModeOverrides.executor',
      'orchestrationModeOverrides.executor.rag', 'orchestrationModeOverrides.executor.semanticRecovery',
      'orchestrationModeOverrides.executor.readActionResolution']
      .map(path => ['contributions', 'template', 'providerConfig', ...path.split('.')]),
  ]
  for (const path of paths) {
    for (const value of [null, [], 'invalid']) {
      const source = manifestFor()
      let parent = source
      for (const key of path.slice(0, -1)) parent = parent[key] ??= {}
      parent[path.at(-1)] = value
      assert.throws(() => buildDealershipQualityTemplate(source, catalogue), path.join('.'))
    }
  }
})

test('unsupported existing managed profile revisions are not silently reinterpreted', () => {
  for (const mutate of [
    value => { value.schemaVersion = 'other-schema' }, value => { value.revision = 2 },
    value => { value.tone.revision = 2 }, value => { value.tone = 'consultative' },
  ]) {
    const source = manifestFor()
    source.contributions.template.promptConfig = { aiBehaviour: expectedBehaviour() }
    mutate(source.contributions.template.promptConfig.aiBehaviour)
    assert.throws(() => buildDealershipQualityTemplate(source, catalogue))
  }
})

test('unsupported, missing or ambiguous catalogue selections fail without matching text or guessing', () => {
  for (const mutate of [
    value => { value.schemaVersion = 'other-schema' }, value => { value.revision = 2 },
    value => { value.groups = {} }, value => { value.groups.shift() },
    value => { value.groups.push(structuredClone(value.groups[0])) },
    value => { value.groups[0].options.shift() },
    value => { value.groups[0].options[0].id = 'Consultative' },
    value => { value.groups[0].options[0].instructions = '' },
    value => { value.groups[0].options[0].instructions = {} },
    value => { value.groups[0].options[0].revision = 2 },
    value => { value.groups[0].options.push(structuredClone(value.groups[0].options[0])) },
  ]) {
    const input = structuredClone(catalogue)
    mutate(input)
    assert.throws(() => buildDealershipQualityTemplate(manifestFor(), input))
  }
})

test('CLI handles saved manifests and published captures without copying wrapper/customer fields', () => {
  const directory = mkdtempSync(join(tmpdir(), 'dealership-template-quality-'))
  try {
    const manifestPath = join(directory, 'manifest.json')
    const cataloguePath = join(directory, 'catalogue.json')
    const cli = fileURLToPath(new URL('./build-quality-template.mjs', import.meta.url))
    writeFileSync(cataloguePath, JSON.stringify(catalogue))
    const source = manifestFor()
    const expected = buildDealershipQualityTemplate(source, catalogue)
    for (const saved of [source, {
      detail: { customerCredential: 'DO_NOT_COPY_WRAPPER_CREDENTIAL' },
      version: { pluginId: source.pluginId, version: source.version, status: 'PUBLISHED', manifest: source },
    }]) {
      writeFileSync(manifestPath, JSON.stringify(saved))
      const before = readFileSync(manifestPath, 'utf8')
      const result = spawnSync(process.execPath, [cli, manifestPath, cataloguePath], { encoding: 'utf8' })
      assert.equal(result.status, 0, result.stderr)
      assert.deepEqual(JSON.parse(result.stdout), expected)
      assert.equal(result.stdout.includes('DO_NOT_COPY_WRAPPER_CREDENTIAL'), false)
      assert.equal(readFileSync(manifestPath, 'utf8'), before)
    }
    for (const saved of [
      { version: { pluginId: source.pluginId, version: '9.0.0', status: 'PUBLISHED', manifest: source } },
      { version: { pluginId: 'wrong-plugin', version: source.version, status: 'PUBLISHED', manifest: source } },
      { version: { pluginId: source.pluginId, version: source.version, status: 'DRAFT', manifest: source } },
      { providerConfig: { apiKey: 'DO_NOT_COPY_CUSTOMER_CREDENTIAL' } },
    ]) {
      writeFileSync(manifestPath, JSON.stringify(saved))
      const result = spawnSync(process.execPath, [cli, manifestPath, cataloguePath], { encoding: 'utf8' })
      assert.equal(result.status, 1)
      assert.equal(result.stdout, '')
      assert.equal(result.stderr.includes('DO_NOT_COPY_CUSTOMER_CREDENTIAL'), false)
    }
    const usage = spawnSync(process.execPath, [cli], { encoding: 'utf8' })
    assert.equal(usage.status, 1)
    assert.match(usage.stderr, /Usage:/)
  } finally { rmSync(directory, { recursive: true, force: true }) }
})
