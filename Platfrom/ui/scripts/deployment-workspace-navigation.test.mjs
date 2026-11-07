import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import vm from 'node:vm'
import ts from 'typescript'

const navigationSource = readFileSync(new URL('../src/brain/brainNavigation.ts', import.meta.url), 'utf8')
const navigationCode = ts.transpileModule(navigationSource, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
const navigation = {}
new Function('exports', navigationCode)(navigation)

function fixture() {
  let location = { pathname: '/deployments', search: '' }, hook = 0
  const memos = [], navigations = [], deployments = [{ id: 'dep-a', status: 'DRAFT' }]
  const preferences = { deploymentWorkspace: { lastDeploymentId: 'dep-a' } }
  const memo = (factory, deps) => {
    const index = hook++, previous = memos[index]
    if (!previous || !deps.every((value, i) => Object.is(value, previous.deps[i]))) {
      memos[index] = { deps, value: factory() }
    }
    return memos[index].value
  }
  const navigate = (...args) => navigations.push(args)
  const api = { fetchDeployments() {}, fetchPlatformUserPreferences() {}, fetchDeploymentWorkspace() {}, updatePlatformUserPreferences() {} }
  const react = { createContext: () => ({ Provider: 'provider' }), useContext() {}, useEffect() {},
    useMemo: memo, useRef: value => ({ current: value }), useState: value => [value, () => {}] }
  const queries = { useQueryClient: () => ({ setQueryData() {} }), useMutation: () => ({ mutate() {} }),
    useQuery: ({ queryFn }) => ({ isLoading: false, isSuccess: true,
      data: queryFn === api.fetchDeployments ? deployments : queryFn === api.fetchPlatformUserPreferences ? preferences : null }) }
  const source = process.env.WORKSPACE_NAVIGATION_BASELINE
    ? execFileSync('git', ['show', `${process.env.WORKSPACE_NAVIGATION_BASELINE}:Platfrom/ui/src/workspace/DeploymentWorkspaceContext.tsx`], { encoding: 'utf8' })
    : readFileSync(new URL('../src/workspace/DeploymentWorkspaceContext.tsx', import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX } }).outputText
  const module = { exports: {} }
  const require = id => {
    if (id === 'react') return react
    if (id === 'react/jsx-runtime') return { jsx: (type, props) => ({ type, props }) }
    if (id === '@tanstack/react-query') return queries
    if (id === 'react-router-dom') return { useLocation: () => location, useNavigate: () => navigate }
    if (id === '../api/platformApi') return api
    if (id === '../brain/brainNavigation') return navigation
    throw new Error('Unexpected fixture import: ' + id)
  }
  vm.runInNewContext(compiled, { module, exports: module.exports, require, URLSearchParams })
  const render = next => { location = next; hook = 0; return module.exports.DeploymentWorkspaceProvider({ children: null }).props.value }
  return { render, navigations }
}

test('deployment selection preserves the latest setup query when identity and query data are unchanged', () => {
  const f = fixture()
  f.render({ pathname: '/deployments', search: '' })
  const value = f.render({ pathname: '/deployments', search: '?view=setup&step=template&offerId=offer-a' })
  value.setSelectedDeploymentId('dep-new')
  assert.deepEqual(JSON.parse(JSON.stringify(f.navigations.at(-1))), [
    { pathname: '/deployments', search: '?view=setup&step=template&offerId=offer-a&deploymentId=dep-new' }, { replace: false },
  ])
})

test('deployment selection uses the current editor route rather than a previous scoped page', () => {
  const f = fixture()
  f.render({ pathname: '/users', search: '?deploymentId=dep-a' })
  const value = f.render({ pathname: '/integrations', search: '?deploymentId=dep-a&target=production' })
  value.setSelectedDeploymentId('dep-b')
  assert.deepEqual(JSON.parse(JSON.stringify(f.navigations.at(-1))), [
    { pathname: '/integrations', search: '?deploymentId=dep-b&target=production' }, { replace: false },
  ])
})

test('clearing a selection preserves current setup and does not navigate to an old page', () => {
  const f = fixture()
  f.render({ pathname: '/providers', search: '?deploymentId=dep-a' })
  const value = f.render({ pathname: '/deployments', search: '?view=setup&step=configure&deploymentId=dep-a' })
  value.setSelectedDeploymentId('')
  assert.deepEqual(JSON.parse(JSON.stringify(f.navigations.at(-1))), [
    { pathname: '/deployments', search: '?view=setup&step=configure' }, { replace: false },
  ])
})

test('a canonical resource path wins over stale query and saved selection', () => {
  const f = fixture()
  const value = f.render({ pathname: '/smart-brains/dep-a/responses', search: '?deploymentId=foreign-brain&targetProfileId=target-production' })
  assert.equal(value.selectedDeploymentId, 'dep-a')
  const next = value.buildWorkspacePath('/prompts')
  assert.equal(next, '/smart-brains/dep-a/responses?targetProfileId=target-production')
})
