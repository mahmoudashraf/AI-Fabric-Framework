import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { buildSupportOffer, buildSupportWorkspaceProfile, supportOfferHash } from './build-support-offer.mjs'

const read = name => JSON.parse(readFileSync(new URL(`../${name}`, import.meta.url), 'utf8'))
const auth = { projectUrl: 'https://customer-auth.example.test', publishableKey: 'sb_publishable_customer_example_only', methods: ['magic-link'] }
const published = manifest => ({ id: `published-${manifest.pluginId}`, pluginId: manifest.pluginId,
  version: manifest.version, status: 'PUBLISHED', publishedAt: '2026-11-03T12:00:00Z', manifest })
const topology = { replicas: 1, cpuMillis: 1500, memoryMiB: 2048, diskMiB: 2048, databases: 1, indexes: 1, dependencies: 0, concurrentApplies: 1 }
function inputs() {
  return structuredClone({ offerId: 'reviewed-offer-example', reviewReference: 'source-only-review-example',
    template: published(read('manifests/ai-support-workspace.1.1.0.manifest.json')),
    pluginVersions: [published({ pluginId: 'mkp-data-ai-support-documents', version: '1.0.0' }),
      published(read('manifests/support-tickets.1.0.0.manifest.json'))],
    artifacts: [{ id: 'reviewed-artifact-example', serviceName: 'ai-fabric-runtime', artifactType: 'DOCKER_IMAGE', artifactRole: 'RUNTIME',
      imageRepository: 'registry.example.test/runtime', imageDigest: `sha256:${'a'.repeat(64)}`,
      capabilityManifest: { capabilities: ['ai-fabric-core', 'ai-fabric-chat-session', 'standalone-conversation-control'] } }],
    targets: [{ profile: { id: 'reviewed-target-example', active: true, environmentName: 'staging', providerType: 'COOLIFY',
      sourceStrategy: 'IMAGE_SOURCE', providerConfig: {}, networkPolicy: {}, resourceDefaults: {} }, intent: 'CUSTOMER_STAGING' }],
    authentication: auth,
    review: { name: 'Support candidate', operations: ['BOOTSTRAP', 'APP_CONFIGURE', 'AUTH_CONFIGURE'], settings: [],
      bindingSlots: [], documentAudiences: ['AUTHENTICATED'], admissionPrerequisites: ['IDENTITY', 'STORAGE'],
      limits: { measuredPolicyRef: 'explicit-review', activeStagingTargets: 1, ...topology, stagingExpiryHours: 24,
        maxExtensionHours: 24, recoveryWindowHours: 24 }, topology, aiStateStorageTopologies: { MANAGED_POSTGRES: topology },
      externalIntegrationProfiles: [], approvedPolicyRef: 'explicit-approved-review', operatingPolicyRef: 'explicit-operating-review',
      identityPolicyRef: 'explicit-identity-review', storagePolicyRef: 'explicit-storage-review' } })
}

test('external source defaults carry support pack identity but no customer project, credentials or managed IDs', () => {
  const defaults = read('fixtures/support-workspace-profile.defaults.json')
  assert.equal(defaults.interaction.packCode, 'support')
  assert.equal(defaults.interaction.packVersion, '1.0.0')
  assert.equal(defaults.authentication, undefined)
  assert.throws(() => buildSupportWorkspaceProfile(), /Assertion/)
  const profile = buildSupportWorkspaceProfile({ authentication: auth })
  assert.equal(profile.authentication.interactionPolicy, 'LOGIN_REQUIRED')
  assert.equal(profile.interaction.connectionProfileCode, 'runtime-authenticated-broker')
  assert.equal(profile.interaction.configuration.capabilities.tickets, false)
  assert.equal(profile.interaction.configuration.capabilities.handover, false)
  assert.equal(profile.interaction.configuration.support.spaceId, undefined)
  assert.equal(profile.interaction.configuration.theme, undefined)
  assert.equal(profile.authentication.configuration.projectUrl, auth.projectUrl)
  assert.equal(buildSupportWorkspaceProfile({ authentication: auth, loginRequired: false }).authentication.interactionPolicy,
    'PUBLIC_WITH_PROTECTED_CAPABILITIES')
})

test('builder uses actual supplied published pins and reviewed target/artifact metadata without mutation or side effects', () => {
  const input = inputs(); const before = structuredClone(input)
  const request = buildSupportOffer(input)
  assert.deepEqual(input, before)
  assert.deepEqual(request.definition.template, { pluginId: input.template.pluginId, versionId: input.template.id,
    manifestSha256: supportOfferHash(input.template.manifest) })
  assert.equal(request.definition.templateId, 'custom-start-from-scratch')
  assert.deepEqual(request.definition.limits, input.review.limits)
  assert.deepEqual(request.definition.topology, input.review.topology)
  assert.equal(request.definition.targets[0].targetProfileId, input.targets[0].profile.id)
  assert.equal(request.definition.artifacts[0].artifactId, input.artifacts[0].id)
  const profile = request.definition.workspaceProfiles[0]
  for (const [setting, prefix, value] of [['INTERACTION_PROFILE_ID', 'workspace', profile.interaction],
    ['THEME_PROFILE_ID', 'theme', profile.theme], ['AUTH_PROFILE_ID', 'auth', profile.authentication]])
    assert.deepEqual(request.definition.settings.find(item => item.setting === setting).allowedValues, [`${prefix}:${supportOfferHash(value)}`])
  assert.doesNotMatch(JSON.stringify(request), /SUPPORT_CASE_HOST|platform-support-case-host|canary|funding|diagnostics|s3:\/\//)
})

test('unpublished or mismatched versions and missing required data plugins are not invented', () => {
  for (const mutate of [input => { input.template.status = 'DRAFT' }, input => { input.template.version = '1.2.0' },
    input => { input.pluginVersions = [] }, input => { input.template.manifest.version = 'other' }]) {
    const input = inputs(); mutate(input); assert.throws(() => buildSupportOffer(input))
  }
})

test('older canonical and handover capabilities cannot qualify a fresh support offer', () => {
  const input = inputs()
  input.artifacts[0].capabilityManifest.capabilities = ['ai-fabric-core', 'ai-fabric-chat-session',
    'conversation-control', 'canonical-conversation-orchestration-state']
  assert.throws(() => buildSupportOffer(input), /standalone-conversation-control/)
  input.pluginVersions = input.pluginVersions.filter(version => version.pluginId !== 'mkp-action-support-tickets')
  input.artifacts[0].capabilityManifest.capabilities.push('standalone-conversation-control')
  assert.throws(() => buildSupportOffer(input), /Missing required published plugin mkp-action-support-tickets/)
})

test('instance ownership, funding and secret values cannot become reviewed public defaults', () => {
  for (const field of ['customerId', 'ownerUserId', 'funding', 'apiKey', 'secretValue', 'openaiApiKey']) {
    const input = inputs(); input.review.topology[field] = 'private-instance-value'
    assert.throws(() => buildSupportOffer(input), /not offer defaults/)
  }
  const input = inputs(); input.workspaceProfile = buildSupportWorkspaceProfile({ authentication: auth })
  input.workspaceProfile.interaction.configuration.support.spaceId = 'existing-private-space'
  assert.throws(() => buildSupportOffer(input), /remain unbound/)
})

test('only public supported authentication keys and exact customer origins are accepted', () => {
  for (const publishableKey of ['sb_secret_private', 'service_role', 'not-a-public-key'])
    assert.throws(() => buildSupportWorkspaceProfile({ authentication: { ...auth, publishableKey } }))
  for (const projectUrl of ['http://auth.example.test', 'https://user:password@auth.example.test', 'https://auth.example.test/path'])
    assert.throws(() => buildSupportWorkspaceProfile({ authentication: { ...auth, projectUrl } }))
})

test('LoomAI reviewed instance requires its explicit private host profile and all five published pins', () => {
  const input = inputs()
  input.template = published(read('manifests/loomai-support-workspace.1.1.0.manifest.json'))
  input.pluginVersions = input.template.manifest.contributions.template.requiredPluginRefs.map(reference => {
    const [pluginId, version] = reference.split('@')
    return published({ pluginId, version })
  })
  assert.throws(() => buildSupportOffer(input), /explicitly reviewed private LoomAI workspace profile/)
  const configuration = read('fixtures/support-experience-config.json')
  const theme = configuration.theme; delete configuration.theme
  input.workspaceProfile = { id: 'reviewed-console-support', label: 'LoomAI Console support', theme,
    interaction: { packCode: 'support', packVersion: '1.0.0', connectionMode: 'BACKEND_MEDIATED_PRIVATE_RUNTIME',
      connectionProfileCode: 'platform-support-case-host', connectionProfileVersion: '1.0.0',
      connectionConfiguration: {}, deliveryMode: 'EMBEDDED', configuration },
    authentication: { profileCode: 'none', profileVersion: '1.0.0', interactionPolicy: 'PUBLIC', configuration: {} } }
  const before = structuredClone(input)
  const result = buildSupportOffer(input)
  assert.deepEqual(input, before)
  assert.equal(result.definition.template.pluginId, 'mkp-template-loomai-support-workspace')
  assert.equal(result.definition.plugins.length, 5)
  assert.deepEqual(result.definition.workspaceProfiles, [input.workspaceProfile])
  assert.equal(result.definition.workspaceProfiles[0].authentication.profileCode, 'none')
  const missing = structuredClone(input); missing.pluginVersions.pop()
  assert.throws(() => buildSupportOffer(missing), /Missing required published plugin/)
  const external = structuredClone(input); external.workspaceProfile = buildSupportWorkspaceProfile({ authentication: auth })
  assert.throws(() => buildSupportOffer(external), /BACKEND_MEDIATED_PRIVATE_RUNTIME/)
})

test('offer pins remain an exact bootstrap set rather than an allowed optional-plugin superset', () => {
  const input = inputs()
  input.pluginVersions.push(published(read('manifests/loomai-support-diagnostics.1.0.3.manifest.json')))
  assert.throws(() => buildSupportOffer(input), /exactly match the reviewed template requiredPluginRefs/)
})
