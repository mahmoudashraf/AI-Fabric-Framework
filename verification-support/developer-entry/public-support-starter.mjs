import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'

export const PUBLIC_SUPPORT_STARTER = Object.freeze({
  offerId: 'public-support-profile-20261108',
  pluginId: 'mkp-template-ai-support-workspace',
  version: '1.2.0',
  workspaceProfileId: 'support-public',
  reviewReference: '010.67:credential-free-public-support-bootstrap:2026-11-08',
})

const canonical = value => Array.isArray(value) ? value.map(canonical) : value && typeof value === 'object'
  ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value
export const publicSupportHash = value => createHash('sha256').update(JSON.stringify(canonical(value))).digest('hex')

export function publicSupportWorkspaceProfile() {
  return {
    id: PUBLIC_SUPPORT_STARTER.workspaceProfileId, label: 'Support workspace',
    theme: { primaryColor: '#0d766e', darkMode: 'auto' },
    interaction: {
      packCode: 'support', packVersion: '1.0.0', connectionMode: 'PUBLIC_RUNTIME_ANONYMOUS',
      connectionProfileCode: 'runtime-anonymous-direct', connectionProfileVersion: '1.0.0',
      deliveryMode: 'STANDALONE', connectionConfiguration: {},
      configuration: {
        schemaVersion: 'loomai-support-experience-config-v1',
        app: { assistantLabel: 'Your support AI', title: 'Help and support' },
        support: { requestLabel: 'My requests', topics: [
          { code: 'setup', label: 'Setup' }, { code: 'service', label: 'Service and support' },
        ], teamVisible: false },
        capabilities: { tickets: false, handover: false, attachments: true, currentPage: false },
        copy: { welcomeMessage: 'Ask a question about our approved support knowledge.' },
      },
    },
    authentication: { profileCode: 'none', profileVersion: '1.0.0', interactionPolicy: 'PUBLIC', configuration: {} },
  }
}

function pin(version) {
  assert.ok(version?.id && version.status === 'PUBLISHED' && Number.isFinite(Date.parse(version.publishedAt)),
    'Supply an actual published Marketplace version summary')
  assert.equal(version.manifest?.pluginId, version.pluginId)
  assert.equal(version.manifest?.version, version.version)
  return { pluginId: version.pluginId, versionId: version.id, manifestSha256: publicSupportHash(version.manifest) }
}

const operations = [
  'TEMPLATE_SELECT', 'BOOTSTRAP_CONFIGURATION', 'BOOTSTRAP', 'READ', 'DRAFT_EDIT', 'VERSION_PUBLISH',
  'BINDING_REPLACE', 'BINDING_REVOKE', 'SOURCE_DISCOVER', 'SOURCE_PREVIEW', 'SOURCE_INDEX', 'SOURCE_REFRESH',
  'SOURCE_REMOVE', 'APP_CONFIGURE', 'DOMAIN_CONFIGURE', 'AUTH_CONFIGURE', 'APPLY', 'VERIFY',
  'PROMOTION_PLAN', 'PROMOTION_APPLY', 'ACTIVATE', 'ROLLBACK', 'STAGING_RETAIN', 'STAGING_EXTEND', 'TARGET_RETIRE',
]

/** Offline PublishOfferRequest qualification. No network, publication, enrollment, funding or resource effects. */
export function buildPublicSupportStarter({ template, plugins, artifact, targets } = {}) {
  assert.equal(template?.pluginId, PUBLIC_SUPPORT_STARTER.pluginId, 'Use the public business Support template, never LoomAI Internal Support')
  assert.equal(template.version, PUBLIC_SUPPORT_STARTER.version)
  assert.equal(template.manifest?.pluginType, 'TEMPLATE')
  const templatePin = pin(template)
  const contribution = template.manifest.contributions.template
  assert.equal(contribution.templateId, 'custom-start-from-scratch')
  assert.equal(contribution.deploymentBehavior.type, 'CONVERSATIONAL')
  assert.equal(contribution.providerConfig?.canonicalConversationsEnabled, true)
  assert.equal(contribution.providerConfig.aiStateStorageMode, undefined, 'AI state storage must remain a creation-time choice')
  for (const key of ['openaiApiKey', 'embeddingApiKey', 'vectorApiKey', 'runtimeDatabasePassword'])
    assert.ok(!Object.hasOwn(contribution.providerConfig, key), 'Provider configuration must not bind instance credentials')
  assert.deepEqual(contribution.deploymentBehavior.defaultExecutionExtensions, [])
  assert.equal(contribution.deploymentBehavior.specialistAssistance.enabled, false)
  const required = ['mkp-data-ai-support-documents@1.1.0', 'mkp-action-support-tickets@1.0.0']
  assert.deepEqual([...contribution.requiredPluginRefs].sort(), [...required].sort())
  assert.ok(Array.isArray(plugins) && plugins.length === required.length)
  assert.deepEqual(plugins.map(version => `${version.pluginId}@${version.version}`).sort(), [...required].sort())
  const pluginPins = plugins.map(pin)
  const document = plugins.find(version => version.pluginId === 'mkp-data-ai-support-documents').manifest
  assert.equal(document.pluginType, 'DATA')
  assert.ok(document.installForm.some(field => field.id === 'documentStorageBindingRef' && field.type === 'text'))
  assert.ok(document.contributions.datasets.length > 0)
  for (const dataset of document.contributions.datasets) {
    assert.equal(dataset.storageScope, 'CUSTOMER_SCOPED')
    assert.equal(dataset.sharingScope, 'DEPLOYMENT_ONLY')
    assert.equal(dataset.sourceConnector.storageOwnership, 'BOUND_CONNECTION')
    assert.equal(dataset.sourceConnector.bindingRefField, 'documentStorageBindingRef')
    assert.equal(dataset.sourceConnector.deleteSourceOnRemoval, false)
    assert.deepEqual([...dataset.sourceConnector.allowedOperations].sort(), ['HEAD', 'LIST', 'READ'])
  }
  assert.ok(document.contributions.knowledgeSources.length > 0)
  for (const source of document.contributions.knowledgeSources) {
    assert.equal(source.filters?.publicationStatus, 'PUBLIC_APPROVED')
    assert.ok(source.authModes.includes('PUBLIC_RUNTIME_ANONYMOUS'))
  }
  assert.ok(artifact?.id && artifact.artifactType === 'DOCKER_IMAGE' && artifact.artifactRole === 'RUNTIME')
  assert.ok(artifact.imageRepository && Number.isFinite(Date.parse(artifact.promotedAt)))
  assert.match(artifact.imageDigest, /^sha256:[a-f0-9]{64}$/)
  assert.ok(artifact.capabilityManifestHash && Array.isArray(artifact.capabilityManifest?.capabilities))
  for (const capability of contribution.deploymentBehavior.requiredRuntimeCapabilityIds)
    assert.ok(artifact.capabilityManifest.capabilities.includes(capability), `Runtime capability required: ${capability}`)
  const artifactPin = { artifactId: artifact.id, fingerprint: publicSupportHash({
    id: artifact.id, serviceName: artifact.serviceName, artifactType: artifact.artifactType, artifactRole: artifact.artifactRole ?? '',
    imageRepository: artifact.imageRepository ?? '', imageDigest: artifact.imageDigest, gitCommitSha: artifact.gitCommitSha ?? '',
    capabilityManifest: artifact.capabilityManifest, capabilityManifestHash: artifact.capabilityManifestHash ?? '',
  }) }
  assert.ok(Array.isArray(targets) && targets.length === 2)
  assert.deepEqual(targets.map(profile => profile.environmentName).sort(), ['production', 'staging'])
  assert.equal(new Set(targets.map(profile => profile.id)).size, 2)
  const targetPins = targets.map(profile => {
    assert.ok(profile.id && profile.active && profile.sourceStrategy === 'IMAGE_SOURCE')
    assert.ok(profile.providerType && profile.providerConfig && profile.networkPolicy && profile.resourceDefaults)
    return { targetProfileId: profile.id,
      intent: profile.environmentName === 'production' ? 'CUSTOMER_PRODUCTION' : 'CUSTOMER_STAGING',
      profileFingerprint: publicSupportHash({ id: profile.id, provider: profile.providerType, environment: profile.environmentName,
        sourceStrategy: profile.sourceStrategy, credentialReference: profile.credentialRefId ?? '',
        providerConfig: profile.providerConfig, networkPolicy: profile.networkPolicy, resourceDefaults: profile.resourceDefaults }),
    }
  })
  const workspace = publicSupportWorkspaceProfile()
  const text = (setting, maxLength) => ({ setting, type: 'TEXT', maxLength, allowedValues: [] })
  const choice = (setting, allowedValues) => ({ setting, type: 'CHOICE', maxLength: 100, allowedValues })
  // Same conservative single-runtime sizing as the existing public knowledge starter; not measured capacity or funding.
  // The optional managed database is AI conversation state, never a customer business system of record.
  const managed = { replicas: 1, cpuMillis: 1750, memoryMiB: 3072, diskMiB: 4096,
    databases: 1, indexes: 1, dependencies: 0, concurrentApplies: 1 }
  const customer = { ...managed, cpuMillis: 1500, memoryMiB: 2560, diskMiB: 0, databases: 0 }
  return { offerId: PUBLIC_SUPPORT_STARTER.offerId, reviewReference: PUBLIC_SUPPORT_STARTER.reviewReference, definition: {
    schemaVersion: '1', name: 'AI Support Experience', templateId: contribution.templateId,
    template: templatePin, plugins: pluginPins, artifacts: [artifactPin], targets: targetPins, operations: [...operations],
    settings: [text('DEPLOYMENT_NAME', 120), text('APP_TITLE', 120), text('APP_DOMAIN', 255),
      choice('AI_STATE_STORAGE_MODE', ['MANAGED_POSTGRES', 'CUSTOMER_POSTGRES']), choice('DOCUMENT_AUDIENCE', ['PUBLIC']),
      ...[['INTERACTION_PROFILE_ID', 'workspace', workspace.interaction], ['THEME_PROFILE_ID', 'theme', workspace.theme],
        ['AUTH_PROFILE_ID', 'auth', workspace.authentication]].map(([setting, prefix, value]) => choice(setting, [`${prefix}:${publicSupportHash(value)}`]))],
    bindingSlots: [
      { id: 'document-storage', kind: 'CUSTOMER_S3', pluginId: 'mkp-data-ai-support-documents',
        installConfigField: 'documentStorageBindingRef', supportedProfileIds: ['reviewed-s3-read-only'] },
      { id: 'ai-state', kind: 'CUSTOMER_AI_STATE', pluginId: null, installConfigField: null, supportedProfileIds: ['CUSTOMER_POSTGRES'] },
    ],
    documentAudiences: ['PUBLIC'], admissionPrerequisites: ['APPROVED_POLICY', 'IMMUTABLE_ARTIFACTS'],
    limits: { ...managed, activeStagingTargets: 1, stagingExpiryHours: 24, maxExtensionHours: 24, recoveryWindowHours: 24,
      measuredPolicyRef: '010.67:public-starter-sizing-only:not-a-live-capacity-measurement' },
    topology: managed, aiStateStorageTopologies: { MANAGED_POSTGRES: { ...managed }, CUSTOMER_POSTGRES: customer },
    approvedPolicyRef: '010.67:public-support-bootstrap-only', operatingPolicyRef: '010.67:customer-owned-bindings-before-apply',
    identityPolicyRef: '010.67:no-shared-customer-identity-provider', storagePolicyRef: '010.67:customer-scoped-bound-document-storage',
    workspaceProfiles: [workspace], externalIntegrationProfiles: [],
  } }
}
