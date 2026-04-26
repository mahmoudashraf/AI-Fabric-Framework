import { z } from 'zod'
import type { PartnerApiClient } from '../auth/apiClient'
import { partnerRuntimeConfig } from '../config/runtimeConfig'

export type PartnerMaxWidgetAuthPath = 'PLATFORM_PRIVATE' | 'PUBLIC_AUTHENTICATED' | 'PUBLIC_ANONYMOUS'

export const PARTNER_MAX_WIDGET_AUTH_PATHS: Array<{
  value: PartnerMaxWidgetAuthPath
  label: string
  detail: string
}> = [
  {
    value: 'PLATFORM_PRIVATE',
    label: 'Partner-secured',
    detail: 'Platform validates this assigned store without exposing private service credentials.',
  },
  {
    value: 'PUBLIC_AUTHENTICATED',
    label: 'Authenticated shopper',
    detail: 'Tests the public storefront path for a signed-in shopper session.',
  },
  {
    value: 'PUBLIC_ANONYMOUS',
    label: 'Anonymous shopper',
    detail: 'Tests the public storefront path for an anonymous shopper session.',
  },
]

const partnerMaxWidgetAnyResponseSchema = z.object({}).passthrough()

export const partnerMaxWidgetRuntimeAuthContextSchema = z.object({
  authMode: z.string().nullable().optional(),
  subjectType: z.string().nullable().optional(),
  subjectId: z.string().nullable().optional(),
  callerType: z.string().nullable().optional(),
  sessionId: z.string().nullable().optional(),
  deploymentId: z.string().nullable().optional(),
  customerId: z.string().nullable().optional(),
  tenantId: z.string().nullable().optional(),
  issuer: z.string().nullable().optional(),
  expiresAt: z.string().nullable().optional(),
  grantedScopes: z.array(z.string()).optional().default([]),
  warnings: z.array(z.string()).optional().default([]),
}).passthrough()

export type PartnerMaxWidgetRuntimeAuthContext = z.infer<typeof partnerMaxWidgetRuntimeAuthContextSchema>
export type PartnerMaxWidgetAnyResponse = z.infer<typeof partnerMaxWidgetAnyResponseSchema>

export function partnerMaxWidgetPath(storeId: string) {
  return `/api/partners/stores/${encodeURIComponent(storeId)}/max-widget`
}

export function partnerMaxWidgetAbsoluteBaseUrl(storeId: string) {
  return `${partnerRuntimeConfig().platformApiBaseUrl}${partnerMaxWidgetPath(storeId)}`
}

export function partnerMaxWidgetAuthPathQuery(authPath: PartnerMaxWidgetAuthPath) {
  return `authPath=${encodeURIComponent(authPath)}`
}

export function fetchPartnerMaxWidgetRuntimeAuthContext(
  api: PartnerApiClient,
  storeId: string,
  authPath: PartnerMaxWidgetAuthPath,
) {
  return api.request(
    `${partnerMaxWidgetPath(storeId)}/chat/me/auth-context?${partnerMaxWidgetAuthPathQuery(authPath)}`,
    partnerMaxWidgetRuntimeAuthContextSchema,
  )
}

export function queryPartnerMaxWidget(
  api: PartnerApiClient,
  storeId: string,
  authPath: PartnerMaxWidgetAuthPath,
  query: string,
  conversationId?: string | null,
) {
  return api.request(`${partnerMaxWidgetPath(storeId)}/chat/me/query?${partnerMaxWidgetAuthPathQuery(authPath)}`, partnerMaxWidgetAnyResponseSchema, {
    method: 'POST',
    body: JSON.stringify({
      query,
      ...(conversationId ? { conversationId } : {}),
    }),
  })
}

export function deletePartnerMaxWidgetConversation(
  api: PartnerApiClient,
  storeId: string,
  authPath: PartnerMaxWidgetAuthPath,
  conversationId: string,
) {
  return api.request(
    `${partnerMaxWidgetPath(storeId)}/chat/me/conversations/${encodeURIComponent(conversationId)}?${partnerMaxWidgetAuthPathQuery(authPath)}`,
    z.null(),
    { method: 'DELETE' },
  )
}
