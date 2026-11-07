import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/brain/brainNavigation.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
const exports = {}
new Function('exports', code)(exports)
const { brainIdFromPath, brainPath, brainSections, legacyBrainSections, isUsableRelease, brainSignInReturn, retainEditingRelease } = exports

test('resource identity lives in the path and preserves an exact target', () => {
  const url = new URL(brainPath('dep/a & b', 'responses/instructions', 'target/staging'), 'https://console.test')
  assert.equal(brainIdFromPath(url.pathname), 'dep/a & b')
  assert.equal(url.searchParams.get('targetProfileId'), 'target/staging')
  assert.equal(url.searchParams.has('deploymentId'), false)
  assert.equal(brainIdFromPath('/prompts'), '')
  assert.equal(brainIdFromPath('/smart-brains/%broken'), '')
})

test('all outcome sections share one route hierarchy', () => {
  assert.deepEqual(brainSections.map(item => item.path), ['', 'knowledge', 'capabilities', 'responses', 'channels', 'human-support', 'try', 'updates', 'settings'])
  assert.equal(new Set(brainSections.map(item => item.path)).size, brainSections.length)
  assert.equal(legacyBrainSections['/plugins'], 'capabilities')
  assert.equal(legacyBrainSections['/prompts'], 'responses')
})

test('sign-in return is restricted to a valid local Brain route', () => {
  assert.equal(brainSignInReturn('/smart-brains/dep-1/responses?targetProfileId=target-1'), '/smart-brains/dep-1/responses?targetProfileId=target-1')
  for (const path of ['https://other.test/smart-brains/dep-1', '//other.test', '/users', '/smart-brains/%broken', '/smart-brains/dep-1\n']) assert.equal(brainSignInReturn(path), null)
})

test('draft or failed provisioning never counts as an applied release', () => {
  assert.equal(isUsableRelease({ status: 'DRAFT', provisioningStatus: 'ACTIVE' }), false)
  assert.equal(isUsableRelease({ status: 'APPLIED_VERIFIED', provisioningStatus: 'DELETED' }), false)
  assert.equal(isUsableRelease({ status: 'APPLIED_VERIFICATION_FAILED', provisioningStatus: 'ACTIVE' }), true)
  assert.equal(isUsableRelease({ status: 'APPLIED_VERIFIED', provisioningStatus: 'ACTIVE' }), true)
})

test('background releases cannot change a dirty or busy task, and never retain a foreign scope', () => {
  const old = { scope: 'brain-a:staging', release: { id: 'release-1' } }
  assert.equal(retainEditingRelease(old, old.scope, { id: 'release-2' }, true), old)
  assert.equal(retainEditingRelease(old, old.scope, { id: 'release-2' }, false).release.id, 'release-2')
  assert.equal(retainEditingRelease(old, 'brain-b:staging', { id: 'foreign-owned-release' }, true).release.id, 'foreign-owned-release')
})

test('pre-deployment storage, staff app links and Desk permissions use their actual contracts', async () => {
  const knowledge = await readFile(new URL('../src/brain/BrainKnowledgePage.tsx', import.meta.url), 'utf8')
  assert.ok(knowledge.includes("!release && task !== 'storage'"))
  assert.ok(knowledge.includes('initialTargetProfileId={targetProfileId}'))
  const experience = await readFile(new URL('../src/brain/BrainExperiencePages.tsx', import.meta.url), 'utf8')
  assert.ok(experience.includes('installationId: app.installationId'))
  assert.ok(experience.includes("session?.role === 'CUSTOMER_ADMIN' ? <CustomerDeskActivation"))
  const routing = await readFile(new URL('../src/layout/PlatformRouting.tsx', import.meta.url), 'utf8')
  assert.ok(routing.includes('session?.authenticated || session?.enabled === false'))
})

test('Brain routing and shell keep global customer pages outside the redesign', async () => {
  const app = await readFile(new URL('../src/App.tsx', import.meta.url), 'utf8')
  assert.ok(app.includes('path="/smart-brains/:brainId/*"'))
  for (const route of ['/kits/:id', '/ai-workspaces', '/support/requests', '/users']) assert.ok(app.includes(`path="${route}"`))
  const shell = await readFile(new URL('../src/brain/SmartBrainShell.tsx', import.meta.url), 'utf8')
  assert.ok(shell.includes('Editing shared draft'))
  assert.ok(shell.includes('useBlocker'))
  assert.ok(shell.includes("href=\"https://loomai.pro/\""))
  assert.ok(!shell.includes('Live ${'))
  assert.ok(shell.includes('No alternative environment'))
})

test('local review blocks resource writes by default', async () => {
  const preview = await readFile(new URL('./smart-brain-preview.mjs', import.meta.url), 'utf8')
  assert.ok(preview.includes("PLATFORM_PREVIEW_ALLOW_WRITES !== 'true'"))
  assert.ok(preview.includes("host: '127.0.0.1'"))
  assert.ok(preview.includes('LOCAL_PREVIEW_READ_ONLY'))
})
