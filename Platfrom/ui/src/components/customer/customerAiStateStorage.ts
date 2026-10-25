import type {
  AiStateStorageMode, CustomerAiStateStorageBindRequest, CustomerAiStateStorageSummary,
  CustomerDeploymentBootstrapRequest, CustomerDeploymentSummary, CustomerOfferSummary,
  DeploymentDraftResponse, DeploymentVersionSummary,
} from '../../api/platformApi'

export const aiStateStorageLabels: Record<AiStateStorageMode, string> = {
  MANAGED_POSTGRES: 'Managed AI-state PostgreSQL',
  CUSTOMER_POSTGRES: 'Customer-provided PostgreSQL',
}

export function aiStateStorageModes(offer: CustomerOfferSummary): AiStateStorageMode[] | null {
  const rules = offer.settings.filter((rule) => rule.setting === 'AI_STATE_STORAGE_MODE')
  if (rules.length === 0) return null
  const rule = rules[0]
  if (rules.length !== 1 || rule.type !== 'CHOICE' || !rule.allowedValues.length
    || rule.allowedValues.some((mode) => mode !== 'MANAGED_POSTGRES' && mode !== 'CUSTOMER_POSTGRES')
    || new Set(rule.allowedValues).size !== rule.allowedValues.length) {
    throw new Error('The reviewed AI-state storage choices are unavailable.')
  }
  return rule.allowedValues as AiStateStorageMode[]
}

export function storedAiStateStorageMode(offer: CustomerOfferSummary, draft: DeploymentDraftResponse) {
  const config = draft.providerConfig
  if (!config || typeof config !== 'object' || Array.isArray(config)) throw new Error('Storage configuration is unavailable.')
  const mode = (config as Record<string, unknown>).aiStateStorageMode
  const modes = aiStateStorageModes(offer)
  if (mode === undefined && modes === null) return undefined
  if ((mode !== 'MANAGED_POSTGRES' && mode !== 'CUSTOMER_POSTGRES') || (modes && !modes.includes(mode))) {
    throw new Error('The stored AI-state storage choice is not permitted by this offer.')
  }
  return mode
}

function ownedOffer(offer: CustomerOfferSummary, customerId: string) {
  if (!customerId || offer.entitlement.customerId !== customerId || offer.entitlement.status !== 'ACTIVE') {
    throw new Error('An active offer for this customer is required.')
  }
}

export function customerStorageBootstrapRequest(offer: CustomerOfferSummary, customerId: string,
  targetProfileId: string, name: string, mode: string, operationKey: string): CustomerDeploymentBootstrapRequest {
  ownedOffer(offer, customerId)
  if (!offer.targets.some((target) => target.id === targetProfileId && target.intent === 'CUSTOMER_STAGING')) {
    throw new Error('Select an entitled customer-staging target.')
  }
  const modes = aiStateStorageModes(offer)
  if (modes ? !modes.includes(mode as AiStateStorageMode) : mode !== '') {
    throw new Error('Select a reviewed AI-state storage choice.')
  }
  return { targetProfileId, name: name.trim(), expectedEntitlementRevision: offer.entitlement.revision,
    operationKey, ...(modes ? { aiStateStorageMode: mode as AiStateStorageMode } : {}) }
}

export async function publishStoredAiStateStorage<T>(
  api: { draft: (id: string) => Promise<DeploymentDraftResponse>; publish: (offerId: string, deploymentId: string,
    body: { draftId: string; targetProfileId: string; operationKey: string }) => Promise<T> },
  offer: CustomerOfferSummary, customerId: string, deployment: CustomerDeploymentSummary,
  expectedDraft: DeploymentDraftResponse, operationKey: string, stillCurrent: () => boolean,
) {
  ownedOffer(offer, customerId)
  if (deployment.offerId !== offer.id || deployment.offerFingerprint !== offer.fingerprint
    || expectedDraft.deploymentId !== deployment.deploymentId) throw new Error('The deployment storage scope changed.')
  const expectedMode = storedAiStateStorageMode(offer, expectedDraft)
  const current = await api.draft(deployment.deploymentId)
  if (current.deploymentId !== deployment.deploymentId || current.id !== expectedDraft.id
    || current.revisionNumber !== expectedDraft.revisionNumber || current.updatedAt !== expectedDraft.updatedAt
    || storedAiStateStorageMode(offer, current) !== expectedMode || !stillCurrent()) {
    throw new Error('Configuration changed. Refresh before publishing.')
  }
  // PublishRequest freezes the stored draft; its server contract has no storage-mode override.
  return api.publish(offer.id, deployment.deploymentId, { draftId: current.id,
    targetProfileId: deployment.bootstrapTargetProfileId, operationKey })
}

export function customerAiStateSlot(offer: CustomerOfferSummary) {
  const slots = offer.bindingSlots.filter((slot) => slot.kind === 'CUSTOMER_AI_STATE'
    && slot.supportedProfileIds.includes('CUSTOMER_POSTGRES'))
  return slots.length === 1 ? slots[0] : null
}

export type AiStateConnectionForm = Omit<CustomerAiStateStorageBindRequest['connection'], 'versionId'>
export type AiStateBindingScope = {
  customerId: string; offer: CustomerOfferSummary; deployment: CustomerDeploymentSummary
  version: DeploymentVersionSummary; targetProfileId: string
}

export function customerAiStateBindingScope(scope: AiStateBindingScope) {
  const { offer, customerId, deployment, version, targetProfileId } = scope
  ownedOffer(offer, customerId)
  const slot = customerAiStateSlot(offer)
  const target = offer.targets.find((item) => item.id === targetProfileId)
  if (!slot || deployment.offerId !== offer.id || deployment.offerFingerprint !== offer.fingerprint
    || version.deploymentId !== deployment.deploymentId || version.status !== 'PUBLISHED'
    || !target?.capabilities.includes('BINDING_REPLACE') || !target.capabilities.includes('READ')) {
    throw new Error('The reviewed customer PostgreSQL binding scope is unavailable.')
  }
  return { versionId: version.id, targetProfileId, bindingSlotId: slot.id }
}

export function customerAiStateBindingRequest(scope: AiStateBindingScope, form: AiStateConnectionForm,
  operationKey: string): CustomerAiStateStorageBindRequest {
  const selected = customerAiStateBindingScope(scope)
  const identifier = (value: string) => /^[a-z][a-z0-9_]{0,62}$/.test(value)
  const forbidden = (value: string) => value === 'public' || value === 'information_schema' || value.startsWith('pg_')
  if (!/^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$/i.test(form.host) || form.host.includes('..')
    || form.host.length > 253 || !Number.isInteger(form.port) || form.port < 1 || form.port > 65535
    || ![form.databaseName, form.runtimeSchema, form.connectorSchema, form.username].every(identifier)
    || form.runtimeSchema === form.connectorSchema || forbidden(form.runtimeSchema) || forbidden(form.connectorSchema)
    || !form.password || form.password !== form.password.trim() || form.password.length > 4096 || !form.migrationAuthorized) {
    throw new Error('Complete the PostgreSQL connection and authorize migrations to distinct dedicated AI schemas.')
  }
  return { expectedEntitlementRevision: scope.offer.entitlement.revision, operationKey,
    targetProfileId: selected.targetProfileId, bindingSlotId: selected.bindingSlotId,
    connection: { versionId: selected.versionId, host: form.host, port: form.port, databaseName: form.databaseName,
      runtimeSchema: form.runtimeSchema, connectorSchema: form.connectorSchema, username: form.username,
      password: form.password, migrationAuthorized: form.migrationAuthorized } }
}

export function checkedAiStateBinding(scope: AiStateBindingScope, binding: CustomerAiStateStorageSummary) {
  customerAiStateBindingScope(scope)
  if (binding.customerId !== scope.customerId || binding.tenantId !== scope.deployment.tenantId
    || binding.deploymentId !== scope.deployment.deploymentId || binding.targetProfileId !== scope.targetProfileId
    || binding.versionId !== scope.version.id || binding.configHash !== scope.version.configHash
    || binding.mode !== 'CUSTOMER_POSTGRES' || binding.lifecycle !== 'RETAIN_EXTERNAL' || !binding.migrationAuthorized) {
    throw new Error('The PostgreSQL readback does not match this deployment, target and version.')
  }
  return binding
}
