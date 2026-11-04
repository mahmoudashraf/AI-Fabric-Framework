import type { SupportHostAdapter, SupportMountContext, SupportRequesterApi, SupportRequest, SupportRequestPage, SupportSession, SupportAgent } from './types'

export class SupportApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string, readonly current?: SupportRequest) {
    super(message); this.name = 'SupportApiError'
  }
}

export function createRequesterApi(context: SupportMountContext, host: SupportHostAdapter = {}): SupportRequesterApi {
  const support = context.configuration.support as { spaceId?: string; requesterOrigin?: string } | undefined
  const base = host.apiBaseUrl ?? (support?.requesterOrigin && support.spaceId
    ? `${support.requesterOrigin.replace(/\/$/, '')}/api/desk/requester/v1/spaces/${encodeURIComponent(support.spaceId)}` : '')
  if (!base) throw new Error('A reviewed requester endpoint or host API adapter is required.')
  const url = new URL(base, document.baseURI)
  const local = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
  if ((url.protocol !== 'https:' && !(local && url.protocol === 'http:')) || url.username || url.password || url.search || url.hash) {
    throw new Error('Requester endpoints require HTTPS without embedded credentials.')
  }
  const root = url.href.replace(/\/$/, '')
  const photos = new Set<string>()
  const pending = new Set<AbortController>()
  let active = true
  const disposePhotos = () => { for (const photo of photos) URL.revokeObjectURL(photo); photos.clear() }
  const destroy = () => { active = false; for (const controller of pending) controller.abort(); pending.clear(); disposePhotos() }
  const owner = context.credentials?.getIdentity?.()
  const assertOwner = () => {
    if (!active) throw new SupportApiError(401, 'IDENTITY_CHANGED', 'Sign in again before accessing your requests.')
    if (!context.credentials?.getIdentity) return
    const current = context.credentials.getIdentity()
    if (owner?.status !== 'authenticated' || current?.status !== 'authenticated' || !owner.subjectKey || owner.subjectKey !== current.subjectKey) {
      disposePhotos()
      throw new SupportApiError(401, 'IDENTITY_CHANGED', 'Sign in again before accessing your requests.')
    }
  }
  async function request<T>(path: string, method = 'GET', body?: unknown, key?: string, runtime = false, timeoutMs = 15000, photo = false): Promise<T> {
    assertOwner()
    const token = await (host.getIdentityAccessToken ?? context.credentials?.getIdentityAccessToken)?.()
    assertOwner()
    if (!token) { disposePhotos(); throw new SupportApiError(401, 'SIGN_IN_REQUIRED', 'Sign in to access your requests.') }
    const headers: Record<string, string> = { Accept: 'application/json', Authorization: `Bearer ${token}` }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (key) headers['Idempotency-Key'] = key
    if (runtime && host.runtimeCredentialMode !== 'host-mediated') {
      const credential = await (host.getRuntimeAccessToken ?? context.credentials?.getRuntimeAccessToken)?.()
      assertOwner()
      if (!credential) throw new SupportApiError(401, 'RUNTIME_IDENTITY_REQUIRED', 'Connect the authorized conversation before continuing.')
      headers['X-LoomAI-Runtime-Authorization'] = `Bearer ${credential}`
    }
    const controller = new AbortController()
    pending.add(controller)
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    try {
      const response = await fetch(`${root}${path}`, { method, headers, credentials: 'omit', cache: 'no-store',
        redirect: 'error', signal: controller.signal, body: body === undefined ? undefined : JSON.stringify(body) })
      assertOwner()
      if (!response.ok) {
        if (response.status === 401) disposePhotos()
        const safe = await response.json().catch(() => ({}))
        const message = response.status === 401 ? 'Sign in again to access your requests.'
          : response.status === 404 ? 'This request is unavailable.'
          : response.status === 409 ? 'This request changed. Review the current details before trying again.'
          : response.status === 403 ? 'This operation is not permitted.' : 'The request service is unavailable. No success has been confirmed.'
        throw new SupportApiError(response.status, typeof safe.code === 'string' ? safe.code : 'REQUEST_FAILED', message,
          response.status === 409 && safe.current ? validateRequest(safe.current) : undefined)
      }
      if (response.status === 204) return undefined as T
      if (photo) {
        if (!/^image\/(jpeg|png|webp)(;|$)/i.test(response.headers.get('Content-Type') ?? '')) throw new Error('The agent portrait is unavailable.')
        const blob = await response.blob()
        assertOwner()
        if (blob.size > 2 * 1024 * 1024) throw new Error('The agent portrait exceeds the supported size.')
        return blob as T
      }
      const result = await response.json() as T
      assertOwner()
      return result
    } finally { pending.delete(controller); clearTimeout(timer) }
  }
  const item = (id: string) => `/requests/${encodeURIComponent(id)}`
  const version = (value: SupportRequest) => {
    if (typeof value.version !== 'string' || !value.version) throw new Error('An opaque request version is required for a conditional command.')
    return { expectedVersion: value.version }
  }
  return {
    session: () => request<SupportSession>('/session'),
    async team() {
      disposePhotos()
      const agents = await request<SupportAgent[]>('/team')
      return Promise.all(agents.map(async agent => {
        if (!agent.photoAvailable) return { ...agent, photoUrl: undefined }
        try {
          const blob = await request<Blob>(`/team/${encodeURIComponent(agent.profileRef)}/photo`, 'GET', undefined, undefined, false, 15000, true)
          assertOwner()
          const photoUrl = URL.createObjectURL(blob)
          photos.add(photoUrl)
          return { ...agent, photoUrl }
        } catch (error) {
          if (error instanceof SupportApiError && error.status === 401) throw error
          return { ...agent, photoUrl: undefined }
        }
      }))
    },
    async list(options) {
      const query = new URLSearchParams({ limit: String(Math.max(1, Math.min(50, options.limit))) })
      if (options.status) query.set('status', options.status)
      if (options.cursor) query.set('cursor', options.cursor)
      const result = await request<SupportRequestPage>(`/requests?${query}`)
      if (!Array.isArray(result.items)) throw new Error('The request list contract is invalid.')
      result.items.forEach(validateRequest)
      return result
    },
    get: async id => validateRequest(await request(item(id))),
    create: async (input, key) => validateRequest(await request('/requests', 'POST', input, key, !!input.existingConversationId)),
    start: async (value, key) => validateRequest(await request(`${item(value.id)}/start`, 'POST', version(value), key, true, 130000)),
    transition: async (value, operation, key) => validateRequest(await request(`${item(value.id)}/${operation}`, 'POST', version(value), key)),
    seen: async (value, key) => validateRequest(await request(`${item(value.id)}/seen`, 'PUT', { ...version(value), seenThrough: value.updatedAt }, key)),
    newEpisode: async (value, key) => validateRequest(await request(`${item(value.id)}/episodes`, 'POST', version(value), key, true)),
    activity: async (value, key) => validateRequest(await request(`${item(value.id)}/activity`, 'POST', version(value), key, true)),
    notifications: () => request('/notifications'),
    setNotifications: (enabled, returnUrl) => request('/notifications', 'PUT', { enabled, returnUrl }),
    destroy,
  }
}

export function validateRequest(value: unknown): SupportRequest {
  const request = value as SupportRequest
  // Required command identity/version and declared status, never inferred from provider text.
  if (!request || typeof request.id !== 'string' || !request.id || typeof request.reference !== 'string'
    || typeof request.providerBindingId !== 'string' || typeof request.subject !== 'string'
    || typeof request.version !== 'string' || !request.version || !['OPEN', 'RESOLVED', 'CANCELLED'].includes(request.status)) {
    throw new Error('The request service returned an incomplete request contract.')
  }
  return request
}
