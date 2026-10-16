import './styles.css'

type AuthSession = {
  access_token: string
  refresh_token?: string
  expires_at?: number
  expires_in?: number
  user?: { id?: string; email?: string }
}

type AppBootstrap = {
  schemaVersion: 'loomai-ai-workspace-app-bootstrap-v1'
  installationId: string
  displayName: string
  interactionPolicy: 'public' | 'login-required' | 'public-with-protected-capabilities'
  delivery: { mode: 'standalone'; closeAllowed: false; appUrl: string }
  authentication: {
    profileCode: 'none' | 'supabase'
    profileVersion: string
    configuration: {
      projectUrl?: string
      publishableKey?: string
      methods?: Array<'email-password' | 'magic-link'>
    }
  }
  installer: { url: string; integrity: string; version: string }
}

declare global {
  interface Window {
    __LOOMAI_ENGAGE_CONFIG__?: { platformApiBaseUrl?: string }
    LoomAIWorkspaceHost?: {
      version: '1.0.0'
      standalone: true
      identity: { status: 'anonymous' | 'authenticated'; subjectKey?: string; displayName?: string }
      getIdentityAccessToken(): Promise<string | null>
      requestSignIn?(): void
      signOut?(): Promise<void>
    }
    MaxMode?: { destroy?(): void }
  }
}

const shell = required<HTMLElement>('shell')
const appHeading = required<HTMLHeadingElement>('app-heading')
const statusPanel = required<HTMLElement>('status-panel')
const statusTitle = required<HTMLElement>('status-title')
const statusCopy = required<HTMLElement>('status-copy')
const spinner = required<HTMLElement>('spinner')
const retryButton = required<HTMLButtonElement>('retry-button')
const authDialog = required<HTMLDialogElement>('auth-dialog')
const authForm = required<HTMLFormElement>('auth-form')
const authClose = required<HTMLButtonElement>('auth-close')
const authEmail = required<HTMLInputElement>('auth-email')
const authPassword = required<HTMLInputElement>('auth-password')
const passwordField = required<HTMLElement>('password-field')
const authError = required<HTMLElement>('auth-error')
const passwordSignIn = required<HTMLButtonElement>('password-sign-in')
const magicLink = required<HTMLButtonElement>('magic-link')

let bootstrap: AppBootstrap | null = null
let session: AuthSession | null = null
let authenticationCallbackError: string | null = null
let authenticationGeneration = 0
let loginAttempt: AbortController | null = null
let refreshInFlight: { token: string; promise: Promise<AuthSession | null> } | null = null
const PROVIDER_REQUEST_TIMEOUT_MS = 5_000
const MAGIC_LINK_ATTEMPT_TTL_MS = 15 * 60 * 1_000
const AUTHENTICATION_REQUIREMENT_SCHEMA = 'loomai-workspace-authentication-requirement-v1'

window.visualViewport?.addEventListener('resize', updateAuthDialogViewport)
window.visualViewport?.addEventListener('scroll', updateAuthDialogViewport)
window.addEventListener('resize', updateAuthDialogViewport)
authDialog.addEventListener('focusin', () => requestAnimationFrame(keepAuthDialogFocusVisible))
retryButton.addEventListener('click', () => void start())
authClose.addEventListener('click', () => authDialog.close())
authDialog.addEventListener('close', cancelLogin)
authDialog.addEventListener('cancel', (event) => {
  if (bootstrap?.interactionPolicy === 'login-required' && !session) event.preventDefault()
  else cancelLogin()
})
authForm.addEventListener('submit', (event) => {
  event.preventDefault()
  void signInWithPassword()
})
magicLink.addEventListener('click', () => void sendMagicLink())
window.addEventListener('loomai:workspace-authentication-required', (event) => {
  if (isAcceptedAuthenticationRequiredEvent(event)) openSignIn()
})
window.addEventListener('loomai:workspace-ready', () => statusPanel.classList.add('hidden'))
window.addEventListener('loomai:workspace-error', (event) => {
  const detail = (event as CustomEvent<Record<string, unknown>>).detail
  showFailure(typeof detail?.message === 'string' ? detail.message : 'The workspace could not start.')
})
window.addEventListener('offline', () => {
  showStatus('You are offline', 'Reconnect to continue using this AI workspace.', false)
  retryButton.classList.add('hidden')
})
window.addEventListener('online', () => window.location.reload())

void start()

async function start() {
  showStatus('Connecting your AI workspace', 'Resolving this app and its assigned deployment.', true)
  retryButton.classList.add('hidden')
  try {
    bootstrap = await fetchBootstrap()
    document.title = bootstrap.displayName
    appHeading.textContent = bootstrap.displayName
    session = await restoreSession(bootstrap)
    configureAuthDialog(bootstrap)
    if (!session && authenticationCallbackError) {
      const closable = bootstrap.interactionPolicy !== 'login-required'
      if (!closable) {
        showStatus(`Welcome to ${bootstrap.displayName}`, 'Sign in before starting a private conversation.', false)
      }
      openSignIn(closable)
      showAuthError(authenticationCallbackError)
      if (!closable) return
    }
    if (bootstrap.interactionPolicy === 'login-required' && !session) {
      showStatus(`Welcome to ${bootstrap.displayName}`, 'Sign in before starting a private conversation.', false)
      openSignIn(false)
      return
    }
    installHostCapabilities(bootstrap)
    await loadInstaller(bootstrap)
  } catch (error) {
    showFailure(error instanceof Error ? error.message : 'The workspace could not start.')
  }
}

async function fetchBootstrap(): Promise<AppBootstrap> {
  const baseUrl = platformBaseUrl()
  const response = await fetch(`${baseUrl}/api/public/ai-workspace/apps/bootstrap`, {
    headers: { Accept: 'application/json' },
    mode: 'cors',
    credentials: 'omit',
    cache: 'no-cache',
  })
  if (!response.ok) throw new Error(response.status === 404 ? 'This AI app is unavailable.' : `App bootstrap returned HTTP ${response.status}.`)
  const value = await response.json() as AppBootstrap
  if (value.schemaVersion !== 'loomai-ai-workspace-app-bootstrap-v1'
    || value.delivery?.mode !== 'standalone'
    || !value.installationId?.startsWith('awi_pub_')) {
    throw new Error('The Platform returned an unsupported app contract.')
  }
  return value
}

function installHostCapabilities(app: AppBootstrap) {
  window.LoomAIWorkspaceHost = {
    version: '1.0.0',
    standalone: true,
    identity: session?.user?.id
      ? { status: 'authenticated', subjectKey: session.user.id, displayName: session.user.email }
      : { status: 'anonymous' },
    async getIdentityAccessToken() {
      if (!session) return null
      session = await ensureFreshSession(app, session)
      if (!session) {
        window.MaxMode?.destroy?.()
        window.location.reload()
        return null
      }
      return session?.access_token || null
    },
    ...(app.authentication.profileCode !== 'none' ? {
      requestSignIn() { openSignIn() },
      async signOut() { await signOut(app) },
    } : {}),
  }
}

async function loadInstaller(app: AppBootstrap) {
  const assetOrigin = new URL(app.installer.url).origin
  if (assetOrigin !== new URL(platformBaseUrl()).origin) throw new Error('Installer origin does not match the Platform.')
  const script = document.createElement('script')
  script.src = app.installer.url
  script.async = true
  script.integrity = app.installer.integrity
  script.crossOrigin = 'anonymous'
  script.dataset.installationId = app.installationId
  document.body.appendChild(script)
  await new Promise<void>((resolve, reject) => {
    const timeout = window.setTimeout(() => reject(new Error('AI Workspace installer timed out.')), 15_000)
    script.addEventListener('load', () => { window.clearTimeout(timeout); resolve() }, { once: true })
    script.addEventListener('error', () => { window.clearTimeout(timeout); reject(new Error('AI Workspace installer could not be loaded.')) }, { once: true })
  })
}

function configureAuthDialog(app: AppBootstrap) {
  const methods = app.authentication.configuration.methods || []
  const passwordEnabled = methods.includes('email-password')
  passwordField.classList.toggle('hidden', !passwordEnabled)
  passwordSignIn.classList.toggle('hidden', !passwordEnabled)
  magicLink.classList.toggle('hidden', !methods.includes('magic-link'))
}

function openSignIn(closable = true) {
  if (!bootstrap || bootstrap.authentication.profileCode !== 'supabase') return
  authClose.classList.toggle('hidden', !closable && bootstrap.interactionPolicy === 'login-required')
  clearAuthError()
  updateAuthDialogViewport()
  if (!authDialog.open) authDialog.showModal()
}

function updateAuthDialogViewport() {
  const viewport = window.visualViewport
  authDialog.style.setProperty('--auth-viewport-height', `${viewport?.height ?? window.innerHeight}px`)
  authDialog.style.setProperty('--auth-viewport-width', `${viewport?.width ?? window.innerWidth}px`)
  authDialog.style.setProperty('--auth-viewport-top', `${viewport?.offsetTop ?? 0}px`)
  authDialog.style.setProperty('--auth-viewport-left', `${viewport?.offsetLeft ?? 0}px`)
  requestAnimationFrame(keepAuthDialogFocusVisible)
}

function keepAuthDialogFocusVisible() {
  const focused = document.activeElement
  if (!authDialog.open || !(focused instanceof HTMLElement) || !authDialog.contains(focused)) return
  const bounds = authDialog.getBoundingClientRect()
  const control = focused.getBoundingClientRect()
  if (control.top < bounds.top + 12) authDialog.scrollTop -= bounds.top + 12 - control.top
  else if (control.bottom > bounds.bottom - 12) authDialog.scrollTop += control.bottom - bounds.bottom + 12
}

async function signInWithPassword() {
  if (!bootstrap) return
  const app = bootstrap
  const generation = ++authenticationGeneration
  loginAttempt?.abort()
  const attempt = new AbortController()
  loginAttempt = attempt
  const auth = supabaseConfig(bootstrap)
  setAuthBusy(true)
  clearAuthError()
  try {
    const response = await fetch(`${auth.projectUrl}/auth/v1/token?grant_type=password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', apikey: auth.publishableKey },
      body: JSON.stringify({ email: authEmail.value.trim(), password: authPassword.value }),
      signal: attempt.signal,
    })
    if (!response.ok) throw new Error(await providerError(response, 'Sign in failed.'))
    const next = await response.json() as AuthSession
    if (generation !== authenticationGeneration || attempt.signal.aborted) return
    await completeLogin(app, next)
  } catch (error) {
    if (!attempt.signal.aborted && generation === authenticationGeneration) {
      showAuthError(error instanceof Error ? error.message : 'Sign in failed.')
    }
  } finally {
    if (loginAttempt === attempt) { loginAttempt = null; setAuthBusy(false) }
  }
}

function cancelLogin() {
  authenticationGeneration++
  loginAttempt?.abort()
  loginAttempt = null
  authPassword.value = ''
  setAuthBusy(false)
}

async function sendMagicLink() {
  if (!bootstrap) return
  const app = bootstrap
  const auth = supabaseConfig(app)
  const generation = ++authenticationGeneration
  loginAttempt?.abort()
  const request = new AbortController()
  loginAttempt = request
  const attemptId = crypto.randomUUID()
  const redirect = new URL(window.location.href)
  redirect.hash = ''
  redirect.searchParams.set('loomai_auth_attempt', attemptId)
  storeMagicLinkAttempt(app, attemptId)
  setAuthBusy(true)
  clearAuthError()
  try {
    const endpoint = new URL(`${auth.projectUrl}/auth/v1/otp`)
    endpoint.searchParams.set('redirect_to', redirect.toString())
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', apikey: auth.publishableKey },
      body: JSON.stringify({ email: authEmail.value.trim(), create_user: false }),
      signal: request.signal,
    })
    if (request.signal.aborted || generation !== authenticationGeneration) return
    if (!response.ok) throw new Error(await providerError(response, 'The sign-in link could not be sent.'))
    statusCopy.textContent = 'Check your email for the secure sign-in link.'
    authDialog.close()
  } catch (error) {
    clearMagicLinkAttempt(app)
    if (!request.signal.aborted && generation === authenticationGeneration) {
      showAuthError(error instanceof Error ? error.message : 'The sign-in link could not be sent.')
    }
  } finally {
    if (loginAttempt === request) { loginAttempt = null; setAuthBusy(false) }
  }
}

async function completeLogin(app: AppBootstrap, nextSession: AuthSession) {
  validateSession(nextSession)
  authenticationGeneration++
  clearWorkspaceIdentityState(app.installationId, 'anonymous')
  clearWorkspaceIdentityStatePrefix(app.installationId, 'authenticated:')
  storeSession(app, nextSession)
  window.MaxMode?.destroy?.()
  window.location.reload()
}

async function signOut(app: AppBootstrap) {
  if (app.authentication.profileCode === 'none') return
  const current = session
  authenticationGeneration++
  loginAttempt?.abort()
  session = null
  const auth = supabaseConfig(app)
  clearWorkspaceIdentityStatePrefix(app.installationId, 'authenticated:')
  clearWorkspaceIdentityState(app.installationId, 'anonymous')
  sessionStorage.removeItem(sessionKey(app))
  clearMagicLinkAttempt(app)
  let failure: Error | null = null
  try {
    window.MaxMode?.destroy?.()
  } catch {
    failure = new Error('Signed out locally; workspace teardown could not be confirmed.')
  }
  delete window.LoomAIWorkspaceHost
  const request = new AbortController()
  const timeout = window.setTimeout(() => request.abort(), PROVIDER_REQUEST_TIMEOUT_MS)
  try {
    if (current?.access_token) {
      const response = await fetch(`${auth.projectUrl}/auth/v1/logout?scope=local`, {
        method: 'POST',
        headers: { apikey: auth.publishableKey, Authorization: `Bearer ${current.access_token}` },
        signal: request.signal,
      })
      if (!response.ok) throw new Error('Provider logout could not be confirmed.')
    }
  } catch {
    failure = new Error('Signed out locally; provider logout could not be confirmed. Issued tokens remain valid until expiry.')
  } finally {
    window.clearTimeout(timeout)
  }
  if (failure) { showFailure(failure.message); throw failure }
  window.location.reload()
}

async function restoreSession(app: AppBootstrap): Promise<AuthSession | null> {
  if (app.authentication.profileCode === 'none') return null
  const fragment = new URLSearchParams(window.location.hash.replace(/^#/, ''))
  const callbackAttemptId = new URL(window.location.href).searchParams.get('loomai_auth_attempt')
  const callbackError = fragment.get('error_description') || fragment.get('error')
  if (callbackError) {
    authenticationCallbackError = consumeMagicLinkAttempt(app, callbackAttemptId)
      ? callbackError.slice(0, 500)
      : 'The sign-in callback could not be verified. Start sign in again.'
    clearAuthenticationCallbackUrl()
    return null
  }
  if (fragment.get('access_token')) {
    if (!consumeMagicLinkAttempt(app, callbackAttemptId)) {
      authenticationCallbackError = 'The sign-in callback could not be verified. Start sign in again.'
      clearAuthenticationCallbackUrl()
      return null
    }
    const next: AuthSession = {
      access_token: fragment.get('access_token') || '',
      refresh_token: fragment.get('refresh_token') || undefined,
      expires_in: Number(fragment.get('expires_in') || 0),
      expires_at: Math.floor(Date.now() / 1000) + Number(fragment.get('expires_in') || 0),
    }
    clearAuthenticationCallbackUrl()
    const user = await fetchSupabaseUser(app, next.access_token)
    next.user = user
    validateSession(next)
    clearWorkspaceIdentityState(app.installationId, 'anonymous')
    clearWorkspaceIdentityStatePrefix(app.installationId, 'authenticated:')
    storeSession(app, next)
    return next
  }
  try {
    const raw = sessionStorage.getItem(sessionKey(app))
    return raw ? await ensureFreshSession(app, JSON.parse(raw) as AuthSession) : null
  } catch {
    sessionStorage.removeItem(sessionKey(app))
    clearWorkspaceIdentityStatePrefix(app.installationId, 'authenticated:')
    return null
  }
}

async function ensureFreshSession(app: AppBootstrap, value: AuthSession | null): Promise<AuthSession | null> {
  if (!value?.access_token) return null
  const invalidate = () => {
    if (value.user?.id) clearWorkspaceIdentityState(app.installationId, `authenticated:${value.user.id}`)
    sessionStorage.removeItem(sessionKey(app))
    return null
  }
  if (!value.user?.id || !Number.isFinite(value.expires_at)) return invalidate()
  if (value.expires_at! > Math.floor(Date.now() / 1000) + 30) return value
  if (!value.refresh_token) return invalidate()
  if (refreshInFlight?.token === value.access_token) return refreshInFlight.promise
  const generation = authenticationGeneration
  const promise = (async () => {
    const auth = supabaseConfig(app)
    const request = new AbortController()
    const timeout = window.setTimeout(() => request.abort(), PROVIDER_REQUEST_TIMEOUT_MS)
    try {
      const response = await fetch(`${auth.projectUrl}/auth/v1/token?grant_type=refresh_token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', apikey: auth.publishableKey },
        body: JSON.stringify({ refresh_token: value.refresh_token }),
        signal: request.signal,
      })
      if (generation !== authenticationGeneration) return null
      if (!response.ok) {
        if (response.status === 401 || response.status === 403 || response.status === 400) return invalidate()
        throw new Error('Identity refresh could not be confirmed.')
      }
      const refreshed = await response.json() as AuthSession
      if (!refreshed.user?.id) refreshed.user = await fetchSupabaseUser(app, refreshed.access_token, request.signal)
      if (generation !== authenticationGeneration) return null
      if (refreshed.user?.id !== value.user?.id) return invalidate()
      try { validateSession(refreshed) } catch { return invalidate() }
      storeSession(app, refreshed)
      return refreshed
    } catch (error) {
      if (generation !== authenticationGeneration) return null
      if (request.signal.aborted) return invalidate()
      throw error
    } finally {
      window.clearTimeout(timeout)
    }
  })()
  refreshInFlight = { token: value.access_token, promise }
  try { return await promise }
  finally { if (refreshInFlight?.promise === promise) refreshInFlight = null }
}

function validateSession(value: AuthSession) {
  if (!value.expires_at && Number.isFinite(value.expires_in) && value.expires_in! > 0) {
    value.expires_at = Math.floor(Date.now() / 1000) + value.expires_in!
  }
  if (!value.access_token || !value.user?.id || !Number.isFinite(value.expires_at)
    || value.expires_at! <= Math.floor(Date.now() / 1000)) throw new Error('The identity provider returned an incomplete or expired session.')
}

async function fetchSupabaseUser(app: AppBootstrap, accessToken: string, signal?: AbortSignal) {
  const auth = supabaseConfig(app)
  const response = await fetch(`${auth.projectUrl}/auth/v1/user`, {
    headers: { apikey: auth.publishableKey, Authorization: `Bearer ${accessToken}` },
    signal,
  })
  if (!response.ok) throw new Error('The signed-in identity could not be verified.')
  return response.json() as Promise<{ id?: string; email?: string }>
}

function supabaseConfig(app: AppBootstrap) {
  const { projectUrl, publishableKey } = app.authentication.configuration
  if (app.authentication.profileCode !== 'supabase' || !projectUrl || !publishableKey) {
    throw new Error('Customer authentication is not configured.')
  }
  return { projectUrl: projectUrl.replace(/\/$/, ''), publishableKey }
}

function storeSession(app: AppBootstrap, value: AuthSession) {
  if (!value.expires_at && value.expires_in) value.expires_at = Math.floor(Date.now() / 1000) + value.expires_in
  sessionStorage.setItem(sessionKey(app), JSON.stringify(value))
}

function sessionKey(app: AppBootstrap) {
  return `loomai_engage_identity:${location.hostname}:${app.installationId}:${app.authentication.profileCode}`
}

function magicLinkAttemptKey(app: AppBootstrap) {
  return `loomai_engage_magic_link:${location.hostname}:${app.installationId}:${app.authentication.profileCode}`
}

function storeMagicLinkAttempt(app: AppBootstrap, attemptId: string) {
  localStorage.setItem(magicLinkAttemptKey(app), JSON.stringify({ attemptId, createdAt: Date.now() }))
}

function clearMagicLinkAttempt(app: AppBootstrap) {
  localStorage.removeItem(magicLinkAttemptKey(app))
}

function consumeMagicLinkAttempt(app: AppBootstrap, callbackAttemptId: string | null) {
  if (!callbackAttemptId) return false
  const key = magicLinkAttemptKey(app)
  const raw = localStorage.getItem(key)
  if (!raw) return false
  try {
    const attempt = JSON.parse(raw) as { attemptId?: unknown; createdAt?: unknown }
    if (attempt.attemptId !== callbackAttemptId) return false
    localStorage.removeItem(key)
    return typeof attempt.createdAt === 'number'
      && Number.isFinite(attempt.createdAt)
      && attempt.createdAt <= Date.now()
      && Date.now() - attempt.createdAt <= MAGIC_LINK_ATTEMPT_TTL_MS
  } catch {
    localStorage.removeItem(key)
    return false
  }
}

function clearAuthenticationCallbackUrl() {
  const next = new URL(window.location.href)
  next.hash = ''
  next.searchParams.delete('loomai_auth_attempt')
  history.replaceState(null, '', `${next.pathname}${next.search}`)
}

function isAcceptedAuthenticationRequiredEvent(event: Event) {
  const detail = recordValue((event as CustomEvent<unknown>).detail)
  if (!detail) return false
  if (Object.hasOwn(detail, 'authenticationRequirement')) {
    const requirement = recordValue(detail.authenticationRequirement)
    return Boolean(requirement
      && requirement.schemaVersion === AUTHENTICATION_REQUIREMENT_SCHEMA
      && requirement.reasonCode === 'AUTHENTICATION_REQUIRED'
      && requirement.retryPolicy === 'EXPLICIT_NEW_REQUEST')
  }
  return Boolean(bootstrap
    && detail.installationId === bootstrap.installationId
    && detail.interactionPolicy === bootstrap.interactionPolicy)
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

function clearWorkspaceIdentityState(installationId: string, identityKey: string) {
  const installationMarker = `:${installationId}:`
  const identitySuffix = `:${identityKey}`
  for (let index = sessionStorage.length - 1; index >= 0; index--) {
    const key = sessionStorage.key(index)
    if (key?.includes(installationMarker) && key.endsWith(identitySuffix)) sessionStorage.removeItem(key)
  }
}

function clearWorkspaceIdentityStatePrefix(installationId: string, identityPrefix: string) {
  const installationMarker = `:${installationId}:`
  const identityMarker = `:${identityPrefix}`
  for (let index = sessionStorage.length - 1; index >= 0; index--) {
    const key = sessionStorage.key(index)
    if (key?.includes(installationMarker)
      && key.includes(identityMarker)) {
      sessionStorage.removeItem(key)
    }
  }
}

function platformBaseUrl() {
  const configured = window.__LOOMAI_ENGAGE_CONFIG__?.platformApiBaseUrl?.trim()
  if (!configured) throw new Error('The shared AI Workspace frontend is not configured with its Platform API.')
  const url = new URL(configured)
  if (url.protocol !== 'https:' && !['localhost', '127.0.0.1'].includes(url.hostname)) {
    throw new Error('The Platform API must use HTTPS.')
  }
  return url.toString().replace(/\/$/, '')
}

function showStatus(title: string, copy: string, busy: boolean) {
  statusPanel.classList.remove('hidden')
  statusTitle.textContent = title
  statusCopy.textContent = copy
  spinner.classList.toggle('hidden', !busy)
}

function showFailure(message: string) {
  showStatus('This AI app is unavailable', message, false)
  retryButton.classList.remove('hidden')
}

function setAuthBusy(busy: boolean) {
  passwordSignIn.disabled = busy
  magicLink.disabled = busy
}

function clearAuthError() {
  authError.classList.add('hidden')
  authError.textContent = ''
}

function showAuthError(message: string) {
  authError.textContent = message
  authError.classList.remove('hidden')
}

async function providerError(response: Response, fallback: string) {
  try {
    const value = await response.json() as { error_description?: string; msg?: string; message?: string }
    return value.error_description || value.msg || value.message || fallback
  } catch {
    return fallback
  }
}

function required<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id)
  if (!element) throw new Error(`Required shell element is missing: ${id}`)
  return element as T
}
