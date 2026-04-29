export interface PartnerRuntimeConfig {
  platformApiBaseUrl: string
  supabaseUrl: string
  supabaseAnonKey: string
}

declare global {
  interface Window {
    __PARTNER_RUNTIME_CONFIG__?: Partial<PartnerRuntimeConfig>
  }
}

function runtimeConfig() {
  if (typeof window === 'undefined') {
    return {}
  }
  return window.__PARTNER_RUNTIME_CONFIG__ ?? {}
}

function normalizeBaseUrl(value: string | undefined) {
  return (value ?? '').replace(/\/+$/, '')
}

export function partnerRuntimeConfig(): PartnerRuntimeConfig {
  const runtime = runtimeConfig()
  return {
    platformApiBaseUrl: normalizeBaseUrl(runtime.platformApiBaseUrl ?? import.meta.env.VITE_PLATFORM_API_BASE_URL),
    supabaseUrl: runtime.supabaseUrl ?? import.meta.env.VITE_SUPABASE_URL ?? '',
    supabaseAnonKey: runtime.supabaseAnonKey ?? import.meta.env.VITE_SUPABASE_ANON_KEY ?? '',
  }
}
