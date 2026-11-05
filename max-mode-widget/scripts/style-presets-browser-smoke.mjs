import assert from 'node:assert/strict'
import http from 'node:http'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { build } from 'esbuild'
import { chromium } from 'playwright'

const repo = resolve(new URL('../..', import.meta.url).pathname)
const evidence = resolve(process.env.ENGAGE_STYLE_EVIDENCE_DIR || `${repo}/verification-support/engage-styles-01061/local`)
await mkdir(evidence, { recursive: true })
const metadata = await build({ entryPoints: [`${repo}/max-mode-widget/src/stylePresets.ts`], bundle: true, write: false, format: 'esm' })
const { WORKSPACE_STYLE_PRESETS } = await import(`data:text/javascript;base64,${Buffer.from(metadata.outputFiles[0].text).toString('base64')}`)
const widget = await readFile(`${repo}/max-mode-widget/dist/max-mode-widget.iife.js`)
const pack = await readFile(`${repo}/experience-packs/dealership-experience/dist/dealership-experience.iife.js`)
const requests = [], errors = [], checks = []
const timestamp = '2026-11-05T12:00:00Z'
let origin
const narrative = 'Current stock includes two fictional options. Compare their features using the listed facts. '.repeat(15)
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://fixture.test')
  if (url.pathname === '/widget.js' || url.pathname === '/pack.js') {
    res.setHeader('Content-Type', 'text/javascript'); return res.end(url.pathname === '/widget.js' ? widget : pack)
  }
  if (url.pathname === '/vehicle.webp') {
    res.setHeader('Content-Type', 'image/webp'); return res.end(await readFile(`${repo}/Platfrom/loomai-site/public/assets/demos/dealership/vehicle-01.webp`))
  }
  if (!url.pathname.startsWith('/api/')) {
    res.setHeader('Content-Type', 'text/html')
    return res.end(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
      <body style="margin:0;background:#f2f4f3"><main><h1>Example website</h1><p>Customer website content.</p></main>
      <script>window.LoomAIWorkspace={registerExperiencePack:pack=>window.pack=pack}</script><script src="/widget.js"></script><script src="/pack.js"></script></body></html>`)
  }
  requests.push({ path: url.pathname, method: req.method })
  res.setHeader('Content-Type', 'application/json')
  if (req.method === 'POST' && url.pathname.endsWith('/suggestions')) return res.end('{"suggestions":[]}')
  if (req.method !== 'GET') { res.statusCode = 500; return res.end('{"message":"Unexpected write"}') }
  if (url.pathname.endsWith('/events')) { res.writeHead(200, { 'Content-Type': 'text/event-stream' }); return res.write(': connected\n\n') }
  if (url.pathname.endsWith('/control')) return res.end(JSON.stringify({ conversationId: 'style-proof', controlState: 'AI_ACTIVE', controlVersion: 1 }))
  const vehicles = [1, 2].map((number, index) => ({ stockId: `EXAMPLE-${number}`, make: 'Example', model: index ? 'Family Hybrid' : 'City EV',
    registrationYear: 2025, priceGbp: index ? 27800 : 24500, mileage: 4200, fuelType: index ? 'Hybrid' : 'Electric', bodyType: 'Hatchback',
    transmission: 'Automatic', availability: 'Available', imageUrl: 'https://fixture-assets.test/vehicle.webp', summary: 'Fictional verification inventory.', sourceLabel: 'Synthetic stock' }))
  res.end(JSON.stringify({ id: 'style-proof', status: 'ACTIVE', createdAt: timestamp, controlState: 'AI_ACTIVE', controlVersion: 1,
    participantMessages: [
      { messageId: 'style-user', role: 'USER', sequence: 1, content: 'Help me compare the current options.', createdAt: timestamp },
      { messageId: 'style-ai', role: 'AI', sequence: 2, content: narrative, createdAt: timestamp },
    ], completedTurns: [{ userMessageId: 'style-user', aiMessageId: 'style-ai', completedAt: timestamp,
      response: { type: 'INFORMATION_PROVIDED', success: true, conversationId: 'style-proof', answer: narrative,
        sources: [{ id: 'style-source', title: 'Current stock evidence', type: 'document', content: 'Fictional stock evidence.', score: .9, metadata: { source: 'read-action', fields: 6 } }],
        actions: [{ action: 'dealership_search_inventory', actionResult: { success: true, data: { _count: 2, results: vehicles,
          source: { sourceId: 'synthetic', synchronizedAt: timestamp } } } }],
      } }],
  }))
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
origin = `http://127.0.0.1:${server.address().port}`
const browser = await chromium.launch({ headless: true })
const rgb = hex => `rgb(${hex.slice(1).match(/../g).map(x => parseInt(x, 16)).join(', ')})`
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.setDefaultTimeout(15000)
  page.on('pageerror', error => errors.push(error.message))
  await page.route('https://fixture-assets.test/vehicle.webp', async route => route.fulfill({ contentType: 'image/webp',
    body: await readFile(`${repo}/Platfrom/loomai-site/public/assets/demos/dealership/vehicle-01.webp`) }))
  await page.goto(origin)
  await page.evaluate(async () => {
    const base = '/api/chat/me/conversations/{conversationId}'
    await window.pack.mount({ installationId: 'style-fixture', assignmentRevision: 'same-revision', deliveryMode: 'embedded',
      configuration: { dealer: { id: 'example', assistantLabel: 'Example AI' }, page: { kind: 'inventory', rootSelector: 'main', contextLabel: 'Inventory' },
        capabilities: { comparison: true, testDrive: true, callback: true }, presentation: { imageHostAllowlist: ['fixture-assets.test'] }, theme: {} },
      widgetConfig: { apiConfig: { chatBaseUrl: location.origin + '/api', probeShellConfigOnOpen: false,
          runtimeAuth: { probeAuthContextOnOpen: false }, conversationHandover: { enabled: true },
          runtimeRoutes: { conversationControlUrlTemplate: base + '/control', conversationEventsUrlTemplate: base + '/events' } },
        hostSession: { namespace: 'style-test', accountKey: 'same-customer' }, features: { attachments: true } },
      maxMode: window.MaxMode, refreshAssignment: async () => {},
    })
    await window.MaxMode.whenReady()
    await window.MaxMode.openConversation('style-proof')
    window.MaxMode.open()
  })
  await page.locator('loomai-dealership-inventory').waitFor()
  await page.waitForFunction(() => [...document.querySelector('#max-mode-widget-shadow-host').shadowRoot.querySelector('loomai-dealership-inventory').shadowRoot.querySelectorAll('img')].some(img => img.complete && img.naturalWidth > 0))
  const root = page.locator('.max-mode-widget-root')
  const input = page.locator('textarea:visible')
  await input.fill('Keep this draft while changing style')
  await page.evaluate(() => {
    window.MaxMode.attachItem({ type: 'document', data: { id: 'retained-document', title: 'Retained context', content: 'Customer-selected context.' } })
    window.originalInput = document.querySelector('#max-mode-widget-shadow-host').shadowRoot.querySelector('textarea')
  })
  const hide = page.getByRole('button', { name: 'Hide', exact: true })
  if (await hide.isVisible()) await hide.click()
  const baseline = await root.evaluate(element => {
    const bubble = getComputedStyle(element.querySelector('[data-mxw-message="user"]'))
    return { background: bubble.backgroundImage, radius: bubble.borderRadius, color: bubble.color, variables: element.style.cssText }
  })
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 })
    for (const style of WORKSPACE_STYLE_PRESETS) {
      for (const darkMode of [false, true]) {
        await page.evaluate(theme => window.MaxMode.setTheme(theme), { stylePreset: style.id, darkMode, primaryColor: undefined, fontFamily: undefined, borderRadius: undefined })
        await page.waitForFunction(id => document.querySelector('#max-mode-widget-shadow-host').shadowRoot.querySelector('.max-mode-widget-root').dataset.mxwStyle === id, style.id)
        assert.equal(await input.inputValue(), 'Keep this draft while changing style')
        assert.equal(await page.evaluate(() => document.querySelector('#max-mode-widget-shadow-host').shadowRoot.querySelector('textarea') === window.originalInput), true)
        assert.equal(await page.evaluate(() => window.MaxMode.getState().conversationId), 'style-proof')
        assert.equal(await page.getByText('Retained context', { exact: true }).count() > 0, true)
        const actual = await root.evaluate(async element => {
          await element.ownerDocument.fonts.ready
          const canvas = getComputedStyle(element.querySelector('[data-mxw-canvas]'))
          const renderer = element.querySelector('loomai-dealership-inventory')
          const card = getComputedStyle(renderer.shadowRoot.querySelector('.vehicle-card'))
          const full = element.querySelector('.mxw-full-answer')
          return { canvas: canvas.backgroundColor, renderer: renderer.dataset.mxwStyle, card: card.backgroundColor,
            fullAnswer: full?.textContent, expanded: full?.open, fonts: [...element.ownerDocument.fonts].map(f => ({ family: f.family, status: f.status })) }
        })
        if (style.id !== 'current') {
          const palette = darkMode ? style.dark : style.light
          assert.equal(actual.canvas, rgb(palette.canvas), `${style.id}/${width} canvas`)
          assert.equal(actual.card, rgb(palette.surface), `${style.id}/${width} custom renderer`)
          assert.equal(actual.renderer, style.id)
          assert.ok(actual.fonts.some(f => f.family.includes(style.font.split(',')[0]) && f.status === 'loaded'), `${style.id} font`)
          assert.ok(actual.fullAnswer.includes(narrative.trim()), 'The complete canonical answer remains available')
          await page.locator('.mxw-full-answer > summary').click()
          assert.equal(await page.locator('.mxw-full-answer').getAttribute('open'), '')
          await page.locator('.mxw-full-answer > summary').click()
        }
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
        checks.push({ style: style.id, darkMode, width, passed: true })
        if (width === 1440 && darkMode === (style.defaultScheme === 'dark')) {
          await page.locator('[data-mxw-message="assistant"]').scrollIntoViewIfNeeded()
          await page.screenshot({ path: `${evidence}/${style.id}-desktop.png`, animations: 'disabled' })
        }
        if (width === 390 && darkMode === (style.defaultScheme === 'dark')) await page.screenshot({ path: `${evidence}/${style.id}-mobile.png`, animations: 'disabled' })
      }
    }
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.evaluate(() => window.MaxMode.setTheme({ stylePreset: 'current', darkMode: false, primaryColor: '#123b35', fontFamily: 'Inter, system-ui, sans-serif', borderRadius: '.5rem' }))
  const restored = await root.evaluate(element => { const bubble = getComputedStyle(element.querySelector('[data-mxw-message="user"]'));
    return { background: bubble.backgroundImage, radius: bubble.borderRadius, color: bubble.color } })
  assert.deepEqual(restored, { background: baseline.background, radius: baseline.radius, color: baseline.color })
  for (const style of WORKSPACE_STYLE_PRESETS) {
    await page.evaluate(theme => window.MaxMode.setTheme(theme), { stylePreset: style.id, darkMode: style.defaultScheme === 'dark', primaryColor: undefined, fontFamily: undefined, borderRadius: undefined })
    await page.getByRole('button', { name: 'Close AI Workspace', exact: true }).click()
    await page.locator('[data-max-mode-companion-input-shell]').waitFor()
    assert.equal(await page.locator('textarea:visible').inputValue(), 'Keep this draft while changing style')
    await page.screenshot({ path: `${evidence}/${style.id}-dock.png`, animations: 'disabled' })
    await page.getByRole('button', { name: 'MAX', exact: true }).click()
    await page.locator('[data-max-mode-view]').waitFor()
  }
  const suggestionDeadline = Date.now() + 12000
  while (!requests.some(item => item.method === 'POST' && item.path.endsWith('/suggestions')) && Date.now() < suggestionDeadline)
    await new Promise(resolve => setTimeout(resolve, 100))
  assert.equal(requests.filter(item => item.method === 'POST' && item.path.endsWith('/suggestions')).length, 1,
    'The existing attachment suggestion request runs once; style changes do not repeat it')
  assert.equal(requests.some(item => item.method !== 'GET' && !item.path.endsWith('/suggestions')), false, 'Style changes do not query AI, execute actions or submit forms')
  assert.deepEqual(errors, [])
  await writeFile(`${evidence}/verification.json`, JSON.stringify({ observedAt: new Date().toISOString(), checks, screenshots: 39,
    currentRestored: true, sameDraftNode: true, sameConversation: true, retainedAttachment: true, noStyleWrites: true, existingAttachmentSuggestionRequests: 1, errors }, null, 2))
  console.log(JSON.stringify({ passed: checks.length, screenshots: 39, noStyleWrites: true, currentRestored: true, evidence }))
} finally {
  await browser.close(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve))
}
