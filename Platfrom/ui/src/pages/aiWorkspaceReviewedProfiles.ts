import type {
  AIWorkspaceInstallationSummary,
  CustomerOfferSummary,
  SaveAIWorkspaceInstallationRequest,
} from '../api/platformApi'

export type ReviewedWorkspaceProfile = {
  id: string
  label: string
  interactionProfileId: string
  themeProfileId: string
  authProfileId: string
  configuration: Pick<SaveAIWorkspaceInstallationRequest,
    'experiencePackCode' | 'experiencePackVersion' | 'connectionMode'
    | 'connectionProfileCode' | 'connectionProfileVersion' | 'connectionConfiguration'
    | 'configuration' | 'deliveryMode' | 'interactionPolicy' | 'authProfileCode'
    | 'authProfileVersion' | 'authConfiguration'>
}

function canonicalJson(value: unknown): string {
  if (value === null) return 'null'
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    return `{${Object.keys(record).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(record[key])}`).join(',')}}`
  }
  if (typeof value === 'string' || typeof value === 'boolean'
    || (typeof value === 'number' && Number.isFinite(value))) return JSON.stringify(value)
  throw new Error('The reviewed profile contains a value that cannot be represented as JSON.')
}

async function profileId(kind: string, value: unknown): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonicalJson(value)))
  return `${kind}:${Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')}`
}

function enumValue(value: string): string {
  return value.replace(/-/g, '_').toUpperCase()
}

function allowedProfile(offer: CustomerOfferSummary, setting: string, id: string): boolean {
  const rule = offer.settings.find((item) => item.setting === setting)
  return rule?.type === 'CHOICE' && id.length <= rule.maxLength && rule.allowedValues.includes(id)
}

const connectionModes: Record<string, string> = {
  PUBLIC_RUNTIME_ANONYMOUS: 'public-runtime-anonymous',
  PUBLIC_RUNTIME_AUTHENTICATED: 'public-runtime-authenticated',
  BACKEND_MEDIATED_PRIVATE_RUNTIME: 'backend-mediated-private-runtime',
}
const deliveryModes: Record<string, string> = { STANDALONE: 'standalone', EMBEDDED: 'embedded' }
const interactionPolicies: Record<string, string> = {
  PUBLIC: 'public', LOGIN_REQUIRED: 'login-required',
  PUBLIC_WITH_PROTECTED_CAPABILITIES: 'public-with-protected-capabilities',
}
const interactionFields = ['packCode', 'packVersion', 'connectionMode', 'connectionProfileCode',
  'connectionProfileVersion', 'deliveryMode', 'connectionConfiguration', 'configuration']
const authenticationFields = ['profileCode', 'profileVersion', 'interactionPolicy', 'configuration']
const unsafeFields = ['secret', 'password', 'apikey', 'authorization', 'credential', 'servicerole',
  'privatekey', 'accesstoken', 'refreshtoken', 'javascript', 'scripturl', 'runtimeurl', 'brokerurl', 'adapterurl']

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    && Object.getPrototypeOf(value) === Object.prototype
}

function exactFields(value: Record<string, unknown>, fields: string[]): boolean {
  return Object.keys(value).length === fields.length
    && fields.every((field) => Object.prototype.hasOwnProperty.call(value, field))
}

function text(value: unknown, limit = 128): value is string {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= limit
}

function publicJson(value: unknown): boolean {
  function inspect(node: unknown, depth: number): boolean {
    if (depth > 16) return false
    if (Array.isArray(node)) return node.every((item) => inspect(item, depth + 1))
    if (record(node)) return Object.entries(node).every(([key, item]) => {
      const normalized = key.toLowerCase().replace(/[-_]/g, '')
      return !['__proto__', 'prototype', 'constructor'].includes(key)
        && !unsafeFields.some((fragment) => normalized.includes(fragment)) && inspect(item, depth + 1)
    })
    if (typeof node === 'string') {
      const normalized = node.trim().toLowerCase()
      return !normalized.startsWith('javascript:') && !normalized.startsWith('data:text/html')
        && !normalized.includes('<script') && !normalized.startsWith('sb_secret_')
    }
    return node === null || typeof node === 'boolean' || (typeof node === 'number' && Number.isFinite(node))
  }
  return inspect(value, 0) && canonicalJson(value).length <= 32_000
}

async function resolveProfile(
  offer: CustomerOfferSummary, id: string, label: string,
  interaction: unknown, theme: unknown, authentication: unknown, includeTheme = true,
): Promise<ReviewedWorkspaceProfile | null> {
  if (!record(interaction) || !exactFields(interaction, interactionFields)
    || !record(authentication) || !exactFields(authentication, authenticationFields)
    || !record(interaction.configuration) || 'theme' in interaction.configuration
    || !record(interaction.connectionConfiguration) || !record(authentication.configuration)
    || !(record(theme) || theme === null)
    || !['packCode', 'packVersion', 'connectionProfileCode', 'connectionProfileVersion']
      .every((key) => text(interaction[key]))
    || !text(authentication.profileCode) || !text(authentication.profileVersion)
    || !publicJson(interaction) || !publicJson(theme) || !publicJson(authentication)) return null
  const connectionMode = typeof interaction.connectionMode === 'string' ? connectionModes[interaction.connectionMode] : null
  const deliveryMode = typeof interaction.deliveryMode === 'string' ? deliveryModes[interaction.deliveryMode] : null
  const interactionPolicy = typeof authentication.interactionPolicy === 'string'
    ? interactionPolicies[authentication.interactionPolicy] : null
  if (typeof connectionMode !== 'string' || typeof deliveryMode !== 'string' || typeof interactionPolicy !== 'string') return null
  if (connectionMode !== 'backend-mediated-private-runtime'
    && Object.keys(interaction.connectionConfiguration).length !== 0) return null
  const interactionProfileId = await profileId('workspace', interaction)
  const themeProfileId = await profileId('theme', theme)
  const authProfileId = await profileId('auth', authentication)
  if (!allowedProfile(offer, 'INTERACTION_PROFILE_ID', interactionProfileId)
    || !allowedProfile(offer, 'THEME_PROFILE_ID', themeProfileId)
    || !allowedProfile(offer, 'AUTH_PROFILE_ID', authProfileId)) return null
  return {
    id, label, interactionProfileId, themeProfileId, authProfileId,
    configuration: structuredClone({
      experiencePackCode: interaction.packCode as string,
      experiencePackVersion: interaction.packVersion as string,
      connectionMode,
      connectionProfileCode: interaction.connectionProfileCode as string,
      connectionProfileVersion: interaction.connectionProfileVersion as string,
      connectionConfiguration: interaction.connectionConfiguration,
      configuration: { ...interaction.configuration, ...(includeTheme ? { theme } : {}) },
      deliveryMode,
      interactionPolicy,
      authProfileCode: authentication.profileCode,
      authProfileVersion: authentication.profileVersion,
      authConfiguration: authentication.configuration,
    }),
  }
}

/** Both published and owned profiles must match all three pins before becoming choices. */
export async function resolveReviewedWorkspaceProfiles(
  offer: CustomerOfferSummary,
  customerId: string,
  installations: AIWorkspaceInstallationSummary[],
): Promise<ReviewedWorkspaceProfile[]> {
  if (offer.entitlement.customerId !== customerId || offer.entitlement.status !== 'ACTIVE') return []
  const choices: ReviewedWorkspaceProfile[] = []
  for (const profile of offer.workspaceProfiles ?? []) {
    if (!record(profile) || !exactFields(profile, ['id', 'label', 'interaction', 'theme', 'authentication'])
      || !text(profile.id) || !text(profile.label, 200) || !(record(profile.theme) || profile.theme === null)) continue
    const id = `offer:${profile.id}`
    if (choices.some((choice) => choice.id === id)) continue
    const choice = await resolveProfile(offer, id, profile.label, profile.interaction, profile.theme, profile.authentication)
    if (choice) choices.push(choice)
  }
  for (const installation of installations) {
    if (installation.customerId !== customerId) continue
    const { theme, ...interactionConfiguration } = installation.configuration
    const choice = await resolveProfile(offer, installation.installationId, installation.displayName, {
      packCode: installation.experiencePackCode,
      packVersion: installation.experiencePackVersion,
      connectionMode: enumValue(installation.connectionMode),
      connectionProfileCode: installation.connectionProfileCode,
      connectionProfileVersion: installation.connectionProfileVersion,
      deliveryMode: enumValue(installation.deliveryMode),
      connectionConfiguration: installation.connectionConfiguration,
      configuration: interactionConfiguration,
    }, theme ?? null, {
      profileCode: installation.authProfileCode,
      profileVersion: installation.authProfileVersion,
      interactionPolicy: enumValue(installation.interactionPolicy),
      configuration: installation.authConfiguration,
    }, Object.prototype.hasOwnProperty.call(installation.configuration, 'theme'))
    if (choice && !choices.some((item) => item.id === choice.id)) choices.push(choice)
  }
  return choices
}

export function reviewedWorkspaceRequest(
  offer: CustomerOfferSummary,
  profile: ReviewedWorkspaceProfile,
  input: Pick<SaveAIWorkspaceInstallationRequest,
    'consumerId' | 'displayName' | 'appSlug' | 'customDomains' | 'allowedOrigins' | 'rowVersion'>,
): SaveAIWorkspaceInstallationRequest {
  if (!allowedProfile(offer, 'INTERACTION_PROFILE_ID', profile.interactionProfileId)
    || !allowedProfile(offer, 'THEME_PROFILE_ID', profile.themeProfileId)
    || !allowedProfile(offer, 'AUTH_PROFILE_ID', profile.authProfileId)) {
    throw new Error('This configuration is no longer included in the reviewed product offer.')
  }
  const title = offer.settings.find((item) => item.setting === 'APP_TITLE')
  const displayName = input.displayName.trim()
  if (!title || !displayName || displayName.length > title.maxLength
    || (title.type === 'CHOICE' && !title.allowedValues.includes(displayName))) {
    throw new Error('The app name is not allowed by the reviewed product offer.')
  }
  return {
    ...structuredClone(profile.configuration),
    consumerId: input.consumerId,
    displayName,
    appSlug: input.appSlug,
    customDomains: [...input.customDomains],
    allowedOrigins: [...input.allowedOrigins],
    ...(input.rowVersion == null ? {} : { rowVersion: input.rowVersion }),
  }
}
