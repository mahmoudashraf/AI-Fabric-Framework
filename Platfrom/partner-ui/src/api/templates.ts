import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import { partnerTemplateApplicationSchema, partnerTemplateSchema } from './schemas'

export function listTemplates(api: PartnerApiClient) {
  return api.request('/api/partners/templates', z.array(partnerTemplateSchema))
}

export function getTemplate(api: PartnerApiClient, templateId: string) {
  return api.request(`/api/partners/templates/${encodeURIComponent(templateId)}`, partnerTemplateSchema)
}

export function applyTemplate(api: PartnerApiClient, templateId: string, storeId?: string) {
  return api.request(`/api/partners/templates/${encodeURIComponent(templateId)}/applications`, partnerTemplateApplicationSchema, {
    method: 'POST',
    body: JSON.stringify({ storeId }),
  })
}

export function listTemplateApplications(api: PartnerApiClient) {
  return api.request('/api/partners/template-applications', z.array(partnerTemplateApplicationSchema))
}
