import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'

// Authoring-time reference data, not runtime authorization or action selection.
export function buildPublicActionReference(config, { maxActions = 20, maxChars = 8000, publicWorkflowNames = [] } = {}) {
  assert.ok(Array.isArray(config?.actions), 'An explicit deployment action configuration is required')
  assert.ok(Number.isInteger(maxActions) && maxActions > 0 && maxActions <= 100)
  assert.ok(Number.isInteger(maxChars) && maxChars >= 512 && maxChars <= 32000)
  assert.ok(Array.isArray(publicWorkflowNames) && publicWorkflowNames.every(name => typeof name === 'string'))
  for (const name of publicWorkflowNames) assert.ok(config.actions.some(action => action.name === name), 'Unknown public workflow')
  const candidates = config.actions.filter(action => action.anonymousAllowed === true || publicWorkflowNames.includes(action.name))
    .map(action => ({
      name: action.name,
      description: action.description,
      accessMode: action.accessMode,
      ...(typeof action.anonymousAllowed === 'boolean' ? { authenticationRequired: !action.anonymousAllowed } : {}),
      ...(typeof action.requiresConfirmation === 'boolean' ? { requiresConfirmation: action.requiresConfirmation } : {}),
      inputs: (Array.isArray(action.params) ? action.params : [])
        .filter(param => param.askUser !== false && !['INTERNAL', 'SYSTEM', 'SECRET'].includes(String(param.visibility ?? '').toUpperCase()))
        .map(param => ({ name: param.name, description: param.description, type: param.type,
          ...(typeof param.required === 'boolean' ? { required: param.required } : {}) })),
    }))
  const reference = {
    source: 'Deployment-owned public action configuration',
    sourceConfigurationSha256: createHash('sha256').update(JSON.stringify(config)).digest('hex'),
    scope: 'Public workflow descriptions only; runtime authorization still controls execution',
    actions: [],
    omittedActions: candidates.length,
  }
  for (const candidate of candidates.slice(0, maxActions)) {
    const next = { ...reference, actions: [...reference.actions, candidate], omittedActions: candidates.length - reference.actions.length - 1 }
    if (JSON.stringify(next).length > maxChars) break
    Object.assign(reference, next)
  }
  assert.ok(candidates.length === 0 || reference.actions.length > 0, 'Reference budget cannot fit a public action')
  return reference
}
