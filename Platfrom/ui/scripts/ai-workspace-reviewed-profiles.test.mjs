import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('../src/pages/aiWorkspaceReviewedProfiles.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const { resolveReviewedWorkspaceProfiles, reviewedWorkspaceRequest } = await import(
  `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`,
)

function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`
  if (value !== null && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`
  }
  return JSON.stringify(value)
}

function pin(kind, value) {
  return `${kind}:${createHash('sha256').update(canonical(value)).digest('hex')}`
}

function installation() {
  return {
    installationId: 'approved-workspace', customerId: 'customer-one', displayName: 'Support workspace',
    experiencePackCode: 'generic-workspace', experiencePackVersion: '1.2.0',
    connectionMode: 'backend-mediated-private-runtime', connectionProfileCode: 'reviewed-private',
    connectionProfileVersion: '2.0.0', connectionConfiguration: { integration: 'approved-support' },
    deliveryMode: 'standalone', interactionPolicy: 'login-required',
    authProfileCode: 'supabase', authProfileVersion: '1.0.0',
    authConfiguration: { projectUrl: 'https://auth.example.test', publishableKey: 'public-test', methods: ['email-password'] },
    configuration: {
      app: { assistantLabel: 'Support AI' },
      page: { rootSelector: '#support', maxChars: 2500 },
      toolGroups: [{ scope: 'default', label: 'Support', tools: [{ id: 'help', query: 'Help me' }] }],
      requestContext: { customContext: { '2': 'second', '10': 'tenth' } },
      presentation: { capabilities: { details: true } },
      theme: { primaryColor: '#176d61', darkMode: true },
    },
  }
}

function offerFor(item) {
  const { theme, ...configuration } = item.configuration
  const pins = [
    ['INTERACTION_PROFILE_ID', pin('workspace', {
      packCode: item.experiencePackCode, packVersion: item.experiencePackVersion,
      connectionMode: item.connectionMode.replaceAll('-', '_').toUpperCase(), connectionProfileCode: item.connectionProfileCode,
      connectionProfileVersion: item.connectionProfileVersion, deliveryMode: item.deliveryMode.replaceAll('-', '_').toUpperCase(),
      connectionConfiguration: item.connectionConfiguration, configuration,
    })],
    ['THEME_PROFILE_ID', pin('theme', theme ?? null)],
    ['AUTH_PROFILE_ID', pin('auth', {
      profileCode: item.authProfileCode, profileVersion: item.authProfileVersion,
      interactionPolicy: item.interactionPolicy.replaceAll('-', '_').toUpperCase(), configuration: item.authConfiguration,
    })],
  ]
  return {
    id: 'support-offer', entitlement: { customerId: 'customer-one', status: 'ACTIVE' },
    workspaceProfiles: [],
    settings: [
      ...pins.map(([setting, id]) => ({ setting, type: 'CHOICE', maxLength: 100, allowedValues: [id] })),
      { setting: 'APP_TITLE', type: 'TEXT', maxLength: 120, allowedValues: [] },
    ],
  }
}

function publishedProfile(item = installation()) {
  const { theme, ...configuration } = item.configuration
  return {
    id: 'support-standard', label: 'Reviewed support experience',
    interaction: {
      packCode: item.experiencePackCode, packVersion: item.experiencePackVersion,
      connectionMode: item.connectionMode.replaceAll('-', '_').toUpperCase(),
      connectionProfileCode: item.connectionProfileCode, connectionProfileVersion: item.connectionProfileVersion,
      deliveryMode: item.deliveryMode.replaceAll('-', '_').toUpperCase(),
      connectionConfiguration: structuredClone(item.connectionConfiguration), configuration: structuredClone(configuration),
    },
    theme: structuredClone(theme),
    authentication: {
      profileCode: item.authProfileCode, profileVersion: item.authProfileVersion,
      interactionPolicy: item.interactionPolicy.replaceAll('-', '_').toUpperCase(),
      configuration: structuredClone(item.authConfiguration),
    },
  }
}

function approvePublishedProfile(offer, definition) {
  offer.workspaceProfiles = [definition]
  for (const [setting, kind, payload] of [
    ['INTERACTION_PROFILE_ID', 'workspace', definition.interaction],
    ['THEME_PROFILE_ID', 'theme', definition.theme],
    ['AUTH_PROFILE_ID', 'auth', definition.authentication],
  ]) {
    const rule = offer.settings.find(item => item.setting === setting)
    rule.allowedValues = [...new Set([...rule.allowedValues, pin(kind, payload)])]
  }
  return offer
}

const input = {
  consumerId: 'support-customer', displayName: '  My support app  ', appSlug: 'support-app',
  customDomains: ['support.example.test'], allowedOrigins: [], rowVersion: 9,
}

test('matches the server structured pins without replacing optional configuration or versions', async () => {
  const original = installation()
  const before = structuredClone(original)
  const offer = offerFor(original)
  const profiles = await resolveReviewedWorkspaceProfiles(offer, 'customer-one', [original])
  assert.equal(profiles.length, 1)
  assert.equal(profiles[0].interactionProfileId, offer.settings[0].allowedValues[0])
  const request = reviewedWorkspaceRequest(offer, profiles[0], input)
  assert.deepEqual(request.configuration, original.configuration)
  assert.deepEqual(request.connectionConfiguration, original.connectionConfiguration)
  assert.deepEqual(request.authConfiguration, original.authConfiguration)
  assert.equal(request.experiencePackVersion, '1.2.0')
  assert.equal(request.connectionProfileVersion, '2.0.0')
  assert.equal(request.displayName, 'My support app')
  assert.equal(request.rowVersion, 9)
  assert.deepEqual(request.customDomains, ['support.example.test'])
  assert.deepEqual(original, before)
  request.configuration.page.maxChars = 500
  assert.equal(profiles[0].configuration.configuration.page.maxChars, 2500)
  assert.equal(original.configuration.page.maxChars, 2500)
})

test('rejects changed interaction, theme, and authentication even when labels and versions still match', async () => {
  const original = installation()
  const offer = offerFor(original)
  const changed = [structuredClone(original), structuredClone(original), structuredClone(original)]
  changed[0].configuration.page.maxChars = 1800
  changed[1].configuration.theme.darkMode = false
  changed[2].authConfiguration.methods.push('magic-link')
  assert.deepEqual(await resolveReviewedWorkspaceProfiles(offer, 'customer-one', changed), [])
})

test('never takes payloads or entitlements from a different customer', async () => {
  const original = installation()
  const offer = offerFor(original)
  assert.deepEqual(await resolveReviewedWorkspaceProfiles(offer, 'customer-one', [
    { ...original, customerId: 'customer-two' },
  ]), [])
  assert.deepEqual(await resolveReviewedWorkspaceProfiles(offer, 'customer-two', [original]), [])
  assert.deepEqual(await resolveReviewedWorkspaceProfiles({
    ...offer, entitlement: { ...offer.entitlement, status: 'REVOKED' },
  }, 'customer-one', [original]), [])
})

test('opaque IDs with no available payload do not produce invented defaults', async () => {
  assert.deepEqual(await resolveReviewedWorkspaceProfiles(offerFor(installation()), 'customer-one', []), [])
  const offer = offerFor(installation())
  offer.settings[0].type = 'TEXT'
  assert.deepEqual(await resolveReviewedWorkspaceProfiles(offer, 'customer-one', [installation()]), [])
})

test('preserves absent theme and extra approved fields rather than adding form defaults', async () => {
  const original = installation()
  delete original.configuration.theme
  const offer = offerFor(original)
  const [profile] = await resolveReviewedWorkspaceProfiles(offer, 'customer-one', [original])
  assert.ok(profile)
  const request = reviewedWorkspaceRequest(offer, profile, input)
  assert.equal('theme' in request.configuration, false)
  assert.deepEqual(request.configuration.requestContext, original.configuration.requestContext)
  assert.deepEqual(request.configuration.toolGroups, original.configuration.toolGroups)
  assert.equal('detailBasePath' in request.configuration.presentation, false)
})

test('rechecks published pins and title rules before producing a request', async () => {
  const original = installation()
  const offer = offerFor(original)
  const [profile] = await resolveReviewedWorkspaceProfiles(offer, 'customer-one', [original])
  const changed = structuredClone(offer)
  changed.settings[0].allowedValues = []
  assert.throws(() => reviewedWorkspaceRequest(changed, profile, input), /no longer included/)
  assert.throws(() => reviewedWorkspaceRequest(offer, profile, { ...input, displayName: ' ' }), /app name/)
  assert.throws(() => reviewedWorkspaceRequest(offer, profile, { ...input, displayName: 'a'.repeat(121) }), /app name/)
  const title = offer.settings.find(rule => rule.setting === 'APP_TITLE')
  title.type = 'CHOICE'
  title.allowedValues = ['Approved app']
  assert.throws(() => reviewedWorkspaceRequest(offer, profile, input), /app name/)
  assert.equal(reviewedWorkspaceRequest(offer, profile, { ...input, displayName: 'Approved app' }).displayName, 'Approved app')
})

test('creates the first app from published normalized profiles with no existing installations', async () => {
  const item = installation()
  item.connectionMode = 'public-runtime-anonymous'
  item.connectionProfileCode = 'runtime-anonymous-direct'
  item.connectionConfiguration = {}
  item.interactionPolicy = 'public-with-protected-capabilities'
  const definition = publishedProfile(item)
  const before = structuredClone(definition)
  const offer = approvePublishedProfile(offerFor(item), definition)
  const [choice] = await resolveReviewedWorkspaceProfiles(offer, 'customer-one', [])
  assert.ok(choice)
  assert.equal(choice.id, 'offer:support-standard')
  assert.equal(choice.label, definition.label)
  const request = reviewedWorkspaceRequest(offer, choice, { ...input, rowVersion: undefined })
  assert.equal(request.connectionMode, 'public-runtime-anonymous')
  assert.equal(request.deliveryMode, 'standalone')
  assert.equal(request.interactionPolicy, 'public-with-protected-capabilities')
  assert.equal(request.experiencePackVersion, item.experiencePackVersion)
  assert.equal(request.connectionProfileVersion, item.connectionProfileVersion)
  assert.deepEqual(request.configuration, item.configuration)
  assert.deepEqual(request.authConfiguration, item.authConfiguration)
  assert.equal('rowVersion' in request, false)
  request.configuration.toolGroups[0].label = 'Changed locally'
  assert.deepEqual(definition, before)
})

test('keeps owned choices alongside published choices without ID collisions', async () => {
  const item = installation()
  const definition = publishedProfile(item)
  definition.id = item.installationId
  const offer = approvePublishedProfile(offerFor(item), definition)
  const choices = await resolveReviewedWorkspaceProfiles(offer, 'customer-one', [item])
  assert.deepEqual(choices.map(choice => choice.id), [`offer:${item.installationId}`, item.installationId])
  assert.deepEqual(choices[0].configuration, choices[1].configuration)
})

test('accepts an explicit published null theme with the existing canonical no-theme pin', async () => {
  const definition = publishedProfile()
  definition.theme = null
  const offer = approvePublishedProfile(offerFor(installation()), definition)
  const [choice] = await resolveReviewedWorkspaceProfiles(offer, 'customer-one', [])
  assert.ok(choice)
  assert.equal(choice.themeProfileId, pin('theme', null))
  const request = reviewedWorkspaceRequest(offer, choice, input)
  assert.equal(request.configuration.theme, null)
  const { theme, ...configuration } = request.configuration
  assert.deepEqual(configuration, definition.interaction.configuration)
  assert.equal(definition.theme, null)
})

test('preserves canonical public authentication without rewriting its origin, key, or method list', async () => {
  const item = installation()
  item.connectionMode = 'public-runtime-authenticated'
  item.connectionProfileCode = 'runtime-authenticated-direct'
  item.connectionConfiguration = {}
  item.authConfiguration = {
    projectUrl: 'https://auth.example.test', publishableKey: 'sb_publishable_UI_FIXTURE',
    methods: ['email-password', 'magic-link'],
  }
  const definition = publishedProfile(item)
  const offer = approvePublishedProfile(offerFor(item), definition)
  const [choice] = await resolveReviewedWorkspaceProfiles(offer, 'customer-one', [])
  assert.ok(choice)
  const request = reviewedWorkspaceRequest(offer, choice, input)
  assert.deepEqual(request.connectionConfiguration, {})
  assert.equal(canonical(request.authConfiguration), canonical(definition.authentication.configuration))
  assert.equal(choice.authProfileId, pin('auth', {
    profileCode: request.authProfileCode, profileVersion: request.authProfileVersion,
    interactionPolicy: request.interactionPolicy.replaceAll('-', '_').toUpperCase(),
    configuration: request.authConfiguration,
  }))
})

test('direct runtime connections reject nonempty configuration even when approved pins match', async () => {
  for (const connectionMode of ['public-runtime-anonymous', 'public-runtime-authenticated']) {
    const item = installation()
    item.connectionMode = connectionMode
    item.connectionProfileCode = connectionMode === 'public-runtime-anonymous'
      ? 'runtime-anonymous-direct' : 'runtime-authenticated-direct'
    item.connectionProfileVersion = '1.0.0'
    item.interactionPolicy = connectionMode === 'public-runtime-anonymous'
      ? 'public-with-protected-capabilities' : 'login-required'
    item.connectionConfiguration = { ignored: 'must not be silently dropped by the registry' }
    const definition = publishedProfile(item)
    const offer = approvePublishedProfile(offerFor(item), definition)
    assert.deepEqual(await resolveReviewedWorkspaceProfiles(offer, 'customer-one', []), [])
    assert.deepEqual(await resolveReviewedWorkspaceProfiles({ ...offer, workspaceProfiles: [] }, 'customer-one', [item]), [])
  }
})

test('verifies each published pin independently, not the profile ID or label', async () => {
  const item = installation()
  const definition = publishedProfile(item)
  const offer = approvePublishedProfile(offerFor(item), definition)
  for (const mutate of [
    profile => { profile.interaction.configuration.page.maxChars = 1800 },
    profile => { profile.theme.darkMode = false },
    profile => { profile.authentication.configuration.methods.push('magic-link') },
  ]) {
    const changed = structuredClone(offer)
    mutate(changed.workspaceProfiles[0])
    assert.deepEqual(await resolveReviewedWorkspaceProfiles(changed, 'customer-one', []), [])
  }
})

test('rejects nested interaction themes, including null, even with matching approved pins', async () => {
  for (const nestedTheme of [null, { primaryColor: '#176d61', darkMode: true }]) {
    const definition = publishedProfile()
    definition.interaction.configuration.theme = nestedTheme
    const offer = approvePublishedProfile(offerFor(installation()), definition)
    assert.deepEqual(await resolveReviewedWorkspaceProfiles(offer, 'customer-one', []), [])
  }
})

test('rejects unsafe or malformed published payloads even if their hashes are listed', async () => {
  for (const mutate of [
    profile => { profile.interaction.consumerId = 'foreign-consumer' },
    profile => { profile.interaction.connectionMode = 'UNSUPPORTED_MODE' },
    profile => { delete profile.interaction.packVersion },
    profile => { profile.interaction.connectionConfiguration.apiKey = 'not-public' },
    profile => { profile.interaction.configuration.requestContext.client_secret = 'not-public' },
    profile => { profile.authentication.configuration.serviceRoleKey = 'not-public' },
    profile => { profile.authentication.configuration.publishableKey = 'sb_secret_not-public' },
    profile => { profile.authentication.configuration.methods = ['javascript:alert(1)'] },
    profile => { profile.theme = [] },
    profile => { profile.secret = 'not-public' },
    profile => { profile.label = { unsafe: 'not a label' } },
  ]) {
    const definition = publishedProfile()
    mutate(definition)
    const offer = approvePublishedProfile(offerFor(installation()), definition)
    assert.deepEqual(await resolveReviewedWorkspaceProfiles(offer, 'customer-one', []), [])
  }
})

test('does not allow extra request properties to overwrite reviewed payloads', async () => {
  const item = installation()
  const offer = approvePublishedProfile(offerFor(item), publishedProfile(item))
  const [choice] = await resolveReviewedWorkspaceProfiles(offer, 'customer-one', [])
  const request = reviewedWorkspaceRequest(offer, choice, {
    ...input, configuration: { unsafeOverride: true }, authConfiguration: { secret: 'not-public' },
    customerId: 'foreign-customer', experiencePackVersion: 'unreviewed',
  })
  assert.deepEqual(request.configuration, item.configuration)
  assert.deepEqual(request.authConfiguration, item.authConfiguration)
  assert.equal(request.experiencePackVersion, item.experiencePackVersion)
  assert.equal('customerId' in request, false)
})
