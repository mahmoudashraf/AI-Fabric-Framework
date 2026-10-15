import assert from 'node:assert/strict'
import { mkdir, readFile } from 'node:fs/promises'
import http from 'node:http'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { chromium } from 'playwright'

const bundle = await readFile(new URL('../dist/max-mode-widget.iife.js', import.meta.url), 'utf8')
const output = process.env.WIDGET_ACCESSIBILITY_OUTPUT || path.join(tmpdir(), 'loomai-widget-accessibility')
await mkdir(output, { recursive: true })
let releaseHandover
let handoverRequested = false
const handoverResponse = new Promise((resolve) => { releaseHandover = resolve })
const server = http.createServer(handler)
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
const origin = `http://127.0.0.1:${server.address().port}`
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' })
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.addInitScript(() => {
    const viewport = new EventTarget()
    let override = {}
    for (const [name, fallback] of Object.entries({ width: 'innerWidth', height: 'innerHeight', offsetTop: null, offsetLeft: null })) {
      Object.defineProperty(viewport, name, { get: () => override[name] ?? (fallback ? window[fallback] : 0) })
    }
    Object.defineProperty(window, 'visualViewport', { value: viewport, configurable: true })
    window.setTestViewport = (value) => {
      override = value
      viewport.dispatchEvent(new Event('resize'))
      viewport.dispatchEvent(new Event('scroll'))
    }
  })
  await page.goto(origin)
  await page.locator('[data-max-mode-view]').waitFor()
  const trigger = page.getByRole('button', { name: 'Talk to a person', exact: true })
  await trigger.click()
  const dialog = page.getByRole('dialog', { name: 'Talk to a person', exact: true })
  await dialog.waitFor()
  assert.equal(await dialog.evaluate((element) => element.matches(':modal')), true)
  assert.equal(await dialog.getByLabel('Email *', { exact: true }).evaluate((element) => element.getRootNode().activeElement === element), true)
  await page.locator('#outside-button').evaluate((element) => element.focus())
  assert.equal(await dialog.evaluate((element) => element.contains(element.getRootNode().activeElement)), true, 'Background accepted focus while the dialog was modal')
  await dialog.getByLabel('Email *', { exact: true }).fill('reader@example.test')
  const close = dialog.getByRole('button', { name: 'Close human support panel' })
  await close.focus()
  await page.keyboard.press('Shift+Tab')
  assert.equal(await dialog.evaluate((element) => element.contains(element.getRootNode().activeElement)), true)
  await page.keyboard.press('Tab')
  assert.equal(await close.evaluate((element) => element.getRootNode().activeElement === element), true)
  await page.keyboard.press('Escape')
  await dialog.waitFor({ state: 'detached' })
  assert.equal(await trigger.evaluate((element) => element.getRootNode().activeElement === element), true, 'Focus did not return to the launcher')

  await trigger.click()
  await dialog.getByLabel('Email *', { exact: true }).fill('reader@example.test')
  await dialog.getByRole('button', { name: 'Talk to a person', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('#max-mode-widget-shadow-host')?.shadowRoot?.querySelector('dialog button[aria-label="Close human support panel"]')?.disabled)
  assert.equal(handoverRequested, true)
  await page.keyboard.press('Escape')
  assert.equal(await dialog.evaluate((element) => element.matches(':modal')), true, 'Escape dismissed an in-flight request')
  releaseHandover()
  await close.waitFor({ state: 'visible' })
  await page.waitForFunction(() => !document.querySelector('#max-mode-widget-shadow-host')?.shadowRoot?.querySelector('dialog button[aria-label="Close human support panel"]')?.disabled)
  await page.keyboard.press('Escape')
  await dialog.waitFor({ state: 'detached' })

  await page.screenshot({ path: path.join(output, 'desktop.png') })
  await page.setViewportSize({ width: 390, height: 844 })
  const protocol = await page.context().newCDPSession(page)
  await protocol.send('Emulation.setSafeAreaInsetsOverride', { insets: { top: 24, bottom: 20, left: 6, right: 6 } })
  await page.evaluate(() => window.setTestViewport({ width: 390, height: 360, offsetTop: 180, offsetLeft: 0 }))
  await page.waitForFunction(() => {
    const view = document.querySelector('#max-mode-widget-shadow-host')?.shadowRoot?.querySelector('[data-max-mode-view]')
    return view && Math.round(view.getBoundingClientRect().height) === 316
  })
  const view = await page.locator('[data-max-mode-view]').boundingBox()
  assert.deepEqual(roundBox(view), { x: 6, y: 204, width: 378, height: 316 })
  const composer = await page.locator('textarea').boundingBox()
  assert(composer && composer.y >= view.y && composer.y + composer.height <= view.y + view.height, 'Composer exceeds the visible keyboard viewport')
  const supportButton = page.getByRole('button', { name: 'Talk to a person', exact: true })
  await supportButton.click()
  await dialog.waitFor()
  const modalBox = await dialog.boundingBox()
  assert.equal(roundBox(modalBox).height, 316)
  assert.equal(roundBox(modalBox).y, 204)
  await dialog.getByLabel('Email *', { exact: true }).fill('mobile@example.test')
  const mobileSubmit = dialog.getByRole('button', { name: 'Talk to a person', exact: true })
  await mobileSubmit.focus()
  const submitBox = await mobileSubmit.boundingBox()
  assert(submitBox && submitBox.y >= modalBox.y && submitBox.y + submitBox.height <= modalBox.y + modalBox.height, 'Keyboard focus did not reveal the action inside the scrolling dialog')
  await page.screenshot({ path: path.join(output, 'mobile-keyboard.png') })
  await page.keyboard.press('Escape')
  await page.evaluate(() => window.setTestViewport({}))
  await page.setViewportSize({ width: 844, height: 390 })
  await page.waitForFunction(() => Math.round(document.querySelector('#max-mode-widget-shadow-host')?.shadowRoot?.querySelector('[data-max-mode-view]')?.getBoundingClientRect().height || 0) === 346)
  assert.equal(roundBox(await page.locator('[data-max-mode-view]').boundingBox()).width, 832)

  await page.evaluate(() => {
    window.MaxMode.destroy()
    window.MaxMode.init({ apiConfig: { chatBaseUrl: location.origin, probeShellConfigOnOpen: false, runtimeAuth: { probeAuthContextOnOpen: false } }, host: { companionDock: true }, features: { cart: false, conversations: false, quickActions: false } })
    window.setTestViewport({ width: 390, height: 360, offsetTop: 0, offsetLeft: 0 })
  })
  const dock = page.locator('.max-mode-companion-dock')
  await dock.waitFor()
  const dockBox = await dock.boundingBox()
  assert(dockBox.x >= 0 && dockBox.x + dockBox.width <= 390 && dockBox.y + dockBox.height <= 340, 'Dock ignores visible viewport or device insets')
  await page.evaluate(() => {
    const host = document.querySelector('#max-mode-widget-shadow-host')
    window.MaxMode.destroy()
    window.setTestViewport({ height: 100, width: 100 })
    window.__removedHostViewport = host.style.getPropertyValue('--max-mode-viewport-height')
  })
  assert.equal(await page.evaluate(() => window.__removedHostViewport), '', 'Unmount left viewport listeners active')
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ status: 'PASS', checks: ['native-modal', 'initial-focus', 'background-inertness', 'tab-containment', 'escape-policy', 'focus-restoration', 'keyboard-viewport', 'safe-area', 'orientation', 'dock', 'unmount-cleanup'], screenshots: output }))
} finally {
  releaseHandover()
  await browser.close()
  await new Promise((resolve) => server.close(resolve))
}

function roundBox(box) {
  assert(box, 'Expected a visible element')
  return Object.fromEntries(Object.entries(box).map(([key, value]) => [key, Math.round(value)]))
}

async function handler(request, response) {
  if (request.url === '/widget.js') {
    response.writeHead(200, { 'Content-Type': 'application/javascript' }).end(bundle)
    return
  }
  if (request.url === '/') {
    const routes = Object.fromEntries(['conversationMessageUrlTemplate', 'handoverRequestUrlTemplate', 'handoverCancelUrlTemplate', 'handoverRetryUrlTemplate', 'conversationControlUrlTemplate', 'conversationEventsUrlTemplate', 'aiResumeRequestUrlTemplate'].map((name) => [name, `/${name}/{conversationId}`]))
    response.writeHead(200, { 'Content-Type': 'text/html' }).end(`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"></head><body><button id="outside-button">Outside action</button><script src="/widget.js"></script><script>
      MaxMode.init(${JSON.stringify({ apiConfig: { chatBaseUrl: origin, probeShellConfigOnOpen: false, runtimeAuth: { probeAuthContextOnOpen: false }, runtimeRoutes: routes, conversationHandover: { enabled: true, intake: { anonymousAllowed: false, fields: [{ key: 'email', type: 'EMAIL', label: 'Email', required: true }] } } }, host: { assistantLabel: 'Support', showUtilityPanel: false }, features: { cart: false, conversations: false, quickActions: false } })}); MaxMode.open();
      </script></body></html>`)
    return
  }
  if (request.url?.startsWith('/handoverRequestUrlTemplate/')) {
    handoverRequested = true
    await handoverResponse
    json(response, 422, { outcome: 'HANDOVER_INTAKE_REQUIRED', conversation: { conversationId: request.url.split('/').at(-1), controlState: 'AI_ACTIVE' }, intakeSchema: { anonymousAllowed: false, fields: [{ key: 'email', type: 'EMAIL', label: 'Email', required: true }] } })
    return
  }
  if (request.url?.startsWith('/conversationEventsUrlTemplate/')) {
    response.writeHead(200, { 'Content-Type': 'text/event-stream' }).end(': test stream\n\n')
    return
  }
  if (request.url?.startsWith('/conversationControlUrlTemplate/')) {
    json(response, 200, { conversationId: request.url.split('/').at(-1).split('?')[0], controlState: 'AI_ACTIVE', messages: [] })
    return
  }
  json(response, 404, { message: 'Not found' })
}

function json(response, code, body) {
  response.writeHead(code, { 'Content-Type': 'application/json' }).end(JSON.stringify(body))
}
