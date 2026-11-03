import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

const read = name => JSON.parse(readFileSync(new URL(`../${name}`, import.meta.url), 'utf8'))
const canonical = value => Array.isArray(value) ? value.map(canonical) : value && typeof value === 'object'
  ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value
export const supportOfferHash = value => createHash('sha256').update(JSON.stringify(canonical(value))).digest('hex')

function noCredentials(value) {
  if (!value || typeof value !== 'object') return
  for (const [field, child] of Object.entries(value)) {
    assert.ok(!['apiKey', 'secretValue', 'password', 'privateKey', 'clientSecret', 'serviceRoleKey', 'accessToken',
      'authorization', 'openaiApiKey', 'funding', 'fundingGrant', 'customerId', 'ownerUserId'].includes(field),
    `Instance credentials, ownership and funding are not offer defaults: ${field}`)
    noCredentials(child)
  }
}

/** Direct external profile. Authentication is explicit owner input, never a bundled project or key. */
export function buildSupportWorkspaceProfile({ authentication, loginRequired = true } = {}) {
  assert.deepEqual(Object.keys(authentication ?? {}).sort(), ['methods', 'projectUrl', 'publishableKey'])
  const url = new URL(authentication.projectUrl)
  assert.ok(url.protocol === 'https:' && url.host === url.host.toLowerCase() && !url.username && !url.password
    && !url.search && !url.hash && url.pathname === '/' && authentication.projectUrl === url.origin,
  'Supply the customer-owned exact HTTPS Supabase project origin')
  const key = authentication.publishableKey
  let publicKey = typeof key === 'string' && key.startsWith('sb_publishable_') && key.length > 15
  if (!publicKey && typeof key === 'string' && key.split('.').length === 3) {
    try { publicKey = JSON.parse(Buffer.from(key.split('.')[1], 'base64url').toString('utf8')).role === 'anon' }
    catch { publicKey = false }
  }
  assert.ok(publicKey && key.length <= 2048 && !/\s/.test(key), 'Only a supported public publishable/anon key is permitted')
  assert.ok(Array.isArray(authentication.methods) && authentication.methods.length > 0 && authentication.methods.length <= 2
    && new Set(authentication.methods).size === authentication.methods.length
    && authentication.methods.every(method => ['email-password', 'magic-link'].includes(method)))
  assert.equal(typeof loginRequired, 'boolean')
  const profile = read('fixtures/support-workspace-profile.defaults.json')
  const configuration = read('fixtures/support-experience-config.json')
  profile.theme = configuration.theme
  delete configuration.theme
  profile.interaction.configuration = configuration
  if (!loginRequired) {
    profile.interaction.connectionMode = 'PUBLIC_RUNTIME_ANONYMOUS'
    profile.interaction.connectionProfileCode = 'runtime-anonymous-direct'
  }
  profile.authentication = { profileCode: 'supabase', profileVersion: '1.0.0',
    interactionPolicy: loginRequired ? 'LOGIN_REQUIRED' : 'PUBLIC_WITH_PROTECTED_CAPABILITIES',
    configuration: structuredClone(authentication) }
  return profile
}

function publishedPin(version) {
  assert.ok(version?.id && version.status === 'PUBLISHED' && Number.isFinite(Date.parse(version.publishedAt)),
    'Supply the actual published Marketplace version summary')
  assert.equal(version.manifest?.pluginId, version.pluginId)
  assert.equal(version.manifest?.version, version.version)
  return { pluginId: version.pluginId, versionId: version.id, manifestSha256: supportOfferHash(version.manifest) }
}

const reviewFields = ['name', 'operations', 'settings', 'bindingSlots', 'documentAudiences', 'admissionPrerequisites',
  'limits', 'topology', 'approvedPolicyRef', 'operatingPolicyRef', 'identityPolicyRef', 'storagePolicyRef',
  'aiStateStorageTopologies', 'externalIntegrationProfiles']

/** Pure PublishOfferRequest composer. Existing publication/admission validators remain authoritative. */
export function buildSupportOffer({ offerId, reviewReference, template, pluginVersions, artifacts, targets, review,
  workspaceProfile, authentication, loginRequired } = {}) {
  assert.ok(typeof offerId === 'string' && offerId.length > 0 && typeof reviewReference === 'string' && reviewReference.length > 0)
  assert.equal(template?.pluginId, 'mkp-template-ai-support-workspace')
  assert.equal(template.version, '1.1.0')
  const templatePin = publishedPin(template)
  assert.equal(template.manifest.pluginType, 'TEMPLATE')
  assert.deepEqual(Object.keys(review ?? {}).sort(), [...reviewFields].sort(), 'Supply the explicit reviewed offer policy, topology and limits')
  noCredentials(review)
  assert.ok(Array.isArray(pluginVersions) && Array.isArray(artifacts) && artifacts.length > 0 && Array.isArray(targets) && targets.length > 0)
  const runtimeArtifacts = artifacts.filter(artifact => artifact.artifactRole === 'RUNTIME')
  assert.ok(runtimeArtifacts.length > 0, 'Supply the actual selected runtime artifacts')
  const requiredRuntimeCapabilities = template.manifest.contributions.template.deploymentBehavior.requiredRuntimeCapabilityIds ?? []
  for (const artifact of runtimeArtifacts) {
    assert.ok(Array.isArray(artifact.capabilityManifest?.capabilities), 'Supply the registered runtime capability array')
    assert.ok(requiredRuntimeCapabilities.every(capability => artifact.capabilityManifest?.capabilities?.includes(capability)),
      'Every offered runtime must advertise the template runtime capabilities, including standalone-conversation-control')
  }
  const plugins = pluginVersions.map(publishedPin)
  assert.equal(new Set(plugins.map(pin => pin.pluginId)).size, plugins.length)
  for (const reference of template.manifest.contributions.template.requiredPluginRefs ?? []) {
    assert.ok(pluginVersions.some(version => `${version.pluginId}@${version.version}` === reference), `Missing required published plugin ${reference}`)
  }
  const profile = workspaceProfile ? structuredClone(workspaceProfile) : buildSupportWorkspaceProfile({ authentication, loginRequired })
  assert.equal(profile.interaction?.packCode, 'support')
  assert.equal(profile.interaction?.packVersion, '1.0.0')
  assert.ok(!profile.interaction.configuration?.support?.spaceId && !profile.interaction.configuration?.support?.ticketProviderBindingId,
    'Managed support references must remain unbound in offer defaults')
  noCredentials(profile)
  const settings = review.settings.filter(rule => !['INTERACTION_PROFILE_ID', 'THEME_PROFILE_ID', 'AUTH_PROFILE_ID'].includes(rule.setting))
  for (const [setting, prefix, payload] of [['INTERACTION_PROFILE_ID', 'workspace', profile.interaction],
    ['THEME_PROFILE_ID', 'theme', profile.theme], ['AUTH_PROFILE_ID', 'auth', profile.authentication]]) {
    settings.push({ setting, type: 'CHOICE', maxLength: 100, allowedValues: [`${prefix}:${supportOfferHash(payload)}`] })
  }
  const definition = { schemaVersion: '1', ...structuredClone(review), templateId: template.manifest.contributions.template.templateId,
    template: templatePin, plugins,
    artifacts: artifacts.map(artifact => {
      assert.ok(artifact?.id && artifact.capabilityManifest && /^sha256:[a-f0-9]{64}$/.test(artifact.imageDigest), 'Supply immutable actual artifact metadata')
      return { artifactId: artifact.id, fingerprint: supportOfferHash({ id: artifact.id, serviceName: artifact.serviceName,
        artifactType: artifact.artifactType, artifactRole: artifact.artifactRole ?? '', imageRepository: artifact.imageRepository ?? '',
        imageDigest: artifact.imageDigest, gitCommitSha: artifact.gitCommitSha ?? '', capabilityManifest: artifact.capabilityManifest,
        capabilityManifestHash: artifact.capabilityManifestHash ?? '' }) }
    }),
    targets: targets.map(({ profile: target, intent }) => {
      assert.ok(target?.active && ['CUSTOMER_STAGING', 'CUSTOMER_PRODUCTION'].includes(intent), 'Supply the actual active target and explicit intent')
      assert.ok(target.id && target.providerConfig && target.networkPolicy && target.resourceDefaults)
      return { targetProfileId: target.id, intent, profileFingerprint: supportOfferHash({ id: target.id, provider: target.providerType,
        environment: target.environmentName, sourceStrategy: target.sourceStrategy, credentialReference: target.credentialRefId ?? '',
        providerConfig: target.providerConfig, networkPolicy: target.networkPolicy, resourceDefaults: target.resourceDefaults }) }
    }), settings, workspaceProfiles: [profile] }
  return { offerId, reviewReference, definition }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  assert.equal(process.argv.length, 3, 'Usage: node build-support-offer.mjs <reviewed-input.json>; offline only')
  console.log(JSON.stringify(buildSupportOffer(JSON.parse(readFileSync(process.argv[2], 'utf8'))), null, 2))
}
