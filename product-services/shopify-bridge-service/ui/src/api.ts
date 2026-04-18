export type ShopifyBridgeShellResponse = {
  appName: string
  serviceRef: string
  environmentScope: string
  status: string
  onboardingPhases: string[]
  launchCapabilities: string[]
}

export async function fetchShell(): Promise<ShopifyBridgeShellResponse> {
  const response = await fetch('/api/app/shell', {
    headers: {
      Accept: 'application/json',
    },
  })
  if (!response.ok) {
    throw new Error(`Failed to load bridge shell: HTTP ${response.status}`)
  }
  return response.json() as Promise<ShopifyBridgeShellResponse>
}
