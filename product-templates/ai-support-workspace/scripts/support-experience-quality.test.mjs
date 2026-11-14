import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const read = name => JSON.parse(readFileSync(new URL(`../${name}`, import.meta.url), 'utf8'))
const manifest = read('manifests/ai-support-workspace.1.1.0.manifest.json')
const template = manifest.contributions.template

test('support experience candidate carries selected current baseline model and bounded quality settings', () => {
  assert.deepEqual(template.providerConfig, {
    canonicalConversationsEnabled: true,
    openaiModel: 'gpt-5.4-mini',
    orchestrationModeOverrides: { executor: {
      readActionResolution: { planningMode: 'ITERATIVE', maxIterations: 2,
        ragCooperationMode: 'RAG_IF_ACTIONS_INSUFFICIENT', maxPlannerContextChars: 12000,
        maxActionEvidenceCharsPerAction: 8000, maxActionEvidenceItems: 10 },
      knowledgeBaseOverviewEnabled: true,
      semanticRecovery: { missingVectorSpace: 'BOUNDED_ALLOWED_FAN_OUT' },
      rag: { fanoutEnabled: true, maxSpaces: 1 },
    } },
  })
  assert.equal(template.promptConfig.ragMaxDocumentsUsedForContext, 8)
  assert.equal(template.promptConfig.ragMaxContextChars, 8000)
  assert.equal(template.promptConfig.ragSimilarityThreshold, undefined)
})

test('canonical conversations are enabled without requiring a human channel or changing published versions', () => {
  assert.equal(template.providerConfig.canonicalConversationsEnabled, true)
  assert.deepEqual(template.deploymentBehavior.defaultExecutionExtensions, [])
  assert.deepEqual(template.requiredPluginRefs, ['mkp-data-ai-support-documents@1.0.0', 'mkp-action-support-tickets@1.0.0'])
  assert.deepEqual(template.requiredPluginInstallDefaults['mkp-action-support-tickets@1.0.0'], {
    config: { ticketCommandsUrl: '${MANAGED_DESK_TICKET_COMMANDS_URL}' },
    secretRefs: { ticketConnectorKey: 'MANAGED_DESK_TICKET_CONNECTOR_KEY' },
  })
  assert.ok(template.recommendedPluginIds.includes('mkp-integration-loomai-desk'))
  assert.equal(read('manifests/ai-support-workspace.1.0.1.manifest.json').contributions.template.providerConfig, undefined)
})

test('baseline prompt guidance is retained alongside confirmed ticket receipts and independent humans', () => {
  const prior = read('fixtures/balanced-support-quality-v2.json').promptConfig
  for (const field of ['intentExtractionPrompt', 'actionSelectionPrompt', 'clarificationPrompt',
    'answerGenerationPrompt', 'retrievalPrompt'])
    assert.equal(template.promptConfig[field], prior[field])
  assert.equal(template.promptConfig.assistantUiPrompt,
    'Write in a clear operator-friendly style suitable for product UIs. Keep formatting compact and easy to scan.')
  assert.ok(template.promptConfig.systemPrompt.startsWith(`${prior.systemPrompt}\n\n`))
  assert.match(template.promptConfig.systemPrompt, /installed confirmed ticket action/)
  assert.match(template.promptConfig.systemPrompt, /successful provider receipt/)
  assert.match(template.promptConfig.systemPrompt, /Human handover is independent/)
  for (const section of [template.providerConfig, template.promptConfig])
    assert.doesNotMatch(JSON.stringify(section), /loomai|supabase|s3:\/\/|service_role|secretRef|openaiApiKey/i)
})

test('historical candidate stays unaccepted while the separate selective release is current', () => {
  const catalog = read('catalog.json')
  const candidate = catalog.supportExperienceCandidates.find(item => item.manifest.endsWith('ai-support-workspace.1.1.0.manifest.json'))
  assert.equal(candidate.publicationScope, 'SOURCE_ONLY')
  assert.equal(candidate.productionDefaultAccepted, false)
  assert.equal(catalog.entries.find(item => item.pluginSlug === 'ai-support-workspace').manifest,
    'manifests/ai-support-workspace.1.3.0.manifest.json')
  assert.equal(read('manifests/ai-support-workspace.1.0.1.manifest.json').contributions.template.providerConfig, undefined)
})
