import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'

export const PUBLIC_STARTERS = Object.freeze({
  dealership: { offerId: 'public-dealership-simulator-v1', name: 'Dealership simulator',
    pluginId: 'mkp-template-simulated-dealership-experience-v1', version: '1.11.0' },
  knowledge: { offerId: 'public-knowledge-workspace-v1', name: 'Knowledge workspace',
    pluginId: 'mkp-template-document-knowledge-assistant', version: '1.2.0' },
})

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical)
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]))
  return value
}
export const hash = value => createHash('sha256').update(JSON.stringify(canonical(value))).digest('hex')

export function publicKnowledgeManifest(original) {
  assert.equal(original.pluginId, 'mkp-data-document-knowledge-s3')
  const manifest = structuredClone(original)
  manifest.version = '1.2.0'
  manifest.displayName = 'Document Knowledge - Approved Public Content'
  manifest.compatibility.supportedAuthModes = ['PRIVATE_RUNTIME_BACKEND_MEDIATED', 'PUBLIC_RUNTIME_AUTHENTICATED', 'PUBLIC_RUNTIME_ANONYMOUS']
  manifest.installForm.push({ id: 'publicContentApprovalConfirmed', label: 'Public content approval confirmed', type: 'boolean',
    required: true, description: 'Only register documents explicitly approved by their owner for public answers.' })
  manifest.contributions.entityConfig['ai-entities'].document['metadata-fields'].push({ name: 'publicationStatus',
    'data-type': 'STRING', destinations: ['VECTOR_METADATA', 'LLM_CONTEXT'], priority: 100, required: true, 'sanitize-pii': false })
  for (const dataset of manifest.contributions.datasets) dataset.documentPolicy.allowedMetadataKeys.push('publicationStatus')
  for (const source of manifest.contributions.knowledgeSources) {
    source.filters.publicationStatus = 'PUBLIC_APPROVED'
    source.authModes = [...manifest.compatibility.supportedAuthModes]
  }
  return manifest
}

export function starterTemplate(kind, original) {
  const starter = PUBLIC_STARTERS[kind]
  assert.ok(starter)
  assert.equal(original.pluginId, starter.pluginId)
  const manifest = structuredClone(original)
  manifest.version = starter.version
  manifest.displayName = starter.name
  const template = manifest.contributions.template
  // Public defaults never pre-authorize a customer's files or bind a provider account.
  if (kind === 'dealership') {
    template.requiredPluginInstallDefaults['mkp-data-dealership-knowledge-v1@1.0.0'].config.publicContentApprovalConfirmed = false
  } else {
    manifest.compatibility.supportedDeploymentTargets = ['dev-openai-milvus']
    template.templateId = 'dev-openai-milvus'
    template.providerConfig = { vectorStrategy: 'milvus', vectorStoragePosture: 'SHARED', vectorProvisioningMode: 'EXTERNAL_EXISTING',
      milvusExternalBindingRequired: true, milvusAuthenticationType: 'TOKEN', milvusSecure: true, milvusPort: 443, openaiMaxTokens: 2048 }
    template.requiredPluginRefs = ['mkp-data-document-knowledge-s3@1.2.0']
    template.requiredPluginInstallDefaults = { 'mkp-data-document-knowledge-s3@1.2.0': {
      config: { documentStorageBindingRef: 'slot:document-storage', publicContentApprovalConfirmed: false }, secretRefs: {} } }
    template.deploymentBehavior.allowedExecutionExtensions = ['HUMAN_REVIEW', 'CONVERSATION_HANDOVER']
    template.deploymentBehavior.defaultExecutionExtensions = []
  }
  template.security = { authzMode: 'ALLOW_VERIFIED', publicRuntimeBootstrapEnabled: true,
    publicRuntimeTokenIssuer: 'runtime-public-bootstrap', publicRuntimeAcceptedIssuers: 'runtime-public-bootstrap',
    publicRuntimeDefaultAudience: 'loomai-ai-workspace', publicRuntimeAcceptedAudiences: 'loomai-ai-workspace' }
  return manifest
}

function workspace(kind) {
  const theme = { darkMode: false, fontFamily: 'Inter, system-ui, sans-serif', borderRadius: '0.5rem', primaryColor: '#176b63' }
  const configuration = kind === 'dealership'
    ? { dealer: { id: 'my-dealership', assistantLabel: 'Dealership AI', sourceMode: 'DEALERSHIP_INVENTORY' },
      page: { kind: 'auto', rootSelector: 'main', contextLabel: 'Current page', maxChars: 1800, maxPages: 3, maxTotalChars: 10000 },
      knowledge: { inventoryVectorSpace: 'dealer-vehicle' }, capabilities: { callback: true, testDrive: true, comparison: true },
      presentation: { imageHostAllowlist: [] } }
    : { app: { assistantLabel: 'Knowledge Assistant' }, capabilities: { currentPage: false, handover: false } }
  return { id: `${kind}-public`, label: PUBLIC_STARTERS[kind].name, theme,
    interaction: { packCode: kind === 'dealership' ? 'dealership' : 'generic-workspace',
      packVersion: kind === 'dealership' ? '1.1.0' : '1.0.0', connectionMode: 'PUBLIC_RUNTIME_ANONYMOUS',
      connectionProfileCode: 'runtime-anonymous-direct', connectionProfileVersion: '1.0.0', connectionConfiguration: {},
      deliveryMode: 'STANDALONE', configuration },
    authentication: { profileCode: 'none', profileVersion: '1.0.0', interactionPolicy: 'PUBLIC', configuration: {} } }
}

const operations = ['TEMPLATE_SELECT', 'BOOTSTRAP_CONFIGURATION', 'BOOTSTRAP', 'READ', 'DRAFT_EDIT', 'VERSION_PUBLISH',
  'BINDING_REPLACE', 'BINDING_REVOKE', 'SOURCE_DISCOVER', 'SOURCE_PREVIEW', 'SOURCE_INDEX', 'SOURCE_REFRESH', 'SOURCE_REMOVE',
  'APP_CONFIGURE', 'DOMAIN_CONFIGURE', 'AUTH_CONFIGURE', 'APPLY', 'VERIFY', 'PROMOTION_PLAN', 'PROMOTION_APPLY', 'ACTIVATE',
  'ROLLBACK', 'STAGING_RETAIN', 'STAGING_EXTEND', 'TARGET_RETIRE']

export function starterOffer(kind, { template, plugins, artifact, targets, externalHosts = {} }) {
  const starter = PUBLIC_STARTERS[kind]
  assert.ok(starter)
  assert.equal(template.pluginId, starter.pluginId)
  assert.equal(template.version, starter.version)
  assert.equal(template.status, 'PUBLISHED')
  const pin = version => ({ pluginId: version.pluginId, versionId: version.id, manifestSha256: hash(version.manifest) })
  const required = template.manifest.contributions.template.requiredPluginRefs
  assert.deepEqual(plugins.map(plugin => `${plugin.pluginId}@${plugin.version}`).sort(), [...required].sort())
  assert.ok(plugins.every(plugin => plugin.status === 'PUBLISHED'))
  assert.equal(artifact.artifactRole, 'RUNTIME')
  assert.ok(artifact.promotedAt)
  assert.match(artifact.imageDigest, /^sha256:[a-f0-9]{64}$/)
  const artifactFingerprint = hash({ id: artifact.id, serviceName: artifact.serviceName, artifactType: artifact.artifactType,
    artifactRole: artifact.artifactRole, imageRepository: artifact.imageRepository, imageDigest: artifact.imageDigest,
    gitCommitSha: artifact.gitCommitSha, capabilityManifest: artifact.capabilityManifest, capabilityManifestHash: artifact.capabilityManifestHash })
  const targetPins = targets.map(profile => ({ targetProfileId: profile.id,
    intent: profile.environmentName === 'production' ? 'CUSTOMER_PRODUCTION' : 'CUSTOMER_STAGING',
    profileFingerprint: hash({ id: profile.id, provider: profile.providerType, environment: profile.environmentName,
      sourceStrategy: profile.sourceStrategy, credentialReference: profile.credentialRefId ?? '', providerConfig: profile.providerConfig,
      networkPolicy: profile.networkPolicy, resourceDefaults: profile.resourceDefaults }) }))
  assert.equal(targetPins.filter(target => target.intent === 'CUSTOMER_STAGING').length, 1)
  assert.equal(targetPins.filter(target => target.intent === 'CUSTOMER_PRODUCTION').length, 1)
  const profile = workspace(kind)
  const text = (setting, maxLength) => ({ setting, type: 'TEXT', maxLength, allowedValues: [] })
  const choice = (setting, allowedValues) => ({ setting, type: 'CHOICE', maxLength: 100, allowedValues })
  const knowledgeId = kind === 'dealership' ? 'mkp-data-dealership-knowledge-v1' : 'mkp-data-document-knowledge-s3'
  const bindingSlots = [{ id: 'document-storage', kind: 'CUSTOMER_S3', pluginId: knowledgeId,
    installConfigField: 'documentStorageBindingRef', supportedProfileIds: ['reviewed-s3-read-only'] },
  { id: 'ai-state', kind: 'CUSTOMER_AI_STATE', pluginId: null, installConfigField: null, supportedProfileIds: ['CUSTOMER_POSTGRES'] }]
  const externalIntegrationProfiles = []
  for (const plugin of plugins.filter(plugin => plugin.installForm.some(field => field.type === 'secretRef'))) {
    const fields = plugin.installForm
    const allowedHosts = externalHosts[plugin.pluginId]
    assert.ok(Array.isArray(allowedHosts) && allowedHosts.length, `Explicit endpoint hosts required for ${plugin.pluginId}`)
    const id = plugin.pluginId.includes('stock') ? 'simulator-stock' : 'simulator-actions'
    bindingSlots.push({ id, kind: 'EXTERNAL_SERVICE', pluginId: plugin.pluginId, installConfigField: null, supportedProfileIds: [id] })
    externalIntegrationProfiles.push({ schemaVersion: 'loomai-external-integration-profile/v1', id, bindingSlotId: id,
      ...pin(plugin), pluginVersionId: plugin.id, targetProfileIds: targets.map(target => target.id),
      configFields: fields.filter(field => field.type !== 'secretRef').map(field => field.id),
      secretFields: fields.filter(field => field.type === 'secretRef').map(field => field.id),
      endpointFields: fields.filter(field => field.type === 'url').map(field => field.id),
      accountScopeFields: fields.some(field => field.id === 'advertiserId') ? ['advertiserId'] : [], allowedHosts })
    delete externalIntegrationProfiles.at(-1).versionId
  }
  const topology = { replicas: 1, cpuMillis: 1750, memoryMiB: 3072, diskMiB: 4096, databases: 1,
    indexes: kind === 'dealership' ? 2 : 1, dependencies: 0, concurrentApplies: 1 }
  const byo = { ...topology, cpuMillis: 1500, memoryMiB: 2560, diskMiB: 0, databases: 0 }
  return { offerId: starter.offerId, reviewReference: '010.49:owner-selected-public-starters:2026-10-28', definition: {
    schemaVersion: '1', name: starter.name, templateId: template.manifest.contributions.template.templateId,
    template: pin(template), plugins: plugins.map(pin), artifacts: [{ artifactId: artifact.id, fingerprint: artifactFingerprint }], targets: targetPins,
    operations, settings: [text('DEPLOYMENT_NAME', 120), text('APP_TITLE', 120), text('APP_DOMAIN', 255),
      choice('AI_STATE_STORAGE_MODE', ['MANAGED_POSTGRES', 'CUSTOMER_POSTGRES']), choice('DOCUMENT_AUDIENCE', ['PUBLIC']),
      choice('INTERACTION_PROFILE_ID', [`workspace:${hash(profile.interaction)}`]), choice('THEME_PROFILE_ID', [`theme:${hash(profile.theme)}`]),
      choice('AUTH_PROFILE_ID', [`auth:${hash(profile.authentication)}`])], bindingSlots, documentAudiences: ['PUBLIC'],
    admissionPrerequisites: ['APPROVED_POLICY', 'IMMUTABLE_ARTIFACTS'],
    limits: { ...topology, activeStagingTargets: 1, stagingExpiryHours: 24, maxExtensionHours: 24, recoveryWindowHours: 24,
      measuredPolicyRef: '010.49:starter-sizing-only:not-a-live-capacity-measurement' }, topology,
    approvedPolicyRef: '010.49:public-bootstrap-only', operatingPolicyRef: '010.49:customer-owned-bindings-before-apply',
    identityPolicyRef: '010.49:no-shared-customer-identity-provider', storagePolicyRef: '010.49:customer-owned-document-storage',
    workspaceProfiles: [profile], aiStateStorageTopologies: { MANAGED_POSTGRES: topology, CUSTOMER_POSTGRES: byo }, externalIntegrationProfiles,
  } }
}
