import { isExactNumber, stringifyExactJson } from '../lib/losslessJson'

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value) && !isExactNumber(value)
}
const owns = (value: object, key: string) => Object.prototype.hasOwnProperty.call(value, key)

function sameJson(before: unknown, after: unknown): boolean {
  if (before === after) return true
  if (record(before) && record(after)) {
    const keys = Object.keys(before)
    return keys.length === Object.keys(after).length && keys.every(key => owns(after, key) && sameJson(before[key], after[key]))
  }
  if (Array.isArray(before) && Array.isArray(after)) return before.length === after.length && before.every((value, index) => sameJson(value, after[index]))
  if (before === undefined || after === undefined) return false
  return stringifyExactJson(before) === stringifyExactJson(after)
}

export type ChangedField = { path: string; key?: unknown; field: string; before: unknown; after: unknown }

export function changedFields(diff: unknown): ChangedField[] {
  const fields: ChangedField[] = []
  if (!Array.isArray(diff)) return fields
  for (const item of diff) {
    if (!record(item) || typeof item.path !== 'string' || !owns(item, 'before') || !owns(item, 'after')) continue
    const walk = (before: unknown, after: unknown, field: string) => {
      if (sameJson(before, after)) return
      if ((record(before) || before === undefined) && (record(after) || after === undefined)) {
        const keys = [...new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})])]
        if (keys.length) {
          for (const key of keys) walk(before && owns(before, key) ? before[key] : undefined,
            after && owns(after, key) ? after[key] : undefined, `${field}/${key.replace(/~/g, '~0').replace(/\//g, '~1')}`)
          return
        }
      }
      // Index is the only JSON array identity; never guess a name/id field.
      if ((Array.isArray(before) || before === undefined) && (Array.isArray(after) || after === undefined)) {
        const length = Math.max(before?.length ?? 0, after?.length ?? 0)
        if (length) {
          for (let index = 0; index < length; index++) walk(before?.[index], after?.[index], `${field}/${index}`)
          return
        }
      }
      fields.push({ path: item.path as string, ...(owns(item, 'key') ? { key: item.key } : {}), field, before, after })
    }
    walk(item.existed === false ? undefined : item.before,
      item.operation === 'REMOVE' || item.operation === 'REMOVE_ITEM' ? undefined : item.after, '')
  }
  return fields
}

export type ValidationSummary = {
  outcome: 'attention' | 'passed' | 'unknown'
  errorCount?: number
  warningCount?: number
  connectionTested?: boolean
  issues: { path?: string; code?: string; message: string }[]
}

export function validationSummary(turn: { validation?: unknown; findings?: unknown }): ValidationSummary {
  const findings = record(turn.findings) ? turn.findings : {}
  const candidates = [turn.validation, findings.validation, findings].filter(record)
  const count = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : undefined
  const errors = candidates.map(value => count(value.errorCount)).filter((value): value is number => value !== undefined)
  const warnings = candidates.map(value => count(value.warningCount)).filter((value): value is number => value !== undefined)
  const attention = candidates.some(value => value.valid === false || value.publishReady === false || (count(value.errorCount) ?? 0) > 0)
  const passed = candidates.some(value => value.valid === true || value.publishReady === true || count(value.errorCount) === 0)
  const issues = candidates.flatMap(value => Array.isArray(value.issues) ? value.issues : []).filter(record)
    .filter(value => typeof value.message === 'string')
    .map(value => ({ message: value.message as string, ...(typeof value.path === 'string' ? { path: value.path } : {}), ...(typeof value.code === 'string' ? { code: value.code } : {}) }))
  return { outcome: attention ? 'attention' : passed ? 'passed' : 'unknown',
    errorCount: errors.length ? Math.max(...errors) : undefined,
    warningCount: warnings.length ? Math.max(...warnings) : undefined,
    connectionTested: candidates.some(value => value.connectionTested === false) ? false : undefined, issues }
}
