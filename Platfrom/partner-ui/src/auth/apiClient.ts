import type { Session } from '@supabase/supabase-js'
import type { ZodSchema } from 'zod'
import { partnerRuntimeConfig } from '../config/runtimeConfig'

const allowedPathPrefixes = ['/api/partners/', '/api/merchant/partner-access/']

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

export function createPartnerApiClient(getSession: () => Promise<Session | null>): PartnerApiClient {
  return {
    async request<T>(
      path: string,
      schema: ZodSchema<T>,
      options: RequestInit & { token?: string | null; anonymous?: boolean } = {},
    ) {
      assertPartnerPath(path)
      const headers = new Headers(options.headers)
      if (!headers.has('Content-Type') && options.body != null) {
        headers.set('Content-Type', 'application/json')
      }
      headers.set('Accept', 'application/json')

      const session = options.anonymous ? null : await getSession()
      const token = options.token ?? session?.access_token
      if (token) {
        headers.set('Authorization', `Bearer ${token}`)
      }

      const response = await fetch(`${apiBaseUrl()}${path}`, {
        ...options,
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
  }
}
