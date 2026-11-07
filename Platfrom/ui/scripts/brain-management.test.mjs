import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { StaticRouter } from 'react-router-dom'

const require = createRequire(import.meta.url)
async function load(path, dependencies = {}) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const exports = {}
  new Function('require', 'exports', ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText)(name => name in dependencies ? dependencies[name] : require(name), exports)
  return exports
}
const navigation = await load('../src/brain/brainNavigation.ts')
const model = await load('../src/brain/brainOverview.ts')
const release = { id: 'release-stage', deploymentId: 'brain', targetProfileId: 'stage', deploymentVersionId: 'v1', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED' }
const app = { deploymentId: 'brain', releaseId: release.id, status: 'ACTIVE', ready: true }
const workspace = { lifecycle: { savedDraftMatchesLatestPublished: true }, latestVersion: { id: 'v1' } }
const input = { workspace, release, latestRelease: release, apps: [app], loading: false, unavailable: false, hasTarget: true }
const state = extra => model.brainOverviewState({ ...input, ...extra })

test('overview recommends a next task using exact-release evidence', () => {
  assert.equal(state({}).next.section, 'try')
  assert.equal(state({ apps: [] }).next.section, 'channels')
  assert.equal(state({ apps: [{ ...app, releaseId: 'production-release' }] }).activeApps.length, 0)
  assert.equal(state({ apps: [{ ...app, releaseId: 'production-release' }] }).next.section, 'channels')
  for (const other of [{ ...app, ready: false }, { ...app, status: 'DRAFT' }, { ...app, status: 'DISABLED' }]) {
    assert.equal(state({ apps: [other] }).next.section, 'channels')
  }
})

test('draft and verification are not presented as a working customer experience', () => {
  assert.equal(state({ release: null, latestRelease: null }).next.section, 'updates')
  assert.match(state({ release: null, latestRelease: null }).next.title, /online/)
  for (const pending of [{ ...release, verificationStatus: 'PENDING' }, { ...release, status: 'VERIFYING' }, { ...release, status: 'APPLIED_VERIFICATION_FAILED' }]) {
    assert.equal(state({ release: pending }).verified, false)
    assert.equal(state({ release: pending }).next.section, 'updates')
  }
  assert.equal(state({ latestRelease: { ...release, status: 'FAILED' } }).next.warning, true)
  assert.match(state({ workspace: { ...workspace, latestVersion: { id: 'v2' } } }).next.title, /not applied/)
  assert.equal(state({ workspace: { ...workspace, lifecycle: { savedDraftMatchesLatestPublished: false } } }).changes, true)
})

test('unknown, loading or failed reads never invent a next action', () => {
  for (const extra of [{ loading: true }, { unavailable: true }, { hasTarget: false }, { workspace: null }]) assert.equal(state(extra).next, null)
})

async function redirectFixture({ search = '', portfolio = { data: [{ deploymentId: 'brain' }] }, releases = { data: [] } } = {}) {
  const navigations = [], queries = []
  const module = await load('../src/components/customer/CustomerBrainManageRedirect.tsx', {
    '../../brain/brainNavigation': navigation, '../../api/platformApi': {},
    '@tanstack/react-query': { useQuery: options => { queries.push(options); return options.queryKey[0] === 'deployment-releases' ? releases : portfolio } },
    'react-router-dom': { ...require('react-router-dom'), useLocation: () => ({ search }), Navigate: props => { navigations.push(props); return null } },
  })
  const markup = renderToStaticMarkup(React.createElement(StaticRouter, null,
    React.createElement(module.CustomerBrainManageRedirect, { customerId: 'customer', brainId: 'brain' })))
  return { markup, navigations, queries, path: module.customerBrainManagePath }
}

test('legacy management links preserve only explicit resource context', async () => {
  const f = await redirectFixture({ search: '?view=manage&deploymentId=brain&targetProfileId=prod%2Fexact&releaseId=r2&step=staging&offerId=ignored' })
  assert.equal(f.navigations[0].to, '/smart-brains/brain?targetProfileId=prod%2Fexact&releaseId=r2')
  assert.equal(f.navigations[0].replace, true)
  assert.equal(f.path('brain/a', ''), '/smart-brains/brain%2Fa')
  assert.equal(f.queries[1].enabled, false)
})

test('unavailable customer brains do not redirect or read their releases', async () => {
  const f = await redirectFixture({ portfolio: { data: [] }, search: '?releaseId=foreign' })
  assert.match(f.markup, /not available in your customer workspace/)
  assert.equal(f.navigations.length, 0)
  assert.equal(f.queries[1].enabled, false)
})

test('release-only deep links resolve their owned destination, not the current default', async () => {
  const f = await redirectFixture({ search: '?releaseId=release-stage', releases: { data: [release] } })
  assert.equal(f.navigations[0].to, '/smart-brains/brain?targetProfileId=stage&releaseId=release-stage')
  for (const data of [[], [{ ...release, deploymentId: 'other-brain' }], [{ ...release, targetProfileId: null }]]) {
    const invalid = await redirectFixture({ search: '?releaseId=release-stage', releases: { data } })
    assert.match(invalid.markup, /No alternative release was selected/)
    assert.equal(invalid.navigations.length, 0)
  }
})

test('failed and pending ownership reads stay visible without navigation', async () => {
  const failed = await redirectFixture({ portfolio: { isError: true, error: new Error('Read unavailable') } })
  assert.match(failed.markup, /Read unavailable/)
  assert.equal(failed.navigations.length, 0)
  const pending = await redirectFixture({ portfolio: { isPending: true } })
  assert.match(pending.markup, /Opening Smart Brain workspace/)
  assert.equal(pending.navigations.length, 0)
})

test('customer management is retired but creation, Engage editor and filtered release tasks remain', async () => {
  const resources = await readFile(new URL('../src/pages/CustomerResourcesPage.tsx', import.meta.url), 'utf8')
  assert.ok(resources.includes("mode !== 'apps' && view === 'manage'"))
  assert.ok(resources.includes('<Navigate to="/deployments" replace />'))
  assert.ok(resources.includes('<CustomerDeploymentConsole session={session} guided />'))
  assert.ok(resources.includes('<AIWorkspacesPage'))
  assert.ok(!resources.includes('<CustomerDeploymentConsole session={session} />'))
  const consoleSource = await readFile(new URL('../src/components/customer/CustomerDeploymentConsole.tsx', import.meta.url), 'utf8')
  assert.ok(!consoleSource.includes('All setup controls'))
  assert.ok(consoleSource.includes('to={brainPath(selectedDeploymentId)}'))
  const updates = await readFile(new URL('../src/brain/BrainUpdatesPage.tsx', import.meta.url), 'utf8')
  assert.ok(updates.includes('BrainCustomerReleaseTasks'))
})

test('overview renders outcome navigation and honest status without the old setup dashboard', async () => {
  const layout = await load('../src/brain/BrainPageLayout.tsx')
  const page = await load('../src/brain/BrainOverviewPage.tsx', {
    '../workspace/DeploymentWorkspaceContext': { useDeploymentWorkspace: () => ({ workspace: { ...workspace, draft: { revisionNumber: 4 } }, workspaceLoading: false, selectedDeploymentSummary: { name: 'Customer AI' } }) },
    './BrainWorkspaceContext': { useBrainWorkspace: () => ({ brainId: 'brain', release, targetProfile: { id: 'stage', environmentName: 'staging' }, href: section => navigation.brainPath('brain', section, 'stage') }) },
    './BrainExperiencePages': { useBrainApps: () => ({ apps: [app], customerId: 'customer' }) },
    './BrainPageLayout': layout, './brainOverview': model, '../api/platformApi': {},
    '@tanstack/react-query': { useQuery: () => ({ data: [release] }) },
  })
  const markup = renderToStaticMarkup(React.createElement(StaticRouter, null, React.createElement(page.BrainOverviewPage)))
  for (const section of ['knowledge', 'capabilities', 'responses', 'channels', 'human-support']) assert.ok(markup.includes(`/smart-brains/brain/${section}?targetProfileId=stage`))
  assert.match(markup, /Verified release/)
  assert.match(markup, /1 active Engage app is assigned to this release/)
  assert.match(markup, /Explore your customer experience/)
  for (const old of ['Create from a reviewed offer', 'All setup controls', 'Operations policy', 'My smart brains']) assert.ok(!markup.includes(old))
})
