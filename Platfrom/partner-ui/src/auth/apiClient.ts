import type { Session } from '@supabase/supabase-js'
import type { ZodSchema } from 'zod'
import { partnerRuntimeConfig } from '../config/runtimeConfig'

const allowedPathPrefixes = ['/api/partners/', '/api/merchant/partner-access/']
const DEFAULT_PARTNER_API_TIMEOUT_MS = 45_000

export class PartnerApiError extends Error {
  status: number
  details: unknown

  constructor(message: string, status: number, details: unknown = null) {
    super(message)
    this.name = 'PartnerApiError'
    this.status = status
    this.details = details
  }
}

export interface PartnerApiClient {
  request<T>(
    path: string,
    schema: ZodSchema<T>,
    options?: RequestInit & { token?: string | null; anonymous?: boolean },
  ): Promise<T>
  download(path: string, options?: RequestInit & { token?: string | null; anonymous?: boolean }): Promise<Blob>
}

function apiBaseUrl() {
  return partnerRuntimeConfig().platformApiBaseUrl
}

function assertPartnerPath(path: string) {
  if (!path.startsWith('/')) {
    throw new Error('Partner API paths must be absolute.')
  }
  if (!allowedPathPrefixes.some((prefix) => path.startsWith(prefix))) {
    throw new Error(`Blocked non-partner API path: ${path}`)
  }
}

async function fetchWithTimeout(input: RequestInfo | URL, init: RequestInit): Promise<Response> {
  const controller = new AbortController()
  const externalSignal = init.signal
  let timedOut = false
  let removeExternalAbort: (() => void) | null = null
  const timeoutId = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, DEFAULT_PARTNER_API_TIMEOUT_MS)

  if (externalSignal?.aborted) {
    controller.abort()
  } else if (externalSignal) {
    const abortFromExternalSignal = () => controller.abort()
    externalSignal.addEventListener('abort', abortFromExternalSignal, { once: true })
    removeExternalAbort = () => externalSignal.removeEventListener('abort', abortFromExternalSignal)
  }

  try {
    return await fetch(input, { ...init, signal: controller.signal })
  } catch (error) {
    if (timedOut) {
      throw new PartnerApiError('Partner API request timed out. Please retry.', 0, {
        timeoutMs: DEFAULT_PARTNER_API_TIMEOUT_MS,
      })
    }
    throw error
  } finally {
    clearTimeout(timeoutId)
    removeExternalAbort?.()
  }
}

export function createPartnerApiClient(getSession: () => Promise<Session | null>): PartnerApiClient {
  return {
    async request<T>(
      path: string,
      schema: ZodSchema<T>,
      options: RequestInit & { token?: string | null; anonymous?: boolean } = {},
    ) {
      assertPartnerPath(path)
      const { token: explicitToken, anonymous, ...requestOptions } = options
      const headers = new Headers(requestOptions.headers)
      if (!headers.has('Content-Type') && options.body != null) {
        headers.set('Content-Type', 'application/json')
      }
      headers.set('Accept', 'application/json')

      const session = anonymous ? null : await getSession()
      const token = explicitToken ?? session?.access_token
      if (token) {
        headers.set('Authorization', `Bearer ${token}`)
      }

      const response = await fetchWithTimeout(`${apiBaseUrl()}${path}`, {
        ...requestOptions,
        headers,
      })

      if (!response.ok) {
        let details: unknown = null
        try {
          details = await response.json()
        } catch {
          details = await response.text()
        }
        const message =
          typeof details === 'object' && details != null && 'message' in details
            ? String((details as { message?: unknown }).message)
            : `Partner API request failed with ${response.status}`
        throw new PartnerApiError(message, response.status, details)
      }

      if (response.status === 204) {
        return schema.parse(null)
      }

      const payload = await response.json()
      return schema.parse(payload)
    },
    async download(path: string, options: RequestInit & { token?: string | null; anonymous?: boolean } = {}) {
      assertPartnerPath(path)
      const { token: explicitToken, anonymous, ...requestOptions } = options
      const headers = new Headers(requestOptions.headers)
      const session = anonymous ? null : await getSession()
      const token = explicitToken ?? session?.access_token
      if (token) {
        headers.set('Authorization', `Bearer ${token}`)
      }
      const response = await fetchWithTimeout(`${apiBaseUrl()}${path}`, { ...requestOptions, headers })
      if (!response.ok) {
        throw new PartnerApiError(`Partner download failed with ${response.status}`, response.status)
      }
      return response.blob()
    },
  }
}
