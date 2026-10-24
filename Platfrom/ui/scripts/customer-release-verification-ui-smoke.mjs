import assert from 'node:assert/strict'
import { mkdtemp, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = await mkdtemp('/private/tmp/01045-release-verification-ui-')
const results = []
let server, browser
const until = async check => {
  const deadline = Date.now() + 10000
  while (!check()) { assert.ok(Date.now() < deadline, 'Expected request was not observed'); await new Promise(r => setTimeout(r, 20)) }
}
try {
  server = await createServer({ root, configFile: false, envFile: false, cacheDir: output + '/vite-cache',
    esbuild: { jsx: 'automatic' }, server: { host: '127.0.0.1', port: 41973, strictPort: false },
    plugins: [{ name: 'release-verification-fixture', enforce: 'pre',
      resolveId(id) { if (id === '/verification-entry.jsx') return '\0verification-entry.jsx' },
      load(id) {
        if (id !== '\0verification-entry.jsx') return
        return `import React,{useState} from 'react';import{createRoot}from'react-dom/client';
          import{QueryClient,QueryClientProvider}from'@tanstack/react-query';
          import{CustomerReleaseVerificationButton}from'/src/components/customer/CustomerReleaseVerificationButton.tsx';
          const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
          function Harness(){const[actor,setActor]=useState('owner'),[release,setRelease]=useState({id:'selected-release',
            deploymentId:'deployment',targetProfileId:'target',deploymentVersionId:'version',status:'APPLIED_VERIFICATION_FAILED',provisioningStatus:'ACTIVE'});
            window.setFixtureActor=setActor;window.setFixtureRelease=setRelease;window.verifiedCount??=0;
            return React.createElement(QueryClientProvider,{client},React.createElement('main',null,
              React.createElement('span',null,actor),React.createElement(CustomerReleaseVerificationButton,{
                session:{actorId:actor,customerId:'customer',authenticationMode:'SESSION',role:'CUSTOMER_ADMIN'},
                offer:{id:'offer',fingerprint:'offer-hash',entitlement:{revision:2}},deploymentId:'deployment',release,
                label:'Verify selected release',onVerified:async()=>{window.verifiedCount++}})));}
          createRoot(document.getElementById('root')).render(React.createElement(Harness));`
      },
      configureServer(vite) { vite.middlewares.use((request, response, next) => {
        if (request.url !== '/verification-fixture') return next()
        response.setHeader('Content-Type', 'text/html')
        response.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/verification-entry.jsx"></script></body></html>')
      }) },
    }],
  })
  await server.listen()
  const base = server.resolvedUrls.local[0]
  browser = await chromium.launch()
  for (const scenario of ['passed', 'failed', 'wrong-release', 'wrong-version', 'scope-during-verification', 'release-during-verification', 'auth-denied', 'not-provisioned']) {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, serviceWorkers: 'block' })
    const page = await context.newPage(), calls = [], errors = []
    let pendingVerification
    page.on('pageerror', e => errors.push(e.message))
    await context.route('**/*', async route => {
      const request = route.request(), url = new URL(request.url())
      assert.equal(url.origin, new URL(base).origin, 'No external services allowed in an offline UI fixture')
      if (!url.pathname.startsWith('/api/')) return route.continue()
      calls.push({ path: url.pathname, query: url.search, method: request.method() })
      assert.equal(url.pathname, '/api/deployments/deployment/verification-runs/recheck')
      assert.equal(request.method(), 'POST')
      assert.equal(url.searchParams.get('releaseId'), 'selected-release', 'Never verify the latest/other release implicitly')
      const respond = () => route.fulfill({ status: scenario === 'auth-denied' ? 403 : 201, contentType: 'application/json', body: JSON.stringify(scenario === 'auth-denied'
        ? { message: 'Release verification is not authorized for this customer.' } : {
        id: 'verification', deploymentId: 'deployment', releaseId: scenario === 'wrong-release' ? 'other-release' : 'selected-release',
        deploymentVersionId: scenario === 'wrong-version' ? 'other-version' : 'version',
        status: scenario === 'failed' ? 'FAILED' : 'PASSED', summaryMessage: '1 failed check',
      }) })
      if (['scope-during-verification', 'release-during-verification'].includes(scenario)) return new Promise(resolve => {
        pendingVerification = async () => { await respond(); resolve() }
      })
      return respond()
    })
    try {
      await page.goto(base + 'verification-fixture')
      const button = page.getByRole('button', { name: 'Verify selected release', exact: true })
      await button.waitFor(); assert.equal(calls.length, 0, 'Mounting must never recheck automatically')
      if (scenario === 'not-provisioned') {
        await page.evaluate(() => window.setFixtureRelease({ id: 'new', deploymentId: 'deployment', targetProfileId: 'target',
          deploymentVersionId: 'version', status: 'PROVISIONING', provisioningStatus: 'RUNNING' }))
        await page.waitForFunction(() => document.querySelector('button')?.disabled)
        assert.equal(calls.length, 0)
      } else {
        await button.click()
        if (['scope-during-verification', 'release-during-verification'].includes(scenario)) {
          await until(() => pendingVerification)
          if (scenario === 'scope-during-verification') {
            await page.evaluate(() => window.setFixtureActor('different-owner'))
            await page.getByText('different-owner', { exact: true }).waitFor()
          } else {
            await page.evaluate(() => window.setFixtureRelease({ id: 'other-release', deploymentId: 'deployment', targetProfileId: 'target',
              deploymentVersionId: 'other-version', status: 'APPLIED_VERIFICATION_FAILED', provisioningStatus: 'ACTIVE' }))
          }
          await pendingVerification()
          await button.waitFor(); await page.waitForFunction(() => !document.querySelector('button')?.disabled)
          assert.equal(calls.length, 1); assert.equal(await page.evaluate(() => window.verifiedCount), 0)
          assert.equal(await page.getByRole('alert').count(), 0, 'Old-scope results must not appear in the new scope')
        } else {
          const expected = scenario === 'passed' ? 'Release verification passed.' : scenario === 'failed'
            ? 'Verification needs attention: 1 failed check' : scenario === 'auth-denied'
              ? 'Release verification is not authorized for this customer.' : 'Verification response did not match the selected release.'
          await page.getByRole('alert').filter({ hasText: expected }).waitFor()
          assert.equal(calls.length, 1); assert.equal(await page.evaluate(() => window.verifiedCount), ['passed', 'failed'].includes(scenario) ? 1 : 0)
        }
      }
      assert.deepEqual(errors, [])
      await page.setViewportSize({ width: 390, height: 844 })
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
      await page.screenshot({ path: output + '/' + scenario + '.png', fullPage: true })
      results.push({ scenario, status: 'PASS', calls })
    } finally { await context.close() }
  }
  console.log(JSON.stringify({ status: 'PASS', scenarios: results.length, output }))
} finally {
  await browser?.close(); await server?.close()
  await writeFile(output + '/report.json', JSON.stringify({ results }, null, 2), { flag: 'wx' })
}
