import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.CUSTOMER_LAYOUT_OUTPUT ?? '/private/tmp/01044-customer-layout-parity-20261023/run-' + Date.now()
const baseline = JSON.parse(await readFile(new URL('../../../verification-support/platform-ui-capability-baseline/2026-10-23-pre-customer-layout.json', import.meta.url)))
await mkdir(output, { recursive: true })
const results = [], errors = [], escaped = []
const moduleId = '\0customer-shell-fixture'
const server = await createServer({
  root, configFile: false, envFile: false, cacheDir: output + '/vite-cache', esbuild: { jsx: 'automatic' },
  logLevel: 'error', server: { host: '127.0.0.1', port: 4398, strictPort: true },
  plugins: [{
    name: 'customer-shell-fixture',
    resolveId(id) { if (id === '/customer-shell-fixture.jsx') return moduleId },
    load(id) {
      if (id !== moduleId) return
      return `
        import React from 'react';
        import { createRoot } from 'react-dom/client';
        import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
        import { MemoryRouter } from 'react-router-dom';
        import { ThemeProvider, CssBaseline } from '@mui/material';
        import { AppShell } from '/src/layout/AppShell.tsx';
        import { DeploymentWorkspaceProvider } from '/src/workspace/DeploymentWorkspaceContext.tsx';
        import { platformTheme } from '/src/theme/platformTheme.ts';
        const role = new URL(location.href).searchParams.get('role');
        const session = { enabled: true, authenticated: true, actorId: 'fixture-user', displayName: 'Fixture User',
          role, customerId: 'fixture-customer', customerName: 'Fixture Company', authenticationMode: 'SESSION',
          canManageUsers: role === 'PLATFORM_ADMIN', canManageCustomers: role === 'PLATFORM_ADMIN',
          canManageUserDirectory: role === 'PLATFORM_ADMIN' };
        const client = new QueryClient({defaultOptions:{queries:{retry:false,staleTime:Infinity}}});
        const deployment = { id:'fixture',name:'Shared Workspace',status:'ACTIVE',behaviorType:'SMART_BRAIN' };
        client.setQueryData(['deployments'],[deployment]);
        client.setQueryData(['deployment-workspace','fixture'],{ deployment,
          documentKnowledgeConfigured:true,externalIntegrationConfigured:true,conversationHandoverConfigured:true });
        createRoot(document.getElementById('root')).render(
          React.createElement(QueryClientProvider,{client},React.createElement(ThemeProvider,{theme:platformTheme},
          React.createElement(CssBaseline),React.createElement(MemoryRouter,{initialEntries:['/shell-probe?deploymentId=fixture']},
          React.createElement(DeploymentWorkspaceProvider,null,React.createElement(AppShell,{session,onSignOut:()=>{}},
          React.createElement('h2',null,'Workspace resources')))))));
      `
    },
    configureServer(vite) {
      vite.middlewares.use((request, response, next) => {
        if (!request.url.startsWith('/shell-probe')) return next()
        response.setHeader('Content-Type', 'text/html')
        response.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script type="module" src="/customer-shell-fixture.jsx"></script></body></html>')
      })
    },
  }],
})
let browser, failure = null
try {
  await server.listen()
  const base = server.resolvedUrls.local[0]
  browser = await chromium.launch({ headless: true })
  for (const role of ['PLATFORM_ADMIN', 'PLATFORM_OPERATOR', 'CUSTOMER_ADMIN']) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' })
    const page = await context.newPage()
    page.setDefaultTimeout(15000)
    page.on('pageerror', error => errors.push(error.message))
    await context.route('**/*', route => {
      const request = route.request(), url = new URL(request.url())
      if (url.origin !== new URL(base).origin || !['GET', 'HEAD'].includes(request.method()) || url.pathname.startsWith('/api/')) {
        escaped.push({ role, method: request.method(), path: url.pathname })
        return route.abort()
      }
      return route.continue()
    })
    await page.goto(base + 'shell-probe?role=' + role)
    await page.getByRole('heading', { name: 'Workspace resources' }).waitFor()
    const nav = page.locator('#platform-navigation')
    const links = nav.getByRole('link')
    const paths = await links.evaluateAll(nodes => nodes.map(node => new URL(node.href).pathname).sort())
    const expected = role === 'CUSTOMER_ADMIN'
      ? ['/deployments', '/ai-workspaces', '/document-knowledge', '/integrations', '/human-review', '/notifications', '/users'].sort()
      : baseline.navigation.filter(item => !item.customerReviewerOnly
        && (role === 'PLATFORM_ADMIN' || (!item.platformAdminOnly && !item.customerManagement && !item.userDirectory)))
        .map(item => item.path).sort()
    assert.deepEqual(paths, expected, role + ' navigation differs from the preserved role boundary')
    await page.screenshot({ path: output + '/' + role + '-desktop.png', fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    const menu = page.getByRole('button', { name: 'Open navigation', exact: true })
    await menu.focus(); await menu.press('Enter')
    await nav.getByRole('link').first().waitFor({ state: 'visible' })
    await page.waitForFunction(() => {
      const box = document.querySelector('#platform-navigation .MuiDrawer-paper')?.getBoundingClientRect()
      return box && Math.abs(box.left) < 1 && box.right <= innerWidth
    })
    await page.keyboard.press('Tab')
    assert.equal(await page.evaluate(() => document.activeElement?.tagName), 'A')
    await page.screenshot({ path: output + '/' + role + '-mobile-navigation.png', fullPage: true })
    await page.keyboard.press('Escape')
    await nav.waitFor({ state: 'hidden' })
    assert.equal(await menu.getAttribute('aria-expanded'), 'false')
    assert.equal(await menu.evaluate(node => node === document.activeElement), true)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true)
    await page.screenshot({ path: output + '/' + role + '-mobile.png', fullPage: true })
    results.push({ role, status: 'PASS', navigationEntries: paths.length, keyboardMenuAndFocusReturn: true, mobileFits: true })
    await context.close()
  }
  assert.deepEqual(errors, [])
  assert.deepEqual(escaped, [])
} catch (error) {
  failure = String(error.stack); process.exitCode = 1
} finally {
  if (browser) await browser.close()
  await server.close()
  const report = { at: new Date().toISOString(), status: failure ? 'FAIL' : 'PASS', results, errors, escaped, failure,
    limitation: 'Actual shared shell, synthetic local role/context fixtures. Not a rerun of every staff mutation or hosted authorization.' }
  await writeFile(output + '/report.actual.json', JSON.stringify(report, null, 2) + '\n', { flag: 'wx' })
  console.log(JSON.stringify(report))
}
