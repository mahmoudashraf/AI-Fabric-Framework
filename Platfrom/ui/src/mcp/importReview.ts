import { changedFields } from '../assistant/turnPresentation'
import { isJsonObject } from '../actions/actionDefinitionModel'
import { stringifyExactJson } from '../lib/losslessJson'
import type { McpActionChange } from './types'

export type ImportReviewRow = { key: string; label: string; before: string; after: string }
const labels = [
  ['displayName', 'Customer-facing name'], ['description', 'Purpose'], ['accessMode', 'Tool effect'],
  ['requiresConfirmation', 'Customer confirmation'], ['anonymousAllowed', 'Who can use it'],
  ['groundingEligible', 'Answer evidence'], ['enabled', 'Availability'], ['inputSchema', 'Expected inputs'],
  ['outputSchema', 'Returned data'], ['toolSchemaHash', 'Tool contract'],
] as const

function display(key: string, value: unknown, side: 'before' | 'after'): string {
  if (value === undefined) return 'Not present'
  if (value === null) return 'Not set'
  if (key === 'accessMode') {
    if (value === 'READ') return 'Read-only'
    if (value === 'READ_WRITE') return 'May read and change data'
    if (value === 'WRITE_ONLY') return 'Changes data'
  }
  if (typeof value === 'boolean') {
    if (key === 'requiresConfirmation') return value ? 'Confirmation required' : 'No confirmation'
    if (key === 'anonymousAllowed') return value ? 'Anonymous visitors allowed' : 'Sign-in required'
    if (key === 'groundingEligible') return value ? 'May support AI answers' : 'Not used as answer evidence'
    if (key === 'enabled') return value ? 'Enabled' : 'Disabled'
  }
  if (key === 'inputSchema' || key === 'outputSchema') {
    const properties = isJsonObject(value) && isJsonObject(value.properties) ? Object.keys(value.properties).length : null
    return `${side === 'before' ? 'Previous' : 'Saved'} schema${properties === null ? '' : ` (${properties} ${properties === 1 ? 'field' : 'fields'})`}`
  }
  if (key === 'toolSchemaHash') return side === 'before' ? 'Previous contract' : 'Saved contract'
  return typeof value === 'string' ? value || '(empty)' : stringifyExactJson(value)
}

export function importReviewRows(change: McpActionChange): ImportReviewRow[] {
  const before = isJsonObject(change.before) ? change.before : {}
  const after = isJsonObject(change.after) ? change.after : {}
  const changed = new Set(changedFields([{ path: '', before: change.before, after: change.after, existed: change.before != null }])
    .map(field => field.field.split('/')[1]))
  return labels.filter(([key]) => changed.has(key)).map(([key, label]) => ({ key, label, before: display(key, before[key], 'before'), after: display(key, after[key], 'after') }))
}
