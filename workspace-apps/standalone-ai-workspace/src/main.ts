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
      requestSignIn(): void
      signOut(): Promise<void>
    }
    MaxMode?: { destroy?(): void }
  }
}

const shell = required<HTMLElement>('shell')
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

retryButton.addEventListener('click', () => void start())
authClose.addEventListener('click', () => authDialog.close())
authForm.addEventListener('submit', (event) => {
  event.preventDefault()
  void signInWithPassword()
})
magicLink.addEventListener('click', () => void sendMagicLink())
window.addEventListener('loomai:workspace-authentication-required', () => openSignIn())
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
      session = await ensureFreshSession(app, session)
      if (!session) {
        window.MaxMode?.destroy?.()
        window.location.reload()
        return null
      }
      return session?.access_token || null
    },
    requestSignIn() { openSignIn() },
    async signOut() { await signOut(app) },
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
  if (!authDialog.open) authDialog.showModal()
}

async function signInWithPassword() {
  if (!bootstrap) return
  const auth = supabaseConfig(bootstrap)
  setAuthBusy(true)
  clearAuthError()
  try {
    const response = await fetch(`${auth.projectUrl}/auth/v1/token?grant_type=password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', apikey: auth.publishableKey },
      body: JSON.stringify({ email: authEmail.value.trim(), password: authPassword.value }),
    })
    if (!response.ok) throw new Error(await providerError(response, 'Sign in failed.'))
    await completeLogin(bootstrap, await response.json() as AuthSession)
  } catch (error) {
    showAuthError(error instanceof Error ? error.message : 'Sign in failed.')
  } finally {
    setAuthBusy(false)
  }
}

async function sendMagicLink() {
  if (!bootstrap) return
  const auth = supabaseConfig(bootstrap)
  setAuthBusy(true)
  clearAuthError()
  try {
    const response = await fetch(`${auth.projectUrl}/auth/v1/otp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', apikey: auth.publishableKey },
      body: JSON.stringify({ email: authEmail.value.trim(), create_user: false, options: { email_redirect_to: window.location.origin } }),
    })
    if (!response.ok) throw new Error(await providerError(response, 'The sign-in link could not be sent.'))
    statusCopy.textContent = 'Check your email for the secure sign-in link.'
    authDialog.close()
  } catch (error) {
    showAuthError(error instanceof Error ? error.message : 'The sign-in link could not be sent.')
  } finally {
    setAuthBusy(false)
  }
}

async function completeLogin(app: AppBootstrap, nextSession: AuthSession) {
  if (!nextSession.access_token || !nextSession.user?.id) throw new Error('The identity provider returned an incomplete session.')
  clearWorkspaceIdentityState(app.installationId, 'anonymous')
  storeSession(app, nextSession)
  window.MaxMode?.destroy?.()
  window.location.reload()
}

async function signOut(app: AppBootstrap) {
  if (app.authentication.profileCode === 'none') return
  const current = await ensureFreshSession(app, session)
  const auth = supabaseConfig(app)
  if (current?.access_token) {
    await fetch(`${auth.projectUrl}/auth/v1/logout`, {
      method: 'POST',
      headers: { apikey: auth.publishableKey, Authorization: `Bearer ${current.access_token}` },
    }).catch(() => undefined)
  }
  if (current?.user?.id) clearWorkspaceIdentityState(app.installationId, `authenticated:${current.user.id}`)
  clearWorkspaceIdentityState(app.installationId, 'anonymous')
  sessionStorage.removeItem(sessionKey(app))
  window.MaxMode?.destroy?.()
  window.location.reload()
}

async function restoreSession(app: AppBootstrap): Promise<AuthSession | null> {
  if (app.authentication.profileCode === 'none') return null
  const fragment = new URLSearchParams(window.location.hash.replace(/^#/, ''))
  const callbackError = fragment.get('error_description') || fragment.get('error')
  if (callbackError) {
    authenticationCallbackError = callbackError.slice(0, 500)
    history.replaceState(null, '', `${location.pathname}${location.search}`)
    return null
  }
  if (fragment.get('access_token')) {
    const next: AuthSession = {
      access_token: fragment.get('access_token') || '',
      refresh_token: fragment.get('refresh_token') || undefined,
      expires_in: Number(fragment.get('expires_in') || 0),
      expires_at: Math.floor(Date.now() / 1000) + Number(fragment.get('expires_in') || 0),
    }
    const user = await fetchSupabaseUser(app, next.access_token)
    next.user = user
    clearWorkspaceIdentityState(app.installationId, 'anonymous')
    storeSession(app, next)
    history.replaceState(null, '', `${location.pathname}${location.search}`)
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
  if (!value.expires_at || value.expires_at > Math.floor(Date.now() / 1000) + 30) return value
  if (!value.refresh_token) return null
  const auth = supabaseConfig(app)
  const response = await fetch(`${auth.projectUrl}/auth/v1/token?grant_type=refresh_token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', apikey: auth.publishableKey },
    body: JSON.stringify({ refresh_token: value.refresh_token }),
  })
  if (!response.ok) {
    if (value.user?.id) clearWorkspaceIdentityState(app.installationId, `authenticated:${value.user.id}`)
    sessionStorage.removeItem(sessionKey(app))
    return null
  }
  const refreshed = await response.json() as AuthSession
  refreshed.user ||= value.user
  storeSession(app, refreshed)
  return refreshed
}

async function fetchSupabaseUser(app: AppBootstrap, accessToken: string) {
  const auth = supabaseConfig(app)
  const response = await fetch(`${auth.projectUrl}/auth/v1/user`, {
    headers: { apikey: auth.publishableKey, Authorization: `Bearer ${accessToken}` },
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
