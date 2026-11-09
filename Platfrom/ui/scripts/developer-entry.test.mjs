import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

async function load(path) {
  const source = await readFile(new URL('../src/developer/' + path, import.meta.url), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  return import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
}
const entry = await load('developerEntry.ts')
const policy = await load('deploymentPolicy.ts')

test('developer entry reuses two exact existing Kits, not new account or product types', () => {
  assert.deepEqual(entry.DEVELOPER_KITS, ['ai-customer-experience', 'ai-support-experience'])
  assert.equal(entry.isDeveloperKit('ai-support-experience'), true)
  for (const value of ['dealership-ai-experience', 'ai-support-experience/extra', null, undefined]) assert.equal(entry.isDeveloperKit(value), false)
  assert.equal(entry.developerKitSetupPath('ai-support-experience'), '/kits/new/ai-support-experience?entryPoint=DEVELOPER')
  assert.equal(entry.isDeveloperEntry('?entryPoint=DEVELOPER'), true)
  assert.equal(entry.isDeveloperEntry('?entryPoint=developer'), false)
})
test('only the dedicated public hostname gets a developer root and canonical Console links', () => {
  assert.equal(entry.developerLandingHost('developer.loomai.pro'), true)
  assert.equal(entry.developerConsoleHref('/sign-in', 'developer.loomai.pro'), 'https://console.loomai.pro/sign-in')
  for (const host of ['develop.loomai.pro', 'console.loomai.pro', 'developer.loomai.pro.evil.test', 'localhost']) {
    assert.equal(entry.developerLandingHost(host), false)
    assert.equal(entry.developerConsoleHref('/sign-in', host), '/sign-in')
  }
})
test('Console experience is an exact account-scoped presentation choice', () => {
  assert.equal(entry.explicitConsoleExperience('?entryPoint=DEVELOPER'), 'DEVELOPER')
  assert.equal(entry.explicitConsoleExperience('?entryPoint=CUSTOMER'), 'CUSTOMER')
  assert.equal(entry.explicitConsoleExperience('?entryPoint=developer'), null)
  assert.equal(entry.explicitConsoleExperience(''), null)
  assert.notEqual(entry.consoleExperienceKey('customer-a', 'owner'), entry.consoleExperienceKey('customer-b', 'owner'))
  assert.notEqual(entry.consoleExperienceKey('customer', 'owner-a'), entry.consoleExperienceKey('customer', 'owner-b'))
  assert.notEqual(entry.consoleExperienceKey('customer:a', 'b'), entry.consoleExperienceKey('customer', 'a:b'))
  const key = entry.consoleExperienceKey('customer', 'owner')
  assert.equal(entry.readConsoleExperience({ getItem: () => 'DEVELOPER' }, key), 'DEVELOPER')
  assert.equal(entry.readConsoleExperience({ getItem: () => 'CUSTOMER' }, key), 'CUSTOMER')
  assert.equal(entry.readConsoleExperience({ getItem: () => 'ADMIN' }, key), null)
  assert.equal(entry.readConsoleExperience({ getItem: () => { throw new Error('Storage disabled') } }, key), null)
  assert.equal(entry.developerEntryPath('/sign-in?kit=ai-support-experience#form'), '/sign-in?kit=ai-support-experience&entryPoint=DEVELOPER#form')
})
test('UI honours persisted allowed environments without reinterpreting arbitrary tags', () => {
  const developer = { allowedEnvironments: ['staging'], tags: ['staging-only'] }
  const business = { allowedEnvironments: ['staging', 'production'], tags: [] }
  assert.equal(policy.stagingOnly(developer), true)
  assert.equal(policy.environmentAllowed(developer, 'staging'), true)
  assert.equal(policy.environmentAllowed(developer, 'production'), false)
  assert.equal(policy.environmentAllowed(business, 'production'), true)
  assert.equal(policy.stagingOnly({ ...business, tags: ['staging-only'] }), false)
  assert.equal(policy.environmentAllowed({ allowedEnvironments: [], tags: [] }, 'production'), false)
})
