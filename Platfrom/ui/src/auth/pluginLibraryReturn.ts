export function pluginLibraryReturn(value: unknown): string | null {
  if (typeof value !== 'string' || value.length > 8192 || /[\r\n\\]/.test(value) || !value.startsWith('/library/plugins')) return null
  try {
    const url = new URL(value, 'https://console.invalid')
    return url.origin === 'https://console.invalid' && /^\/library\/plugins(?:\/|$)/.test(url.pathname)
      ? `${url.pathname}${url.search}${url.hash}` : null
  } catch { return null }
}
