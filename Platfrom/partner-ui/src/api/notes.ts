import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import { partnerStoreNoteSchema } from './schemas'

export function listStoreNotes(api: PartnerApiClient, storeId: string) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/notes`, z.array(partnerStoreNoteSchema))
}

export function createStoreNote(api: PartnerApiClient, storeId: string, bodyMarkdown: string) {
  return api.request(`/api/partners/stores/${encodeURIComponent(storeId)}/notes`, partnerStoreNoteSchema, {
    method: 'POST',
    body: JSON.stringify({ bodyMarkdown }),
  })
}
