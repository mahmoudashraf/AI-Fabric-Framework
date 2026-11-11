import assert from 'node:assert/strict'
import test from 'node:test'
import { hostedRuntimeTarget } from './hostedTargets.mjs'

const profiles = ['staging', 'production'].flatMap(environmentName => [
  { id: `dtp-coolify-${environmentName}`, environmentName, active: true, defaultForRuntime: true, sourceStrategy: 'GENERATED_SOURCE' },
  { id: `dtp-coolify-${environmentName}-behavior`, environmentName, active: true, defaultForRuntime: false, sourceStrategy: 'IMAGE_SOURCE' },
])

test('four real profile choices resolve to the explicitly qualified immutable destinations', () => {
  assert.equal(hostedRuntimeTarget(profiles, 'staging'), 'dtp-coolify-staging-behavior')
  assert.equal(hostedRuntimeTarget(profiles, 'production'), 'dtp-coolify-production-behavior')
})
test('qualification never silently falls back to a mutable or inactive destination', () => {
  assert.throws(() => hostedRuntimeTarget(profiles.filter(value => !value.id.endsWith('-behavior')), 'production'), /missing/)
  assert.throws(() => hostedRuntimeTarget(profiles.map(value => ({ ...value, active: false })), 'production'), /active/)
  assert.throws(() => hostedRuntimeTarget(profiles.map(value => ({ ...value, sourceStrategy: 'GENERATED_SOURCE' })), 'production'), /immutable runtime artifact/)
})
