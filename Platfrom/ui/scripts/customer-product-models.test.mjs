import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/customer/customerProductModels.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
const { customerProducts, activeProductDeployments, productSetupPath } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`)
const offer = (id, pack = 'dealership') => ({ id, name: `Reviewed ${id}`, templateId: 'shared-template', entitlement: { status: 'ACTIVE' }, workspaceProfiles: [{ interaction: { packCode: pack } }] })
const deployment = (id, offerId, status = 'DRAFT') => ({ deploymentId: id, offerId, status })
const app = (id, deploymentId, status = 'ACTIVE') => ({ installationId: id, deploymentId, status })

test('reviewed experience identity groups template revisions without choosing an offer automatically', () => {
  const products = customerProducts([offer('old'), offer('current')], [deployment('a', 'old'), deployment('b', 'current', 'ACTIVE')], [app('app-b', 'b')])
  assert.equal(products.length, 1); assert.equal(products[0].name, 'Dealership AI')
  assert.deepEqual(products[0].offers.map(o => o.id), ['old', 'current'])
  assert.deepEqual(products[0].apps.map(a => a.installationId), ['app-b'])
  assert.deepEqual(activeProductDeployments(products[0]).map(d => d.deploymentId), ['b'])
})
test('labels never infer a product from words in an offer name', () => {
  const named = { ...offer('generic', 'generic-workspace'), name: 'Dealership Shopify automotive words' }
  assert.equal(customerProducts([named], [], [])[0].name, 'AI workspace')
})
test('unknown or ambiguous pack metadata uses the reviewed template identity rather than another product', () => {
  const unknown = offer('unknown', 'unrecognized-pack')
  assert.equal(customerProducts([unknown], [], [])[0].name, unknown.name)
  const ambiguous = { ...offer('ambiguous'), workspaceProfiles: [{ interaction: { packCode: 'dealership' } }, { interaction: { packCode: 'generic-workspace' } }] }
  const product = customerProducts([ambiguous], [], [])[0]
  assert.equal(product.id, 'template:shared-template'); assert.equal(product.name, ambiguous.name)
})
test('only exact deployment ownership relates apps to a product, including an active binding before status catches up', () => {
  const products = customerProducts([offer('a')], [deployment('mine', 'a')], [app('mine-app', 'mine'), app('foreign', 'other'), app('standalone', null)])
  assert.deepEqual(products[0].apps.map(a => a.installationId), ['mine-app'])
  assert.deepEqual(activeProductDeployments(products[0]).map(d => d.deploymentId), ['mine'])
})
test('inactive app bindings do not turn unused draft configurations into active products', () => {
  const products = customerProducts([offer('a')], [deployment('draft', 'a')], [app('disabled', 'draft', 'DISABLED')])
  assert.deepEqual(activeProductDeployments(products[0]), [])
  assert.equal(products[0].apps.length, 1, 'History is retained rather than deleted')
})
test('setup selects the exact entitled offer with URL encoding, without deploy or activation parameters', () => {
  const path = productSetupPath(offer('offer/one&other'))
  const url = new URL(path, 'https://console.test')
  assert.equal(url.searchParams.get('offerId'), 'offer/one&other')
  assert.deepEqual([...url.searchParams.keys()].sort(), ['offerId', 'step', 'view'])
  assert.equal(url.pathname, '/deployments')
})
