import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdir, readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import ts from 'typescript'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const require = createRequire(import.meta.url)
const source = await readFile(new URL('../src/components/kits/KitIntegrationJourney.tsx', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: {
  target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
} })
const imports = []
const journey = {}
new Function('require', 'exports', compiled.outputText)(name => {
  imports.push(name)
  if (name === './KitIntegrationJourney.css') return {}
  if (name === 'react-router-dom') return { Link: ({ to, children, ...props }) => React.createElement('a', { ...props, href: to }, children) }
  if (name === 'react/jsx-runtime' || name.startsWith('@mui/')) return require(name)
  throw new Error(`Unexpected integration journey dependency: ${name}`)
}, journey)
const renderJourney = kind => renderToStaticMarkup(React.createElement(journey.KitIntegrationJourney, { kind, getStartedPath: '/sign-up?kit=example' }))

test('every kit has the ordered LoomAI-owned integration journey and real destination links', () => {
  for (const kind of ['dealership', 'adaptive', 'support']) {
    const html = renderJourney(kind)
    assert.match(html, /<ol class="ek-integration-steps">/)
    const steps = [...html.matchAll(/<h3>(.*?)<\/h3>/g)].map(match => match[1])
    assert.deepEqual(steps.slice(3, 7), ['Share requirements and access', 'LoomAI connects your systems', 'Test with your own team', 'Go live'])
    assert.match(html, /The LoomAI technical team connects your approved systems and knowledge/)
    assert.match(html, /href="\/support"/)
    assert.match(html, /href="\/sign-up\?kit=example"/)
    assert.doesNotMatch(html, /<form|<input|<button|£500|First 3 months free|Free integration|Only pay when value starts/)
  }
})

test('integration scope stays explicit and does not imply third-party partnerships', () => {
  for (const kind of ['dealership', 'adaptive', 'support']) {
    const html = renderJourney(kind)
    assert.match(html, /approved access and the agreed scope/)
    assert.match(html, /do not imply an official partnership or endorsement/)
    assert.match(html, /external integrations|External systems stay separate/)
  }
  assert.match(renderJourney('dealership'), /stock feeds and customer systems/)
  assert.match(renderJourney('dealership'), /any external provider costs/)
})

test('support and general kits retain domain-specific content without borrowing dealership pricing', () => {
  const support = renderJourney('support')
  assert.match(support, /Choose request tracking and human support separately/)
  assert.match(support, /approved help articles, policies and troubleshooting guidance/)
  assert.doesNotMatch(support, /vehicle|dealership|stock feeds/i)
  const adaptive = renderJourney('adaptive')
  assert.match(adaptive, /product information, service details and policies/)
  assert.doesNotMatch(adaptive, /vehicle|dealership|stock feeds/i)
})

test('journey is presentation-only and uses existing theme tokens without global styles', async () => {
  assert.ok(imports.every(name => name === './KitIntegrationJourney.css' || name === 'react-router-dom' || name === 'react/jsx-runtime' || name.startsWith('@mui/')))
  assert.doesNotMatch(source, /\b(?:fetch\(|localStorage|sessionStorage|onSubmit|checkout|stripe)/i)
  const css = await readFile(new URL('../src/components/kits/KitIntegrationJourney.css', import.meta.url), 'utf8')
  assert.match(css, /var\(--ek-text\)|var\(--ek-muted\)/)
  assert.doesNotMatch(css, /[\d.]+(?:vw|vmin|vmax)|letter-spacing:\s*(?!0[;\s])[-\d.]/)
  for (const match of css.matchAll(/border-radius:\s*(\d+)px/g)) assert.ok(Number(match[1]) <= 8)
})

const definitions = [
  { code: 'dealership-ai-experience', name: 'Dealership AI Experience', experiencePackCode: 'dealership', demoUrl: 'https://demo.example.test/dealership',
    summary: 'Help buyers choose with confidence.', description: 'Dealership detail description from the catalogue.', features: ['Vehicle discovery', 'Test-drive enquiries'] },
  { code: 'ai-customer-experience', name: 'AI Customer Experience', experiencePackCode: 'generic-workspace', demoUrl: null,
    summary: 'Help customers take the next step.', description: 'General detail description from the catalogue.', features: ['Approved business knowledge', 'Customer next steps'] },
  { code: 'ai-support-experience', name: 'AI Support Experience', experiencePackCode: 'support', demoUrl: null,
    summary: 'Help customers solve problems.', description: 'Support detail description from the catalogue.', features: ['Approved support knowledge', 'Optional human handover'] },
].map(definition => ({ ...definition, version: '1.0.0', defaultWorkspaceName: definition.name }))

test('real detail pages retain offers, preview, demo, links and responsive light/dark layouts', { timeout: 180_000 }, async t => {
  const output = process.env.KIT_INTEGRATION_OUTPUT || '/private/tmp/loomai-kit-integration-journey'
  await mkdir(output, { recursive: true })
  const root = fileURLToPath(new URL('..', import.meta.url))
  const authId = '\0kit-integration-auth', entryId = '\0kit-integration-entry'
  const server = await createServer({ root, configFile: false, envFile: false, logLevel: 'error', esbuild: { jsx: 'automatic' },
    cacheDir: `${output}/vite-cache-${process.pid}`, resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 0 },
    plugins: [{ name: 'kit-integration-detail-fixture', enforce: 'pre', resolveId(id) {
      if (id.endsWith('/auth/PlatformAuthProvider')) return authId
      if (id === '/kit-integration-fixture.jsx') return entryId
    }, load(id) {
      if (id === authId) return 'export function usePlatformAuth(){return {session:null}}'
      if (id === entryId) return `import React from 'react';import{createRoot}from'react-dom/client';
        import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{BrowserRouter}from'react-router-dom';
        import{ExperienceKitsPublicPage}from'/src/pages/ExperienceKitsPublicPage.tsx';
        createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client:new QueryClient({defaultOptions:{queries:{retry:false}}})},
          React.createElement(BrowserRouter,null,React.createElement(ExperienceKitsPublicPage,{code:location.pathname.split('/')[2],authenticated:new URLSearchParams(location.search).get('authenticated')==='true'}))));`
    }, configureServer(vite) { vite.middlewares.use((req, res, next) => {
      if (!new URL(req.url, 'http://fixture').pathname.startsWith('/experiences')) return next()
      res.setHeader('Content-Type', 'text/html')
      res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/kit-integration-fixture.jsx"></script></body></html>')
    }) } }],
  })
  t.after(() => server.close())
  await server.listen()
  const browser = await chromium.launch({ headless: true })
  t.after(() => browser.close())
  const page = await browser.newPage()
  page.setDefaultTimeout(15_000)
  const errors = [], calls = []
  page.on('pageerror', error => { errors.push(error.message); console.error(error.message) })
  page.on('response', response => { if (response.status() >= 400) console.error(`${response.status()} ${response.url()}`) })
  await page.route('**/api/**', route => {
    const request = route.request(), path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    calls.push({ path, method: request.method() })
    return route.fulfill({ status: path === '/api/public/experience-kits' ? 200 : 503, json: path === '/api/public/experience-kits' ? definitions : { message: 'No fixture endpoint' } })
  })
  const base = server.resolvedUrls.local[0]
  for (const definition of definitions) {
    await page.goto(`${base}experiences/${definition.code}`)
    try { await page.getByRole('heading', { name: definition.name, exact: true }).waitFor() } catch (error) {
      throw new Error(`${error.message}\nPage errors: ${JSON.stringify(errors)}\nAPI calls: ${JSON.stringify(calls)}\nPage: ${await page.locator('body').innerText()}`)
    }
    await page.getByText(definition.description, { exact: true }).waitFor()
    for (const feature of definition.features) await page.getByText(feature, { exact: true }).waitFor()
    assert.equal(await page.locator('.ek-integration-steps > li').count(), 4)
    assert.equal(await page.getByRole('link', { name: 'Discuss integration', exact: true }).getAttribute('href'), '/support')
    for (const link of await page.getByRole('link', { name: 'Get started', exact: true }).all()) {
      assert.equal(await link.getAttribute('href'), `/sign-up?kit=${definition.code}`)
    }
    const detailText = await page.locator('.ek-public-detail').innerText()
    if (definition.code === 'dealership-ai-experience') {
      for (const copy of ['£500 / month', 'Only pay when value starts', 'First 3 months free', 'Free integration']) assert.ok(detailText.includes(copy), copy)
      assert.match(detailText, /fictional inventory and a simulated AutoTrader integration/)
      const demo = page.getByRole('link', { name: 'Try demo', exact: true })
      assert.equal(await demo.getAttribute('href'), definition.demoUrl)
      assert.equal(await demo.getAttribute('target'), '_blank')
      assert.equal(await demo.getAttribute('rel'), 'noopener noreferrer')
    } else {
      assert.doesNotMatch(detailText, /£500|Only pay when value starts|First 3 months free|Free integration/)
      assert.equal(await page.getByRole('link', { name: 'Try demo', exact: true }).count(), 0)
    }
    await page.getByRole('button', { name: 'Preview experience', exact: true }).click()
    await page.getByRole('dialog').waitFor()
    await page.getByRole('button', { name: 'Close preview', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    for (const mode of ['dark', 'light']) {
      if (await page.locator('.ek-public-page').getAttribute('data-kit-theme') !== mode) {
        await page.getByRole('button', { name: `Switch to ${mode} mode`, exact: true }).click()
      }
      for (const width of [1440, 768, 390, 320]) {
        await page.setViewportSize({ width, height: 1000 })
        const bounds = await page.evaluate(async () => {
          await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
          return { viewport: innerWidth, scroll: document.documentElement.scrollWidth,
            overflowing: [...document.querySelectorAll('body *')].filter(element => element.getBoundingClientRect().right > innerWidth + 1)
              .map(element => ({ tag: element.tagName, className: element.className, width: element.getBoundingClientRect().width })).slice(0, 12) }
        })
        assert.ok(bounds.scroll <= bounds.viewport, `${definition.code} ${mode} ${width}: ${JSON.stringify(bounds)}`)
        if (width === 1440 || width === 390) await page.screenshot({ path: `${output}/${definition.code}-${mode}-${width}.png`, fullPage: true })
      }
    }
    await page.goto(`${base}experiences/${definition.code}?authenticated=true`)
    await page.getByRole('heading', { name: definition.name, exact: true }).waitFor()
    for (const link of await page.getByRole('link', { name: 'Get started', exact: true }).all()) {
      assert.equal(await link.getAttribute('href'), `/kits/new/${definition.code}`)
    }
  }
  assert.deepEqual(errors, [])
  assert.ok(calls.length >= 6)
  assert.ok(calls.every(call => call.method === 'GET' && call.path === '/api/public/experience-kits'))
  console.log(`Kit detail screenshots: ${output}`)
})
