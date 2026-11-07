import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const read = path => readFile(new URL(path, import.meta.url), 'utf8')
async function load(path) {
  const result = ts.transpileModule(await read(path), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } })
  const exports = {}
  new Function('exports', result.outputText)(exports)
  return exports
}
const nav = await load('../src/account/accountNavigation.ts')
const model = await load('../src/account/customerTeamModel.ts')
const user = (overrides = {}) => ({ id: 'user-a', customerId: 'customer-a', role: 'CUSTOMER_ADMIN', displayName: 'Alex Morgan', email: 'alex@example.test', status: 'ACTIVE', ...overrides })

test('canonical account sections do not introduce resource/environment scope', () => {
  assert.deepEqual(nav.accountSections.map(item => item.path), ['profile', 'team', 'loomai-access', 'security'])
  assert.equal(nav.accountPath('team', '?deploymentId=dep-a&targetProfileId=stage&releaseId=release-a'), '/account/team?fromBrain=dep-a&targetProfileId=stage')
  assert.equal(nav.accountPath('team', '?customerId=other&role=PLATFORM_ADMIN'), '/account/team')
  assert.equal(nav.accountPath('other'), '/account/team')
})
test('legacy users and known account routes keep safe sign-in return paths', () => {
  assert.equal(nav.accountSignInReturn('/users?deploymentId=dep-a'), '/account/team?fromBrain=dep-a')
  assert.equal(nav.accountSignInReturn('/account/security'), '/account/security')
  for (const value of ['https://evil.example/account/team', '//evil.example/account/team', '/account/team/extra', '/accounting', '/users\n', null, {}, '/account/team#fragment']) {
    assert.equal(nav.accountSignInReturn(value), null)
  }
  assert.equal(nav.accountPath('team', '?fromBrain=//evil.example&targetProfileId=anything'), '/account/team')
})
test('account frame only matches account namespace and legacy users', () => {
  assert.equal(nav.isAccountPath('/users'), true)
  assert.equal(nav.isAccountPath('/account/team'), true)
  assert.equal(nav.isAccountPath('/accounting'), false)
  assert.equal(nav.isAccountPath('/smart-brains/dep-a'), false)
})
test('mixed ownership or staff-role responses cannot render customer team data', () => {
  assert.deepEqual(model.workspaceMembers([user()], 'customer-a'), [user()])
  for (const row of [user({ customerId: 'customer-b' }), user({ role: 'PLATFORM_OPERATOR' })]) {
    assert.throws(() => model.workspaceMembers([user(), row], 'customer-a'), /does not match this workspace/)
  }
})
test('human list search and status filters preserve source data and do not change permissions', () => {
  const users = [user(), user({ id: 'user-b', email: 'maya@example.test', displayName: 'Maya Reed', status: 'DISABLED' })]
  const before = structuredClone(users)
  assert.deepEqual(model.filterMembers(users, ' MAYA ', ''), [users[1]])
  assert.deepEqual(model.filterMembers(users, 'example.test', 'ACTIVE'), [users[0]])
  assert.deepEqual(model.filterMembers(users, '', 'DISABLED'), [users[1]])
  assert.deepEqual(model.filterMembers(users, 'absent', ''), [])
  assert.deepEqual(users, before)
})
test('create and update lock role and owner to current account; password is not trimmed', () => {
  assert.deepEqual(model.memberCreatePayload('customer-a', { email: ' alex@example.test ', displayName: ' Alex ', password: ' secret ', role: 'PLATFORM_ADMIN', customerId: 'other' }), {
    email: 'alex@example.test', displayName: 'Alex', password: ' secret ', role: 'CUSTOMER_ADMIN', customerId: 'customer-a',
  })
  assert.deepEqual(model.memberUpdatePayload('customer-a', ' Maya ', 'DISABLED'), { displayName: 'Maya', status: 'DISABLED', role: 'CUSTOMER_ADMIN', customerId: 'customer-a' })
})
test('current actor uses the authenticated email, not the database user ID', () => {
  assert.equal(model.isCurrentMember(user(), 'Alex@Example.test'), true)
  assert.equal(model.isCurrentMember(user(), 'user-a'), false)
  assert.equal(model.isCurrentMember(user(), 'other@example.test'), false)
})
test('stale name/status snapshots cannot overwrite another administrator update', () => {
  const expected = user()
  assert.deepEqual(model.unchangedMember([user()], 'customer-a', expected), user())
  for (const changed of [user({ status: 'DISABLED' }), user({ displayName: 'Changed name' }), user({ email: 'changed@example.test' })]) {
    assert.throws(() => model.unchangedMember([changed], 'customer-a', expected), /member changed/)
  }
  assert.throws(() => model.unchangedMember([], 'customer-a', expected), /member changed/)
})
test('customer team uses existing commands without a deployment assignment or new identity system', async () => {
  const source = await read('../src/account/CustomerTeamWorkspace.tsx')
  assert.match(source, /fetchPlatformUserAccessOverview\(\)/)
  assert.match(source, /createPlatformUser\(memberCreatePayload\(session.customerId, form\)\)/)
  assert.match(source, /updatePlatformUser\(current.user.id, memberUpdatePayload/)
  assert.match(source, /resetPlatformUserPassword\(current.user.id/)
  assert.doesNotMatch(source, /upsertDeploymentAssignment|fetchPlatformCustomers|PLATFORM_OPERATOR|\bfetch\(/)
  assert.match(source, /No invitation email is sent/)
  assert.match(source, /not Supabase sign-in/)
})
test('pending/dirty inputs are guarded and secret fields are cleared when editors close', async () => {
  const team = await read('../src/account/CustomerTeamWorkspace.tsx')
  const shell = await read('../src/account/CustomerAccountShell.tsx')
  assert.match(team, /command.current/)
  assert.match(team, /useAccountEditor\(dirty, busy, discard\)/)
  assert.match(team, /setForm\(blank\)/)
  assert.match(team, /isCurrentMember\(selected, session.actorId\)/)
  assert.match(shell, /useBlocker/)
  assert.match(shell, /beforeunload/)
  assert.match(shell, /!editor\?\.busy && <Button/)
})
test('staff users retain their existing page and customers receive a separate account frame', async () => {
  const app = await read('../src/App.tsx')
  assert.match(app, /customerWorkspace && isAccountPath\(location.pathname\) \? CustomerAccountShell/)
  assert.match(app, /path="\/users" element=\{<UsersPage/)
  assert.match(app, /path="\/account\/\*" element=\{<CustomerAccountPage/)
  const page = await read('../src/pages/CustomerAccountPage.tsx')
  assert.doesNotMatch(page, /<UsersPage|deploymentId|useDeploymentWorkspace/)
  assert.match(page, /isCustomerSession/)
  const security = await read('../src/account/AccountSecurityPanel.tsx')
  assert.match(security, /<SupabaseIdentityForm emailLinkAddress=\{email\} onBusyChange=\{setBusy\}/)
  assert.match(security, /to="\/auth\/recovery"/)
  assert.doesNotMatch(security, /\/recover-account/)
  assert.match(security, /useAccountEditor\(false, busy, discard\)/)
})
test('collaboration retains existing guarded grant/revoke contract and adds focused drawer', async () => {
  const source = await read('../src/components/customer/CustomerStaffAccessPanel.tsx')
  for (const contract of ['currentScope.current === scope', 'request.current?.abort()', 'grantCustomerStaffAccess(customerId, email.trim(), controller.signal)', 'revokeCustomerStaffAccess(customerId, target.id, controller.signal)']) assert.ok(source.includes(contract))
  assert.match(source, /workspacePresentation && <Drawer/)
  assert.match(source, /useAccountEditor/)
})

test('existing API wrappers send account-wide reads and exact member command bodies; errors stay visible', async () => {
  const previousWindow = globalThis.window, previousFetch = globalThis.fetch
  globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://account.test' } }
  const compiled = ts.transpileModule(await read('../src/api/platformApi.ts'), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  const api = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
  const calls = []
  globalThis.fetch = async (url, init) => { calls.push({ url, init }); return new Response('{}') }
  try {
    await api.fetchPlatformUserAccessOverview()
    assert.equal(calls.at(-1).url, 'https://account.test/api/platform/users/access-overview')
    const create = model.memberCreatePayload('customer-a', { email: 'new@example.test', displayName: 'New member', password: 'synthetic-test-only' })
    await api.createPlatformUser(create)
    assert.equal(calls.at(-1).init.method, 'POST')
    assert.equal(calls.at(-1).init.credentials, 'include')
    assert.deepEqual(JSON.parse(calls.at(-1).init.body), create)
    const edit = model.memberUpdatePayload('customer-a', 'Updated member', 'DISABLED')
    await api.updatePlatformUser('user-a', edit)
    assert.equal(calls.at(-1).url, 'https://account.test/api/platform/users/user-a')
    assert.deepEqual(JSON.parse(calls.at(-1).init.body), edit)
    globalThis.fetch = async () => new Response(JSON.stringify({ message: 'The last active administrator cannot be disabled.' }), { status: 409 })
    await assert.rejects(api.updatePlatformUser('user-a', edit), /last active administrator/)
  } finally { globalThis.fetch = previousFetch; globalThis.window = previousWindow }
})
