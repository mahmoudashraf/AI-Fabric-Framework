import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { chromium } from 'playwright'

const origins = (process.env.WORKSPACE_LIVE_ORIGINS || '').split(',').filter(Boolean)
assert(origins.length >= 2, 'WORKSPACE_LIVE_ORIGINS must name at least two reviewed live installations.')
const output = resolve(process.env.WORKSPACE_LIVE_OUTPUT || '/private/tmp/loomai-engage-live-gate')
const prompt = process.env.WORKSPACE_LIVE_PROMPT || 'Which current electric vehicles are under GBP 40,000?'
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
const report = { checkedAt: new Date().toISOString(), results: [], errors: [] }

try {
  for (const origin of origins) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' })
    const page = await context.newPage()
    page.setDefaultTimeout(90_000)
    const errors = []
    let messagePosts = 0
    page.on('pageerror', error => errors.push(error.message))
    page.on('request', request => {
      if (isMessage(request.url()) && request.method() === 'POST') messagePosts++
    })
    await page.goto(origin)
    await page.locator('textarea').waitFor({ state: 'visible' })
    const responsePromise = page.waitForResponse(response => isMessage(response.url()) && response.request().method() === 'POST')
    await page.locator('textarea').fill(prompt)
    await page.locator('textarea').press('Enter')
    const response = await responsePromise
    const answer = await response.json()
    assert.equal(response.status(), 200)
    assert.equal(answer.success, true, `${origin}: ${answer.type || 'unknown'} / ${answer.fallbackReason || ''}`)
    assert((answer.sources?.length || 0) > 0, `${origin}: no approved sources were returned`)
    await page.waitForFunction(() => {
      const key = Object.keys(sessionStorage).find(key => key.startsWith('maxmode_widget_state:'))
      return key && JSON.parse(sessionStorage.getItem(key)).conversationId
    })
    await page.evaluate(() => window.MaxMode.attachItem({
      type: 'current-page',
      data: { id: 'live-gate-page', title: 'Live gate reference page', content: 'This is a synthetic attachment used to verify conversation continuity.', metadata: { attachmentKind: 'CURRENT_PAGE', capturedCharacters: 88 } },
    }))
    await page.waitForFunction(() => {
      const key = Object.keys(sessionStorage).find(key => key.startsWith('maxmode_widget_state:'))
      return key && JSON.parse(sessionStorage.getItem(key)).attachedItems.some(item => item.data?.id === 'live-gate-page')
    })
    const before = await state(page)
    const postsBeforeReload = messagePosts
    await page.reload()
    await page.locator('textarea').waitFor({ state: 'visible' })
    const after = await state(page)
    assert.equal(after.conversationId, before.conversationId, 'Reload replaced the owned conversation')
    assert.equal(after.attachmentCount, before.attachmentCount, 'Reload lost attachments')
    assert.equal(after.messageCount, before.messageCount, 'Reload lost messages')
    assert.equal(messagePosts, postsBeforeReload, 'Reload replayed a message')
    const slug = new URL(origin).hostname.replaceAll('.', '-')
    await page.screenshot({ path: resolve(output, `${slug}-desktop.png`), fullPage: false })
    const layouts = []
    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 844 })
      const box = await page.locator('textarea').boundingBox()
      assert(box && box.x >= 0 && box.x + box.width <= width + 1, `Composer overflows at ${width}px`)
      await page.screenshot({ path: resolve(output, `${slug}-${width}.png`), fullPage: false })
      layouts.push({ width, composerFits: true })
    }
    assert.deepEqual(errors, [], 'Browser exceptions occurred')
    report.results.push({ origin, runtimeOrigin: new URL(response.url()).origin, success: answer.success, type: answer.type,
      sources: answer.sources.length, actions: answer.actions?.length || 0, conversationId: after.conversationId,
      reloadPreservedMessages: true, reloadPreservedAttachments: true, reloadReplayedRequests: false, layouts, browserErrors: errors })
    await context.close()
  }
} catch (error) {
  report.errors.push(error.message)
  process.exitCode = 1
} finally {
  await browser.close()
  await writeFile(resolve(output, 'report.json'), `${JSON.stringify(report, null, 2)}\n`)
  console.log(JSON.stringify(report, null, 2))
}

function isMessage(url) {
  return /\/api\/chat\/me\/conversations\/[^/]+\/messages$/.test(new URL(url).pathname)
}

async function state(page) {
  return page.evaluate(() => {
    const key = Object.keys(sessionStorage).find(key => key.startsWith('maxmode_widget_state:'))
    const value = key ? JSON.parse(sessionStorage.getItem(key)) : {}
    return { conversationId: value.conversationId, messageCount: value.chatMessages?.length || 0, attachmentCount: value.attachedItems?.length || 0 }
  })
}
