import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import { once } from 'node:events'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.KIT_CARDS_EVIDENCE_DIR ?? path.join(root, 'test-results/kit-cards')
let server, browser
let origin = process.env.SITE_ORIGIN
const errors = [], checks = [], layouts = []
const kits = {
  generic: 'ai-customer-experience', dealership: 'dealership-ai-experience', support: 'ai-support-experience',
}
const bookingUrl = 'https://calendly.com/mahmoud-elgammal-loomai/loomai-integration-customer-success'
try {
  if (!origin) {
    const probe = createServer().listen(0, '127.0.0.1')
    await once(probe, 'listening')
    const port = probe.address().port
    await new Promise(resolve => probe.close(resolve))
    origin = `http://127.0.0.1:${port}`
    server = spawn(process.execPath, ['deploy/container/server.mjs'], {
      cwd: root, env: { ...process.env, PORT: String(port), LOOMAI_SITE_DIST_DIR: path.join(root, 'dist') }, stdio: 'pipe',
    })
    for (let i = 0; ; i++) {
      try { if ((await fetch(`${origin}/health`)).ok) break } catch {}
      assert.ok(i < 100, 'Site server did not start')
      await new Promise(resolve => setTimeout(resolve, 100))
    }
  }
  await mkdir(output, { recursive: true })
  browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({ colorScheme: 'dark', viewport: { width: 1536, height: 1200 } })
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  let bookingRequests = 0
  await page.route('https://calendly.com/**', route => {
    bookingRequests++
    return route.fulfill({ contentType: 'text/html', body: '<!doctype html><html><body><h1>Scheduling fixture</h1><button>10:00</button></body></html>' })
  })
  for (const route of ['/', '/experiences']) {
    await page.goto(origin + route)
    assert.equal(await page.locator('html').getAttribute('data-theme'), 'light')
    const bookingLinks = await page.locator('a.integration-call').all()
    assert.ok(bookingLinks.length >= 3)
    for (const link of bookingLinks) {
      assert.equal(await link.getAttribute('href'), bookingUrl)
      assert.equal(await link.getAttribute('target'), '_blank')
      assert.equal(await link.getAttribute('rel'), 'noopener noreferrer')
      assert.equal(await link.getAttribute('aria-haspopup'), 'dialog')
    }
    assert.equal(await page.locator('iframe[src*="calendly.com"]').count(), 0)
    const bookingTrigger = page.locator('.home-kits').getByRole('link', { name: 'Start a project', exact: true })
    const beforeBooking = bookingRequests
    await bookingTrigger.click()
    const contactPanel = page.getByRole('dialog', { name: "Let's connect your AI to your business.", exact: true })
    await contactPanel.waitFor()
    assert.equal(bookingRequests, beforeBooking)
    assert.equal(await contactPanel.locator('iframe').count(), 0)
    await contactPanel.getByText('Founder / AI Enablement Engineer', { exact: true }).waitFor()
    await contactPanel.getByText('United Kingdom', { exact: true }).waitFor()
    assert.equal(await contactPanel.getByRole('link', { name: 'Message me on WhatsApp', exact: true }).getAttribute('href'), 'https://wa.me/447729946319')
    await contactPanel.getByRole('button', { name: 'Book a 30-min call', exact: true }).click()
    const bookingPanel = page.getByRole('dialog', { name: 'Book a call with Mo', exact: true })
    await bookingPanel.waitFor()
    await bookingPanel.frameLocator('iframe').getByRole('button', { name: '10:00' }).click()
    assert.equal(bookingRequests, beforeBooking + 1)
    assert.equal(page.url(), origin + route)
    assert.equal(context.pages().length, 1)
    await bookingPanel.getByRole('button', { name: "Back to Mo's details", exact: true }).click()
    await contactPanel.waitFor()
    await contactPanel.locator('iframe').waitFor({ state: 'detached' })
    await contactPanel.getByRole('button', { name: 'Close integration panel' }).click()
    await bookingPanel.waitFor({ state: 'hidden' })
    await bookingPanel.locator('iframe').waitFor({ state: 'detached' })
    assert.equal(await page.locator('iframe[src*="calendly.com"]').count(), 0)
    assert.ok(await bookingTrigger.evaluate(el => el === document.activeElement))
    await bookingTrigger.click()
    await page.keyboard.press('Escape')
    await bookingPanel.waitFor({ state: 'hidden' })
    await bookingPanel.locator('iframe').waitFor({ state: 'detached' })
    checks.push(`${route}:lazy-booking-panel-slot-close-escape-focus-page-retained`)
    const section = page.locator('.home-kits')
    assert.equal(await section.locator('.home-kit').count(), 3)
    for (const [kind, code] of Object.entries(kits)) {
      const option = section.locator(`.home-kit-option[data-kind="${kind}"]`)
      assert.equal(await option.locator('.home-kit__explore').getAttribute('href'), `https://console.loomai.pro/experiences/${code}`)
      assert.equal(await option.locator('.home-kit-offer').count(), kind === 'dealership' ? 1 : 0)
      assert.equal(await option.locator('.home-kit__desk > span img').getAttribute('src'), '/assets/experiences/whatsapp.svg')
      const portrait = option.locator('.home-kit__desk > img')
      const channels = option.locator('.home-kit__desk > span')
      const portraitBox = await portrait.boundingBox(), channelBox = await channels.boundingBox()
      assert.ok(channelBox.y + channelBox.height <= portraitBox.y + 1, 'Channels must be above the Desk portrait')
    }
    assert.equal(await section.getByText('Only pay when value starts', { exact: true }).count(), 1)
    assert.equal(await section.getByText('First 3 months free', { exact: true }).count(), 1)
    assert.equal(await section.getByText('Free integration', { exact: true }).count(), 1)
    assert.equal(await section.getByRole('link', { name: 'Try live demo', exact: true }).getAttribute('href'), '/demos/dealership-ai')
    checks.push(`${route}:three-kits-detail-links-dealership-only-offer-channel-icons`)

    for (const kind of Object.keys(kits)) {
      const trigger = section.locator(`[data-catalogue-preview="${kind}"]`)
      await trigger.click()
      const dialog = page.locator(`#catalogue-preview-${kind}`)
      await dialog.waitFor({ state: 'visible' })
      assert.equal(await dialog.getAttribute('data-format'), 'workspace')
      for (const format of ['website', 'standalone', 'workspace']) {
        const button = dialog.locator(`[data-catalogue-format="${format}"]`)
        await button.click()
        assert.equal(await button.getAttribute('aria-pressed'), 'true')
        assert.equal(await dialog.getAttribute('data-format'), format)
        assert.ok(await dialog.locator('.catalogue-dialog__app').isVisible())
        assert.equal(await dialog.evaluate(el => el.scrollWidth > el.clientWidth + 1), false, `${kind} ${format} dialog overflow`)
        if (format !== 'workspace') assert.ok(await dialog.locator('[data-catalogue-address]').innerText())
      }
      if (kind === 'support') {
        assert.equal(await dialog.locator('form,input,textarea').count(), 0)
        assert.equal(await dialog.locator('.home-kit-scene__request').count(), 1)
        await dialog.screenshot({ path: path.join(output, 'support-preview-light.png') })
      }
      await page.keyboard.press('Escape')
      await dialog.waitFor({ state: 'hidden' })
      await trigger.click()
      await dialog.locator('[data-catalogue-close]').click()
      await dialog.waitFor({ state: 'hidden' })
      assert.ok(await trigger.evaluate(el => el === document.activeElement), 'Preview restores trigger focus')
    }
    checks.push(`${route}:all-nine-preview-formats-close-escape-focus`)
  }
  await page.goto(origin + '/')
  for (const theme of ['light', 'dark']) {
    if (theme === 'dark') await page.getByRole('button', { name: 'Switch to dark mode', exact: true }).click()
    assert.equal(await page.locator('html').getAttribute('data-theme'), theme)
    for (const width of [1536, 1280, 768, 390, 320]) {
      await page.setViewportSize({ width, height: 1200 })
      const section = page.locator('.home-kits')
      await section.scrollIntoViewIfNeeded()
      await section.locator('img').evaluateAll(async images => {
        await Promise.all(images.map(image => image.decode()))
      })
      await page.evaluate(() => document.fonts.ready)
      assert.equal(await page.evaluate(() => document.fonts.check('16px "LoomAI Kit Cards"')), true, 'Bundled kit-card font loads')
      const pageOverflow = await page.evaluate(() => [...document.querySelectorAll('body *')]
        .filter(el => el.checkVisibility() && el.getBoundingClientRect().right > innerWidth + 1)
        .slice(0, 8).map(el => ({ tag: el.tagName, class: el.className, right: el.getBoundingClientRect().right })))
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `${width} ${theme} page overflow: ${JSON.stringify(pageOverflow)}`)
      const metrics = await section.evaluate(el => ({
        font: getComputedStyle(el).fontFamily,
        cards: [...el.querySelectorAll('.home-kit')].map(card => {
          const rect = card.getBoundingClientRect(), button = card.querySelector('.home-kit__explore').getBoundingClientRect()
          return { x: rect.x, y: rect.y, width: rect.width, height: rect.height, buttonY: button.y, buttonHeight: button.height }
        }),
        overflow: [...el.querySelectorAll('h3,p,.home-kit__capabilities > span,.home-kit__products > div,.home-kit-offer')]
          .filter(el => el.scrollWidth > el.clientWidth + 1).map(el => el.textContent),
      }))
      assert.ok(metrics.font.includes('LoomAI Kit Cards'))
      assert.deepEqual(metrics.overflow, [], `${width} ${theme} text overflow`)
      if (width >= 1280) {
        assert.ok(Math.max(...metrics.cards.map(c => c.y)) - Math.min(...metrics.cards.map(c => c.y)) < 1)
        assert.ok(Math.max(...metrics.cards.map(c => c.buttonY)) - Math.min(...metrics.cards.map(c => c.buttonY)) < 1)
        assert.ok(Math.max(...metrics.cards.map(c => c.height)) - Math.min(...metrics.cards.map(c => c.height)) < 1)
      } else {
        assert.ok(metrics.cards.every((c, i, cards) => !i || c.y > cards[i - 1].y + cards[i - 1].height))
      }
      layouts.push({ width, theme, ...metrics })
      if ([1536, 390].includes(width)) await section.screenshot({ path: path.join(output, `homepage-kits-${width}-${theme}.png`) })
      if (width === 320) {
        for (const kind of Object.keys(kits)) {
          await section.locator(`[data-catalogue-preview="${kind}"]`).click()
          const dialog = page.locator(`#catalogue-preview-${kind}`)
          for (const format of ['website', 'standalone', 'workspace']) {
            await dialog.locator(`[data-catalogue-format="${format}"]`).click()
            assert.equal(await dialog.evaluate(el => el.scrollWidth > el.clientWidth + 1), false, `Mobile ${kind} ${format} overflow`)
          }
          await dialog.locator('[data-catalogue-close]').click()
        }
        await page.getByRole('button', { name: 'Open navigation', exact: true }).click()
        const mobileBooking = page.locator('[data-mobile-menu]').getByRole('link', { name: 'Talk to Mo', exact: true })
        assert.equal(await mobileBooking.getAttribute('href'), bookingUrl)
        await mobileBooking.click()
        const panel = page.getByRole('dialog', { name: "Let's connect your AI to your business.", exact: true })
        await panel.waitFor()
        assert.equal(await panel.evaluate(el => el.scrollWidth > el.clientWidth), false)
        await panel.getByRole('button', { name: 'Close integration panel' }).click()
        await panel.waitFor({ state: 'hidden' })
        await panel.locator('iframe').waitFor({ state: 'detached' })
        assert.ok(await mobileBooking.evaluate(el => el === document.activeElement))
        await page.getByRole('button', { name: 'Close navigation', exact: true }).click()
      }
    }
  }
  await page.reload()
  assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark')
  checks.push('ten-responsive-theme-layouts-images-fonts-offers-and-all-mobile-previews')
  assert.deepEqual(errors, [])
  const result = { status: 'PASS', origin, checks, layouts, errors }
  await writeFile(path.join(output, 'report.json'), JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify({ status: result.status, origin, checks, evidence: output }, null, 2))
} finally {
  await browser?.close()
  if (server) {
    const stopped = once(server, 'exit')
    server.kill('SIGTERM')
    await stopped
  }
}
