/// <reference types="vite/client" />

interface Window {
  __PLATFORM_RUNTIME_CONFIG__?: {
    apiBaseUrl?: string
  }
}

interface ImportMetaEnv {
  readonly VITE_PLATFORM_API_BASE_URL?: string
}
