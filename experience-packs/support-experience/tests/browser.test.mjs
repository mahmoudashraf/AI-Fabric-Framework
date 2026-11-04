import assert from 'node:assert/strict'
import test from 'node:test'
import { mkdir } from 'node:fs/promises'
import { chromium } from '../../../max-mode-widget/node_modules/playwright/index.mjs'
import { startPreview } from '../scripts/preview-server.mjs'

test('standalone stays full-screen above an existing startup shell and keeps mobile composer reachable', { timeout: 45000 }, async () => {
  const fixture = await startPreview()
  const browser = await chromium.launch({ headless: true })
  try {
    for (const viewport of [{ width: 1360, height: 900 }, { width: 390, height: 844 }, { width: 360, height: 640 }]) {
      const page = await browser.newPage({ viewport })
      await page.goto(fixture.url)
      await page.waitForFunction(() => window.fixtureReady && !window.controller.getState().busy)
      await page.evaluate(() => {
        const startup = document.createElement('main'); startup.style.height = '100dvh'; document.body.prepend(startup)
        window.controller.setPresentation('standalone')
      })
      const bounds = await page.locator('[data-loomai-support]').boundingBox()
      assert.equal(bounds.x, 0); assert.equal(bounds.y, 0)
      assert.equal(bounds.width, viewport.width); assert.equal(bounds.height, viewport.height)
      const composer = page.frameLocator('iframe[data-loomai-workspace-frame]').locator('textarea')
      await composer.fill('An unsent mobile draft')
      const input = await composer.boundingBox()
      assert.ok(input.y >= 0 && input.y + input.height <= viewport.height, 'Composer must fit the actual standalone viewport')
      await page.getByRole('button', { name: 'Request help', exact: true }).click()
      await page.getByLabel('Subject', { exact: true }).fill('Phone form')
      await page.getByLabel('Details', { exact: true }).fill('An editable unsaved request')
      await page.getByRole('button', { name: 'Cancel', exact: true }).click()
      assert.equal(await composer.inputValue(), 'An unsent mobile draft')
      await page.evaluate(() => window.controller.showRequests())
      await page.getByRole('checkbox', { name: 'Email request updates', exact: true }).waitFor()
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
      await page.close()
    }
  } finally { await browser.close(); await fixture.close() }
})

test('email updates are opt-in, reversible and visibly fail without hiding saved requests on desktop/mobile', { timeout: 45000 }, async () => {
  const fixture = await startPreview()
  const browser = await chromium.launch({ headless: true })
  try {
    for (const width of [1360, 390]) {
      const page = await browser.newPage({ viewport: { width, height: 844 } })
      await page.goto(fixture.url)
      await page.waitForFunction(() => window.fixtureReady && !window.controller.getState().busy)
      await page.evaluate(() => window.controller.showRequests())
      const toggle = page.getByRole('checkbox', { name: 'Email request updates', exact: true })
      await toggle.waitFor()
      assert.equal(await toggle.isChecked(), false)
      await toggle.check()
      await page.waitForFunction(() => window.controller.getState().notifications?.enabled && !window.controller.getState().busy)
      assert.equal(await toggle.isChecked(), true)
      const enabled = fixture.requests.filter(row => row.path.endsWith('/notifications') && row.method === 'PUT').at(-1)
      assert.equal(enabled.body.enabled, true)
      assert.equal(enabled.body.email, undefined)
      assert.equal(enabled.body.returnUrl, fixture.url.replace(/\/$/, '') + '/')
      await toggle.uncheck()
      await page.waitForFunction(() => window.controller.getState().notifications?.enabled === false && !window.controller.getState().busy)
      await page.route('**/api/support/notifications', route => route.fulfill({ json: {
        available: true, enabled: true, deliveryStatus: 'FAILED', lastFailureCode: 'SMTP_UNAVAILABLE',
      } }))
      await page.getByRole('button', { name: 'Refresh', exact: true }).click()
      await page.getByText('An email update could not be delivered. Your request is saved; check My Requests.', { exact: true }).waitFor()
      assert.equal(await page.getByRole('button', { name: 'Return to chat', exact: true }).isVisible(), true)
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
      await page.close()
    }
  } finally { await browser.close(); await fixture.close() }
})

test('handover-only mount loads the authenticated roster with no ticket controls or requests', { timeout: 45000 }, async () => {
  const fixture = await startPreview()
  const browser = await chromium.launch({ headless: true })
  fixture.conversations.set('preview-chat', { controlState: 'HUMAN_ACTIVE', controlVersion: 4 })
  try {
    for (const [handover, teamVisible] of [[true, true], [true, false], [false, true]]) {
      const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
      const calls = [], errors = []
      page.on('pageerror', error => errors.push(error.message))
      page.on('request', request => { const url = new URL(request.url()); if (url.pathname.startsWith('/api/support/')) calls.push(url.pathname) })
      await page.route('**/api/support/session', async route => {
        const response = await route.fetch(), session = await response.json()
        await route.fulfill({ response, json: { ...session, capabilities: [],
          space: { ...session.space, enabled: false, humanHandoverEnabled: true, capabilities: [] } } })
      })
      await page.goto(`${fixture.url}?tickets=false&handover=${handover}&team=${teamVisible}`)
      await page.waitForFunction(() => window.fixtureReady && !window.controller.getState().busy)
      assert.equal(calls.filter(path => path.endsWith('/session')).length, handover ? 1 : 0)
      assert.equal(calls.filter(path => path.endsWith('/team')).length, handover && teamVisible ? 1 : 0)
      assert.equal(await page.getByText('Sample assigned agent', { exact: true }).isVisible(), handover && teamVisible)
      assert.equal(await page.getByRole('button', { name: 'Request help', exact: true }).count(), 0)
      assert.equal(await page.getByRole('button', { name: 'My Requests', exact: true }).count(), 0)
      await page.evaluate(async () => {
        window.controller.openRequestForm()
        await window.controller.showRequests()
        await window.controller.openRequest('not-a-ticket')
        await window.controller.refresh()
      })
      assert.equal(await page.evaluate(() => window.controller.getState().view), 'chat')
      assert.equal(await page.evaluate(() => window.controller.getState().request), null)
      assert.equal(calls.some(path => path.startsWith('/api/support/requests')), false)
      assert.equal(await page.getByText('AI support and request tracking', { exact: true }).count(), 0)
      if (handover) {
        assert.deepEqual(await page.evaluate(() => window.controller.getState().session.capabilities), [])
        assert.equal(await page.evaluate(() => window.fixtureFrame.maxMode.getState().controlState), 'HUMAN_ACTIVE')
      }
      assert.deepEqual(errors, [])
      await page.evaluate(() => window.controller.destroy())
      await page.close()
    }
  } finally { await browser.close(); await fixture.close() }
})

test('real engine survives page/widget/SPA changes and generic request lifecycle on desktop/mobile', { timeout: 90000 }, async () => {
  const fixture = await startPreview()
  const browser = await chromium.launch({ headless: true })
  const output = new URL('../test-results/', import.meta.url)
  await mkdir(output, { recursive: true })
  try {
    for (const viewport of [{ width: 1360, height: 1040 }, { width: 1024, height: 800 }, { width: 390, height: 844 }]) {
      const page = await browser.newPage({ viewport })
      const errors = []
      page.on('pageerror', error => errors.push(error.message))
      await page.goto(fixture.url)
      await page.waitForFunction(() => window.fixtureReady && !window.controller.getState().busy)
      const frame = page.frameLocator('iframe[data-loomai-workspace-frame]')
      await frame.getByText('This is a synthetic conversation from the fixture preview. No live support service was contacted.', { exact: true }).waitFor()
      assert.equal(await frame.getByRole('button', { name: 'How can this support experience help?', exact: true }).count(), 0,
        'Starter suggestions must not cover a restored conversation')
      await frame.locator('textarea').fill('An unsent draft that must survive layout changes')
      const bounds = await frame.locator('textarea').boundingBox()
      assert.ok(bounds.y + bounds.height <= viewport.height, 'The contained composer must fit inside the viewport')
      await page.evaluate(() => { window.originalFrame = document.querySelector('iframe'); window.fixtureFrame.maxMode.attachItem({ type: 'document', data: { id: 'retained-context', title: 'Retained sample context', content: 'Synthetic attached context.' } }) })
      await frame.getByText('Retained sample context', { exact: false }).first().waitFor()
      const before = fixture.requests.length
      await page.evaluate(() => window.controller.setVisible(false))
      assert.equal(await page.locator('[data-loomai-support]').isVisible(), false)
      assert.equal(await page.locator('iframe').isVisible(), false)
      await page.evaluate(() => window.controller.setVisible(true))
      await page.evaluate(() => {
        const c=window.controller
        c.setPresentation('widget'); c.openChat(); c.setVisible(false); c.setVisible(true)
        c.setTheme('dark'); c.setTheme('light')
        c.setPresentation('page',document.getElementById('support-preview'))
      })
      await page.evaluate(() => { window.controller.setPresentation('widget'); window.controller.openChat() })
      const widgetRect = await page.locator('[data-loomai-support]').evaluate(el => {
        const rect=el.shadowRoot.querySelector('.shell').getBoundingClientRect(); return {x:rect.x,y:rect.y,width:rect.width,height:rect.height}
      })
      assert.ok(widgetRect.width <= Math.min(460, viewport.width - 32) + 1)
      assert.ok(widgetRect.y >= 0)
      await page.evaluate(() => { window.fixtureFrame.setLayer(1101); window.fixtureFrame.setLayer(2147483001) })
      await page.screenshot({ path: new URL(`support-widget-${viewport.width}.png`, output).pathname, fullPage: true })
      await page.evaluate(() => window.controller.setPresentation('page',document.getElementById('support-preview')))
      assert.equal(await frame.locator('textarea').inputValue(), 'An unsent draft that must survive layout changes')
      assert.equal(await page.evaluate(() => document.querySelector('iframe') === window.originalFrame), true)
      assert.equal(fixture.requests.slice(before).filter(req => req.method !== 'GET').length, 0)
      await page.evaluate(() => { const old=document.getElementById('support-preview'); old.remove(); const replacement=document.createElement('div'); replacement.id='support-preview'; document.querySelector('main').append(replacement) })
      await page.waitForFunction(() => document.getElementById('support-preview').querySelector('[data-loomai-support]'))
      assert.equal(await page.evaluate(() => document.querySelector('iframe') === window.originalFrame), true)
      assert.equal(await frame.locator('textarea').inputValue(), 'An unsent draft that must survive layout changes')
      await page.getByRole('button', { name: 'Request help', exact: true }).click()
      await page.getByLabel('Subject', { exact: true }).fill('A sample issue')
      const beforeFormRefresh = fixture.requests.length
      await page.evaluate(() => window.controller.refresh())
      await page.evaluate(() => window.dispatchEvent(new Event('focus')))
      await page.waitForTimeout(350)
      assert.equal(fixture.requests.length, beforeFormRefresh, 'Background chat reads must not block intake editing')
      assert.equal(await page.getByLabel('Subject', { exact: true }).inputValue(), 'A sample issue')
      assert.equal(await page.getByLabel('Subject', { exact: true }).isEnabled(), true)
      await page.getByLabel('Details', { exact: true }).fill('Synthetic issue details for this fixture.')
      await page.getByLabel('Topic', { exact: true }).selectOption('guidance')
      await page.screenshot({ path: new URL(`support-form-${viewport.width}.png`, output).pathname, fullPage: true })
      await page.getByRole('button', { name: 'Submit request', exact: true }).click()
      await page.waitForFunction(() => window.controller.getState().request && !window.controller.getState().busy)
      const saved = await page.evaluate(() => window.controller.getState().request)
      assert.equal(saved.subject, 'A sample issue')
      assert.equal(await frame.locator('textarea').inputValue(), '', 'An explicit ticket scope switch clears the prior draft')
      assert.equal(await frame.getByText('Retained sample context', { exact: false }).count(), 0, 'A prior ticket attachment cannot become current input in a new scope')
      assert.equal(await page.evaluate(() => { try { window.fixtureFrame.maxMode.setConnection({apiConfig:{chatBaseUrl:location.origin+'/api/runtime'},hostSession:{namespace:'foreign',accountKey:'foreign-account'}});return false } catch{return true} }), true)
      await page.evaluate(id => window.controller.openRequest(id), saved.id)
      await page.getByRole('button', { name: 'Resolve request', exact: true }).click()
      await page.waitForFunction(() => window.controller.getState().request.status === 'RESOLVED' && !window.controller.getState().busy, null, { timeout: 5000 }).catch(async error => {
        throw new Error(`${error.message}\nState: ${JSON.stringify(await page.evaluate(() => window.controller.getState()))}\nRequests: ${JSON.stringify(fixture.requests)}`)
      })
      assert.equal(await page.evaluate(() => window.fixtureFrame.maxMode.getState().controlState), 'AI_ACTIVE')
      assert.equal(await frame.locator('textarea').isDisabled(), true, 'A closed request must require explicit reopen before another reply')
      await page.getByRole('button', { name: 'Reopen request', exact: true }).click()
      await page.waitForFunction(() => window.controller.getState().request.status === 'OPEN' && !window.controller.getState().busy)
      assert.equal(await frame.locator('textarea').isDisabled(), false)
      await page.screenshot({ path: new URL(`support-detail-${viewport.width}.png`, output).pathname, fullPage: true })
      const overflow = await page.evaluate(() => {
        const root=document.querySelector('[data-loomai-support]').shadowRoot
        return [...root.querySelectorAll('button,input,select,textarea,h1,h2,h3')].filter(el=>el.getBoundingClientRect().width && el.scrollWidth>el.clientWidth+2).map(el=>el.textContent)
      })
      assert.deepEqual(overflow, [], 'Text and controls must fit their containers')
      assert.equal(await page.locator('iframe').count(), 1)
      assert.equal(await page.evaluate(() => window.originalFrame === document.querySelector('iframe')), true)
      assert.deepEqual(errors, [])
      await page.evaluate(() => window.controller.destroy())
      assert.equal(await page.locator('iframe').count(), 0)
      await page.close()
    }
    const page = await browser.newPage()
    await page.goto(`${fixture.url}?tickets=false&attachments=false`)
    await page.waitForFunction(() => window.fixtureReady)
    assert.equal(await page.getByRole('button', { name: 'Request help', exact: true }).count(), 0)
    assert.equal(await page.getByRole('button', { name: 'My Requests', exact: true }).count(), 0)
    assert.equal(await page.evaluate(() => { try { window.fixtureFrame.maxMode.attachItem({type:'document',data:{id:'disabled'}});return false } catch { return true } }), true)
    await page.goto(`${fixture.url}?runtime=off`)
    await page.waitForFunction(() => window.fixtureReady)
    assert.match(await page.getByRole('alert').innerText(), /Saved requests can still/)
    await page.evaluate(() => window.controller.showRequests())
    assert.equal(await page.evaluate(() => window.controller.getState().view), 'requests')
    assert.equal(await page.evaluate(() => window.controller.getState().error), null)
    await page.close()
  } finally { await browser.close(); await fixture.close() }
})

test('terminal continuation is explicit and empty, old history remains readable, and linked-ticket forms use fresh scope', { timeout: 90000 }, async () => {
  const fixture = await startPreview()
  const browser = await chromium.launch({ headless: true })
  try {
    for (const [viewport, direct] of [[{ width: 1360, height: 1040 }, false], [{ width: 1360, height: 1040 }, true], [{ width: 390, height: 844 }, true]]) {
      const page = await browser.newPage({ viewport })
      const errors = []
      page.on('pageerror', error => errors.push(error.message))
      await page.goto(direct ? `${fixture.url}?requester=direct` : fixture.url)
      await page.waitForFunction(() => window.fixtureReady && !window.controller.getState().busy)
      await page.evaluate(() => window.controller.openRequestForm())
      assert.equal(await page.getByText('Link my current conversation', { exact: true }).isVisible(), true)
      async function submit(subject) {
        await page.getByLabel('Subject', { exact: true }).fill(subject)
        await page.getByLabel('Details', { exact: true }).fill('Original intake must not be replayed.')
        await page.getByRole('button', { name: 'Submit request', exact: true }).click()
        await page.waitForFunction(subject => window.controller.getState().request?.subject === subject && !window.controller.getState().busy, subject)
        return page.evaluate(() => window.controller.getState().request)
      }
      const first = await submit('First sample request')
      await page.evaluate(() => window.controller.openRequestForm())
      assert.equal(await page.getByText('Link my current conversation', { exact: true }).isVisible(), false)
      const second = await submit('New request from a ticket conversation')
      const requesterRoot = direct ? '/api/desk/requester/v1/spaces/preview-space' : '/api/support'
      const creates = fixture.requests.filter(request => request.method === 'POST' && request.path === `${requesterRoot}/requests`)
      assert.equal(creates.at(-1).body.existingConversationId, undefined)
      assert.notEqual(second.conversation.conversationId, first.conversation.conversationId)
      fixture.conversations.set(second.conversation.conversationId, { controlState: 'CLOSED', controlVersion: 2 })
      await page.waitForFunction(() => !window.fixtureFrame.maxMode.getState().busy)
      await page.evaluate(() => window.fixtureFrame.maxMode.refreshConversation())
      await page.waitForFunction(() => !window.fixtureFrame.maxMode.getState().busy)
      await page.evaluate(id => window.controller.openRequest(id), second.id)
      const frame = page.frameLocator('iframe[data-loomai-workspace-frame]')
      try {
        await page.getByRole('button', { name: 'Continue in a new conversation', exact: true }).waitFor()
      } catch (error) {
        error.message += '\nContinuation state: ' + JSON.stringify(await page.evaluate(() => ({
          chat: window.fixtureFrame.maxMode.getState(), request: window.controller.getState().request,
          error: window.controller.getState().error,
          panel: document.querySelector('[data-loomai-support]')?.shadowRoot?.querySelector('.panel')?.textContent,
        }))) + '\nPage errors: ' + JSON.stringify(errors)
        throw error
      }
      const beforeStatus = fixture.requests.length
      await page.getByRole('button', { name: 'Resolve request', exact: true }).click()
      await page.waitForFunction(() => window.controller.getState().request.status === 'RESOLVED' && !window.controller.getState().busy)
      assert.equal(await page.getByRole('button', { name: 'Continue in a new conversation', exact: true }).count(), 0)
      await page.getByRole('button', { name: 'Reopen request', exact: true }).click()
      await page.waitForFunction(() => window.controller.getState().request.status === 'OPEN' && !window.controller.getState().busy)
      assert.equal(await page.evaluate(() => window.fixtureFrame.maxMode.getState().controlState), 'CLOSED')
      assert.equal(await frame.locator('textarea').isDisabled(), true)
      assert.equal(fixture.requests.slice(beforeStatus).filter(request => /\/(start|messages)$/.test(request.path)).length, 0)
      await page.screenshot({ path: new URL(`../test-results/support-terminal-${viewport.width}.png`, import.meta.url).pathname, fullPage: true })
      assert.deepEqual(await page.evaluate(() => [...document.querySelector('[data-loomai-support]').shadowRoot.querySelectorAll('button,h1,h2,h3')]
        .filter(element => element.getBoundingClientRect().width && element.scrollWidth > element.clientWidth + 2).map(element => element.textContent)), [])
      const beforeContinuation = fixture.requests.length
      await page.getByRole('button', { name: 'Continue in a new conversation', exact: true }).click()
      await page.waitForFunction(oldId => window.controller.getState().request.conversation.conversationId !== oldId && !window.controller.getState().busy, second.conversation.conversationId)
      const continued = await page.evaluate(() => window.controller.getState().request)
      assert.equal(continued.openingMessageRequired, false)
      assert.equal(continued.conversation.delivery, 'NOT_REQUIRED')
      assert.equal(continued.episodes.length, 2)
      assert.equal(await page.evaluate(() => window.fixtureFrame.maxMode.getState().controlState), 'AI_ACTIVE')
      await page.waitForFunction(() => !document.querySelector('iframe').contentDocument.body.textContent.includes('A sample question about the guidance.'))
      assert.equal(await frame.locator('textarea').isDisabled(), false)
      await page.screenshot({ path: new URL(`../test-results/support-continuation-${viewport.width}.png`, import.meta.url).pathname, fullPage: true })
      assert.equal(fixture.requests.slice(beforeContinuation).filter(request => /\/(start|messages)$/.test(request.path)).length, 0)
      const command = fixture.requests.slice(beforeContinuation).find(request => request.path.endsWith('/episodes'))
      assert.deepEqual(Object.keys(command.body), ['expectedVersion'])
      assert.match(command.key, /^support-episode-/)
      assert.equal(command.authorization, 'Bearer fixture-token')
      assert.equal(command.runtimeAuthorization, direct ? 'Bearer fixture-runtime-token' : undefined)
      const runtimePath = `/api/runtime/chat/me/conversations/${continued.conversation.conversationId}`
      const calls = fixture.requests.slice(beforeContinuation)
      const initialized = calls.findIndex(request => request.path === `${runtimePath}/control` && request.status === 200)
      const firstItem = calls.findIndex(request => request.path === runtimePath)
      assert.ok(initialized >= 0 && initialized < firstItem, 'Authorized control initialization must precede the first canonical item read')
      assert.equal(calls[initialized].authorization, 'Bearer fixture-runtime-token')
      assert.ok(calls.filter(request => request.path === runtimePath).length >= 2, 'Both provider verification and engine open must read initialized canonical history')
      assert.ok(calls.filter(request => request.path === runtimePath).every(request => request.status === 200))
      assert.deepEqual(fixture.conversations.get(continued.conversation.conversationId).participantMessages, [])
      await page.getByRole('button', { name: 'Open', exact: true }).first().click()
      await page.waitForFunction(oldId => window.fixtureFrame.maxMode.getState().conversationId === oldId && !window.controller.getState().busy, second.conversation.conversationId)
      assert.equal(await page.evaluate(() => window.fixtureFrame.maxMode.getState().controlState), 'CLOSED')
      assert.equal(await page.getByRole('button', { name: 'Continue in a new conversation', exact: true }).count(), 0)
      fixture.conversations.set(second.conversation.conversationId, { controlState: 'AI_ACTIVE', controlVersion: 3 })
      await page.waitForFunction(() => !window.fixtureFrame.maxMode.getState().busy)
      await page.evaluate(() => window.fixtureFrame.maxMode.refreshConversation())
      assert.equal(await page.evaluate(() => window.fixtureFrame.maxMode.getState().controlState), 'AI_ACTIVE')
      assert.equal(await frame.locator('textarea').isDisabled(), true, 'Archive editability must not depend on historical control state')
      await page.evaluate(() => window.controller.refresh())
      assert.equal(await frame.locator('textarea').isDisabled(), true, 'State updates must not unlock an archived episode of an open ticket')
      await page.waitForFunction(() => !window.fixtureFrame.maxMode.getState().busy && !window.controller.getState().busy)
      await page.evaluate(id => window.controller.openRequest(id), continued.id)
      await page.waitForFunction(id => window.fixtureFrame.maxMode.getState().conversationId === id && !window.fixtureFrame.maxMode.getState().busy && !window.controller.getState().busy, continued.conversation.conversationId)
      assert.equal(await frame.locator('textarea').isDisabled(), false, 'Returning to the latest open episode restores editability')
      assert.deepEqual(errors, [])
      await page.evaluate(() => window.controller.destroy())
      await page.close()
    }
  } finally { await browser.close(); await fixture.close() }
})

test('uninitialized canonical items return 404 and foreign credentials cannot initialize control', async () => {
  const fixture = await startPreview()
  const path = `${fixture.url}/api/runtime/chat/me/conversations/uninitialized-owner-scope`
  try {
    const headers = { Authorization: 'Bearer fixture-runtime-token' }
    assert.equal((await fetch(path, { headers })).status, 404)
    assert.equal((await fetch(`${path}/control`, { headers: { Authorization: 'Bearer foreign-fixture-token' } })).status, 401)
    assert.equal(fixture.conversations.has('uninitialized-owner-scope'), false)
    assert.equal((await fetch(path, { headers })).status, 404)
    assert.equal((await fetch(`${path}/control`, { headers })).status, 200)
    const initialized = await fetch(path, { headers })
    assert.equal(initialized.status, 200)
    assert.deepEqual((await initialized.json()).participantMessages, [])
    assert.ok(fixture.requests.every(request => request.method === 'GET'))
  } finally { await fixture.close() }
})

test('direct continuation fails visibly when its provider omits empty control initialization', { timeout: 45000 }, async () => {
  const fixture = await startPreview(0, { initializeEpisodes: false })
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage()
    await page.goto(`${fixture.url}?requester=direct`)
    await page.waitForFunction(() => window.fixtureReady && !window.controller.getState().busy)
    await page.evaluate(() => window.controller.openRequestForm())
    await page.getByLabel('Subject', { exact: true }).fill('A required initialization contract')
    await page.getByLabel('Details', { exact: true }).fill('No invented message may create the empty episode.')
    await page.getByRole('button', { name: 'Submit request', exact: true }).click()
    await page.waitForFunction(() => window.controller.getState().request && !window.controller.getState().busy && !window.fixtureFrame.maxMode.getState().busy)
    const previous = await page.evaluate(() => window.controller.getState().request.conversation.conversationId)
    fixture.conversations.set(previous, { controlState: 'CLOSED', controlVersion: 2 })
    await page.evaluate(id => window.controller.openRequest(id), await page.evaluate(() => window.controller.getState().request.id))
    await page.getByRole('button', { name: 'Continue in a new conversation', exact: true }).waitFor()
    const before = fixture.requests.length
    await page.getByRole('button', { name: 'Continue in a new conversation', exact: true }).click()
    await page.waitForFunction(() => window.controller.getState().request.episodes.length === 2 && !window.controller.getState().busy && window.controller.getState().error)
    const continued = await page.evaluate(() => window.controller.getState().request)
    const path = `/api/runtime/chat/me/conversations/${continued.conversation.conversationId}`
    const calls = fixture.requests.slice(before)
    assert.equal(fixture.conversations.has(continued.conversation.conversationId), false)
    assert.ok(calls.some(request => request.path === path && request.status === 404))
    assert.equal(calls.some(request => request.path === `${path}/control`), false)
    assert.equal(calls.some(request => /\/(start|messages)$/.test(request.path)), false)
    assert.equal(continued.episodes[0].conversationId, previous)
    assert.equal(await page.evaluate(() => window.fixtureFrame.maxMode.getState().status), 'error')
    assert.equal(await page.evaluate(() => window.fixtureFrame.maxMode.getState().conversationId), null)
    await page.getByRole('alert').filter({ hasText: 'could not be confirmed' }).waitFor()
    await page.evaluate(() => window.controller.destroy())
    await page.close()
  } finally { await browser.close(); await fixture.close() }
})

test('canonical updates observe activity without erasing unread when history is hidden; delivery checks are read-only', { timeout: 45000 }, async () => {
  const fixture = await startPreview()
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1360, height: 1040 } })
    await page.goto(fixture.url)
    await page.waitForFunction(() => window.fixtureReady)
    await page.evaluate(() => window.controller.openRequestForm())
    await page.getByLabel('Subject', { exact: true }).fill('Activity observation')
    await page.getByLabel('Details', { exact: true }).fill('Synthetic activity.')
    await page.getByRole('button', { name: 'Submit request', exact: true }).click()
    await page.waitForFunction(() => window.controller.getState().request && !window.controller.getState().busy)
    const ticket = await page.evaluate(() => window.controller.getState().request)
    await page.evaluate(() => window.controller.setVisible(false))
    const before = fixture.requests.length
    fixture.conversations.set(ticket.conversation.conversationId, { controlState: 'HUMAN_ACTIVE', controlVersion: 3, lastInteractionAt: '2026-01-01T10:15:00Z' })
    await page.evaluate(() => window.fixtureFrame.maxMode.refreshConversation())
    await page.waitForFunction(() => window.controller.getState().request.unread && !window.controller.getState().busy, null, { timeout: 15000 })
    const activity = fixture.requests.slice(before).filter(request => request.path.endsWith('/activity'))
    assert.equal(activity.length, 1)
    assert.ok(activity[0].key)
    assert.equal(fixture.requests.slice(before).filter(request => request.path.endsWith('/seen')).length, 0)
    await page.evaluate(async () => { window.controller.setVisible(true); await window.controller.showRequests() })
    await page.getByText('New update', { exact: false }).waitFor()
    await page.evaluate(() => window.controller.destroy())
    await page.close()

    const uncertain = await browser.newPage()
    let starts = 0
    await uncertain.route('**/api/support/requests/*/start', async route => {
      starts++; await route.fulfill({ status: 503, json: { code: 'TICKET_DELIVERY_UNCERTAIN' } })
    })
    await uncertain.goto(fixture.url)
    await uncertain.waitForFunction(() => window.fixtureReady)
    await uncertain.evaluate(() => window.controller.openRequestForm())
    await uncertain.getByLabel('Subject', { exact: true }).fill('Uncertain saved opening')
    await uncertain.getByLabel('Details', { exact: true }).fill('Do not replay this opening.')
    await uncertain.getByRole('button', { name: 'Submit request', exact: true }).click()
    await uncertain.waitForFunction(() => window.controller.getState().request && !window.controller.getState().busy && window.controller.getState().error)
    assert.equal(starts, 1)
    await uncertain.getByRole('button', { name: 'Check conversation delivery', exact: true }).click()
    await uncertain.waitForFunction(() => !window.controller.getState().busy)
    assert.equal(starts, 1, 'Checking uncertain delivery must not replay /start')
    await uncertain.evaluate(() => window.controller.destroy())
    await uncertain.close()
  } finally { await browser.close(); await fixture.close() }
})
