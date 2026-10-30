import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { mkdir } from 'node:fs/promises'

const root = fileURLToPath(new URL('..', import.meta.url))
const entry = '\0team-intake-fixture.jsx', authModule = '\0team-intake-auth.jsx'
const evidence = '/private/tmp/loomai-support-ui-01054'
await mkdir(evidence, { recursive: true })
const calls = [], errors = [], consoleErrors = []
let allowed = true, expectedToken = 'fixture-registered-agent'
const server = await createServer({ root, configFile: false, envFile: false, logLevel: 'error', esbuild: { jsx: 'automatic' },
  server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'team-intake-fixture', enforce: 'pre',
    resolveId(id) { if (id === '/team-intake-fixture.jsx') return entry; if (id.endsWith('/auth/PlatformAuthProvider') || id === './PlatformAuthProvider') return authModule },
    load(id) {
      if (id === entry) return `import React from'react';import{createRoot}from'react-dom/client';
        import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{PlatformRouting}from'/src/layout/PlatformRouting.tsx';
        window.__identity={subject:'desk-agent',token:'fixture-registered-agent'};
        createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client:new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}})},React.createElement(PlatformRouting)));`
      if (id === authModule) return `import{useEffect,useState}from'react';import{PlatformApiError}from'/src/api/platformApi.ts';
        function change(value){window.__identity=value;window.dispatchEvent(new Event('fixture-identity'));}
        export function startCustomerIdentityLink(){throw new Error('Staff intake must not create Console memberships')}
        export function usePlatformAuth(){const[,render]=useState(0);useEffect(()=>{const fn=()=>render(n=>n+1);window.addEventListener('fixture-identity',fn);return()=>window.removeEventListener('fixture-identity',fn)},[]);
          const identity=window.__identity;
          return {apiKey:'',session:identity?.platformAdmin?{authenticated:true,role:'PLATFORM_ADMIN',actorId:'platform-admin'}:null,
            isLoading:false,customerAuthLoading:false,customerConfig:{enabled:true,projectUrl:'https://identity.example.test'},
            error:identity?.subject?new PlatformApiError(403,'Console identity not linked','CUSTOMER_IDENTITY_NOT_LINKED'):null,
            supabaseSession:identity?.subject?{user:{id:identity.subject,email:'agent@example.test'}}:null,
            supabase:{auth:{getSession:async()=>({data:{session:window.__identity?.subject?{user:{id:window.__identity.subject},access_token:window.__identity.token}:null}}),
              signInWithPassword:async input=>{window.__signIn=input;change({subject:'desk-agent',token:'fixture-registered-agent'});return{error:null}},
              resetPasswordForEmail:async()=>({error:null})}},signOut:async()=>change(null),
            signInCustomer:async()=>{throw new Error('Do not onboard Desk agents into Console customers')}};}`
    },
    configureServer(vite) { vite.middlewares.use((req, res, next) => {
      if (new URL(req.url, 'http://fixture').pathname !== '/support/team-intake') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/team-intake-fixture.jsx"></script></body></html>')
    }) },
  }] })
await server.listen()
const browser = await chromium.launch({ headless: true })
let page
try {
  page = await browser.newPage({ viewport: { width: 1450, height: 940 } })
  page.setDefaultTimeout(25_000)
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()) })
  page.on('requestfailed', request => errors.push(`${request.url()}: ${request.failure()?.errorText}`))
  await page.route('**/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    calls.push({ path, method: request.method(), authorization: request.headers().authorization })
    assert.equal(path, '/api/support/staff/intake', 'The staff page must not onboard customers, load transcripts or contact another API')
    assert.equal(request.method(), 'GET', 'Staff intake is read-only')
    assert.equal(request.headers()['x-platform-api-key'], undefined)
    assert.equal(request.headers()['x-loomai-desk-service-key'], undefined)
    const permitted = allowed && request.headers().authorization === `Bearer ${expectedToken}`
    return route.fulfill({ status: permitted ? 200 : 403, contentType: 'application/json', body: JSON.stringify(permitted ? [{
      caseId: 'sup-unstarted', reference: 'SUP-OUTAGE-1', summary: 'Help connecting a website while runtime is unavailable',
      details: 'My original request remains saved.', status: 'OPEN', version: 1, accountLabel: 'Northfield', resourceLabels: ['Website workspace'],
      consent: { status: 'DECLINED', summary: 'Status-check permission not granted', updatedAt: null }, initialMessageDelivery: 'UNKNOWN',
      createdAt: '2026-10-30T12:00:00Z', updatedAt: '2026-10-30T12:01:00Z',
    }] : { message: 'SUPPORT access required' }) })
  })
  await page.goto(server.resolvedUrls.local[0] + 'support/team-intake')
  await page.getByRole('heading', { name: 'Team intake', exact: true }).waitFor({ timeout: 60_000 })
  await page.getByText('SUP-OUTAGE-1', { exact: true }).waitFor()
  assert.equal(await page.getByRole('textbox').count(), 0, 'There must be no second chat/reply editor')
  assert.equal(await page.getByText('Create your customer account', { exact: true }).count(), 0)
  await page.screenshot({ path: `${evidence}/04-team-intake.png`, fullPage: true })
  expectedToken = 'fixture-rotated-agent'
  await page.evaluate(token => { window.__identity.token = token }, expectedToken)
  const refreshed = page.waitForResponse(response => response.url().includes('/api/support/staff/intake'))
  await page.getByRole('button', { name: 'Refresh team intake', exact: true }).click()
  await refreshed
  assert.equal(calls.at(-1).authorization, `Bearer ${expectedToken}`)
  await page.getByText('SUP-OUTAGE-1', { exact: true }).waitFor()
  allowed = false
  await page.getByRole('button', { name: 'Refresh team intake', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: 'current SUPPORT assignment' }).waitFor()
  assert.equal(await page.getByText('SUP-OUTAGE-1', { exact: true }).count(), 0, 'A current 403 must not display cached case data')

  await page.evaluate(() => { window.__identity = { subject: null, platformAdmin: true }; window.dispatchEvent(new Event('fixture-identity')) })
  await page.getByRole('heading', { name: 'Sign in for team intake', exact: true }).waitFor()
  assert.equal(await page.getByText('SUP-OUTAGE-1', { exact: true }).count(), 0, 'A Platform admin principal grants no staff access')
  const beforeLogin = calls.length
  allowed = true; expectedToken = 'fixture-registered-agent'
  await page.getByRole('textbox', { name: 'Email', exact: true }).fill('agent@example.test')
  await page.getByLabel(/^Password/).fill('fixture-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.getByText('SUP-OUTAGE-1', { exact: true }).waitFor()
  assert.ok(calls.length > beforeLogin)
  assert.equal(new URL(page.url()).pathname, '/support/team-intake', 'Desk-only sign-in must not redirect to customer onboarding')
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await page.getByRole('heading', { name: 'Sign in for team intake', exact: true }).waitFor()
  assert.equal(await page.getByText('SUP-OUTAGE-1', { exact: true }).count(), 0)
  assert.deepEqual(errors, [])
  console.log('Support team-intake UI passed: real App early branch, Desk-only sign-in, refreshed browser bearer, SUPPORT denial, cached-row removal, admin non-authority and logout. API responses are local fixtures, not hosted JWT proof.')
} catch (error) {
  console.error(JSON.stringify({ errors, consoleErrors, calls, body: await page?.locator('body').innerText().catch(() => '') }, null, 2))
  throw error
} finally {
  await page?.screenshot({ path: `${evidence}/team-intake-final.png`, fullPage: true }).catch(() => {})
  await browser.close(); await server.close()
}
