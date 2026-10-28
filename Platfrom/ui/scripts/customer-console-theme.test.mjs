import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test, { after, before } from 'node:test'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer } from 'vite'

const preferenceKey = 'loomai-customer-console-color-scheme'
const root = fileURLToPath(new URL('..', import.meta.url))
const moduleId = '\0customer-console-theme-fixture'
let server, browser, cacheDir, base

before(async () => {
  cacheDir = await mkdtemp(join(tmpdir(), 'customer-console-theme-'))
  server = await createServer({
    root, configFile: false, envFile: false, cacheDir,
    esbuild: { jsx: 'automatic' }, logLevel: 'error',
    server: { host: '127.0.0.1', port: 0 },
    plugins: [{
      name: 'customer-console-theme-fixture',
      resolveId(id) { if (id === '/customer-console-theme-fixture.jsx') return moduleId },
      load(id) {
        if (id !== moduleId) return
        return `
          import React from 'react';
          import { createRoot } from 'react-dom/client';
          import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
          import { MemoryRouter } from 'react-router-dom';
          import { ThemeProvider, CssBaseline, useTheme } from '@mui/material';
          import { CustomerShellLayout } from '/src/layout/CustomerConsoleShell.tsx';
          import { AppShell } from '/src/layout/AppShell.tsx';
          import { DeploymentWorkspaceProvider } from '/src/workspace/DeploymentWorkspaceContext.tsx';
          import { platformTheme } from '/src/theme/platformTheme.ts';
          const role = new URL(location.href).searchParams.get('role') ?? 'CUSTOMER_ADMIN';
          const session = {
            enabled: true, authenticated: true, actorId: 'theme-test-user', displayName: 'Theme User',
            role, customerId: 'theme-test-customer', customerName: 'Theme Test', authenticationMode: 'SESSION',
            canManageUsers: role === 'PLATFORM_ADMIN', canManageCustomers: role === 'PLATFORM_ADMIN',
            canManageUserDirectory: role === 'PLATFORM_ADMIN',
          };
          function Probe() {
            const theme = useTheme();
            return React.createElement(React.Fragment, null,
              React.createElement('output', { 'data-testid': 'palette-mode' }, theme.palette.mode),
              React.createElement('input', { 'aria-label': 'Unsaved draft' }));
          }
          const props = { session, onSignOut: async () => {}, buildWorkspacePath: path => path };
          const shell = role === 'CUSTOMER_ADMIN'
            ? React.createElement(CustomerShellLayout, props, React.createElement(Probe))
            : React.createElement(DeploymentWorkspaceProvider, null,
                React.createElement(AppShell, props, React.createElement(Probe)));
          const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
          createRoot(document.getElementById('root')).render(
            React.createElement(React.StrictMode, null,
              React.createElement(QueryClientProvider, { client },
                React.createElement(ThemeProvider, { theme: platformTheme },
                  React.createElement(CssBaseline),
                  React.createElement(MemoryRouter, { initialEntries: ['/home'] }, shell)))));
        `
      },
      configureServer(vite) {
        vite.middlewares.use((request, response, next) => {
          if (new URL(request.url, 'http://localhost').pathname !== '/theme-fixture') return next()
          response.setHeader('Content-Type', 'text/html')
          response.end('<!doctype html><html><body><div id="root"></div><script type="module" src="/customer-console-theme-fixture.jsx"></script></body></html>')
        })
      },
    }],
  })
  await server.listen()
  base = server.resolvedUrls.local[0]
  browser = await chromium.launch({ headless: true })
})

after(async () => {
  await browser?.close()
  await server?.close()
  if (cacheDir) await rm(cacheDir, { recursive: true, force: true })
})

async function fixture(t, { saved = null, colorScheme = 'light', blocked = null, role = 'CUSTOMER_ADMIN' } = {}) {
  const context = await browser.newContext({
    colorScheme, viewport: { width: 1440, height: 960 }, serviceWorkers: 'block',
    storageState: { cookies: [], origins: [{
      origin: new URL(base).origin,
      localStorage: saved === null ? [] : [{ name: preferenceKey, value: saved }],
    }] },
  })
  const errors = [], unexpectedRequests = []
  t.after(async () => {
    await context.close()
    assert.deepEqual(errors, [], 'Rendering and theme controls must not throw')
    assert.deepEqual(unexpectedRequests, [], 'Theme fixtures must not contact APIs or external services')
  })
  await context.addInitScript(blocked => {
    const deny = () => { throw new DOMException('Storage is blocked', 'SecurityError') }
    if (blocked === 'access') Object.defineProperty(window, 'localStorage', { get: deny })
    if (blocked === 'read-write') {
      Storage.prototype.getItem = deny
      Storage.prototype.setItem = deny
    }
    if (blocked === 'write') Storage.prototype.setItem = deny
  }, blocked)
  await context.route('**/*', route => {
    const request = route.request(), url = new URL(request.url())
    if (url.origin !== new URL(base).origin || url.pathname.startsWith('/api/') || request.method() !== 'GET') {
      unexpectedRequests.push(`${request.method()} ${url.href}`)
      return route.abort()
    }
    return route.continue()
  })
  const page = await context.newPage()
  page.setDefaultTimeout(15_000)
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(`${base}theme-fixture?role=${role}`)
  await page.getByTestId('palette-mode').waitFor()
  return page
}

async function expectMode(page, mode) {
  await page.waitForFunction(mode =>
    document.querySelector('[data-customer-theme]')?.getAttribute('data-customer-theme') === mode
    && document.querySelector('[data-testid="palette-mode"]')?.textContent === mode, mode)
  assert.equal(await page.locator('[data-customer-theme]').evaluate(node => getComputedStyle(node).backgroundColor),
    mode === 'dark' ? 'rgb(23, 25, 28)' : 'rgb(247, 248, 250)')
}

const storedPreference = page => page.evaluate(key => localStorage.getItem(key), preferenceKey)
const chooser = page => page.getByRole('combobox', { name: 'Customer color theme', exact: true })

async function choose(page, label) {
  await chooser(page).click()
  await page.getByRole('option', { name: label, exact: true }).click()
}

test('new visitors default to dark under a light OS without persisting an explicit preference', async t => {
  const page = await fixture(t)
  await expectMode(page, 'dark')
  assert.equal(await chooser(page).innerText(), 'Dark')
  assert.equal(await storedPreference(page), null)
})

for (const saved of ['light', 'dark']) {
  test(`saved ${saved} overrides the OS and survives reload`, async t => {
    const page = await fixture(t, { saved, colorScheme: saved === 'light' ? 'dark' : 'light' })
    await expectMode(page, saved)
    assert.equal(await chooser(page).innerText(), saved === 'light' ? 'Light' : 'Dark')
    await page.emulateMedia({ colorScheme: 'light' })
    await expectMode(page, saved)
    await page.emulateMedia({ colorScheme: 'dark' })
    await expectMode(page, saved)
    await page.reload()
    await expectMode(page, saved)
    assert.equal(await storedPreference(page), saved)
  })
}

test('saved system follows the OS at startup, on changes, and after reload', async t => {
  const page = await fixture(t, { saved: 'system' })
  await expectMode(page, 'light')
  assert.equal(await chooser(page).innerText(), 'System')
  await page.emulateMedia({ colorScheme: 'dark' })
  await expectMode(page, 'dark')
  await page.reload()
  await expectMode(page, 'dark')
  await page.emulateMedia({ colorScheme: 'light' })
  await expectMode(page, 'light')
  assert.equal(await storedPreference(page), 'system')
})

for (const saved of ['', 'sepia', '{"mode":"light"}', 'null']) {
  test(`malformed stored preference ${JSON.stringify(saved)} renders with the dark fallback`, async t => {
    const page = await fixture(t, { saved })
    await expectMode(page, 'dark')
    assert.equal(await chooser(page).innerText(), 'Dark')
    assert.equal(await storedPreference(page), saved)
  })
}

for (const blocked of ['access', 'read-write']) {
  test(`blocked localStorage ${blocked} does not break rendering or controls`, async t => {
    const page = await fixture(t, { blocked })
    await expectMode(page, 'dark')
    await choose(page, 'Light')
    await expectMode(page, 'light')
    await choose(page, 'System')
    await page.emulateMedia({ colorScheme: 'dark' })
    await expectMode(page, 'dark')
    assert.equal(await chooser(page).innerText(), 'System')
  })
}

test('blocked writes preserve a readable saved preference and allow in-memory changes', async t => {
  const page = await fixture(t, { saved: 'light', colorScheme: 'dark', blocked: 'write' })
  await expectMode(page, 'light')
  await choose(page, 'Dark')
  await expectMode(page, 'dark')
  assert.equal(await storedPreference(page), 'light')
})

test('existing theme controls save all three choices without losing an unsaved draft', async t => {
  const page = await fixture(t)
  const draft = page.getByRole('textbox', { name: 'Unsaved draft', exact: true })
  await draft.fill('Keep this draft')
  for (const [label, saved, mode] of [['Light', 'light', 'light'], ['Dark', 'dark', 'dark'], ['System', 'system', 'light']]) {
    await choose(page, label)
    await expectMode(page, mode)
    assert.equal(await storedPreference(page), saved)
    assert.equal(await draft.inputValue(), 'Keep this draft')
  }
  await page.reload()
  await expectMode(page, 'light')
  assert.equal(await chooser(page).innerText(), 'System')
})

for (const role of ['PLATFORM_ADMIN', 'PLATFORM_OPERATOR']) {
  test(`${role} retains the staff light theme under a dark OS and saved customer dark preference`, async t => {
    const page = await fixture(t, { role, saved: 'dark', colorScheme: 'dark' })
    assert.equal(await page.getByTestId('palette-mode').innerText(), 'light')
    assert.equal(await page.locator('[data-customer-theme]').count(), 0)
    assert.equal(await chooser(page).count(), 0)
    assert.equal(await storedPreference(page), 'dark')
  })
}
