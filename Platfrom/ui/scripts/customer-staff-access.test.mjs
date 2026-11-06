import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/api/customerStaffAccessApi.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
const calls = []
let reply, failure
const api = {}
new Function('require', 'exports', compiled)(() => ({ platformRequest: async (...args) => {
  calls.push(args)
  if (failure) throw failure
  return typeof reply === 'function' ? reply(...args) : reply
} }), api)
const row = (overrides = {}) => ({ id: 'grant-a', customerId: 'customer-a', userId: 'operator-a', email: 'engineer@loomai.example', displayName: 'Integration engineer',
  status: 'ACTIVE', grantedAt: '2026-11-06T12:00:00Z', revokedAt: null, ...overrides })

test('reads use customer-session endpoint and preserve abort signals without a customer override', async () => {
  calls.length = 0; failure = null; reply = [row()]
  const signal = new AbortController().signal
  assert.deepEqual(await api.fetchCustomerStaffAccess('customer-a', signal), reply)
  assert.deepEqual(calls, [['/api/customer/staff-access', { signal }]])
})

test('grant posts only the exact email; no staff role, invite, password or target override', async () => {
  calls.length = 0; reply = row()
  const signal = new AbortController().signal
  await api.grantCustomerStaffAccess('customer-a', 'engineer@loomai.example', signal)
  assert.equal(calls[0][0], '/api/customer/staff-access')
  assert.equal(calls[0][1].method, 'POST')
  assert.equal(calls[0][1].signal, signal)
  assert.deepEqual(JSON.parse(calls[0][1].body), { email: 'engineer@loomai.example' })
})

test('revoke encodes the exact grant id and sends no body', async () => {
  calls.length = 0; reply = row({ id: 'grant/a&b', status: 'REVOKED', revokedAt: '2026-11-06T12:30:00Z' })
  const signal = new AbortController().signal
  await api.revokeCustomerStaffAccess('customer-a', 'grant/a&b', signal)
  assert.deepEqual(calls, [['/api/customer/staff-access/grant%2Fa%26b', { method: 'DELETE', signal }]])
})

test('query keys isolate actor, customer and authentication mode', () => {
  const key = api.customerStaffAccessKeys.list('actor-a', 'customer-a', 'SUPABASE')
  assert.notDeepEqual(key, api.customerStaffAccessKeys.list('actor-b', 'customer-a', 'SUPABASE'))
  assert.notDeepEqual(key, api.customerStaffAccessKeys.list('actor-a', 'customer-b', 'SUPABASE'))
  assert.notDeepEqual(key, api.customerStaffAccessKeys.list('actor-a', 'customer-a', 'SESSION'))
})

test('a mixed-tenant list rejects the whole response rather than exposing a partial list', async () => {
  reply = [row(), row({ customerId: 'customer-b', id: 'foreign' })]
  await assert.rejects(api.fetchCustomerStaffAccess('customer-a'), /does not match this customer workspace/)
  for (const value of [null, {}, row()]) {
    reply = value
    await assert.rejects(api.fetchCustomerStaffAccess('customer-a'), /could not be loaded/)
  }
})

test('grant rejects foreign, mismatched-email and non-active receipts', async () => {
  for (const value of [row({ customerId: 'customer-b' }), row({ email: 'someone-else@loomai.example' }), row({ status: 'REVOKED' })]) {
    reply = value
    await assert.rejects(api.grantCustomerStaffAccess('customer-a', 'engineer@loomai.example'), /does not match/)
  }
  reply = row({ email: 'Engineer@loomai.example' })
  assert.equal((await api.grantCustomerStaffAccess('customer-a', 'engineer@loomai.example')).status, 'ACTIVE')
})

test('revoke rejects foreign, wrong-target and non-revoked receipts', async () => {
  for (const value of [row({ customerId: 'customer-b' }), row({ id: 'other-grant', status: 'REVOKED' }), row()]) {
    reply = value
    await assert.rejects(api.revokeCustomerStaffAccess('customer-a', 'grant-a'), /does not match/)
  }
})

test('aborted calls do not dispatch, and aborted responses never become usable data', async () => {
  const controller = new AbortController()
  controller.abort()
  calls.length = 0
  await assert.rejects(api.fetchCustomerStaffAccess('customer-a', controller.signal), { name: 'AbortError' })
  await assert.rejects(api.grantCustomerStaffAccess('customer-a', 'engineer@loomai.example', controller.signal), { name: 'AbortError' })
  await assert.rejects(api.revokeCustomerStaffAccess('customer-a', 'grant-a', controller.signal), { name: 'AbortError' })
  assert.equal(calls.length, 0)
  const late = new AbortController()
  reply = () => { late.abort(); return row() }
  await assert.rejects(api.grantCustomerStaffAccess('customer-a', 'engineer@loomai.example', late.signal), { name: 'AbortError' })
})

test('backend eligibility errors remain exact, including global administrator rejection', async () => {
  const error = new Error('Global platform administrator access cannot be granted or revoked here.')
  failure = error
  await assert.rejects(api.grantCustomerStaffAccess('customer-a', 'admin@loomai.example'), value => value === error)
  failure = null
})
