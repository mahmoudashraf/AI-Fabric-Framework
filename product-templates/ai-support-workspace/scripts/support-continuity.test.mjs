import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = version => JSON.parse(readFileSync(new URL(`../manifests/loomai-support-diagnostics.${version}.manifest.json`, import.meta.url)))

test('conversation diagnostics retain exact registered tools and expose only safe selection requirements', () => {
  const previous = read('1.0.3'), current = read('1.0.4')
  assert.equal(current.pluginId, previous.pluginId)
  assert.deepEqual(current.installForm, previous.installForm)
  assert.deepEqual(current.contributions.actions.map(a => a.actionId), previous.contributions.actions.map(a => a.actionId))
  for (const action of current.contributions.actions) {
    const old = previous.contributions.actions.find(a => a.actionId === action.actionId)
    assert.equal(action.readOnly,true)
    assert.equal(action.anonymousAllowed,false)
    assert.deepEqual(action.route,old.route)
    assert.deepEqual(action.requiredScopes,old.requiredScopes)
    assert.deepEqual(action.llmFacts.copyFields,[...old.llmFacts.copyFields,'requirement'])
    assert.deepEqual(action.params,[])
  }
})
