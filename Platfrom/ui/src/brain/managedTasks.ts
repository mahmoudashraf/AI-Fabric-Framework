import { isJsonObject, type JsonObject } from '../actions/actionDefinitionModel'
import { parseExactObject, stringifyExactJson } from '../lib/losslessJson'

export type ManagedTask = {
  code: string; name: string; description: string; instructions: string
  inputSchema: JsonObject; outputMode: 'JSON' | 'TEXT'; outputSchema?: JsonObject
  vectorSpaces: string[]; readActions: string[]; deadlineSeconds: number; maxOutputTokens: number
}
export type TaskEditor = Omit<ManagedTask, 'inputSchema' | 'outputSchema'> & {
  key: string; inputSchemaText: string; outputSchemaText: string
}
export type TasksEditor = { enabled: boolean; tasks: TaskEditor[] }
export const fieldTypes = ['string', 'number', 'integer', 'boolean', 'object', 'array'] as const
export const TASK_LIMIT = 16
export const SCHEMA_BYTES = 16 * 1024
const objectSchema = () => ({ type: 'object', properties: {} })
const answerSchema = () => ({ type: 'object', properties: { answer: { type: 'string' } }, required: ['answer'] })
export const taskCodeValid = (code: string) => /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/.test(code) && code.length <= 40

export function newTask(): TaskEditor {
  return { key: crypto.randomUUID(), code: '', name: '', description: '', instructions: '',
    inputSchemaText: stringifyExactJson(objectSchema(), 2), outputMode: 'JSON',
    outputSchemaText: stringifyExactJson(answerSchema(), 2), vectorSpaces: [], readActions: [],
    deadlineSeconds: 45, maxOutputTokens: 1200 }
}

export function readTasksEditor(behavior: unknown): TasksEditor {
  if (behavior != null && !isJsonObject(behavior)) throw new Error('Behaviour configuration must be an object. Repair it in Advanced configuration first.')
  const config = isJsonObject(behavior) ? behavior.managedTasks : undefined
  if (config === undefined) return { enabled: false, tasks: [] }
  if (!isJsonObject(config) || typeof config.enabled !== 'boolean' || !Array.isArray(config.tasks)) {
    throw new Error('Managed Tasks configuration is invalid. Repair it in Advanced configuration first.')
  }
  const tasks = config.tasks.map(value => {
    if (!isJsonObject(value)) throw new Error('Each task must be an object. Repair it in Advanced configuration first.')
    for (const field of ['code', 'name', 'description', 'instructions']) {
      if (typeof value[field] !== 'string') throw new Error(`Task ${field} must be text. Repair it in Advanced configuration first.`)
    }
    for (const field of ['vectorSpaces', 'readActions']) {
      if (!Array.isArray(value[field]) || value[field].some(item => typeof item !== 'string')) throw new Error(`Task ${field} must be a list of names.`)
    }
    if (!['JSON', 'TEXT'].includes(String(value.outputMode)) || typeof value.deadlineSeconds !== 'number' || typeof value.maxOutputTokens !== 'number') {
      throw new Error('Task output mode and execution limits are invalid. Repair them in Advanced configuration first.')
    }
    return { key: crypto.randomUUID(), code: value.code as string, name: value.name as string,
      description: value.description as string, instructions: value.instructions as string,
      outputMode: value.outputMode as 'JSON' | 'TEXT', vectorSpaces: [...value.vectorSpaces as string[]],
      readActions: [...value.readActions as string[]], deadlineSeconds: value.deadlineSeconds as number,
      maxOutputTokens: value.maxOutputTokens as number,
      inputSchemaText: stringifyExactJson(value.inputSchema ?? null, 2),
      outputSchemaText: stringifyExactJson(value.outputSchema ?? answerSchema(), 2) }
  })
  return { enabled: config.enabled, tasks }
}

export function parseTaskSchema(source: string, label: string): JsonObject {
  const schema = parseExactObject(source, label)
  if (new TextEncoder().encode(stringifyExactJson(schema)).length > SCHEMA_BYTES) throw new Error(`${label} must be at most 16 KiB.`)
  if (schema.type !== 'object') throw new Error(`${label} must have type "object".`)
  if (schema.properties !== undefined && !isJsonObject(schema.properties)) throw new Error(`${label} properties must be an object.`)
  if (schema.required !== undefined && (!Array.isArray(schema.required) || schema.required.some(item => typeof item !== 'string'))) {
    throw new Error(`${label} required must be a list of field names.`)
  }
  const inspect = (value: unknown): void => {
    if (!isJsonObject(value)) return
    for (const [key, entry] of Object.entries(value)) {
      if (['$id', '$dynamicRef', '$dynamicAnchor', '$recursiveRef', '$recursiveAnchor'].includes(key)) throw new Error(`${label}: external identifiers and dynamic references are not supported.`)
      if (key === '$ref' && (typeof entry !== 'string' || entry !== '#' && !entry.startsWith('#/'))) throw new Error(`${label}: use local JSON-pointer references only, such as #/$defs/item.`)
      if (['properties', 'patternProperties', '$defs', 'definitions', 'dependentSchemas'].includes(key) && isJsonObject(entry)) Object.values(entry).forEach(inspect)
      else if (['allOf', 'anyOf', 'oneOf', 'prefixItems'].includes(key) && Array.isArray(entry)) entry.forEach(inspect)
      else if (['items', 'additionalProperties', 'unevaluatedProperties', 'unevaluatedItems', 'contains', 'propertyNames', 'not', 'if', 'then', 'else', 'contentSchema'].includes(key)) {
        if (Array.isArray(entry)) entry.forEach(inspect); else inspect(entry)
      }
    }
  }
  inspect(schema)
  return schema
}

export function taskErrors(task: TaskEditor, tasks: TaskEditor[]): string[] {
  const errors: string[] = []
  if (!taskCodeValid(task.code)) errors.push('Code: use lowercase letters, numbers and single hyphens, up to 40 characters.')
  if (tasks.some(other => other.key !== task.key && other.code === task.code)) errors.push('Code must be unique in this Brain.')
  if (!task.name.trim() || task.name.length > 120) errors.push('Name is required, up to 120 characters.')
  if (!task.description.trim() || task.description.length > 500) errors.push('Description is required, up to 500 characters.')
  if (!task.instructions.trim() || task.instructions.length > 8000) errors.push('Instructions are required, up to 8,000 characters.')
  if (!Number.isInteger(task.deadlineSeconds) || task.deadlineSeconds < 5 || task.deadlineSeconds > 120) errors.push('Deadline must be a whole number from 5 to 120 seconds.')
  if (!Number.isInteger(task.maxOutputTokens) || task.maxOutputTokens < 128 || task.maxOutputTokens > 4096) errors.push('Output limit must be a whole number from 128 to 4,096 tokens.')
  for (const [label, source] of [['Input schema', task.inputSchemaText], ...(task.outputMode === 'JSON' ? [['Output schema', task.outputSchemaText]] : [])]) {
    try { parseTaskSchema(source, label) } catch (error) { errors.push(error instanceof Error ? error.message : `${label} is invalid.`) }
  }
  return errors
}

export function tasksErrors(editor: TasksEditor): string[] {
  return [...(editor.enabled && editor.tasks.length === 0 ? ['Add at least one task before enabling Managed Tasks.'] : []),
    ...(editor.tasks.length > TASK_LIMIT ? ['A Brain supports at most 16 tasks.'] : []),
    ...editor.tasks.flatMap((task, index) => taskErrors(task, editor.tasks).map(error => `${task.name || `Task ${index + 1}`}: ${error}`))]
}

// Own only managedTasks. Runtime-compiled fields and hashes never originate in this editor.
export function taskBehaviorPatch(behavior: unknown, editor: TasksEditor): JsonObject {
  const errors = tasksErrors(editor)
  if (errors.length) throw new Error(errors[0])
  if (behavior != null && !isJsonObject(behavior)) throw new Error('Behaviour configuration must be an object.')
  return { ...(behavior ?? {}) as JsonObject, managedTasks: { enabled: editor.enabled, tasks: editor.tasks.map(task => ({
    code: task.code, name: task.name, description: task.description, instructions: task.instructions,
    inputSchema: parseTaskSchema(task.inputSchemaText, 'Input schema'), outputMode: task.outputMode,
    ...(task.outputMode === 'JSON' ? { outputSchema: parseTaskSchema(task.outputSchemaText, 'Output schema') } : {}),
    vectorSpaces: task.vectorSpaces, readActions: task.readActions,
    deadlineSeconds: task.deadlineSeconds, maxOutputTokens: task.maxOutputTokens,
  })) } }
}

export function tasksIdentity(editor: TasksEditor): string {
  return JSON.stringify({ ...editor, tasks: editor.tasks.map(({ key: _key, ...task }) => task) })
}

export function managedTasksConfigIdentity(behavior: unknown): string {
  const identity = (value: unknown): string => {
    if (Array.isArray(value)) return `[${value.map(identity).join(',')}]`
    if (isJsonObject(value)) return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${identity(value[key])}`).join(',')}}`
    return value === undefined ? 'undefined' : stringifyExactJson(value)
  }
  return identity(isJsonObject(behavior) ? behavior.managedTasks : undefined)
}

export function editSchemaField(source: string, name: string, operation: { type?: string; required?: boolean; add?: boolean; remove?: boolean }): string {
  const schema = parseTaskSchema(source, 'Schema')
  const properties = { ...(schema.properties as JsonObject ?? {}) }
  const exists = Object.prototype.hasOwnProperty.call(properties, name)
  if (!name.trim()) throw new Error('Enter a field name.')
  if (operation.add && exists) throw new Error('This field already exists.')
  if (!operation.add && !exists) throw new Error('This field no longer exists.')
  let required = [...(schema.required as string[] ?? [])]
  if (operation.remove) { delete properties[name]; required = required.filter(field => field !== name) }
  else {
    const previous = properties[name]
    if (operation.add || operation.type) {
      if (!operation.add && !isJsonObject(previous)) throw new Error('Edit this advanced field in JSON.')
      const next: JsonObject = { ...(isJsonObject(previous) ? previous : {}), ...(operation.type ? { type: operation.type } : {}) }
      if (operation.type === 'array' && next.items === undefined) next.items = { type: 'string' }
      Object.defineProperty(properties, name, { value: next, enumerable: true, configurable: true, writable: true })
    }
    if (operation.required === true && !required.includes(name)) required.push(name)
    if (operation.required === false) required = required.filter(field => field !== name)
  }
  return stringifyExactJson({ ...schema, properties, ...(schema.required !== undefined || required.length ? { required } : {}) }, 2)
}

export function taskTestBlock(input: {
  dirty: boolean; enabled: boolean; targetProfileId: string; releaseVersionId?: string | null
  draftRevision?: number; workspaceRevision?: number; savedDraftMatchesLatestPublished?: boolean
  latestPublishedVersionId?: string | null; hasAppliedTask: boolean
}): string | null {
  if (input.dirty) return 'Save and apply your task changes before testing.'
  if (!input.enabled) return 'Enable Managed Tasks, then save and apply an update before testing.'
  if (!input.targetProfileId || !input.releaseVersionId) return 'Apply a version to this environment before testing.'
  if (!input.savedDraftMatchesLatestPublished || input.draftRevision !== input.workspaceRevision
    || input.latestPublishedVersionId !== input.releaseVersionId) return 'This draft is newer than, or different from, the applied version. Publish and apply your changes in Updates first.'
  if (!input.hasAppliedTask) return 'This task is not available in the applied version. Apply an update first.'
  return null
}

export function taskRuntimeExample(runtimeBaseUrl: string | null, code: string, input: JsonObject): string {
  let base = '<runtime-base-url>'
  if (runtimeBaseUrl) {
    try { const url = new URL(runtimeBaseUrl); if (['https:', 'http:'].includes(url.protocol) && !url.username && !url.password && !url.search && !url.hash) base = url.toString().replace(/\/$/, '') } catch { /* Display a placeholder for invalid metadata. */ }
  }
  const quoted = (value: string) => `'${value.replace(/'/g, `'"'"'`)}'`
  return [
    `curl --request POST ${quoted(`${base}/api/tasks/v1/tasks/${encodeURIComponent(code)}/execute`)}`,
    "  --header 'X-AIFABRIC-RUNTIME-API-KEY: <private-runtime-api-key>'",
    "  --header 'X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer <private-runtime-token>'",
    "  --header 'Content-Type: application/json'",
    `  --data ${quoted(stringifyExactJson({ input }, 2))}`,
  ].join(' \\\n')
}
