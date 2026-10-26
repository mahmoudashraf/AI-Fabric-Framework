import type { CustomerAuthConfig } from '../api/platformApi'

export type DeskAuthorizationDetails = {
  authorization_id: string
  redirect_uri: string
  client: { id: string; name?: string }
  scope: string
}

export function requireDeskAuthorization(config: CustomerAuthConfig, details: DeskAuthorizationDetails, authorizationId: string) {
  const client = config.deskClients.find(value => value.clientId === details.client.id)
  if (!config.enabled || details.authorization_id !== authorizationId || !client?.redirectUris.includes(details.redirect_uri)) {
    throw new Error('This authorization request is not for a configured LoomAI Desk client.')
  }
  if (details.scope.split(' ').filter(Boolean).some(scope => !['openid', 'email', 'profile'].includes(scope))) {
    throw new Error('LoomAI Desk requested an unsupported identity permission.')
  }
}

export function requireDeskCallback(config: CustomerAuthConfig, redirect: string, expectedCallback?: string) {
  const url = new URL(redirect)
  const base = `${url.origin}${url.pathname}`
  if (url.username || url.password || url.hash || (expectedCallback && base !== expectedCallback)
    || !config.deskClients.some(client => client.redirectUris.includes(base))) {
    throw new Error('The authorization callback is not a configured LoomAI Desk address.')
  }
  return url.href
}
