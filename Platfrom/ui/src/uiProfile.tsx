import type { ReactNode } from 'react'

type UIProfileConfig = NonNullable<Window['__PLATFORM_RUNTIME_CONFIG__']>

export function isDeveloperUI(config: UIProfileConfig | undefined = typeof window === 'undefined' ? undefined : window.__PLATFORM_RUNTIME_CONFIG__) {
  return config?.environment === 'staging' && config.uiProfile === 'developer'
}

export function CommercialContent({ children }: { children: ReactNode }) {
  return isDeveloperUI() ? null : <>{children}</>
}

export function PersonalContactContent({ children }: { children: ReactNode }) {
  return isDeveloperUI() ? null : <>{children}</>
}
