import assert from 'node:assert/strict'
import test from 'node:test'
import { buildPublicSupportStarter, PUBLIC_SUPPORT_STARTER, publicSupportHash, publicSupportWorkspaceProfile } from './public-support-starter.mjs'

const capabilities = ['ai-fabric-core', 'ai-fabric-chat-session', 'standalone-conversation-control']
const published = manifest => ({ id: `version-${manifest.pluginId}`, pluginId: manifest.pluginId,
  version: manifest.version, status: 'PUBLISHED', publishedAt: '2026-11-08T00:00:00Z', manifest })
function inputs() {
  const template = published({ pluginId: PUBLIC_SUPPORT_STARTER.pluginId, version: '1.2.0', pluginType: 'TEMPLATE', contributions: {
    template: { templateId: 'custom-start-from-scratch',
      requiredPluginRefs: ['mkp-data-ai-support-documents@1.1.0', 'mkp-action-support-tickets@1.0.0'],
      providerConfig: { canonicalConversationsEnabled: true, openaiModel: 'gpt-5.4-mini' },
      deploymentBehavior: { type: 'CONVERSATIONAL', requiredRuntimeCapabilityIds: [...capabilities],
        defaultExecutionExtensions: [], specialistAssistance: { enabled: false } },
    },
  } })
  const document = published({ pluginId: 'mkp-data-ai-support-documents', version: '1.1.0', pluginType: 'DATA',
    installForm: [{ id: 'documentStorageBindingRef', type: 'text', required: true }], contributions: {
      datasets: [{ storageScope: 'CUSTOMER_SCOPED', sharingScope: 'DEPLOYMENT_ONLY', sourceConnector: {
        storageOwnership: 'BOUND_CONNECTION', bindingRefField: 'documentStorageBindingRef',
        allowedOperations: ['LIST', 'READ', 'HEAD'], deleteSourceOnRemoval: false,
      } }],
      knowledgeSources: [{ filters: { publicationStatus: 'PUBLIC_APPROVED' }, authModes: ['PUBLIC_RUNTIME_ANONYMOUS'] }],
    } })
  return { template, plugins: [document, published({ pluginId: 'mkp-action-support-tickets', version: '1.0.0', pluginType: 'ACTION' })],
    artifact: { id: 'runtime-reviewed', serviceName: 'ai-fabric-runtime', artifactType: 'DOCKER_IMAGE', artifactRole: 'RUNTIME',
      imageRepository: 'registry.example.test/runtime', imageDigest: `sha256:${'a'.repeat(64)}`,
      promotedAt: '2026-11-08T00:00:00Z', gitCommitSha: 'reviewed-source',
      capabilityManifest: { capabilities: [...capabilities] }, capabilityManifestHash: 'b'.repeat(64) },
    targets: ['staging', 'production'].map(environmentName => ({ id: `target-${environmentName}`, active: true,
      environmentName, providerType: 'COOLIFY', sourceStrategy: 'IMAGE_SOURCE', credentialRefId: 'server-credential-reference',
      providerConfig: { serverUuid: `server-${environmentName}` }, networkPolicy: {}, resourceDefaults: {} })),
  }
}

function freeze(value) {
  if (value && typeof value === 'object') { Object.values(value).forEach(freeze); Object.freeze(value) }
  return value
}

test('same existing business kit receives a new credential-free enrollment offer, not a new template or account', () => {
  const input = inputs(); const before = structuredClone(input)
  const result = buildPublicSupportStarter(freeze(input))
  assert.deepEqual(input, before)
  assert.equal(result.offerId, 'public-support-profile-20261108')
  assert.deepEqual(result.definition.template, { pluginId: input.template.pluginId, versionId: input.template.id,
    manifestSha256: publicSupportHash(input.template.manifest) })
  assert.deepEqual(result.definition.plugins.map(plugin => plugin.pluginId).sort(),
    ['mkp-action-support-tickets', 'mkp-data-ai-support-documents'])
  assert.equal(result.definition.templateId, 'custom-start-from-scratch')
  assert.equal(result.definition.providerConfig, undefined)
  assert.equal(result.definition.operations.includes('PROMOTION_APPLY'), true)
  assert.equal(result.definition.targets.find(target => target.intent === 'CUSTOMER_STAGING').targetProfileId, 'target-staging')
  assert.equal(result.definition.targets.find(target => target.intent === 'CUSTOMER_PRODUCTION').targetProfileId, 'target-production')
})

test('support workspace uses registered public contracts with tickets and human handover unbound until configured', () => {
  const profile = publicSupportWorkspaceProfile()
  assert.equal(profile.id, 'support-public')
  assert.equal(profile.interaction.packCode, 'support')
  assert.equal(profile.interaction.packVersion, '1.0.0')
  assert.equal(profile.interaction.configuration.schemaVersion, 'loomai-support-experience-config-v1')
  assert.equal(profile.interaction.connectionMode, 'PUBLIC_RUNTIME_ANONYMOUS')
  assert.equal(profile.interaction.connectionProfileCode, 'runtime-anonymous-direct')
  assert.deepEqual(profile.interaction.connectionConfiguration, {})
  assert.deepEqual(profile.authentication, { profileCode: 'none', profileVersion: '1.0.0', interactionPolicy: 'PUBLIC', configuration: {} })
  assert.equal(profile.interaction.configuration.capabilities.tickets, false)
  assert.equal(profile.interaction.configuration.capabilities.handover, false)
  assert.equal(profile.interaction.configuration.support.spaceId, undefined)
  assert.equal(profile.interaction.configuration.support.ticketProviderBindingId, undefined)
  assert.equal(profile.interaction.configuration.theme, undefined)
  profile.interaction.configuration.capabilities.tickets = true
  assert.equal(publicSupportWorkspaceProfile().interaction.configuration.capabilities.tickets, false)
})

test('both AI-state choices have explicit topologies and document storage remains a customer-scoped slot', () => {
  const { definition } = buildPublicSupportStarter(inputs())
  assert.deepEqual(definition.settings.find(rule => rule.setting === 'AI_STATE_STORAGE_MODE').allowedValues,
    ['MANAGED_POSTGRES', 'CUSTOMER_POSTGRES'])
  assert.deepEqual(Object.keys(definition.aiStateStorageTopologies).sort(), ['CUSTOMER_POSTGRES', 'MANAGED_POSTGRES'])
  assert.equal(definition.aiStateStorageTopologies.MANAGED_POSTGRES.databases, 1)
  assert.equal(definition.aiStateStorageTopologies.CUSTOMER_POSTGRES.databases, 0)
  assert.deepEqual(definition.bindingSlots, [
    { id: 'document-storage', kind: 'CUSTOMER_S3', pluginId: 'mkp-data-ai-support-documents',
      installConfigField: 'documentStorageBindingRef', supportedProfileIds: ['reviewed-s3-read-only'] },
    { id: 'ai-state', kind: 'CUSTOMER_AI_STATE', pluginId: null, installConfigField: null, supportedProfileIds: ['CUSTOMER_POSTGRES'] },
  ])
  assert.deepEqual(definition.documentAudiences, ['PUBLIC'])
  assert.deepEqual(definition.externalIntegrationProfiles, [])
  assert.match(definition.limits.measuredPolicyRef, /not-a-live-capacity-measurement/)
})

test('pin fingerprints and public profile setting identifiers match the canonical server contracts', () => {
  const input = inputs(); const { definition } = buildPublicSupportStarter(input)
  const { artifact: a } = input
  assert.equal(definition.artifacts[0].fingerprint, publicSupportHash({ id: a.id, serviceName: a.serviceName,
    artifactType: a.artifactType, artifactRole: a.artifactRole, imageRepository: a.imageRepository,
    imageDigest: a.imageDigest, gitCommitSha: a.gitCommitSha, capabilityManifest: a.capabilityManifest,
    capabilityManifestHash: a.capabilityManifestHash }))
  const target = input.targets[0]
  assert.equal(definition.targets[0].profileFingerprint, publicSupportHash({ id: target.id, provider: target.providerType,
    environment: target.environmentName, sourceStrategy: target.sourceStrategy, credentialReference: target.credentialRefId,
    providerConfig: target.providerConfig, networkPolicy: target.networkPolicy, resourceDefaults: target.resourceDefaults }))
  const profile = definition.workspaceProfiles[0]
  for (const [setting, prefix, value] of [['INTERACTION_PROFILE_ID', 'workspace', profile.interaction],
    ['THEME_PROFILE_ID', 'theme', profile.theme], ['AUTH_PROFILE_ID', 'auth', profile.authentication]])
    assert.deepEqual(definition.settings.find(rule => rule.setting === setting).allowedValues, [`${prefix}:${publicSupportHash(value)}`])
  assert.equal(publicSupportHash({ a: 1, b: 2 }), publicSupportHash({ b: 2, a: 1 }))
})

test('private extras, customer documents, provider credentials, funding and host configuration are never copied', () => {
  const input = inputs()
  input.customerId = 'PRIVATE_CUSTOMER'; input.funding = { providerKey: 'PRIVATE_PROVIDER_KEY' }
  input.workspaceProfile = { authentication: { projectUrl: 'PRIVATE_AUTH_URL' } }
  input.targets[0].providerConfig.authorization = 'PRIVATE_HOST_AUTHORIZATION'
  const result = JSON.stringify(buildPublicSupportStarter(input))
  for (const secret of ['PRIVATE_CUSTOMER', 'PRIVATE_PROVIDER_KEY', 'PRIVATE_AUTH_URL', 'PRIVATE_HOST_AUTHORIZATION', 'server-credential-reference'])
    assert.ok(!result.includes(secret))
  for (const key of ['customerId', 'ownerUserId', 'funding', 'bucket', 'apiKey', 'password', 'allowance', 'businessDatabase'])
    assert.ok(!result.includes(`"${key}"`))
})

for (const [label, mutate] of [
  ['internal template', input => { input.template.pluginId = 'mkp-template-loomai-support-workspace' }],
  ['old version', input => { input.template.version = '1.1.0' }],
  ['unpublished template', input => { input.template.status = 'DRAFT' }],
  ['unpublished dependency', input => { input.plugins[0].status = 'DRAFT' }],
  ['missing dependency', input => { input.plugins.pop() }],
  ['extra plugin', input => { input.plugins.push(published({ pluginId: 'mkp-action-private-diagnostics', version: '1.0.0' })) }],
  ['pinned AI-state mode', input => { input.template.manifest.contributions.template.providerConfig.aiStateStorageMode = 'MANAGED_POSTGRES' }],
  ['instance provider key', input => { input.template.manifest.contributions.template.providerConfig.openaiApiKey = 'PRIVATE' }],
  ['private-only documents', input => { input.plugins[0].manifest.contributions.knowledgeSources[0].authModes = ['PRIVATE_RUNTIME_BACKEND_MEDIATED'] }],
  ['unapproved documents', input => { input.plugins[0].manifest.contributions.knowledgeSources[0].filters.publicationStatus = 'PRIVATE' }],
  ['shared documents', input => { input.plugins[0].manifest.contributions.datasets[0].sharingScope = 'CUSTOMER_SHARED' }],
  ['mode-specific document storage', input => { input.plugins[0].manifest.contributions.datasets[0].sourceConnector.storageOwnership = 'CUSTOMER_MANAGED' }],
  ['source deletion', input => { input.plugins[0].manifest.contributions.datasets[0].sourceConnector.deleteSourceOnRemoval = true }],
  ['unpromoted artifact', input => { input.artifact.promotedAt = null }],
  ['non-runtime artifact', input => { input.artifact.artifactRole = 'MANAGED_DEPENDENCY' }],
  ['mutable artifact', input => { input.artifact.imageDigest = 'main' }],
  ['missing runtime capability', input => { input.artifact.capabilityManifest.capabilities.pop() }],
  ['incomplete targets', input => { input.targets.pop() }],
  ['inactive target', input => { input.targets[0].active = false }],
  ['wrong environment', input => { input.targets[0].environmentName = 'development' }],
  ['duplicate target', input => { input.targets[1].id = input.targets[0].id }],
  ['mutable build target', input => { input.targets[0].sourceStrategy = 'GIT_SOURCE' }],
]) test(`qualification rejects ${label}`, () => {
  const input = inputs(); mutate(input)
  assert.throws(() => buildPublicSupportStarter(input))
})
