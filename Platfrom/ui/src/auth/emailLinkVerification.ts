import type { SupabaseClient } from '@supabase/supabase-js'

type EmailLink = { tokenHash: string; type: 'email' | 'recovery' }
let attempt: { key: string; result: Promise<string> } | null = null

export function readEmailLink(search: string, expectedType: EmailLink['type']): EmailLink | null {
  const query = new URLSearchParams(search)
  if (!query.has('token_hash')) return null
  const tokenHash = query.get('token_hash') ?? ''
  if (!tokenHash || tokenHash.length > 1024 || query.get('type') !== expectedType) throw new Error('This email link is invalid. Request a new link.')
  return { tokenHash, type: expectedType }
}

export function verifyEmailLinkOnce(client: SupabaseClient, projectUrl: string, link: EmailLink) {
  const key = `${projectUrl}:${link.type}:${link.tokenHash}`
  // React remounts must not consume the same one-use email credential twice.
  if (attempt?.key === key) return attempt.result
  const result = (async () => {
    const response = await client.auth.verifyOtp({ token_hash: link.tokenHash, type: link.type })
    if (response.error) throw response.error
    const { user, session } = response.data
    if (!user || !session || session.user.id !== user.id) throw new Error('The email link did not establish a verified session.')
    return user.id
  })()
  attempt = { key, result }
  return result
}
