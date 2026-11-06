import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = path => readFile(new URL(path, import.meta.url), 'utf8')
const calls = []
const code = ts.transpileModule(await source('../src/api/experienceKitsApi.ts'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText
const api = {}
new Function('require', 'exports', code)(() => ({ platformRequest: async (...args) => { calls.push(args); return args } }), api)

test('staff reads encode the target and item, preserve abort signals and never use customer transport', async () => {
  calls.length = 0
  const signal = new AbortController().signal
  await api.fetchStaffExperienceKits('customer/a', signal)
  await api.fetchStaffExperienceKitCatalogue('customer/a', signal)
  await api.fetchStaffExperienceKit('customer/a', 'kit/b', signal)
  assert.deepEqual(calls.map(([path]) => path), [
    '/api/staff/customers/customer%2Fa/experience-kits',
    '/api/staff/customers/customer%2Fa/experience-kits/catalogue',
    '/api/staff/customers/customer%2Fa/experience-kits/kit%2Fb',
  ])
  assert.ok(calls.every(([, init]) => init.signal === signal && !init.method))
})

test('prepare posts no payload; server owns template enrollment and account prerequisites', async () => {
  calls.length = 0
  await api.prepareStaffExperienceKits('customer/a')
  assert.deepEqual(calls, [['/api/staff/customers/customer%2Fa/experience-kits/prepare', { method: 'POST' }]])
})

test('staff create and update use unchanged customer DTOs, not impersonation or lifecycle payloads', async () => {
  calls.length = 0
  const create = { kitCode: 'dealership-ai-experience', name: 'Dealer kit', offerId: 'offer', targetProfileId: 'stage',
    workspaceProfileId: 'profile', workspaceName: 'Dealer AI', brandColor: '#409aff', deliveryMode: 'both',
    appSlug: 'dealer-ai', websiteOrigin: 'https://dealer.example', humanSupportEnabled: true,
    ticketingEnabled: false, ticketProvider: null, operationKey: 'same-retry-key' }
  const update = { name: 'Dealer kit', workspaceName: 'Updated AI', brandColor: '#409aff', deliveryMode: 'standalone',
    appSlug: 'dealer-ai', humanSupportEnabled: false, ticketingEnabled: false, ticketProvider: null,
    expectedRevision: 3, expectedWorkspaceRevision: 8, expectedEmbeddedWorkspaceRevision: 9 }
  await api.createStaffExperienceKit('customer/a', create)
  await api.createStaffExperienceKit('customer/a', create)
  await api.updateStaffExperienceKit('customer/b', 'kit/c', update)
  assert.deepEqual(calls.map(([path, init]) => [path, init.method]), [
    ['/api/staff/customers/customer%2Fa/experience-kits', 'POST'],
    ['/api/staff/customers/customer%2Fa/experience-kits', 'POST'],
    ['/api/staff/customers/customer%2Fb/experience-kits/kit%2Fc', 'PUT'],
  ])
  assert.deepEqual(calls.map(([, init]) => JSON.parse(init.body)), [create, create, update])
})

test('staff query keys separate actors, targets, resources and customer-only cached responses', () => {
  for (const key of ['customer', 'catalogue', 'list', 'detail']) {
    const value = api.staffExperienceKitKeys[key]('actor-a', 'customer-a', 'kit-a')
    assert.notDeepEqual(value, api.staffExperienceKitKeys[key]('actor-b', 'customer-a', 'kit-a'))
    assert.notDeepEqual(value, api.staffExperienceKitKeys[key]('actor-a', 'customer-b', 'kit-a'))
    assert.notDeepEqual(value, api.experienceKitKeys[key]('customer-a', 'kit-a'))
  }
  assert.notDeepEqual(api.staffExperienceKitKeys.detail('actor', 'customer', 'a'), api.staffExperienceKitKeys.detail('actor', 'customer', 'b'))
})

test('customer transport retains all existing routes and payloads', async () => {
  calls.length = 0
  await api.fetchExperienceKitCatalogue()
  await api.fetchExperienceKits()
  await api.fetchExperienceKit('kit/a')
  await api.createExperienceKit({ operationKey: 'customer-key' })
  await api.updateExperienceKit('kit/a', { expectedRevision: 4 })
  assert.deepEqual(calls.map(([path]) => path), ['/api/customer-experience-kits/catalogue', '/api/customer-experience-kits',
    '/api/customer-experience-kits/kit%2Fa', '/api/customer-experience-kits', '/api/customer-experience-kits/kit%2Fa'])
  assert.deepEqual(JSON.parse(calls[3][1].body), { operationKey: 'customer-key' })
})

test('staff routes, role gate and customer selection reuse the setup and canonical editors only', async () => {
  const [page, setup, routes, customers] = await Promise.all([
    source('../src/pages/StaffExperienceKitsPage.tsx'), source('../src/components/kits/ExperienceKitSetup.tsx'),
    source('../src/App.tsx'), source('../src/pages/CustomersPage.tsx'),
  ])
  for (const path of ['/customers/:customerId/kits', '/customers/:customerId/kits/new/:code', '/customers/:customerId/kits/:id']) assert.ok(routes.includes(path))
  assert.ok(page.includes("['PLATFORM_ADMIN', 'PLATFORM_OPERATOR'].includes(auth.session.role"))
  assert.ok(page.includes("{isAdmin && <CustomerAccessDialog"))
  assert.ok(page.includes('key={`${auth.session.actorId}:${customerId}`}'))
  assert.ok(page.includes('navigate(staffKitsPath(event.target.value))'))
  assert.ok(page.includes('<ExperienceKitSetup customerId={customerId}'))
  assert.ok(page.includes('to="/users"'))
  assert.ok(page.includes('to={`/ai-workspaces?${new URLSearchParams({ customerId,'))
  assert.ok(page.includes('installationId: kit.installationId'))
  assert.ok(page.includes('installationId: kit.embeddedInstallationId'))
  assert.ok(!/ExperienceKitOverview|fetchCustomerDeploymentPortfolio|enrollPublicCustomerTemplates|applyCustomer|publishCustomer|activateAIWorkspace/.test(page))
  assert.ok(customers.includes("auth.session?.authenticated && auth.session.role === 'PLATFORM_ADMIN' && <Button"))
  assert.ok(setup.includes('props.customerId, props.actorId, props.code, props.basePath, props.transport?.queryScope'))
  assert.ok(setup.includes('transport?.create ?? createExperienceKit'))
  assert.ok(setup.includes('navigate(`${basePath}/${encodeURIComponent(kit.id)}`)'))
  assert.ok(setup.includes("transport ? 'loomai:staff-kit-create' : 'loomai:kit-create'"))
})

test('customer access stays in the selected kit workspace and refreshes preparation without closing handover instructions', async () => {
  const page = await source('../src/pages/StaffExperienceKitsPage.tsx')
  const setup = await source('../src/components/kits/ExperienceKitSetup.tsx')
  assert.ok(page.includes('<CustomerAccessDialog customerId={customerId} customerName={customer.name} open={accessOpen}'))
  assert.ok(page.includes('onClose={() => setAccessOpen(false)} onPrepared={accessPrepared}'))
  assert.ok(page.includes('Give customer access'))
  assert.ok(page.includes('key={accessRevision}'))
  assert.ok(page.includes('preparationRevision={accessRevision}'))
  assert.ok(setup.includes('accessRevision.current !== preparationRevision'))
  const callback = page.slice(page.indexOf('const accessPrepared ='), page.indexOf('if (customers.isPending)'))
  assert.ok(callback.includes('if (!mounted.current) return'))
  assert.ok(callback.includes('setAccessRevision(value => value + 1)'))
  assert.ok(callback.includes('staffExperienceKitKeys.catalogue(actorId, customerId)'))
  assert.ok(!callback.includes('setAccessOpen'))
})
