import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import { brainCases } from './area-matrix.mjs'
import { resourceCases } from './resource-area-matrix.mjs'

test('live matrix covers every published Build with AI task with its actual profile', () => {
  const template = JSON.parse(fs.readFileSync(new URL('../../Platfrom/backend/src/main/resources/assistant/configuration-assistant-workflow-v1.json', import.meta.url)))
  const cases = [...brainCases, ...resourceCases]
  assert.equal(new Set(cases.map(c => c.id)).size, cases.length)
  assert.equal(new Set(cases.map(c => c.task)).size, cases.length)
  assert.deepEqual(cases.map(c => c.task).sort(), template.taskBindings.map(b => b.task).sort())
  for (const c of cases) {
    assert.equal(c.profile, template.taskBindings.find(b => b.task === c.task).profileId)
    assert.equal(typeof c.auto, 'string'); assert.equal(typeof c.quick, 'string')
    assert.equal(typeof c.verify, 'function')
  }
  assert.equal(cases.length, 13)
})

test('Kit guidance remains explicitly read-only rather than being counted as autonomous authoring', () => {
  const guide = brainCases.find(c => c.task === 'KIT')
  assert.equal(guide.readOnly, true)
  assert.deepEqual(guide.fields, [])
  assert.equal(guide.profile, 'guide')
})
