import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'

const require = createRequire(import.meta.url)
async function load(path, dependencies) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText
  const exports = {}
  new Function('require', 'exports', code)(name => name in dependencies ? dependencies[name] : require(name), exports)
  return exports
}
const calls = []
const api = await load('../../api/staffCustomerAccessApi.ts', {
  './platformApi': { platformRequest: async (...args) => { calls.push(args); return args } },
})
const dialog = await load('./CustomerAccessDialog.tsx', {
  '../../api/staffCustomerAccessApi': api, '../../auth/PlatformAuthProvider': {},
})

test('access preparation uses the encoded selected customer, shared auth transport and only recipient details', async () => {
  calls.length = 0
  const signal = new AbortController().signal
  const body = { email: 'real@example.test', displayName: 'Real Customer' }
  await api.prepareStaffCustomerAccess('customer/a', body, signal)
  assert.equal(calls.length, 1)
  assert.equal(calls[0][0], '/api/staff/customers/customer%2Fa/access')
  assert.equal(calls[0][1].method, 'POST')
  assert.equal(calls[0][1].signal, signal)
  assert.deepEqual(JSON.parse(calls[0][1].body), body)
  assert.equal(calls[0].length, 2, 'Default shared authentication policy is preserved')
})

test('recipient links are stable existing auth routes, never workspace creation or an external redirect', () => {
  const links = dialog.customerAccessLinks('https://console.example.test/current')
  assert.deepEqual(links.map(link => link.url), [
    'https://console.example.test/sign-up',
    'https://console.example.test/recover-account',
    'https://console.example.test/sign-in',
  ])
  assert.ok(links.every(link => !link.url.includes('@') && !link.url.includes('?')))
})
