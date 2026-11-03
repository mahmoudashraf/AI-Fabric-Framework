import { createRequesterApi, SupportApiError } from '../../../../experience-packs/support-experience/src/api'
import type { SupportAgent, SupportHostAdapter, SupportMountContext, SupportWorkspaceBootstrap } from '../../../../experience-packs/support-experience/src/types'
import { getPlatformApiBaseUrl, getStoredPlatformApiKey, platformCredentials, PlatformApiError, platformRequest } from './platformApi'

export type SupportResource = { type: 'DEPLOYMENT' | 'ENGAGE_APP' | 'CONNECTION'; id: string }
export type SupportRequestContext = {
  ticketId: string
  resources: SupportResource[]
  consent: { granted: boolean; version: number; changedAt: string | null; revokedAt: string | null }
  revision: number
}
export interface ConsoleSupportWorkspaceBootstrap extends SupportWorkspaceBootstrap {
  conversationId: string
  principalNamespace: string
  installationId: string
  workspaceAssetUrl: string
  workspaceIntegrity: string
  experienceModuleUrl: string
  experienceIntegrity: string
  assignmentRevision: string
  configuration: Record<string, unknown>
}
const root = '/api/support'
const item = (id: string) => `${root}/requests/${encodeURIComponent(id)}`
export const getSupportWorkspace = (id?: string, signal?: AbortSignal) => platformRequest<ConsoleSupportWorkspaceBootstrap>(
  id ? `${item(id)}/workspace` : `${root}/workspace`, { signal },
)
export const getSupportRequestContext = (id: string, signal?: AbortSignal) => platformRequest<SupportRequestContext>(`${item(id)}/context`, { signal })
export const updateSupportResources = (context: SupportRequestContext, resources: SupportResource[]) => platformRequest<SupportRequestContext>(`${item(context.ticketId)}/context`, {
  method: 'PUT', body: JSON.stringify({ resources, expectedRevision: context.revision }),
})
export const changeSupportConsent = (context: SupportRequestContext, granted: boolean) => platformRequest<SupportRequestContext>(`${item(context.ticketId)}/context/consent`, {
  method: 'PUT', body: JSON.stringify({ granted, expectedVersion: context.consent.version }),
})

export function supportErrorMessage(error: unknown) {
  if (error instanceof PlatformApiError || error instanceof SupportApiError) {
    if (error.status === 401) return 'Your customer session has expired. Sign in again to continue.'
    if (error.status === 403) return 'This request or resource is not available to your customer account.'
    if (error.status === 404) return 'This support request is unavailable.'
    if (error.status === 409) return 'The request context changed. Refresh and review it before trying again.'
  }
  return 'Support is temporarily unavailable. No success has been confirmed.'
}

export async function getSupportAgentPhoto(reference: string, signal: AbortSignal) {
  const route = `${root}/team/${encodeURIComponent(reference)}/photo`
  const credentials = await platformCredentials(route, getStoredPlatformApiKey())
  const response = await fetch(getPlatformApiBaseUrl() + route, {
    ...credentials, signal, redirect: 'error', cache: 'no-store',
  })
  if (!response.ok || !response.headers.get('content-type')?.startsWith('image/jpeg')) throw new Error('Agent photo is unavailable.')
  return response.blob()
}

/** Ticket transport and state belong to the pack; only authenticated Console portraits are adapted here. */
export function createConsoleSupportApi(context: SupportMountContext, host: SupportHostAdapter, assertOwner: () => void) {
  const api = createRequesterApi(context, { ...host, apiBaseUrl: `${getPlatformApiBaseUrl().replace(/\/$/, '')}${root}` })
  const photos = new Set<string>()
  const controller = new AbortController()
  return {
    api: {
      ...api,
      async team() {
        assertOwner()
        const team = await platformRequest<{ agents: { agentReference: string; displayName: string; photoAvailable: boolean }[] }>(`${root}/team`)
        assertOwner()
        const agents = team.agents.map(agent => ({ profileRef: agent.agentReference, displayName: agent.displayName, photoAvailable: agent.photoAvailable }))
        const result = await Promise.all(agents.map(async (agent): Promise<SupportAgent> => {
          if (!agent.photoAvailable) return { ...agent, photoUrl: undefined }
          try {
            const blob = await getSupportAgentPhoto(agent.profileRef, controller.signal)
            assertOwner()
            if (controller.signal.aborted) return { ...agent, photoUrl: undefined }
            const photoUrl = URL.createObjectURL(blob)
            photos.add(photoUrl)
            return { ...agent, photoUrl }
          } catch { return { ...agent, photoUrl: undefined } }
        }))
        assertOwner()
        return result
      },
    },
    destroy() { controller.abort(); for (const url of photos) URL.revokeObjectURL(url); photos.clear() },
  }
}
