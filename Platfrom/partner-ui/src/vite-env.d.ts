/// <reference types="vite/client" />

interface Window {
  MaxMode?: {
    init: (config: {
      apiConfig: {
        chatBaseUrl: string
        crudBaseUrl?: string
        defaultHeaders?: Record<string, string>
        fetchCredentials?: RequestCredentials
        runtimeRoutes?: {
          chatQueryUrl?: string
          suggestionsUrl?: string
          authContextUrl?: string
          shellConfigUrl?: string
          conversationsUrl?: string
          conversationItemUrlTemplate?: string
        }
        runtimeAuth?: {
          probeAuthContextOnOpen?: boolean
        }
      }
      integrationMode?: 'backend-mediated-private-runtime' | 'public-runtime-authenticated' | 'public-runtime-anonymous'
      features?: {
        cart?: boolean
        debug?: boolean
        conversations?: boolean
        quickActions?: boolean
      }
      theme?: {
        primaryColor?: string
        borderRadius?: string
        fontFamily?: string
        darkMode?: boolean | 'auto'
      }
      position?: 'bottom-right' | 'bottom-left'
      launcher?: boolean
      onEvent?: (event: { type: string; data?: unknown; timestamp: string }) => void
      onClose?: () => void
    }) => void
    open: () => void
    close: () => void
    toggle: () => void
    destroy: () => void
    version: string
  }
}
