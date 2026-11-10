import { isExactNumber, parseExactJson, parseExactObject, stringifyExactJson } from '../lib/losslessJson'

export type JsonObject = Record<string, unknown>

// ConnectorActionDefinition / ConnectorActionParamDefinition, framework 0.8.46.
export const ACTION_ACCESS_MODES = ['READ', 'READ_WRITE', 'WRITE_ONLY'] as const
export const ACTION_PARAM_TYPES = ['STRING', 'INTEGER', 'NUMBER', 'BOOLEAN', 'OBJECT', 'ARRAY', 'UNKNOWN'] as const
export const ACTION_PARAM_SOURCES = ['USER', 'INTERNAL'] as const
export const ACTION_RESULT_HINTS = ['DEFAULT', 'LIST', 'DETAIL', 'STATUS'] as const
export const ACTION_RENAME_MESSAGE = 'Action names cannot be changed here until route and other dependency renaming is supported.'
export type ActionParameterJsonField = 'defaultValue' | 'allowedValues' | 'resolveFrom' | 'items' | 'properties' | 'requiredProperties'

export function parseActionParameterJson(field: ActionParameterJsonField, source: string): unknown {
  const value = parseExactJson(source)
  if (field === 'defaultValue') return value
  if (field === 'allowedValues' || field === 'requiredProperties') {
    if (!Array.isArray(value) || value.some(item => typeof item !== 'string')) throw new Error(`${field} must be an array of strings.`)
  } else if (!isJsonObject(value)) {
    throw new Error(`${field} must be a JSON object.`)
  } else if (field === 'properties' && Object.values(value).some(item => !isJsonObject(item))) {
    throw new Error('Each properties entry must be a parameter definition object.')
  }
  return value
}

export function parseActionParameterInteger(source: string): unknown {
  const raw = source.trim()
  if (!raw) return undefined
  try {
    const value = parseExactJson(raw)
    if (typeof value !== 'number' && !isExactNumber(value)) throw new Error()
    const integer = BigInt(raw)
    // Connector min/max are signed Java Long values, not JavaScript doubles.
    if (integer < -9223372036854775808n || integer > 9223372036854775807n) throw new Error()
    return value
  } catch {
    throw new Error('Enter a signed 64-bit integer, or leave blank. Invalid input is not applied.')
  }
}

export function isJsonObject(value: unknown): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value) && !isExactNumber(value)
}

export function readActionCatalogue(editorValue: string): { root: JsonObject; actions: unknown[] } {
  const root = parseExactObject(editorValue, 'The actions configuration')
  if (root.actions !== undefined && !Array.isArray(root.actions)) {
    throw new Error('The actions field must be an array.')
  }
  return { root, actions: Array.isArray(root.actions) ? root.actions : [] }
}

function selectedAction(actions: unknown[], index: number): JsonObject {
  const action = actions[index]
  if (!isJsonObject(action)) throw new Error('This action must be a JSON object. Repair it in Advanced catalogue JSON.')
  return action
}

function writeActions(root: JsonObject, actions: unknown[]): string {
  return stringifyExactJson({ ...root, actions }, 2)
}

export function replaceActionDefinition(editorValue: string, index: number, replacement: unknown): string {
  const { root, actions } = readActionCatalogue(editorValue)
  const previous = selectedAction(actions, index)
  if (!isJsonObject(replacement)) throw new Error('An action definition must be a JSON object.')
  // Names are dependency keys, not labels. Do not leave routes/interceptors pointing at an old name.
  if (replacement.name !== previous.name) throw new Error(ACTION_RENAME_MESSAGE)
  return writeActions(root, actions.map((action, i) => i === index ? replacement : action))
}

export function patchActionDefinition(editorValue: string, index: number, patch: JsonObject): string {
  const { actions } = readActionCatalogue(editorValue)
  return replaceActionDefinition(editorValue, index, { ...selectedAction(actions, index), ...patch })
}

export function actionNameError(name: string, actions: unknown[]): string | null {
  if (!name.trim()) return 'Action name is required, for example lookup_order.'
  const normalized = name.trim().toLowerCase()
  if (actions.some(action => isJsonObject(action) && typeof action.name === 'string'
    && action.name.trim().toLowerCase() === normalized)) return 'An action with this name already exists.'
  return null
}

export function appendActionDefinition(editorValue: string, name: string): string {
  const { root, actions } = readActionCatalogue(editorValue)
  const error = actionNameError(name, actions)
  if (error) throw new Error(error)
  return writeActions(root, [...actions, {
    name: name.trim(), accessMode: 'READ', anonymousAllowed: false,
    requiresConfirmation: false, groundingEligible: true,
    readActionResolutionEligible: false, params: [],
  }])
}

export function actionRemovalBlock(editorValue: string, index: number, routedActionNames: string[] = []): string | null {
  const { root, actions } = readActionCatalogue(editorValue)
  const action = actions[index]
  if (!isJsonObject(action) || typeof action.name !== 'string') return null
  const name = action.name.trim().toLowerCase()
  const matches = (value: unknown) => typeof value === 'string' && value.trim().toLowerCase() === name
  if (routedActionNames.some(matches)) return 'This action has a connector route. Clear its route target in HTTP routes & connector and save the draft before removing it.'
  const interceptors = Array.isArray(root.confirmationInterceptors) ? root.confirmationInterceptors : []
  for (const interceptor of interceptors) {
    if (!isJsonObject(interceptor)) continue
    const trigger = isJsonObject(interceptor.trigger) ? interceptor.trigger : {}
    const decision = isJsonObject(interceptor.decision) ? interceptor.decision : {}
    const stack = isJsonObject(interceptor.stack) ? interceptor.stack : {}
    if (matches(decision.action)
      || (Array.isArray(trigger.pendingActions) && trigger.pendingActions.some(matches))
      || (Array.isArray(stack.popPreviousIfActionIn) && stack.popPreviousIfActionIn.some(matches))) {
      return 'A confirmation interceptor references this action. Update that dependency in Advanced catalogue JSON before removing it.'
    }
  }
  return null
}

export function removeActionDefinition(editorValue: string, index: number, routedActionNames: string[] = []): string {
  const { root, actions } = readActionCatalogue(editorValue)
  if (index < 0 || index >= actions.length) throw new Error('Select an action to remove.')
  const error = actionRemovalBlock(editorValue, index, routedActionNames)
  if (error) throw new Error(error)
  return writeActions(root, actions.filter((_, i) => i !== index))
}

export function readActionParams(action: JsonObject): unknown[] {
  if (action.params === undefined) return []
  if (!Array.isArray(action.params)) throw new Error('The params field must be an array. Repair it in Advanced action JSON.')
  return action.params
}

export function patchActionParameter(editorValue: string, actionIndex: number, paramIndex: number, patch: JsonObject): string {
  const { actions } = readActionCatalogue(editorValue)
  const params = readActionParams(selectedAction(actions, actionIndex))
  if (!isJsonObject(params[paramIndex])) throw new Error('This parameter must be a JSON object. Repair it in Advanced action JSON.')
  return patchActionDefinition(editorValue, actionIndex, {
    params: params.map((param, index) => index === paramIndex ? { ...param as JsonObject, ...patch } : param),
  })
}

export function appendActionParameter(editorValue: string, actionIndex: number): string {
  const { actions } = readActionCatalogue(editorValue)
  const params = readActionParams(selectedAction(actions, actionIndex))
  let counter = 1
  while (params.some(param => isJsonObject(param) && param.name === `parameter_${counter}`)) counter++
  return patchActionDefinition(editorValue, actionIndex, {
    params: [...params, { name: `parameter_${counter}`, type: 'STRING', required: false, visibility: 'USER' }],
  })
}

export function removeActionParameter(editorValue: string, actionIndex: number, paramIndex: number): string {
  const { actions } = readActionCatalogue(editorValue)
  const params = readActionParams(selectedAction(actions, actionIndex))
  return patchActionDefinition(editorValue, actionIndex, { params: params.filter((_, index) => index !== paramIndex) })
}
