export const DEVELOPER_ENTRY_POINT = 'DEVELOPER' as const
export const DEVELOPER_KITS = ['ai-customer-experience', 'ai-support-experience'] as const
export type DeveloperKitCode = typeof DEVELOPER_KITS[number]
export type ConsoleExperience = 'DEVELOPER' | 'CUSTOMER'

export function explicitConsoleExperience(search: string): ConsoleExperience | null {
  const value = new URLSearchParams(search).get('entryPoint')
  return value === 'DEVELOPER' || value === 'CUSTOMER' ? value : null
}

export function consoleExperienceKey(customerId: string, actorId: string) {
  return `loomai-console-experience:${encodeURIComponent(customerId)}:${encodeURIComponent(actorId)}`
}

export function readConsoleExperience(storage: Pick<Storage, 'getItem'>, key: string): ConsoleExperience | null {
  try {
    const value = storage.getItem(key)
    return value === 'DEVELOPER' || value === 'CUSTOMER' ? value : null
  } catch { return null }
}

export function developerEntryPath(path: string) {
  const url = new URL(path, 'https://console.invalid')
  url.searchParams.set('entryPoint', DEVELOPER_ENTRY_POINT)
  return `${url.pathname}${url.search}${url.hash}`
}

export function isDeveloperKit(code: string | null | undefined): code is DeveloperKitCode {
  return DEVELOPER_KITS.some(value => value === code)
}

export function isDeveloperEntry(search: string) {
  return new URLSearchParams(search).get('entryPoint') === DEVELOPER_ENTRY_POINT
}

export function developerKitSetupPath(code: DeveloperKitCode) {
  return developerEntryPath(`/kits/new/${code}`)
}

/** Host routing is presentation only; resource policy comes from the server. */
export function developerLandingHost(hostname: string) {
  return hostname.toLowerCase() === 'developer.loomai.pro'
}

export function developerConsoleHref(path: string, hostname = window.location.hostname) {
  return developerLandingHost(hostname) ? `https://console.loomai.pro${path}` : path
}
