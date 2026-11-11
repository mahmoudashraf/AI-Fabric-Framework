import assert from 'node:assert/strict'

const runtimeTargets = {
  staging: 'dtp-coolify-staging-behavior',
  production: 'dtp-coolify-production-behavior',
}

// The qualification canary selects these exact authorized destinations through the Brain header URL.
export function hostedRuntimeTarget(profiles, environment) {
  const id = runtimeTargets[environment]
  assert.ok(id, 'Only the staging and production qualification environments are supported')
  const profile = profiles.find(value => value.id === id)
  assert.ok(profile, `The authorized immutable ${environment} destination is missing`)
  assert.equal(profile.active, true, 'The qualification destination must be active')
  assert.equal(profile.environmentName, environment)
  assert.equal(profile.sourceStrategy, 'IMAGE_SOURCE', 'This qualification requires the immutable runtime artifact destination')
  return profile.id
}
