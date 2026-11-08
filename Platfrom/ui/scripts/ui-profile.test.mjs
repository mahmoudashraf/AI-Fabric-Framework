import test from 'node:test'
import assert from 'node:assert/strict'
import { access, readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { createServer } from 'node:net'
import { request as httpRequest } from 'node:http'
import { once } from 'node:events'
import { spawn } from 'node:child_process'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

const require = createRequire(import.meta.url)
async function load(relative, dependencies = {}) {
  const source = await readFile(new URL(relative, import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } })
  const exports = {}
  new Function('require', 'exports', compiled.outputText)(name => {
    if (name in dependencies) return dependencies[name]
    if (name === 'react' || name === 'react/jsx-runtime' || name.startsWith('@mui/')) return require(name)
    throw new Error(`Unexpected presentation dependency: ${name}`)
  }, exports)
  return exports
}

const commercial = await load('../src/components/kits/KitCommercialPresentation.tsx')
const integration = await load('../src/integrationTeam.ts')
const contact = await load('../src/components/IntegrationCallButton.tsx', { '../integrationTeam': integration })

test('retired presentation wrappers and profile-only catalogue styles are removed', async () => {
  await assert.rejects(access(new URL('../src/uiProfile.tsx', import.meta.url)), { code: 'ENOENT' })
  for (const path of ['components/kits/KitChooser.tsx', 'components/kits/KitCommercialPresentation.tsx',
    'components/kits/KitDetailPage.tsx', 'components/kits/KitIntegrationJourney.tsx',
    'pages/MarketplacePage.tsx', 'pages/ProductServicesPage.tsx',
    'components/IntegrationCallButton.tsx', 'pages/ExperienceKitsPublicPage.tsx']) {
    const source = await readFile(new URL('../src/' + path, import.meta.url), 'utf8')
    assert.doesNotMatch(source, /CommercialContent|PersonalContactContent|isDeveloperUI|uiProfile/, path)
  }
  const styles = await readFile(new URL('../src/components/kits/KitCatalogueCards.css', import.meta.url), 'utf8')
  assert.doesNotMatch(styles, /ek-kit-option-without-offer/)
  const types = await readFile(new URL('../src/vite-env.d.ts', import.meta.url), 'utf8')
  assert.doesNotMatch(types, /uiProfile/)
})

test('standard offers and contact content stay visible even with obsolete browser profile data', () => {
  const original = globalThis.window
  const render = Component => renderToStaticMarkup(React.createElement(Component))
  try {
    delete globalThis.window
    const expectedOffers = ['adaptive', 'dealership', 'support'].map(kind =>
      render(() => React.createElement(commercial.KitLaunchOffer, { kind })))
    const expectedContact = render(contact.IntegrationCallButton)
    assert.match(expectedOffers[0], /Tailored pricing|Integration included/)
    assert.match(expectedOffers[1], /500 \/ month/)
    assert.match(expectedOffers[1], /First 3 months free/)
    assert.match(expectedOffers[2], /Knowledge onboarding included/)
    assert.match(expectedContact, /Talk to Mo/)
    assert.ok(expectedContact.includes(`href="${integration.integrationTeam.bookingUrl}"`))
    for (const environment of ['staging', 'production']) {
      globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { environment, uiProfile: 'developer' } }
      for (const [index, kind] of ['adaptive', 'dealership', 'support'].entries()) {
        assert.equal(render(() => React.createElement(commercial.KitLaunchOffer, { kind })), expectedOffers[index])
      }
      assert.equal(render(contact.IntegrationCallButton), expectedContact)
    }
  } finally {
    if (original === undefined) delete globalThis.window
    else globalThis.window = original
  }
})

for (const [environment, oldProfile, apiVariable, apiBaseUrl] of [
  ['staging', 'developer', 'PLATFORM_UI_API_BASE_URL', 'https://staging-api.example.test'],
  ['staging', 'standard', 'PLATFORM_UI_API_BASE_URL', 'https://staging-api.example.test'],
  ['production', 'developer', 'PLATFORM_UI_API_BASE_URL', 'https://production-api.example.test'],
  ['', 'developer', 'VITE_PLATFORM_API_BASE_URL', 'https://fallback-api.example.test'],
  ['staging', '', 'PLATFORM_UI_API_BASE_URL', ''],
]) {
  test(`UI runtime retains standard content config for ${environment || 'unset'}/${oldProfile || 'unset'} without a presentation profile`, { timeout: 10000 }, async () => {
    const reservation = createServer().listen(0, '127.0.0.1')
    await once(reservation, 'listening')
    const port = reservation.address().port
    await new Promise(resolve => reservation.close(resolve))
    const child = spawn(process.execPath, [new URL('../deploy/railway/server.mjs', import.meta.url).pathname], {
      env: { ...process.env, PORT: String(port), PLATFORM_UI_API_BASE_URL: '', VITE_PLATFORM_API_BASE_URL: '',
        [apiVariable]: apiBaseUrl, PLATFORM_UI_ENVIRONMENT: environment, PLATFORM_UI_PROFILE: oldProfile },
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    try {
      await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('UI server readiness timed out')), 5000)
        child.once('error', error => { clearTimeout(timer); reject(error) })
        child.once('exit', code => { clearTimeout(timer); reject(new Error('UI server exited: ' + code)) })
        child.stdout.on('data', () => { clearTimeout(timer); resolve() })
      })
      const response = await fetch(`http://127.0.0.1:${port}/runtime-config.js?uiProfile=developer`)
      assert.equal(response.status, 200)
      assert.equal(response.headers.get('cache-control'), 'no-store')
      assert.match(response.headers.get('content-type'), /application\/javascript/)
      const sandbox = { window: {} }
      runInNewContext(await response.text(), sandbox)
      const config = sandbox.window.__PLATFORM_RUNTIME_CONFIG__
      assert.deepEqual(Object.keys(config).sort(), ['apiBaseUrl', 'environment'])
      assert.equal(config.environment, environment === 'staging' ? 'staging' : 'production')
      assert.equal(config.apiBaseUrl, apiBaseUrl)
      assert.equal(Object.isFrozen(config), true)
      const canonical = await new Promise((resolve, reject) => {
        const request = httpRequest({ host: '127.0.0.1', port, path: '/sign-in?kit=ai-support-experience&entryPoint=DEVELOPER', headers: { Host: 'developer.loomai.pro' } }, response => {
          response.resume(); resolve({ status: response.statusCode, headers: response.headers })
        })
        request.once('error', reject); request.end()
      })
      assert.equal(canonical.status, 302)
      assert.equal(canonical.headers.location, 'https://console.loomai.pro/sign-in?kit=ai-support-experience&entryPoint=DEVELOPER')
      assert.equal(canonical.headers['cache-control'], 'no-store')
    } finally {
      if (child.exitCode === null) { child.kill('SIGTERM'); await once(child, 'exit') }
    }
  })
}
