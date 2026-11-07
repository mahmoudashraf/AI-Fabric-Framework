import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

const require = createRequire(import.meta.url)
async function load(path, dependencies = {}) {
  const result = {}
  new Function('require', 'exports', ts.transpileModule(await readFile(new URL(path, import.meta.url), 'utf8'), { compilerOptions: {
    target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText)(name => name in dependencies ? dependencies[name] : require(name), result)
  return result
}
const nav = await load('../src/brain/brainNavigation.ts')
const helper = await load('../src/brain/brainReleasePresentation.ts', { './brainNavigation': nav })
const grid = await load('../src/brain/BrainReleaseVersions.tsx', { './brainReleasePresentation': helper })
const picker = await load('../src/brain/BrainEnvironmentSwitch.tsx', { './brainReleasePresentation': helper })
const stage = { id: 'stage', name: 'Staging London', environmentName: 'staging', active: true }
const prod = { id: 'prod', name: 'Production London', environmentName: 'production', active: true }
const active = { id: 'r1', targetProfileId: 'stage', deploymentVersionId: 'v1', provisioningStatus: 'ACTIVE', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', createdAt: '2026-11-07T10:00:00Z' }
const version = { id: 'v1', versionLabel: 'v1', status: 'PUBLISHED', publishedAt: active.createdAt }

test('environment grouping retains every exact authorized destination without fake defaults', () => {
  assert.deepEqual(helper.groupEnvironmentTargets([prod, { ...stage, id: 'stage-2' }, stage]).map(([name, rows]) => [name, rows.map(row => row.id)]),
    [['staging', ['stage-2', 'stage']], ['production', ['prod']]])
  assert.deepEqual(helper.groupEnvironmentTargets([]), [])
})

test('applied, verification and failed-attempt states are distinct', () => {
  assert.equal(helper.targetReleaseState([], 'stage').label, 'Not deployed')
  assert.equal(helper.targetReleaseState([active], 'stage').label, 'Verified')
  assert.equal(helper.targetReleaseState([active], 'prod').active, null)
  assert.equal(helper.targetReleaseState([{ ...active, verificationStatus: 'NOT_RUN' }], 'stage').label, 'Applied')
  assert.equal(helper.targetReleaseState([{ ...active, status: 'APPLIED_VERIFICATION_FAILED', verificationStatus: 'FAILED' }], 'stage').label, 'Verification failed')
  const failed = { ...active, id: 'r2', deploymentVersionId: 'v2', status: 'FAILED', provisioningStatus: 'FAILED', verificationStatus: 'NOT_RUN', createdAt: '2026-11-07T11:00:00Z' }
  const state = helper.targetReleaseState([active, failed], 'stage')
  assert.equal(state.label, 'Update failed')
  assert.equal(state.active.id, 'r1', 'failure must not erase the still applied version')
})

test('provisioning and verification never display as verified, inactive infrastructure is not applied', () => {
  assert.equal(helper.targetReleaseState([{ ...active, status: 'PROVISIONING', provisioningStatus: 'AWAITING_CONFIRMATION', verificationStatus: 'NOT_RUN' }], 'stage').label, 'Updating')
  assert.equal(helper.targetReleaseState([{ ...active, status: 'VERIFYING', verificationStatus: 'RUNNING' }], 'stage').label, 'Verifying')
  assert.equal(helper.targetReleaseState([{ ...active, provisioningStatus: 'DELETED' }], 'stage').label, 'Not deployed')
})

test('published grid shows exact environment verification separately from publication', () => {
  const html = renderToStaticMarkup(React.createElement(grid.BrainReleaseVersions, { versions: [version], profiles: [stage, prod], releases: [active],
    evidenceReady: true, selectedTargetId: 'prod', canOperate: true, busy: false, onReview() {}, onDetails() {}, onActivity() {} }))
  for (const label of ['Published versions', 'Published', 'Staging', 'Production', 'Verified', 'Review &amp; apply']) assert.ok(html.includes(label), label)
  assert.ok(!html.includes('Advanced version actions'))
})

test('missing environment evidence is unavailable, not a false empty deployment', () => {
  const html = renderToStaticMarkup(React.createElement(grid.BrainReleaseVersions, { versions: [version], profiles: [stage, prod], releases: [],
    evidenceReady: false, selectedTargetId: 'prod', canOperate: true, busy: false, onReview() {}, onDetails() {}, onActivity() {} }))
  assert.ok(html.includes('Unavailable'))
  assert.ok(!html.includes('Verified'))
  assert.ok(html.includes('disabled=""'))
})

test('environment controls show both choices with state, and no technical profile dropdown', () => {
  const html = renderToStaticMarkup(React.createElement(picker.BrainEnvironmentSwitch, { profiles: [stage, prod], releases: [active], versions: [version],
    selectedId: 'prod', unavailable: false, loading: false, disabled: false, onChange() {} }))
  for (const label of ['View Staging', 'View Production', 'Verified', 'Not deployed']) assert.ok(html.includes(label), label)
  assert.ok(!html.includes('role="combobox"'))
})

test('each environment button is natively disabled while a release is running', () => {
  const html = renderToStaticMarkup(React.createElement(picker.BrainEnvironmentSwitch, { profiles: [stage, prod], releases: [{ ...active, status: 'PROVISIONING', provisioningStatus: 'RUNNING' }], versions: [version],
    selectedId: 'prod', unavailable: false, loading: false, disabled: false, onChange() {} }))
  for (const name of ['View Staging', 'View Production']) {
    const button = html.match(new RegExp(`<button[^>]*aria-label="${name}"[^>]*>`))?.[0]
    assert.ok(button?.includes('disabled=""'), name)
  }
})
