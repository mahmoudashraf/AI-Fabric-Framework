import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { createServer } from 'node:net'
import { once } from 'node:events'
import { spawn } from 'node:child_process'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

const require = createRequire(import.meta.url)
const source = await readFile(new URL('../src/uiProfile.tsx', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX } }).outputText
const exports = {}
new Function('require', 'exports', code)(require, exports)
const { isDeveloperUI, CommercialContent, PersonalContactContent } = exports

test('developer presentation requires both explicit staging and developer settings', () => {
  assert.equal(isDeveloperUI({ environment: 'staging', uiProfile: 'developer' }), true)
  for (const config of [undefined, {}, { environment: 'production', uiProfile: 'developer' },
    { environment: 'staging', uiProfile: 'standard' }, { uiProfile: 'developer' }, { environment: 'staging' }]) {
    assert.equal(isDeveloperUI(config), false)
  }
})

test('developer wrappers do not mount commercial or personal-contact children', () => {
  const original = globalThis.window
  try {
    globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { environment: 'staging', uiProfile: 'developer' } }
    const NotMounted = () => { throw new Error('Hidden components must not mount dialogs or trackers') }
    for (const wrapper of [CommercialContent, PersonalContactContent]) {
      assert.equal(renderToStaticMarkup(React.createElement(wrapper, null, React.createElement(NotMounted))), '')
    }
    globalThis.window.__PLATFORM_RUNTIME_CONFIG__.environment = 'production'
    for (const wrapper of [CommercialContent, PersonalContactContent]) {
      assert.match(renderToStaticMarkup(React.createElement(wrapper, null, React.createElement('span', null, 'Normal customer content'))), /Normal customer content/)
    }
  } finally {
    if (original === undefined) delete globalThis.window
    else globalThis.window = original
  }
})

test('commercial and contact surfaces use explicit wrappers, not text removal', async () => {
  for (const path of ['components/kits/KitCommercialPresentation.tsx', 'components/kits/KitChooser.tsx',
    'components/kits/KitDetailPage.tsx', 'pages/MarketplacePage.tsx', 'pages/ProductServicesPage.tsx']) {
    assert.ok((await readFile(new URL('../src/' + path, import.meta.url), 'utf8')).includes('<CommercialContent>'), path)
  }
  const contact = await readFile(new URL('../src/components/IntegrationCallButton.tsx', import.meta.url), 'utf8')
  assert.ok(contact.includes('return <PersonalContactContent>'))
  assert.ok(contact.includes('</Dialog></PersonalContactContent>'))
  const chooser = await readFile(new URL('../src/components/kits/KitChooser.tsx', import.meta.url), 'utf8')
  const styles = await readFile(new URL('../src/components/kits/KitCatalogueCards.css', import.meta.url), 'utf8')
  assert.ok(chooser.includes("isDeveloperUI() ? ' ek-kit-option-without-offer'"))
  assert.match(styles, /\.ek-kit-option\.ek-kit-option-without-offer \{ grid-template-rows: 1fr; gap: 0; \}/)
  assert.ok(!source.includes('localStorage'))
  assert.ok(!source.includes('URLSearchParams'))
})

for (const [environment, requestedProfile, expected] of [
  ['staging', 'developer', 'developer'], ['staging', 'standard', 'standard'],
  ['production', 'developer', 'standard'], ['', 'developer', 'standard'],
]) {
  test(`UI server profile ${environment || 'unset'}/${requestedProfile} resolves to ${expected}`, { timeout: 10000 }, async () => {
    const reservation = createServer().listen(0, '127.0.0.1')
    await once(reservation, 'listening')
    const port = reservation.address().port
    await new Promise(resolve => reservation.close(resolve))
    const child = spawn(process.execPath, [new URL('../deploy/railway/server.mjs', import.meta.url).pathname], {
      env: { ...process.env, PORT: String(port), PLATFORM_UI_API_BASE_URL: 'https://staging-api.example.test', PLATFORM_UI_ENVIRONMENT: environment, PLATFORM_UI_PROFILE: requestedProfile },
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    try {
      await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('UI server readiness timed out')), 5000)
        child.once('error', error => { clearTimeout(timer); reject(error) })
        child.once('exit', code => { clearTimeout(timer); reject(new Error('UI server exited: ' + code)) })
        child.stdout.on('data', () => { clearTimeout(timer); resolve() })
      })
      const response = await fetch(`http://127.0.0.1:${port}/runtime-config.js`)
      assert.equal(response.status, 200)
      assert.equal(response.headers.get('cache-control'), 'no-store')
      const sandbox = { window: {} }
      runInNewContext(await response.text(), sandbox)
      assert.equal(sandbox.window.__PLATFORM_RUNTIME_CONFIG__.uiProfile, expected)
      assert.equal(sandbox.window.__PLATFORM_RUNTIME_CONFIG__.environment, environment === 'staging' ? 'staging' : 'production')
      assert.equal(sandbox.window.__PLATFORM_RUNTIME_CONFIG__.apiBaseUrl, 'https://staging-api.example.test')
    } finally {
      if (child.exitCode === null) { child.kill('SIGTERM'); await once(child, 'exit') }
    }
  })
}
