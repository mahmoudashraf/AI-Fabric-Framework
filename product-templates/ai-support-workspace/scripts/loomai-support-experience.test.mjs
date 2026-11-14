import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = name => JSON.parse(readFileSync(new URL(`../${name}`, import.meta.url), 'utf8'))
const generic = read('manifests/ai-support-workspace.1.1.0.manifest.json')
const instance = read('manifests/loomai-support-workspace.1.1.0.manifest.json')
const template = instance.contributions.template

test('LoomAI instance retains the current complete generic quality and canonical conversation defaults', () => {
  assert.equal(instance.pluginId, 'mkp-template-loomai-support-workspace')
  assert.equal(instance.version, '1.1.0')
  for (const field of ['providerConfig', 'promptConfig'])
    assert.deepEqual(template[field], generic.contributions.template[field])
  assert.deepEqual(template.deploymentBehavior, { ...generic.contributions.template.deploymentBehavior,
    defaultExecutionExtensions: ['CONVERSATION_HANDOVER'] })
  for (const field of ['answerGenerationPrompt', 'retrievalPrompt', 'assistantUiPrompt'])
    assert.ok(template.promptConfig[field]?.length > 0)
  assert.equal(template.providerConfig.canonicalConversationsEnabled, true)
  assert.match(template.promptConfig.systemPrompt, /successful provider receipt/)
})

test('exact instance bootstrap pins use existing primitives with no native ticket or diagnostic aliases', () => {
  assert.deepEqual(template.requiredPluginRefs, [
    'mkp-data-ai-support-documents@1.0.0', 'mkp-action-support-tickets@1.0.0',
    'mkp-action-loomai-support-diagnostics@1.0.3', 'mkp-integration-loomai-desk@1.0.1',
    'mkp-action-conversation-handover@1.1.0',
  ])
  assert.equal(template.requiredPluginInstallDefaults['mkp-action-support-tickets@1.0.0'], undefined)
  assert.equal(template.requiredPluginInstallDefaults['mkp-action-loomai-support-diagnostics@1.0.3'], undefined)
  assert.deepEqual(template.requiredPluginInstallDefaults['mkp-integration-loomai-desk@1.0.1'], {
    config: { bindingId: '${MANAGED_DESK_BINDING_ID}', providerBaseUrl: '${MANAGED_DESK_PROVIDER_BASE_URL}',
      operatorConsoleUrl: '${MANAGED_DESK_OPERATOR_CONSOLE_URL}' },
    secretRefs: { providerApiKeySecretRef: 'MANAGED_DESK_PROVIDER_API_KEY',
      runtimeCallbackSigningSecretRef: 'MANAGED_DESK_RUNTIME_CALLBACK_SIGNING_SECRET',
      providerWebhookSigningSecretRef: 'MANAGED_DESK_PROVIDER_WEBHOOK_SIGNING_SECRET' },
  })
  assert.deepEqual(template.deploymentBehavior.defaultExecutionExtensions, ['CONVERSATION_HANDOVER'])
  assert.equal(template.deploymentBehavior.specialistAssistance.enabled, false)
  assert.doesNotMatch(JSON.stringify(instance), /MANAGED_DESK_TICKET|service_role|s3:\/\/|SUPPORT_CASE_HOST|spaceId|publishableKey/)
})

test('historical internal candidate stays separate from the published selective defaults', () => {
  const catalog = read('catalog.json')
  const candidate = catalog.supportExperienceCandidates.find(value => value.manifest.endsWith('loomai-support-workspace.1.1.0.manifest.json'))
  assert.equal(candidate.publicationScope, 'SOURCE_ONLY')
  assert.equal(candidate.productionDefaultAccepted, false)
  assert.equal(catalog.entries.find(value => value.pluginSlug === 'ai-support-workspace').manifest,
    'manifests/ai-support-workspace.1.3.0.manifest.json')
  assert.equal(catalog.entries.find(value => value.pluginSlug === 'loomai-support-workspace').manifest,
    'manifests/loomai-support-workspace.1.3.0.manifest.json')
  assert.deepEqual(generic.contributions.template.requiredPluginRefs,
    ['mkp-data-ai-support-documents@1.0.0', 'mkp-action-support-tickets@1.0.0'])
  assert.equal(generic.contributions.template.requiredPluginInstallDefaults['mkp-action-support-tickets@1.0.0']
    .config.ticketCommandsUrl, '${MANAGED_DESK_TICKET_COMMANDS_URL}')
})
