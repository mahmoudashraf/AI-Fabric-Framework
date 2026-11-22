import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {specialistDefaults,diagnosticManifest} from './build-specialist-defaults.mjs'
const read = path => JSON.parse(readFileSync(new URL(path,import.meta.url)))

test('bundled defaults match source profiles and immutable diagnostic snapshot',()=>{
  assert.deepEqual(specialistDefaults(),read('../../../Platfrom/backend/src/main/resources/platform-templates/support-specialist-defaults.json'))
  assert.deepEqual(diagnosticManifest(),read('../manifests/loomai-support-diagnostics.1.1.0.manifest.json'))
})
test('each plan is bounded to declared areas, private tools never leak into the reusable profile',()=>{
  const defaults=specialistDefaults()
  for(const profile of [defaults.generic,defaults.loomai]){
    assert.equal(profile.areas.length,5); assert.equal(profile.resolutions.length,5)
    const ids=new Set(profile.areas.map(a=>a.id))
    for(const plan of profile.resolutions)for(const id of plan.areaIds)assert.ok(ids.has(id))
    assert.equal(profile.limits.maxParallelWorkers,2);assert.equal(profile.limits.deadlineSeconds,75)
  }
  assert.ok(defaults.generic.areas.every(a=>a.readActions.length===0))
  const actions=diagnosticManifest().contributions.actions
  assert.equal(actions.length,6)
  for(const a of actions){assert.equal(a.readOnly,true);assert.equal(a.anonymousAllowed,false);assert.deepEqual(a.params,[]);assert.deepEqual(a.requiredScopes,['support:diagnostics:read'])}
})
