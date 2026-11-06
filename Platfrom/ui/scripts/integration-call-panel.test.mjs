import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { mkdir } from 'node:fs/promises'

test('personal contact panel keeps booking lazy, dismissible, isolated and responsive in both Console themes', { timeout: 90_000 }, async t => {
  const root = fileURLToPath(new URL('..', import.meta.url))
  const server = await createServer({ root, configFile: false, envFile: false, logLevel: 'error', esbuild: { jsx: 'automatic' },
    cacheDir: `/private/tmp/loomai-booking-vite-${process.pid}`, server: { host: '127.0.0.1', port: 0 },
    plugins: [{ name: 'booking-fixture', resolveId(id) { if (id === '/booking-fixture.jsx') return '\0booking-fixture' },
      load(id) {
        if (id !== '\0booking-fixture') return
        return `import React from 'react';import{createRoot}from'react-dom/client';
          import{ThemeProvider,createTheme}from'@mui/material';import{IntegrationCallButton}from'/src/components/IntegrationCallButton.tsx';
          const mode=new URLSearchParams(location.search).get('theme')||'light';
          createRoot(document.getElementById('root')).render(React.createElement(ThemeProvider,{theme:createTheme({palette:{mode}})},
            React.createElement('main',{style:{padding:16,minHeight:1600}},React.createElement('input',{'aria-label':'Unsubmitted draft',defaultValue:'Keep my project details'}),
            React.createElement(IntegrationCallButton),React.createElement(IntegrationCallButton,null,'Start a project'))));`
      }, configureServer(vite) { vite.middlewares.use((req, res, next) => {
        if (new URL(req.url, 'http://fixture').pathname !== '/booking-test') return next()
        res.setHeader('Content-Type', 'text/html')
        res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><div id="root"></div><script type="module" src="/booking-fixture.jsx"></script></body></html>')
      }) } }],
  })
  t.after(() => server.close())
  await server.listen()
  const browser = await chromium.launch({ headless: true })
  t.after(() => browser.close())
  const page = await browser.newPage()
  const errors = [], requests = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('https://calendly.com/**', route => {
    requests.push(route.request().url())
    return route.fulfill({ contentType: 'text/html', body: '<!doctype html><html><body><h1>Scheduling fixture</h1><button>10:00</button><input aria-label="Booking name"></body></html>' })
  })
  for (const theme of ['light', 'dark']) {
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(`${server.resolvedUrls.local[0]}booking-test?theme=${theme}`)
      const trigger = page.getByRole('link', { name: 'Talk to Mo', exact: true })
      await trigger.waitFor()
      assert.equal(await page.locator('iframe').count(), 0)
      const before = requests.length
      await page.getByRole('textbox', { name: 'Unsubmitted draft' }).fill('Untouched customer draft')
      const url = page.url()
      await trigger.click()
      const contact = page.getByRole('dialog', { name: "Let's connect your AI to your business." })
      await contact.waitFor()
      assert.equal(await page.locator('iframe').count(), 0)
      assert.equal(requests.length, before)
      await contact.getByRole('heading', { name: 'Mo.Elgammal', exact: true }).waitFor()
      await contact.getByText('Founder / AI Enablement Engineer', { exact: true }).waitFor()
      await contact.getByText('United Kingdom', { exact: true }).waitFor()
      assert.equal(await contact.getByRole('link', { name: 'Message me on WhatsApp', exact: true }).getAttribute('href'), 'https://wa.me/447729946319')
      assert.equal(await contact.getByRole('link', { name: '+44 7729 946319', exact: true }).getAttribute('href'), 'tel:+447729946319')
      assert.equal(await contact.getByRole('link', { name: 'Send an integration request', exact: true }).getAttribute('href'), '/support?new=1')
      const contactBounds = await contact.boundingBox()
      assert.ok(contactBounds.x >= 0 && contactBounds.x + contactBounds.width <= width + 1)
      assert.equal(await contact.evaluate(element => element.scrollWidth > element.clientWidth + 1), false)
      if (process.env.INTEGRATION_CONTACT_EVIDENCE_DIR) {
        await mkdir(process.env.INTEGRATION_CONTACT_EVIDENCE_DIR, { recursive: true })
        await contact.screenshot({ path: `${process.env.INTEGRATION_CONTACT_EVIDENCE_DIR}/console-contact-${theme}-${width}.png`, animations: 'disabled' })
      }
      await contact.getByRole('button', { name: 'Book a 30-min call', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: 'Book a call with Mo', exact: true })
      await dialog.waitFor()
      await page.frameLocator('iframe').getByRole('button', { name: '10:00' }).click()
      assert.equal(requests.length, before + 1)
      const embed = new URL(requests.at(-1))
      assert.equal(embed.host, 'calendly.com')
      assert.equal(embed.searchParams.get('embed_domain'), new URL(url).host)
      assert.equal(embed.searchParams.get('embed_type'), 'Inline')
      assert.deepEqual([...embed.searchParams.keys()].sort(), ['embed_domain', 'embed_type'])
      assert.equal(await page.locator('iframe').getAttribute('referrerpolicy'), 'no-referrer')
      assert.equal(page.url(), url)
      assert.equal(page.context().pages().length, 1)
      const bounds = await dialog.boundingBox()
      assert.ok(bounds.x >= 0 && bounds.y >= 0 && bounds.x + bounds.width <= width + 1 && bounds.y + bounds.height <= 901)
      assert.equal(await dialog.evaluate(element => element.scrollWidth > element.clientWidth), false)
      await page.getByRole('button', { name: "Back to Mo's details", exact: true }).click()
      await contact.waitFor()
      assert.equal(await page.locator('iframe').count(), 0)
      await contact.getByRole('button', { name: 'Close integration panel', exact: true }).click()
      await dialog.waitFor({ state: 'hidden' })
      assert.equal(await page.locator('iframe').count(), 0)
      assert.equal(await trigger.evaluate(element => document.activeElement === element), true)
      assert.equal(await page.getByRole('textbox', { name: 'Unsubmitted draft' }).inputValue(), 'Untouched customer draft')
      await page.getByRole('link', { name: 'Start a project', exact: true }).click()
      await contact.waitFor()
      assert.equal(await page.locator('iframe').count(), 0)
      await page.keyboard.press('Escape')
      await contact.waitFor({ state: 'hidden' })
      await trigger.click()
      await contact.waitFor()
      await page.mouse.click(2, 2)
      await contact.waitFor({ state: 'hidden' })
      assert.equal(await page.locator('iframe').count(), 0)
    }
  }
  assert.deepEqual(errors, [])
})
