const attempts = new Map<string, { identity: string; result: Promise<string> }>()

export function authorizeDeskOnce(authorizationId: string, identity: string, approve: () => Promise<string>): Promise<string> {
  const pending = attempts.get(authorizationId)
  if (pending) {
    if (pending.identity !== identity) return Promise.reject(new Error('The authorization identity changed. Open LoomAI Desk again.'))
    return pending.result
  }
  const key = `loomai-desk-authorization-attempt:${authorizationId}`
  try {
    if (sessionStorage.getItem(key)) throw new Error('This authorization was already attempted. Open LoomAI Desk again for a new request.')
    sessionStorage.setItem(key, 'ATTEMPTED')
  } catch (error) { return Promise.reject(error) }
  const result = Promise.resolve().then(approve)
  attempts.set(authorizationId, { identity, result })
  return result
}
