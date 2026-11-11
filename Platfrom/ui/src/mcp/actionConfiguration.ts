import { isJsonObject, readActionCatalogue, readActionParams, replaceActionDefinition, type JsonObject } from '../actions/actionDefinitionModel'
import { cloneExactJson, stringifyExactJson } from '../lib/losslessJson'

const nativeFields = ['adapterType', 'execution', 'inputSchema', 'outputSchema', 'route'] as const
const collectionFields = new Set(['description', 'visibility', 'askUser', 'sensitive', 'defaultValue', 'resolveFrom'])

export function mcpActionDestination(action: JsonObject | null): JsonObject | null {
  if (!action) return null
  const execution = isJsonObject(action.execution) ? action.execution : {}
  if (action.adapterType !== 'mcp-tool' && execution.adapterType !== 'mcp-tool') return null
  return isJsonObject(execution.mcp) ? execution.mcp : {}
}

export function mcpLocalSettings(action: JsonObject): JsonObject {
  return cloneExactJson(Object.fromEntries(Object.entries(action).filter(([key]) => !nativeFields.some(field => field === key))))
}

function canonical(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonical)
  return isJsonObject(value) ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value
}

function parameterContract(action: JsonObject): unknown {
  return readActionParams(action).map(param => isJsonObject(param)
    ? Object.fromEntries(Object.entries(param).filter(([key]) => !collectionFields.has(key))) : param)
    .map(value => stringifyExactJson(canonical(value))).sort()
}

export function replaceMcpLocalSettings(editorValue: string, index: number, replacement: unknown): string {
  const current = readActionCatalogue(editorValue).actions[index]
  if (!isJsonObject(current) || !mcpActionDestination(current)) throw new Error('Select an imported MCP action.')
  if (!isJsonObject(replacement)) throw new Error('Local action settings must be a JSON object.')
  // Native schema and destination are discovered execution contracts, not local collection hints.
  if (nativeFields.some(field => Object.prototype.hasOwnProperty.call(replacement, field))) {
    throw new Error('MCP destination and native schemas are read-only here. Manage the connection or refresh its tools in MCP connections.')
  }
  if (stringifyExactJson(parameterContract(current)) !== stringifyExactJson(parameterContract(replacement))) {
    throw new Error('Imported parameter names, types, requirements and schemas are read-only. Change only local collection hints, defaults or trusted resolvers, or refresh tools in MCP connections.')
  }
  const next = { ...replacement }
  for (const field of nativeFields) if (Object.prototype.hasOwnProperty.call(current, field)) next[field] = current[field]
  return replaceActionDefinition(editorValue, index, next)
}
