import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.KIT_CATALOGUE_OUTPUT || '/private/tmp/loomai-kit-catalogue'
await mkdir(output, { recursive: true })
const definitions = [
  { code: 'ai-customer-experience', name: 'AI Customer Experience', summary: 'Turn questions into answers, discovery and useful actions.',
    description: 'Your knowledge, live integrations and useful actions in one branded workspace.', features: ['Your knowledge', 'Live integrations', 'Branded AI workspace'], demoUrl: null },
  { code: 'dealership-ai-experience', name: 'Dealership AI Experience', summary: 'Help buyers find the right car and take the next step.',
    description: 'A configured workspace for stock discovery, comparisons and dealership knowledge. Connect your authorized stock source or start with the simulator.',
    features: ['Live stock', 'Vehicle comparisons', 'Dealership policies'], demoUrl: 'https://loomai.pro/demos/dealership-ai' },
  { code: 'ai-support-experience', name: 'AI Support Experience', summary: 'Help customers resolve problems and keep every request connected to the conversation.',
    description: 'Your approved knowledge, tracked requests and optional human support.', features: ['Approved support knowledge', 'Tracked requests', 'Optional human handover'], demoUrl: null },
]
const server = await createServer({ root, configFile: false, envFile: false, logLevel: 'error', esbuild: { jsx: 'automatic' },
  server: { host: '127.0.0.1', port: 0 }, resolve: { dedupe: ['react', 'react-dom'] },
  plugins: [{ name: 'kit-catalogue-review', enforce: 'pre', resolveId(id) {
    if (id.endsWith('/auth/PlatformAuthProvider')) return '\0kit-review-auth'
    if (id === '/kit-review.jsx') return '\0kit-review-entry'
  }, load(id) {
    if (id === '\0kit-review-auth') return 'export function usePlatformAuth(){return{session:null,isLoading:false}}'
    if (id === '\0kit-review-entry') return `import React from'react';import{createRoot}from'react-dom/client';import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{BrowserRouter,useLocation}from'react-router-dom';
      import{ExperienceKitsPublicPage}from'/src/pages/ExperienceKitsPublicPage.tsx';
      function Review(){const path=useLocation().pathname;return React.createElement(ExperienceKitsPublicPage,{code:path.split('/')[2]||undefined})}
      createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client:new QueryClient({defaultOptions:{queries:{retry:false}}})},React.createElement(BrowserRouter,null,React.createElement(Review))));`
  }, configureServer(vite) { vite.middlewares.use((req, res, next) => {
    if (!new URL(req.url, 'http://review').pathname.startsWith('/experiences')) return next()
    res.setHeader('Content-Type', 'text/html')
    res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;font-family:Arial,sans-serif"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/kit-review.jsx"></script></body></html>')
  }) } }],
})
await server.listen()
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1536, height: 1100 } })
const calls = [], errors = [], results = []
page.on('pageerror', error => errors.push(error.message))
page.setDefaultTimeout(20_000)
await page.route('**/api/**', route => {
  if (!new URL(route.request().url()).pathname.startsWith('/api/')) return route.continue()
  calls.push({ method: route.request().method(), url: new URL(route.request().url()).pathname })
  assert.equal(route.request().method(), 'GET', 'Marketing review must not mutate customer resources')
  assert.equal(new URL(route.request().url()).pathname, '/api/public/experience-kits')
  return route.fulfill({ json: definitions })
})
const base = server.resolvedUrls.local[0]
const bounds = async label => {
  await page.evaluate(async () => {
    await Promise.all([...document.images].map(async img => { img.loading = 'eager'; await img.decode().catch(() => {}) }))
    await document.fonts.ready
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  })
  const state = await page.evaluate(() => ({ viewport: innerWidth, width: document.documentElement.scrollWidth,
    broken: [...document.images].filter(img => !img.complete || !img.naturalWidth).map(img => img.src),
    overflow: [...document.querySelectorAll('main *')].filter(el => el.getBoundingClientRect().width && el.getBoundingClientRect().right > innerWidth + 1).map(el => el.className).slice(0, 8) }))
  assert.ok(state.width <= state.viewport + 1, `${label}: ${JSON.stringify(state)}`)
  assert.deepEqual(state.broken, [], label)
  results.push({ label, ...state })
}
try {
  for (const scheme of ['dark', 'light']) {
    await page.goto(base + 'experiences')
    const current = await page.locator('.ek-page').getAttribute('data-kit-theme')
    if (current !== scheme) await page.getByRole('button', { name: `Switch to ${scheme} mode` }).click()
    await page.locator('.ek-catalogue-card').first().waitFor()
    for (const width of [1536, 1280, 768, 390]) {
      await page.setViewportSize({ width, height: 1100 })
      await bounds(`${scheme}-${width}-catalogue`)
      assert.equal(await page.locator('.ek-catalogue-card').count(), 3)
      assert.equal(await page.getByLabel('Dealership offer', { exact: true }).count(), 1)
      assert.equal(await page.getByText('Coming soon', { exact: true }).count(), 0)
      if (width === 1536) {
        const geometry = await page.evaluate(() => {
          const rects = selector => [...document.querySelectorAll(selector)].map(element => {
            const rect = element.getBoundingClientRect(); return { y: rect.y, height: rect.height, width: rect.width, bottom: rect.bottom }
          })
          return { cards: rects('.ek-catalogue-card'), buttons: rects('.ek-card-explore'), scenes: rects('.ek-card-scene'),
            avatars: rects('.ek-card-products .ek-desk-mark > img'), channels: rects('.ek-card-products .ek-desk-channels'),
            titleSize: getComputedStyle(document.querySelector('.ek-card-identity h2')).fontSize,
            font: getComputedStyle(document.querySelector('.ek-catalogue-page')).fontFamily }
        })
        assert.ok(geometry.cards.every(card => card.y < 220 && card.height < 790), JSON.stringify(geometry))
        assert.ok(Math.max(...geometry.buttons.map(rect => rect.y)) - Math.min(...geometry.buttons.map(rect => rect.y)) < 2, JSON.stringify(geometry))
        assert.ok(Math.max(...geometry.scenes.map(rect => rect.y)) - Math.min(...geometry.scenes.map(rect => rect.y)) < 2, JSON.stringify(geometry))
        assert.equal(geometry.avatars.length, 3)
        assert.ok(geometry.channels.every((rect, index) => rect.bottom <= geometry.avatars[index].y + 1), JSON.stringify(geometry))
        assert.equal(geometry.titleSize, '28px')
        assert.match(geometry.font, /LoomAI Catalogue/)
        results.push({ label: `${scheme}-reference-proportions`, ...geometry })
      }
      if (width === 1536 || width === 390) await page.screenshot({ path: `${output}/catalogue-${scheme}-${width}.png`, fullPage: true })
    }
    await page.setViewportSize({ width: 1536, height: 1100 })
    for (const [kind, label] of [['adaptive', 'Preview workspace'], ['dealership', 'Preview workspace'], ['support', 'Preview support']]) {
      await page.locator(`.ek-kit-option[data-kind="${kind}"]`).getByRole('button', { name: label, exact: true }).click()
      const dialog = page.getByRole('dialog')
      await dialog.waitFor()
      for (const name of ['Website AI chat', 'Max AI workspace', 'Standalone AI space']) await dialog.getByRole('button', { name, exact: true }).click()
      if (kind === 'adaptive') {
        await dialog.getByRole('navigation', { name: 'Example workspace tools' }).getByRole('button', { name: 'Compare', exact: true }).click()
        await dialog.getByRole('heading', { name: 'Compare your options', exact: true }).waitFor()
        await dialog.getByRole('button', { name: 'Ask our team', exact: true }).click()
        await dialog.getByText('Alex joined this example conversation', { exact: true }).waitFor()
      }
      if (kind === 'support') {
        await dialog.getByRole('checkbox', { name: 'Human support sample' }).check()
        await dialog.getByText('Example support team', { exact: true }).waitFor()
      }
      await dialog.getByRole('button', { name: 'Close preview', exact: true }).click()
      await page.getByRole('dialog').waitFor({ state: 'detached' })
    }
    for (const definition of definitions) {
      await page.goto(`${base}experiences/${definition.code}`)
      await page.getByRole('heading', { name: definition.name, exact: true }).waitFor()
      assert.equal(await page.getByLabel('Dealership offer', { exact: true }).count(), definition.code === 'dealership-ai-experience' ? 1 : 0)
      await page.getByRole('heading', { name: 'You know your business. We handle the integration.', exact: true }).waitFor()
      for (const width of [1536, 390]) {
        await page.setViewportSize({ width, height: 1100 })
        await bounds(`${scheme}-${width}-${definition.code}`)
        if (width === 1536) await page.screenshot({ path: `${output}/${definition.code}-${scheme}.png`, fullPage: true })
      }
      assert.ok((await page.getByRole('link', { name: 'Get started', exact: true }).first().getAttribute('href')).includes(`/sign-up?kit=${definition.code}`))
      await page.getByRole('link', { name: 'Back to experiences', exact: true }).click()
      await page.getByRole('heading', { name: 'Choose your AI experience', exact: true }).waitFor()
    }
  }
  assert.deepEqual(errors, [])
  await writeFile(`${output}/verification.json`, JSON.stringify({ results, calls, errors, note: 'Actual React components, synthetic public catalogue, read-only. Not live provider qualification.' }, null, 2))
  console.log(`PASS: ${results.length} responsive/theme states; all preview formats, comparison, handover, three detail journeys. No mutations or browser errors. ${output}`)
} finally {
  await browser.close()
  await server.close()
}
