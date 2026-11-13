const groupKeys = ['tone', 'answerBreadth', 'answerLength', 'nextSteps']

export function resolveManagedBehaviour(catalogue, selection) {
  if (!catalogue || typeof catalogue !== 'object' || Array.isArray(catalogue)
    || catalogue.schemaVersion !== selection.schemaVersion || catalogue.revision !== selection.revision) {
    throw new Error('Unsupported behaviour catalogue schema or revision.')
  }
  if (!Array.isArray(catalogue.groups)) throw new Error('Behaviour catalogue groups must be an array.')
  if (!selection.selections || !Object.keys(selection.selections).length
    || Object.keys(selection.selections).some(key => !groupKeys.includes(key))) {
    throw new Error('Unsupported managed behaviour selection.')
  }
  const result = { schemaVersion: selection.schemaVersion, revision: selection.revision }
  for (const key of groupKeys.filter(key => Object.hasOwn(selection.selections, key))) {
    const groups = catalogue.groups.filter(group => group?.key === key)
    if (groups.length !== 1 || !Array.isArray(groups[0].options)) {
      throw new Error(`Behaviour catalogue requires one ${key} group with options.`)
    }
    const choices = groups[0].options.filter(option => option?.id === selection.selections[key])
    if (choices.length !== 1 || typeof choices[0].instructions !== 'string' || !choices[0].instructions.trim()) {
      throw new Error(`Behaviour catalogue requires one ${key} selection with instructions.`)
    }
    if (Object.hasOwn(choices[0], 'revision') && choices[0].revision !== selection.revision) {
      throw new Error('Unsupported behaviour catalogue option revision.')
    }
    result[key] = { presetId: selection.selections[key], revision: selection.revision, instructions: choices[0].instructions }
  }
  return result
}
