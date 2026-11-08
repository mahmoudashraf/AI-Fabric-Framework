import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const read = file => readFile(new URL(file, import.meta.url), 'utf8')
const code = ts.transpileModule(await read('../src/brain/brainNavigation.ts'), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
const api = {}; new Function('exports', code)(api)
const path = (route, search) => new URL(api.scopedBrainTaskPath('brain/a', route, search), 'https://console.test')

test('legacy document links resolve to one source editor with exact release and environment', () => {
  const url = path('/document-knowledge', '?deploymentId=brain/a&sourceId=doc/a&targetProfileId=prod&releaseId=r1&editor=configuration')
  assert.equal(url.pathname, '/smart-brains/brain%2Fa/knowledge/sources/doc%2Fa')
  assert.equal(url.searchParams.get('targetProfileId'), 'prod')
  assert.equal(url.searchParams.get('releaseId'), 'r1')
  assert.equal(url.searchParams.has('editor'), false)
  assert.equal(path('/document-knowledge', '?view=storage').pathname, '/smart-brains/brain%2Fa/knowledge/storage')
  assert.equal(path('/document-knowledge', '?view=manage').pathname, '/smart-brains/brain%2Fa/knowledge/documents')
})

test('connection and review links preserve supported explicit identities', () => {
  assert.equal(path('/integrations', '?connectionId=stock/a').pathname, '/smart-brains/brain%2Fa/knowledge/connections/stock%2Fa')
  assert.equal(path('/integrations', '?handoverOnly=true').pathname, '/smart-brains/brain%2Fa/human-support')
  const review = path('/human-review', '?taskId=review/1&targetProfileId=staging&releaseId=r2')
  assert.equal(review.pathname, '/smart-brains/brain%2Fa/action-reviews')
  assert.equal(review.searchParams.get('taskId'), 'review/1')
  assert.equal(review.searchParams.get('releaseId'), 'r2')
  assert.equal(path('/human-support', '').pathname, '/smart-brains/brain%2Fa/human-support')
})

test('customer menu has only resource-level entries, while staff global pages stay available', async () => {
  const shell = await read('../src/layout/CustomerConsoleShell.tsx')
  for (const route of Object.keys(api.scopedBrainTasks)) assert.ok(!shell.includes(`path: '${route}'`))
  const app = await read('../src/App.tsx')
  assert.match(app, /path="\/document-knowledge" element=\{<DocumentKnowledgePage/)
  assert.match(app, /path="\/integrations" element=\{<IntegrationsPage/)
  assert.match(app, /path="\/human-review" element=\{<HumanReviewPage/)
  assert.equal((app.match(/element=\{<CustomerBrainTaskRedirect/g) ?? []).length, 4)
})

test('legacy task without a Brain asks explicitly, never uses a remembered customer selection', async () => {
  const context = await read('../src/workspace/DeploymentWorkspaceContext.tsx')
  assert.match(context, /session\?\.role === 'CUSTOMER_ADMIN' && location.pathname in scopedBrainTasks/)
  assert.match(context, /!isScopedPage \|\| explicitBrainChoice/)
  const adapter = await read('../src/brain/CustomerBrainTaskRedirect.tsx')
  assert.match(adapter, /params|new URLSearchParams/)
  assert.ok(!adapter.includes('selectedDeploymentId'))
})

test('Knowledge reuses one document controller and explicitly excludes handover queries from live data', async () => {
  const knowledge = await read('../src/brain/BrainKnowledgePage.tsx')
  for (const label of ['All sources', 'Documents', 'Live data', 'Storage & uploads']) assert.ok(knowledge.includes(`label="${label}"`))
  assert.match(knowledge, /initialSourceId=\{sourceId\} dataOnly lockReleaseSelection workspaceView/)
  assert.match(knowledge, /workspaceView="source"/)
  const integrations = await read('../src/pages/IntegrationsPage.tsx')
  assert.match(integrations, /const handoverLive = !dataOnly/)
  assert.match(integrations, /handoverLive && handover.data/)
  assert.match(integrations, /sourceId: source.sourceId, operationKey: crypto.randomUUID\(\)/)
  assert.match(integrations, /webhook.reconcileDataSourceRef === initialSourceId/)
  assert.ok(!integrations.includes('webhook.sourceId === initialSourceId'))
})

test('operational tasks have no misleading publish button; review route is first class', async () => {
  const shell = await read('../src/brain/SmartBrainShell.tsx')
  assert.match(shell, /\['updates', 'action-reviews', 'human-support', 'knowledge'\]\.includes\(section\)/)
  assert.ok(api.brainSections.some(item => item.path === 'action-reviews' && item.group === 'Operations'))
  assert.match(shell, /query.set\('releaseId', requestedRelease\)/)
})
