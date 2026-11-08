export const DEVELOPER_ENTRY_POINT = 'DEVELOPER' as const
export const DEVELOPER_KITS = ['ai-customer-experience', 'ai-support-experience'] as const
export type DeveloperKitCode = typeof DEVELOPER_KITS[number]

export function isDeveloperKit(code: string | null | undefined): code is DeveloperKitCode {
  return DEVELOPER_KITS.some(value => value === code)
}

export function isDeveloperEntry(search: string) {
  return new URLSearchParams(search).get('entryPoint') === DEVELOPER_ENTRY_POINT
}

export function developerKitSetupPath(code: DeveloperKitCode) {
  return `/kits/new/${code}?entryPoint=${DEVELOPER_ENTRY_POINT}`
}

/** Host routing is presentation only; resource policy comes from the server. */
export function developerLandingHost(hostname: string) {
  return hostname.toLowerCase() === 'developer.loomai.pro'
}

export function developerConsoleHref(path: string, hostname = window.location.hostname) {
  return developerLandingHost(hostname) ? `https://console.loomai.pro${path}` : path
}
