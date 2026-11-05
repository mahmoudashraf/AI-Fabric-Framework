import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import ts from 'typescript'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const require = createRequire(import.meta.url)
async function loadLocal(relative, dependencies = {}) {
  const source = await readFile(new URL(relative, import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } })
  const exports = {}
  new Function('require', 'exports', compiled.outputText)(name => {
    if (name in dependencies) return dependencies[name]
    if (name === 'react' || name === 'react/jsx-runtime' || name.startsWith('@mui/')) return require(name)
    throw new Error(`Unexpected booking dependency: ${name}`)
  }, exports)
  return exports
}
const config = await loadLocal('../src/integrationTeam.ts')
const booking = await loadLocal('../src/components/IntegrationCallButton.tsx', { '../integrationTeam': config })
const source = await readFile(new URL('../src/components/kits/KitIntegrationJourney.tsx', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: {
  target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
} })
const imports = []
const journey = {}
new Function('require', 'exports', compiled.outputText)(name => {
  imports.push(name)
  if (name === './KitIntegrationJourney.css') return {}
  if (name === '../IntegrationCallButton') return booking
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
    assert.ok(html.includes(`href="${config.integrationTeam.bookingUrl}"`))
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
  assert.ok(imports.every(name => name === './KitIntegrationJourney.css' || name === '../IntegrationCallButton' || name === 'react-router-dom' || name === 'react/jsx-runtime' || name.startsWith('@mui/')))
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
  const errors = [], calls = [], observations = []
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
    const projectLinks = await page.getByRole('link', { name: 'Start a project', exact: true }).all()
    assert.equal(projectLinks.length, 2)
    for (const link of projectLinks) {
      assert.equal(await link.getAttribute('href'), config.integrationTeam.bookingUrl)
      assert.equal(await link.getAttribute('target'), '_blank')
      assert.equal(await link.getAttribute('rel'), 'noopener noreferrer')
      assert.equal(await link.getAttribute('aria-haspopup'), 'dialog')
    }
    assert.equal(await page.getByRole('link', { name: 'LoomAI website', exact: true }).getAttribute('href'), 'https://loomai.pro/')
    assert.equal(await page.locator('.ek-detail-outcome').count(), 3)
    assert.equal(await page.locator('.ek-detail-products > a').count(), 3)
    await page.getByText('What we connect, and what you control', { exact: true }).click()
    await page.getByRole('heading', { name: 'Your business. Connected by our team.', exact: true }).waitFor()
    await page.getByText('What we connect, and what you control', { exact: true }).click()
    for (const format of ['Website AI chat', 'Max AI workspace', 'Standalone AI space']) {
      await page.getByRole('button', { name: `Preview ${format} for ${definition.name}`, exact: true }).click()
      await page.getByRole('dialog').waitFor()
      assert.equal(await page.getByRole('dialog').getByRole('button', { name: format, exact: true }).getAttribute('aria-pressed'), 'true')
      await page.getByRole('button', { name: 'Close preview', exact: true }).click()
      await page.getByRole('dialog').waitFor({ state: 'hidden' })
    }
    const installLinks = await page.getByRole('link', { name: 'Install kit', exact: true }).all()
    assert.equal(installLinks.length, 2)
    for (const link of installLinks) {
      assert.equal(await link.getAttribute('href'), `/kits/new/${definition.code}`)
    }
    const detailText = await page.locator('.ek-public-detail').innerText()
    if (definition.code === 'dealership-ai-experience') {
      for (const copy of ['£500 / month', 'Only pay when value starts', 'First 3 months free', 'Free integration']) assert.ok(detailText.includes(copy), copy)
      assert.match(detailText, /fictional inventory and a simulated AutoTrader integration/)
      const demo = page.getByRole('link', { name: 'Try the live demo', exact: true }).first()
      assert.equal(await demo.getAttribute('href'), definition.demoUrl)
      assert.equal(await demo.getAttribute('target'), '_blank')
      assert.equal(await demo.getAttribute('rel'), 'noopener noreferrer')
    } else {
      assert.doesNotMatch(detailText, /£500|Only pay when value starts|First 3 months free|Free integration/)
      assert.equal(await page.getByRole('link', { name: 'Try the live demo', exact: true }).count(), 0)
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
          await document.fonts.ready
          await Promise.all([...document.images].map(async image => { image.loading = 'eager'; await image.decode().catch(() => {}) }))
          window.scrollTo(0, 0)
          await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
          return { viewport: innerWidth, scroll: document.documentElement.scrollWidth,
            broken: [...document.images].filter(image => !image.complete || !image.naturalWidth).map(image => image.getAttribute('src')),
            heroBottom: document.querySelector('.ek-detail-hero').getBoundingClientRect().bottom,
            formats: [...document.querySelectorAll('.ek-detail-formats .ek-format-grid > button')].map(element => {
              const visual = element.querySelector('.ek-delivery-visual').getBoundingClientRect()
              const input = element.querySelector('.ek-screen-composer').getBoundingClientRect()
              return { width: element.getBoundingClientRect().width, y: visual.y, bottom: visual.bottom, inputBottom: input.bottom }
            }),
            overflowing: [...document.querySelectorAll('body *')].filter(element => element.getBoundingClientRect().right > innerWidth + 1)
              .map(element => ({ tag: element.tagName, className: element.className, width: element.getBoundingClientRect().width })).slice(0, 12) }
        })
        assert.ok(bounds.scroll <= bounds.viewport, `${definition.code} ${mode} ${width}: ${JSON.stringify(bounds)}`)
        assert.deepEqual(bounds.broken, [])
        if (width === 1440) {
          assert.ok(bounds.heroBottom < 700, JSON.stringify(bounds))
          assert.ok(Math.max(...bounds.formats.map(format => format.width)) - Math.min(...bounds.formats.map(format => format.width)) < 2, JSON.stringify(bounds))
          assert.ok(Math.max(...bounds.formats.map(format => format.y)) - Math.min(...bounds.formats.map(format => format.y)) < 2, JSON.stringify(bounds))
        }
        assert.ok(bounds.formats.every(format => format.inputBottom <= format.bottom + 1), JSON.stringify(bounds))
        observations.push({ code: definition.code, mode, width, ...bounds })
        if (width === 1440 || width === 390) await page.screenshot({ path: `${output}/${definition.code}-${mode}-${width}.png`, fullPage: true })
      }
    }
    await page.goto(`${base}experiences/${definition.code}?authenticated=true`)
    await page.getByRole('heading', { name: definition.name, exact: true }).waitFor()
    for (const link of await page.getByRole('link', { name: 'Install kit', exact: true }).all()) {
      assert.equal(await link.getAttribute('href'), `/kits/new/${definition.code}`)
    }
  }
  assert.deepEqual(errors, [])
  assert.ok(calls.length >= 6)
  assert.ok(calls.every(call => call.method === 'GET' && call.path === '/api/public/experience-kits'))
  await writeFile(`${output}/verification.json`, JSON.stringify({ observations, errors, calls,
    note: 'Actual detail components, synthetic public catalogue and auth flags. No live AI, customer write or deployment qualification.' }, null, 2))
  console.log(`Kit detail screenshots: ${output}`)
})
