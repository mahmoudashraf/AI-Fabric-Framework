import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'
const require = createRequire(import.meta.url)
const compile = source => ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
const exact = {}
new Function('require', 'exports', compile(await readFile(new URL('../src/lib/losslessJson.ts', import.meta.url), 'utf8')))(require, exact)
const source = compile(await readFile(new URL('../src/pluginLibrary/pluginLibraryApi.ts', import.meta.url), 'utf8'))
const manifest = '{"pluginType":"DATA","pluginId":"records","version":"1.0.0","opaque":{"n":1234567890123456789012345,"d":0.123456789012345678900,"null":null}}'
const summary = () => ({ id: 'plugin/one', ownerCustomerId: 'customer', displayName: 'Service records', status: 'ACTIVE', manifest: exact.parseExactObject(manifest), revision: 2,
  versions: [{ id: 'version', version: '1.0.0', manifest: exact.parseExactObject(manifest) }], canManage: true,
  installs: { savedCount: 1, appliedCount: 1, saved: [{ deploymentId: 'brain/one', deploymentName: 'Service AI', pluginVersion: '1.0.0', status: 'ENABLED' }],
    applied: [{ deploymentId: 'brain/one', targetProfileId: 'staging', releaseId: 'release/one', pluginVersion: '1.0.0' }] } })
function fixture() {
  const calls = [], api = {}
  new Function('require', 'exports', source)(name => name.endsWith('losslessJson') ? exact : {
    platformRequest: async (path, init, policy, decode) => {
      calls.push({ path, init, policy })
      const response = path === '/api/customer/plugin-library?customerId=customer' && !init ? { customerId: 'customer', canManage: true, plugins: [summary()] }
        : path.includes('/versions/') ? summary().versions[0] : path.includes('/check') ? { valid: true, revision: 2, connectionTested: false, issues: [] } : summary()
      return decode(exact.stringifyExactJson(response))
    },
  }, api)
  return { api: api.pluginLibraryApi, calls }
}

test('version save is a single atomic owner-scoped command with exact source and expected revision', async () => {
  const { api, calls } = fixture()
  const result = await api.createVersion('plugin/one', { manifestJson: manifest, expectedRevision: 2, version: '1.0.0' }, 'customer')
  assert.equal(calls.length, 1)
  assert.equal(calls[0].path, '/api/customer/plugin-library/plugin%2Fone/versions?customerId=customer')
  assert.equal(calls[0].init.method, 'POST')
  assert.match(calls[0].init.body, /1234567890123456789012345/)
  assert.match(calls[0].init.body, /0\.123456789012345678900/)
  assert.equal(exact.parseExactObject(calls[0].init.body).expectedRevision, 2)
  assert.match(result.manifestJson, /1234567890123456789012345/)
})

test('library uses server ownership and permission, separates saved installations from applied uses', async () => {
  const { api } = fixture()
  const list = await api.list('customer')
  assert.equal(list.customerId, 'customer'); assert.equal(list.canManage, true)
  assert.equal(list.plugins[0].savedInstallations, 1); assert.equal(list.plugins[0].appliedUses, 1)
  const detail = await api.get('plugin/one', 'customer')
  assert.equal(detail.usage.length, 2)
  assert.match(detail.usage[0].state, /Saved/)
  assert.equal(detail.usage[1].state, 'Applied release')
  assert.match(detail.usage[1].returnTo, /targetProfileId=staging/)
})

test('checking is not saving or claiming provider connectivity', async () => {
  const { api, calls } = fixture()
  const result = await api.check('plugin/one', { manifestJson: manifest, expectedRevision: 2 }, 'customer')
  assert.equal(result.valid, true); assert.equal(result.connectionTested, false)
  assert.equal(calls.length, 1); assert.match(calls[0].path, /\/check\?/)
  await assert.rejects(api.save('plugin/one', { manifestJson: '{bad', expectedRevision: 2 }, 'customer'))
  assert.equal(calls.length, 1, 'Invalid source never submits a replacement object')
})
