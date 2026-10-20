export type DurableCommandHold = {
  schema: 1
  scope: string
  operationKey: string
  commandSha256: string
  recordedAt: string
}

type HoldStorage = Pick<Storage, 'getItem' | 'setItem'>
export type DurableCommandHolds = {
  read: (scope: string) => DurableCommandHold | null
  claim: (scope: string, operationKey: string, commandBody: string, stillCurrent: () => boolean) => Promise<void>
}

const prefix = 'platform.command-hold.v1:'
const blocked = () => new Error('Submission protection is unavailable. No new request may be sent; reconcile the command.')

/** Only identifiers and a command digest are persisted, never request contents or credentials. Holds are not auto-cleared. */
export function createDurableCommandHolds(options: {
  storage?: () => HoldStorage
  exclusive?: (key: string, work: () => void) => Promise<void>
} = {}): DurableCommandHolds {
  const storage = options.storage ?? (() => window.localStorage)
  const exclusive = options.exclusive ?? (async (key, work) => {
    if (typeof navigator === 'undefined' || !navigator.locks) throw blocked()
    await navigator.locks.request(key, { mode: 'exclusive' }, work)
  })
  const read = (scope: string) => {
    try {
      if (!scope) throw blocked()
      const raw = storage().getItem(prefix + scope)
      if (raw === null) return null
      const value = JSON.parse(raw) as DurableCommandHold
      if (!value || Object.keys(value).sort().join(',') !== 'commandSha256,operationKey,recordedAt,schema,scope'
        || value.schema !== 1 || value.scope !== scope || typeof value.operationKey !== 'string'
        || !/^[A-Za-z0-9._:-]{1,128}$/.test(value.operationKey) || typeof value.commandSha256 !== 'string'
        || !/^[a-f0-9]{64}$/.test(value.commandSha256) || typeof value.recordedAt !== 'string'
        || !Number.isFinite(Date.parse(value.recordedAt))) throw blocked()
      return value
    } catch { throw blocked() }
  }
  return { read, async claim(scope, operationKey, commandBody, stillCurrent) {
    if (!scope || !/^[A-Za-z0-9._:-]{1,128}$/.test(operationKey) || !commandBody) throw blocked()
    let digest: string
    try {
      const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(commandBody))
      digest = Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, '0')).join('')
    } catch { throw blocked() }
    await exclusive(prefix + scope, () => {
      if (!stillCurrent()) throw new Error('Command authority or scope changed. Review its current state.')
      if (read(scope)) throw new Error('A submission intent is already recorded. Reconcile it before any further action.')
      const hold: DurableCommandHold = { schema: 1, scope, operationKey, commandSha256: digest, recordedAt: new Date().toISOString() }
      try {
        const raw = JSON.stringify(hold)
        storage().setItem(prefix + scope, raw)
        if (storage().getItem(prefix + scope) !== raw) throw blocked()
      } catch { throw blocked() }
    })
  } }
}
