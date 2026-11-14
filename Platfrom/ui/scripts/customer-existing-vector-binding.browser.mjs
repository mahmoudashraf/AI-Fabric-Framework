import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'
import { createServer, transformWithEsbuild } from 'vite'
import react from '@vitejs/plugin-react'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('../', import.meta.url))
const cache = await mkdtemp(`${tmpdir()}/existing-vector-binding-`)
const fixture = `
const copy = value => structuredClone(value);
const params = new URLSearchParams(location.search);
const profile = (id, environmentName) => ({id,environmentName,active:true,defaultForRuntime:false});
const identity = {authenticated:true,actorId:'owner',customerId:'customer',role:'CUSTOMER_ADMIN',authenticationMode:'SESSION'};
export const state = window.vectorFixture = {calls:[],customerId:'customer',
  targets:[profile('stage-old','staging'),profile('stage-image','staging'),profile('production','production')],
  releases:params.get('mode')==='other-release'?[{id:'old-release',deploymentId:'brain',targetProfileId:'stage-old',createdAt:'2026-11-14',status:'APPLIED_VERIFIED'}]:[]};
if(params.get('mode')==='inactive')state.targets[1].active=false;
const version={id:'v1',deploymentId:'brain',status:'PUBLISHED',versionLabel:'v1',configHash:'hash'};
export function usePlatformAuth(){return {session:identity}}
export async function fetchPlatformAuthSession(){return copy(identity)}
export async function fetchDeploymentWorkspace(){return {deployment:{id:'brain',binding:{customerId:state.customerId}},access:{canAdmin:true}}}
export async function fetchDeploymentTargetOptions(){return copy(state.targets)}
export async function fetchDeploymentReleases(){return copy(state.releases)}
export async function fetchDeploymentVersions(){return [copy(version)]}
export async function fetchDeploymentVersionManifest(){return {deploymentId:'brain',versionId:'v1',providerConfig:{vectorStrategy:'milvus',vectorProvisioningMode:'EXTERNAL_EXISTING',vectorStoragePosture:'SHARED',milvusExternalBindingRequired:true,milvusAuthenticationType:'TOKEN'}}}
export class PlatformApiError extends Error {constructor(status){super('Not found');this.status=status}}
export async function fetchCustomerExistingVectorBinding(){throw new PlatformApiError(404)}
export async function bindCustomerExistingVector(deploymentId,targetId,payload){state.calls.push({deploymentId,targetId,payload});return {bindingId:'binding',deploymentId,targetProfileId:targetId,versionId:payload.versionId,clusterId:payload.clusterId,authType:payload.authType,lifecycle:'RETAIN_EXTERNAL',databaseName:'default',endpointHost:'vector.invalid',collectionPrefix:'owned_'}}
`
const harness = `
import React from 'react';import {createRoot} from 'react-dom/client';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {ThemeProvider,CssBaseline,Box} from '@mui/material';
import {createConsoleTheme} from '/src/theme/consoleTheme.ts';
import {CustomerExistingVectorBinding} from '/src/components/customer/CustomerExistingVectorBinding.tsx';
const params=new URLSearchParams(location.search),target=params.has('target')?params.get('target'):undefined;
createRoot(document.getElementById('root')).render(<ThemeProvider theme={createConsoleTheme('dark')}><CssBaseline/><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><Box sx={{p:3,maxWidth:1000}}><CustomerExistingVectorBinding deploymentId="brain" targetProfileId={target}/></Box></QueryClientProvider></ThemeProvider>);
`
const server = await createServer({ root, configFile: false, cacheDir: cache,
  resolve: { dedupe: ['react', 'react-dom'] }, plugins: [react(), {
    name: 'existing-vector-fixture', enforce: 'pre',
    resolveId(id, importer) {
      if (id === '/virtual:vector-harness') return '\0vector-harness'
      if (importer?.endsWith('/CustomerExistingVectorBinding.tsx')
        && ['../../api/platformApi', '../../auth/PlatformAuthProvider'].includes(id)) return '\0vector-fixture'
    },
    async load(id) {
      if (id === '\0vector-fixture') return fixture
      if (id === '\0vector-harness') return (await transformWithEsbuild(harness, 'harness.jsx', { loader: 'jsx', jsx: 'automatic' })).code
    },
    configureServer(vite) { vite.middlewares.use((req, res, next) => {
      if (!req.url?.startsWith('/vector-check')) return next()
      res.setHeader('Content-Type', 'text/html')
      res.end('<html><body><div id="root"></div><script type="module" src="/virtual:vector-harness"></script></body></html>')
    }) },
  }], server: { host: '127.0.0.1', port: 0, hmr: false } })

let browser
try {
  await server.listen()
  browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1200, height: 900 } })
  page.setDefaultTimeout(15000)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const open = async query => {
    await page.goto(`${server.resolvedUrls.local[0]}vector-check?${query}`)
    await page.getByRole('button', { name: 'Existing vector service', exact: true }).click()
    const version = page.getByRole('combobox', { name: 'Published vector version', exact: true })
    await version.click()
    await page.getByRole('option', { name: 'v1', exact: true }).click()
  }
  const fill = async () => {
    for (const [label, value] of [['Zilliz cluster ID', 'cluster'], ['Zilliz project ID', 'project'],
      ['Zilliz region ID', 'region'], ['Milvus serving token', 'synthetic-serving-token']]) {
      await page.getByLabel(label, { exact: true }).fill(value)
    }
  }
  for (const mode of ['', 'other-release']) {
    await open(`target=stage-image&mode=${mode}`)
    await page.getByText('No vector binding is recorded for this target.', { exact: true }).waitFor()
    await fill()
    await page.getByRole('button', { name: 'Check and bind existing vector service', exact: true }).click()
    await page.getByText('Stored vector binding: binding', { exact: true }).waitFor()
    const calls = await page.evaluate(() => window.vectorFixture.calls)
    assert.equal(calls.length, 1)
    assert.equal(calls[0].targetId, 'stage-image')
    assert.equal(calls[0].payload.versionId, 'v1')
    assert.equal(await page.getByText('Choose an unambiguous environment in the Smart Brain header before binding storage.', { exact: true }).count(), 0)
  }
  for (const query of ['target=missing', 'target=stage-image&mode=inactive', 'target=']) {
    await open(query)
    await page.getByText('Choose an unambiguous environment in the Smart Brain header before binding storage.', { exact: true }).waitFor()
    assert.equal(await page.getByLabel('Milvus serving token', { exact: true }).count(), 0)
    assert.deepEqual(await page.evaluate(() => window.vectorFixture.calls), [])
  }
  await open('target=stage-image')
  await fill()
  await page.evaluate(() => { window.vectorFixture.targets.find(target => target.id === 'stage-image').active = false })
  await page.getByRole('button', { name: 'Check and bind existing vector service', exact: true }).click()
  await page.getByText('The customer, target or published version changed. Reopen the connection before binding.', { exact: true }).waitFor()
  assert.deepEqual(await page.evaluate(() => window.vectorFixture.calls), [])
  await open('target=stage-image')
  await fill()
  await page.evaluate(() => { window.vectorFixture.customerId = 'another-customer' })
  await page.getByRole('button', { name: 'Check and bind existing vector service', exact: true }).click()
  await page.getByText('The customer, target or published version changed. Reopen the connection before binding.', { exact: true }).waitFor()
  assert.deepEqual(await page.evaluate(() => window.vectorFixture.calls), [])
  assert.deepEqual(errors, [])
  console.log('PASS: exact immutable target in display and POST, other-profile releases, absent/inactive targets, fresh target and customer revalidation.')
} finally {
  await browser?.close()
  await server.close()
  await rm(cache, { recursive: true, force: true })
}
