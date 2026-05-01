import type { PartnerApiClient } from '../auth/apiClient'
import { partnerProductControlSchema } from './schemas'

export interface UpdatePartnerWidgetSettingsPayload {
  launcherLabel: string
  welcomeMessage: string
  shellModeProfile: string
  debugEnabled: boolean
  enabledSurfaces: string[]
  defaultConversationMode: string
  allowedConversationModes: string[]
  pageModeMappings: Record<string, string>
}

export interface UpdatePartnerSourceSettingsPayload {
  productsEnabled: boolean
  collectionsEnabled: boolean
  pagesEnabled: boolean
  policiesEnabled: boolean
  articlesEnabled: boolean
  metaobjectsEnabled: boolean
}

export interface UpdatePartnerSupportProfilePayload {
  contactEmail: string | null
  contactUrl: string | null
  helpCenterUrl: string | null
  orderLookupPageUrl: string | null
  supportPolicyNote: string | null
}

export interface ActivatePartnerPackageTrialPayload {
  tierKey: string
  trialDays: number
  reason?: string | null
}

export interface DeactivatePartnerPackageTrialPayload {
  reason?: string | null
}

export function getProductControls(api: PartnerApiClient, storeId: string) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/product-controls`, partnerProductControlSchema)
}

export function updateProductWidgetSettings(
  api: PartnerApiClient,
  storeId: string,
  payload: UpdatePartnerWidgetSettingsPayload,
) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/product-controls/widget-settings`, partnerProductControlSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateProductSourceSettings(
  api: PartnerApiClient,
  storeId: string,
  payload: UpdatePartnerSourceSettingsPayload,
) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/product-controls/source-settings`, partnerProductControlSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateProductSupportProfile(
  api: PartnerApiClient,
  storeId: string,
  payload: UpdatePartnerSupportProfilePayload,
) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/product-controls/support-profile`, partnerProductControlSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function activatePackageTrial(
  api: PartnerApiClient,
  storeId: string,
  payload: ActivatePartnerPackageTrialPayload,
) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/package-trials`, partnerProductControlSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deactivatePackageTrial(
  api: PartnerApiClient,
  storeId: string,
  trialId: string,
  payload: DeactivatePartnerPackageTrialPayload,
) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/package-trials/${encodeURIComponent(trialId)}/deactivate`, partnerProductControlSchema, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
