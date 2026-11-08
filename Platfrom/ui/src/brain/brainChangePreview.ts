import type { AiBehaviourCatalogue } from '../api/platformApi'
import { AI_BEHAVIOUR_GROUP_KEYS, AI_BEHAVIOUR_PROMPT_KEYS, promptConfigIdentity, readAiBehaviour } from '../aiBehaviourProfiles'

export type PreviewBaseline = 'applied' | 'new' | 'unavailable'
export type PreviewValue = { id: string; label: string; current: string; proposed: string; kind: 'Added' | 'Removed' | 'Changed' }
export type ChangePreview = {
  title: string; impact: string; editor: string | null; editorLabel: string
  values: PreviewValue[]; fieldChanges: Array<{ path: string; change: string }>; baseline: PreviewBaseline
}

const areas: Record<string, [string, string, string | null, string]> = {
  promptConfig: ['How your AI replies', 'The reply guidance used by this Smart Brain.', 'responses', 'Responses'],
  actionsConfig: ['What your AI can do', 'The capabilities available to your AI and their declared requirements.', 'capabilities/actions', 'AI capabilities'],
  entityConfig: ['How knowledge is organised', 'The configured knowledge collections. This does not show live indexing status.', 'knowledge', 'Knowledge'],
  knowledgeSourceConfig: ['What your AI knows', 'The configured knowledge sources. Adding a source does not prove its documents are indexed.', 'knowledge', 'Knowledge'],
  providerConfig: ['AI models & search', 'The models and search settings used after this version is applied.', 'settings/models', 'AI models'],
  routingConfig: ['Where capabilities connect', 'The saved connections used to reach integrated services.', 'capabilities/actions', 'Capability connections'],
  securityConfig: ['Who can access your AI', 'The access settings applied to this Smart Brain.', 'settings/access', 'Access settings'],
  shellConfig: ['What customers see', 'The workspace defaults in this version. Engage app settings remain separate.', 'channels', 'Channels'],
  marketplaceDatasetConfig: ['Connected datasets', 'The dataset configuration supplied to this Smart Brain.', 'knowledge', 'Knowledge'],
  behaviorConfig: ['How conversations run', 'The saved conversation and execution settings.', null, 'AI behaviour'],
}

function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

export function sectionPreviewBaseline(current: unknown, key: string, baseline: PreviewBaseline): PreviewBaseline {
  return baseline === 'applied' && !Object.prototype.hasOwnProperty.call(record(current), key) ? 'unavailable' : baseline
}

export function configurationFieldChanges(current: unknown, next: unknown, path = '$'): Array<{ path: string; change: string }> {
  if (promptConfigIdentity(current) === promptConfigIdentity(next)) return []
  if (current && next && typeof current === 'object' && typeof next === 'object' && !Array.isArray(current) && !Array.isArray(next)) {
    return [...new Set([...Object.keys(current), ...Object.keys(next)])].sort()
      .flatMap(key => configurationFieldChanges(record(current)[key], record(next)[key], `${path}.${key}`))
  }
  return [{ path, change: current === undefined ? 'Added' : next === undefined ? 'Removed' : 'Changed' }]
}

function text(value: unknown): string | undefined { return typeof value === 'string' && value.trim() ? value : undefined }
function scalar(value: unknown): string | undefined {
  return text(value) ?? (typeof value === 'boolean' ? value ? 'Enabled' : 'Disabled' : typeof value === 'number' ? String(value) : undefined)
}
function booleanLabel(value: unknown, yes: string, no: string): string | undefined { return typeof value === 'boolean' ? value ? yes : no : undefined }
function editablePrompt(config: Record<string, unknown>, key: string): string | undefined {
  const metadata = record(config.aiBehaviour)
  const source = record(metadata.sourcePrompts)[key], expanded = record(metadata.expandedPrompts)[key]
  // Match the compiler's exact provenance check, not wording or semantic similarity.
  return readAiBehaviour(config) && typeof source === 'string' && typeof expanded === 'string' && config[key] === expanded ? text(source) : text(config[key])
}

type NamedEntry = { id: string | undefined; label: string | undefined; value: Record<string, unknown> }
function entries(key: string, config: Record<string, unknown>): NamedEntry[] {
  if (key === 'entityConfig' && Object.prototype.hasOwnProperty.call(config, 'ai-entities')) {
    return Object.entries(record(config['ai-entities'])).map(([id, value]) => ({ id, label: text(record(value).displayName) ?? id, value: record(value) }))
  }
  const field = key === 'actionsConfig' ? 'actions' : key === 'entityConfig' ? 'entities' : 'sources'
  return Array.isArray(config[field]) ? config[field].map(value => {
    const row = record(value)
    return { id: text(key === 'actionsConfig' ? row.name : key === 'entityConfig' ? row.entityType : row.id ?? row.sourceId),
      label: text(row.displayName) ?? text(row.attributionLabel), value: row }
  }) : []
}

// Project only declared presentation fields. Never render arbitrary config values or credential references.
export function buildChangePreview(key: string, current: unknown, proposed: unknown, baseline: PreviewBaseline, catalogue?: AiBehaviourCatalogue): ChangePreview {
  const [title, impact, editor, editorLabel] = areas[key] ?? [key, 'Saved configuration for this area.', 'settings/technical-files', 'Technical settings']
  const result: ChangePreview = { title, impact, editor, editorLabel, values: [],
    fieldChanges: baseline === 'unavailable' ? [] : configurationFieldChanges(baseline === 'new' ? undefined : current, proposed), baseline }
  const before = record(current), after = record(proposed)
  const add = (id: string, label: string, left: string | undefined, right: string | undefined, changed = left !== right) => {
    if (baseline === 'applied' && !changed || left === undefined && right === undefined) return
    result.values.push({ id, label, current: baseline === 'unavailable' ? 'Unavailable' : baseline === 'new' ? 'Not applied' : left ?? 'Not configured',
      proposed: right ?? 'Not configured', kind: baseline !== 'applied' || left === undefined ? 'Added' : right === undefined ? 'Removed' : 'Changed' })
  }
  if (key === 'promptConfig') {
    const oldMetadata = readAiBehaviour(current), newMetadata = readAiBehaviour(proposed)
    for (const groupKey of AI_BEHAVIOUR_GROUP_KEYS) {
      const group = catalogue?.groups.find(item => item.key === groupKey)
      const label = group?.label ?? ({ tone: 'Tone', answerBreadth: 'Answer scope', answerLength: 'Answer length', nextSteps: 'Next steps' })[groupKey]
      const oldProfile = oldMetadata?.[groupKey], newProfile = newMetadata?.[groupKey]
      const profileLabel = (profile: typeof oldProfile) => !profile ? undefined : profile.presetId === 'custom' ? 'Custom'
        : group?.options.find(option => option.id === profile.presetId)?.label ?? 'Saved profile (label unavailable)'
      add(`profile:${groupKey}`, label, profileLabel(oldProfile), profileLabel(newProfile), oldProfile?.presetId !== newProfile?.presetId)
      add(`instructions:${groupKey}`, `${label} instructions`, text(oldProfile?.instructions), text(newProfile?.instructions))
    }
    const labels: Record<string, string> = { systemPrompt: 'General instructions', intentExtractionPrompt: 'Understanding requests', actionSelectionPrompt: 'Choosing capabilities',
      clarificationPrompt: 'Asking for details', answerGenerationPrompt: 'Writing answers', retrievalPrompt: 'Using knowledge', assistantUiPrompt: 'Workspace reply guidance' }
    for (const field of AI_BEHAVIOUR_PROMPT_KEYS) {
      const left = editablePrompt(before, field), right = editablePrompt(after, field)
      if (left !== right) add(field, labels[field], left ?? 'Generated/default guidance', right ?? 'Generated/default guidance on publish', true)
    }
  }
  if (['actionsConfig', 'entityConfig', 'knowledgeSourceConfig'].includes(key)) {
    const noun = key === 'actionsConfig' ? 'Capability' : key === 'entityConfig' ? 'Collection' : 'Source'
    const oldRows = entries(key, before), newRows = entries(key, after)
    const unique = (rows: NamedEntry[]) => rows.every(row => row.id) && new Set(rows.map(row => row.id)).size === rows.length
    // Without stable unique identities, report the list change without guessing which item changed.
    if (unique(oldRows) && unique(newRows)) {
      const oldMap = new Map(oldRows.map(row => [row.id, row])), newMap = new Map(newRows.map(row => [row.id, row]))
      for (const id of new Set([...oldMap.keys(), ...newMap.keys()])) {
        const oldRow = oldMap.get(id), newRow = newMap.get(id)
        if (baseline === 'applied' && promptConfigIdentity(oldRow?.value) === promptConfigIdentity(newRow?.value)) continue
        const name = newRow?.label ?? oldRow?.label ?? `${noun} ${String(id)}`
        add(`item:${String(id)}`, name, oldRow ? 'Configured' : undefined, newRow ? 'Configured' : undefined, true)
        if (oldRow && newRow) add(`name:${String(id)}`, `${noun} name`, oldRow.label, newRow.label)
        if (key === 'actionsConfig') {
          for (const [fieldName, label, yes, no] of [['requiresConfirmation', 'Customer confirmation', 'Required', 'Not required'], ['anonymousAllowed', 'Anonymous access', 'Allowed', 'Not allowed']] as const) {
            if ([oldRow?.value[fieldName], newRow?.value[fieldName]].some(value => value !== undefined && typeof value !== 'boolean')) continue
            add(`${String(id)}:${fieldName}`, `${name} · ${label}`, booleanLabel(oldRow?.value[fieldName], yes, no), booleanLabel(newRow?.value[fieldName], yes, no))
          }
          const accessLabels: Record<string, string> = { READ: 'Read-only', WRITE_ONLY: 'Write', READ_WRITE: 'Read and write' }
          const accessLabel = (value: unknown) => typeof value === 'string' && Object.prototype.hasOwnProperty.call(accessLabels, value) ? accessLabels[value] : undefined
          if (![oldRow?.value.accessMode, newRow?.value.accessMode].some(value => value !== undefined && !accessLabel(value))) {
            add(`${String(id)}:accessMode`, `${name} · Capability access`, accessLabel(oldRow?.value.accessMode), accessLabel(newRow?.value.accessMode))
          }
        }
      }
    } else if (promptConfigIdentity(before) !== promptConfigIdentity(after)) {
      add('list', `${noun} list`, `${oldRows.length} configured`, `${newRows.length} configured`, true)
    }
  }
  const knownFields: Record<string, Array<[string, string]>> = {
    providerConfig: [['llmProvider', 'AI provider'], ['openaiModel', 'OpenAI model'], ['embeddingProvider', 'Embedding provider'], ['vectorDatabaseProvider', 'Search provider']],
    shellConfig: [['defaultConversationMode', 'Conversation mode']],
  }
  for (const [field, label] of knownFields[key] ?? []) add(field, label, scalar(before[field]), scalar(after[field]))
  if (key === 'shellConfig') {
    add('greeting.title', 'Welcome title', text(record(before.greeting).title), text(record(after.greeting).title))
    add('greeting.message', 'Welcome message', text(record(before.greeting).message), text(record(after.greeting).message))
  }
  return result
}

export function previewHeadline(preview: ChangePreview): string | null {
  const first = preview.values[0]
  if (!first) return null
  if (preview.baseline === 'unavailable') return `Saved draft: ${first.label}`
  if (first.id === 'list') return `${first.label} updated`
  if (first.kind === 'Added') return `+ ${first.label}`
  if (first.kind === 'Removed') return `- ${first.label}`
  if (first.id.startsWith('item:')) return `${first.label} updated`
  if (first.current === first.proposed) return `${first.label} updated`
  // Long instructions belong in the focused panel, not a truncated before/after claim.
  if (first.current.length > 65 || first.proposed.length > 65 || first.current.includes('\n') || first.proposed.includes('\n')) return `${first.label} updated`
  return `${first.current} → ${first.proposed}`
}
