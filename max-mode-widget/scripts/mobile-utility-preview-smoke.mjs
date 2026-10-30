import assert from 'node:assert/strict'
import http from 'node:http'
import { mkdir, readFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { chromium } from 'playwright'

const bundle = await readFile(new URL('../dist/max-mode-widget.iife.js', import.meta.url), 'utf8')
const output = process.env.WIDGET_PREVIEW_OUTPUT || path.join(tmpdir(), 'loomai-widget-mobile-preview')
await mkdir(output, { recursive: true })
let queryCount = 0
const server = http.createServer(handler)
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const origin = `http://127.0.0.1:${server.address().port}`
const browser = await chromium.launch({ headless: true })
const cases = [
  { name: 'mobile-disabled', utility: false, width: 390, height: 844 },
  { name: 'mobile-enabled', utility: true, width: 390, height: 844 },
  { name: 'mobile-default', width: 390, height: 844 },
  { name: 'mobile-empty-rail', utility: false, emptyRail: true, width: 390, height: 844 },
  { name: 'desktop-disabled', utility: false, width: 1440, height: 900 },
]

try {
  for (const fixture of cases) {
    const page = await browser.newPage({ viewport: { width: fixture.width, height: fixture.height }, reducedMotion: 'reduce' })
    page.setDefaultTimeout(8_000)
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    try {
      await page.goto(origin)
      await page.evaluate(({ origin, fixture }) => {
        const host = {
          assistantLabel: 'Policy assistant',
          welcomeMessage: 'Approved policy help.',
          toolRail: { items: fixture.emptyRail ? [] : [
            { id: 'host-references', label: 'References', action: 'open-documents' },
            { id: 'host-policy', label: 'Review policy', action: 'prompt', query: 'Review the policy.' },
          ] },
        }
        if (fixture.utility !== undefined) host.showUtilityPanel = fixture.utility
        window.MaxMode.init({
          apiConfig: { chatBaseUrl: origin, probeShellConfigOnOpen: false, runtimeAuth: { probeAuthContextOnOpen: false },
            runtimeRoutes: { chatQueryUrl: '/api/chat/query', suggestionsUrl: '/api/chat/suggestions' } },
          storageNamespace: fixture.name,
          host,
          features: { cart: false, conversations: false, quickActions: false },
        })
        window.MaxMode.open()
      }, { origin, fixture })

      const first = await send(page, 'Show the first policy source.')
      assert.equal(await page.getByRole('heading', { name: 'New sources', exact: true }).count(), 0,
        'Initial source history must not open an additional-source preview')
      const second = await send(page, 'Show another policy source.')
      const mobile = fixture.width < 768
      const preview = page.locator('div.fixed.top-20.bottom-20')
      if (!fixture.emptyRail) {
        // This badge is updated in the same source-sync effect that opens the preview.
        await page.locator('[data-max-mode-tool-rail-item="host-references"]').getByText('2', { exact: true }).waitFor({ state: 'attached' })
      } else {
        await page.waitForFunction(() => document.querySelector('#max-mode-widget-shadow-host')?.shadowRoot
          ?.textContent?.includes('2 documents'))
      }

      if (mobile && fixture.utility !== false) {
        await page.getByRole('heading', { name: 'New sources', exact: true }).waitFor()
        await page.waitForFunction(() => {
          const panel = document.querySelector('#max-mode-widget-shadow-host')?.shadowRoot?.querySelector('div.fixed.top-20.bottom-20')
          return panel && Math.abs(panel.getBoundingClientRect().right - innerWidth) < 1
        })
        await preview.getByText(second.sources[0].title, { exact: true }).waitFor()
        assert.equal(await page.getByRole('heading', { name: 'New Products', exact: true }).count(), 0)
        await page.screenshot({ path: path.join(output, `${fixture.name}.png`) })
        await preview.getByRole('button').first().click()
        await preview.waitFor({ state: 'detached' })
      } else {
        assert.equal(await preview.count(), 0, 'Disabled utilities must not render the automatic preview or its backdrop')
        assert.equal(await page.getByRole('heading', { name: 'New sources', exact: true }).count(), 0)
        const unobscured = await page.getByText(second.answer, { exact: true }).evaluate(element => {
          element.scrollIntoView({ block: 'center' })
          const box = element.getBoundingClientRect()
          const hit = element.getRootNode().elementFromPoint(box.x + box.width / 2, box.y + box.height / 2)
          return hit === element || element.contains(hit)
        })
        assert.equal(unobscured, true, 'The source preview obscures the answer')
        await page.screenshot({ path: path.join(output, `${fixture.name}.png`) })
      }

      const writesBeforeEvidence = queryCount
      if (mobile) {
        if (fixture.emptyRail) assert.equal(await page.locator('[data-max-mode-tool-rail]').count(), 0)
        else {
          await page.getByRole('button', { name: 'Run Review policy', exact: true }).waitFor()
          await page.getByRole('button', { name: 'Open References', exact: true }).click()
          await page.getByRole('heading', { name: 'Context Documents', exact: true }).waitFor()
          await page.getByText(first.sources[0].title, { exact: true }).last().waitFor()
          await page.getByText(second.sources[0].title, { exact: true }).last().waitFor()
          await page.getByRole('button', { name: 'Close', exact: true }).click()
          await page.getByRole('heading', { name: 'Context Documents', exact: true }).waitFor({ state: 'detached' })
        }
        await page.getByRole('button').filter({ has: page.getByText(second.sources[0].content, { exact: true }) }).click()
        await page.getByRole('heading', { name: second.sources[0].title, exact: true }).waitFor()
        await page.getByText(second.sources[0].content, { exact: true }).filter({ visible: true }).last().waitFor()
      } else {
        await page.getByRole('heading', { name: 'Context Panel', exact: true }).waitFor()
        await page.getByText(second.sources[0].title, { exact: true }).waitFor()
      }
      assert.equal(queryCount, writesBeforeEvidence, 'Viewing source evidence must not replay a query')
      assert.deepEqual(errors, [])
    } finally { await page.close() }
  }
  assert.equal(queryCount, cases.length * 2)
  console.log(JSON.stringify({ status: 'PASS', cases: cases.map(fixture => fixture.name),
    checks: ['utility-opt-out', 'default-and-opt-in-preview', 'neutral-label', 'source-evidence', 'custom-and-empty-rails', 'unobscured-answer', 'no-query-replay'], screenshots: output }))
} finally {
  await browser.close()
  server.closeAllConnections()
  await new Promise(resolve => server.close(resolve))
}

async function send(page, query) {
  const received = page.waitForResponse(response => response.url() === `${origin}/api/chat/query` && response.request().method() === 'POST')
  await page.locator('textarea').fill(query)
  await page.locator('textarea').press('Enter')
  const response = await received
  assert.equal(response.status(), 200)
  const result = await response.json()
  await page.getByText(result.answer, { exact: true }).waitFor()
  return result
}

async function handler(request, response) {
  if (request.url === '/widget.js') {
    response.writeHead(200, { 'Content-Type': 'application/javascript' }).end(bundle)
    return
  }
  if (request.url === '/') {
    response.writeHead(200, { 'Content-Type': 'text/html' }).end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><script src="/widget.js"></script></body></html>')
    return
  }
  if (request.url === '/api/chat/query' && request.method === 'POST') {
    for await (const chunk of request) { /* Drain the local fixture request. */ }
    queryCount += 1
    json(response, 200, { success: true, type: 'INFORMATION_PROVIDED', conversationId: 'policy-fixture',
      answer: `The approved policy answer ${queryCount}.`,
      sources: [{ id: `policy-${queryCount}`, title: `Policy guide ${queryCount}`, content: `Policy evidence ${queryCount}.`, type: 'policy', score: 0.9 }],
      actions: [], suggestions: [] })
    return
  }
  if (request.url === '/api/chat/suggestions') { json(response, 200, { success: true, suggestions: [] }); return }
  json(response, 404, { message: 'Not found' })
}

function json(response, status, value) {
  response.writeHead(status, { 'Content-Type': 'application/json' }).end(JSON.stringify(value))
}
