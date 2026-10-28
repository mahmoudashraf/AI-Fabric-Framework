import assert from 'node:assert/strict'
import { test } from 'node:test'
import { hash, PUBLIC_STARTERS, publicKnowledgeManifest, starterOffer, starterTemplate } from './public-starters.mjs'

const base = kind => ({ pluginId: PUBLIC_STARTERS[kind].pluginId, version: 'previous',
  compatibility: {}, contributions: { template: { security: {}, deploymentBehavior: {}, requiredPluginRefs: [],
    requiredPluginInstallDefaults: { 'mkp-data-dealership-knowledge-v1@1.0.0': { config: { publicContentApprovalConfirmed: true } } } } } })
const document = { pluginId: 'mkp-data-document-knowledge-s3', version: '1.1.0', compatibility: {},
  installForm: [{ id: 'documentStorageBindingRef', type: 'text' }], contributions: {
    entityConfig: { 'ai-entities': { document: { 'metadata-fields': [] } } },
    datasets: [{ documentPolicy: { allowedMetadataKeys: ['originalFilename'] } }],
    knowledgeSources: [{ filters: { datasetId: 'document-knowledge' } }] } }

function input() {
  const manifest = starterTemplate('knowledge', base('knowledge'))
  return { template: { id: 'version-knowledge', pluginId: manifest.pluginId, version: manifest.version, status: 'PUBLISHED', manifest },
    plugins: [{ id: 'version-data', pluginId: document.pluginId, version: '1.2.0', status: 'PUBLISHED',
      manifest: publicKnowledgeManifest(document), installForm: [] }],
    artifact: { id: 'runtime', serviceName: 'ai-fabric-runtime', artifactType: 'DOCKER_IMAGE', artifactRole: 'RUNTIME',
      promotedAt: '2026-10-28T00:00:00Z', imageDigest: `sha256:${'a'.repeat(64)}`, imageRepository: 'example/runtime',
      gitCommitSha: 'commit', capabilityManifest: {}, capabilityManifestHash: 'b'.repeat(64) },
    targets: ['staging', 'production'].map(environmentName => ({ id: environmentName, environmentName,
      providerType: 'COOLIFY', sourceStrategy: 'IMAGE_SOURCE', providerConfig: {}, networkPolicy: {}, resourceDefaults: {} })) }
}

test('only the two owner-selected public starters are defined', () => {
  assert.deepEqual(Object.keys(PUBLIC_STARTERS), ['dealership', 'knowledge'])
})
test('dealership defaults never pre-approve customer documents', () => {
  const original = base('dealership')
  const result = starterTemplate('dealership', original)
  assert.equal(result.contributions.template.requiredPluginInstallDefaults['mkp-data-dealership-knowledge-v1@1.0.0'].config.publicContentApprovalConfirmed, false)
  assert.equal(original.contributions.template.requiredPluginInstallDefaults['mkp-data-dealership-knowledge-v1@1.0.0'].config.publicContentApprovalConfirmed, true)
})
test('anonymous document retrieval requires the complete explicit publication contract', () => {
  const result = publicKnowledgeManifest(document)
  assert.equal(document.version, '1.1.0')
  assert.equal(result.contributions.knowledgeSources[0].filters.publicationStatus, 'PUBLIC_APPROVED')
  assert.ok(result.contributions.knowledgeSources[0].authModes.includes('PUBLIC_RUNTIME_ANONYMOUS'))
  assert.ok(result.contributions.datasets[0].documentPolicy.allowedMetadataKeys.includes('publicationStatus'))
  assert.equal(result.contributions.entityConfig['ai-entities'].document['metadata-fields'][0].required, true)
})
test('knowledge template uses external vectors and an unapproved symbolic document slot', () => {
  const result = starterTemplate('knowledge', base('knowledge')).contributions.template
  assert.equal(result.providerConfig.vectorProvisioningMode, 'EXTERNAL_EXISTING')
  assert.deepEqual(result.requiredPluginInstallDefaults['mkp-data-document-knowledge-s3@1.2.0'], {
    config: { documentStorageBindingRef: 'slot:document-storage', publicContentApprovalConfirmed: false }, secretRefs: {} })
})
test('new offers contain no canary account, identity provider, funding or customer storage', () => {
  const result = starterOffer('knowledge', input())
  assert.equal(result.offerId, PUBLIC_STARTERS.knowledge.offerId)
  assert.deepEqual(result.definition.workspaceProfiles[0].authentication,
    { profileCode: 'none', profileVersion: '1.0.0', interactionPolicy: 'PUBLIC', configuration: {} })
  assert.deepEqual(result.definition.bindingSlots.map(slot => slot.kind), ['CUSTOMER_S3', 'CUSTOMER_AI_STATE'])
  assert.deepEqual(Object.keys(result.definition.aiStateStorageTopologies).sort(), ['CUSTOMER_POSTGRES', 'MANAGED_POSTGRES'])
  assert.ok(!/Northfield|supabase|bucket|allowance|apiKey|password|INBOX_OPERATOR|BASIC_INBOX/.test(JSON.stringify(result)))
  assert.equal(result.definition.settings.find(setting => setting.setting === 'AUTH_PROFILE_ID').allowedValues[0],
    `auth:${hash(result.definition.workspaceProfiles[0].authentication)}`)
})
test('an unpublished dependency or unpromoted runtime cannot enter a public offer', () => {
  const value = input()
  value.plugins[0].status = 'DRAFT'
  assert.throws(() => starterOffer('knowledge', value))
  value.plugins[0].status = 'PUBLISHED'
  value.artifact.promotedAt = null
  assert.throws(() => starterOffer('knowledge', value))
})
test('incomplete target placement or plugin composition is rejected', () => {
  const value = input()
  value.targets.pop()
  assert.throws(() => starterOffer('knowledge', value))
  assert.throws(() => starterOffer('knowledge', { ...input(), plugins: [] }))
})
