import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { chromium } from 'playwright'

const origins = (process.env.WORKSPACE_LIVE_ORIGINS || '').split(',').filter(Boolean)
assert(origins.length >= 2, 'Provide two separately bound live workspace origins.')
const output = resolve(process.env.WORKSPACE_HANDOVER_OUTPUT || '/private/tmp/loomai-engage-handover-live-gate')
await mkdir(output, { recursive: true })
const report = { checkedAt: new Date().toISOString(), apps: [], errors: [] }
const browser = await chromium.launch()
const cleanups = []
try {
  for (const [index, origin] of origins.entries()) {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    page.setDefaultTimeout(120_000)
    const app = { origin, checks: {} }
    report.apps.push(app)
    await page.goto(origin)
    await page.locator('textarea').waitFor({ state: 'visible' })
    const queryResponse = page.waitForResponse(response => response.request().method() === 'POST' && /\/conversations\/[^/]+\/messages$/.test(new URL(response.url()).pathname))
    await page.locator('textarea').fill('Which electric vehicles are currently available?')
    await page.locator('textarea').press('Enter')
    const answerResponse = await queryResponse
    const answer = await answerResponse.json()
    assert.equal(answerResponse.status(), 200)
    assert.equal(answer.success, true)
    const conversationId = answer.conversationId
    app.conversationId = conversationId
    const runtimeOrigin = new URL(answerResponse.url()).origin
    const credential = await page.evaluate(() => {
      const key = Object.keys(sessionStorage).find(key => key.startsWith('maxmode_public_runtime_session_credential_v2:'))
      return key ? JSON.parse(sessionStorage.getItem(key)) : null
    })
    let token = credential?.token
    assert(token, 'No anonymous owner token captured')
    await page.getByRole('button', { name: 'Talk to a person', exact: true }).first().click()
    const dialog = page.getByRole('dialog', { name: 'Talk to a person' })
    const admissionResponse = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/handover-requests'))
    await dialog.getByRole('button', { name: 'Continue anonymously', exact: true }).click()
    const admitted = await admissionResponse
    assert.equal(admitted.status(), 202)
    const admission = await admitted.json()
    const admittedHandoverId = admission.conversation?.handoverId
    assert(admittedHandoverId)
    let cleaned = false
    cleanups.push(async () => {
      if (cleaned) return
      const response = await page.request.post(`${runtimeOrigin}/api/chat/me/conversations/${conversationId}/handover-requests/${admittedHandoverId}/cancel`, {
        headers: { Origin: origin, Authorization: `Bearer ${token}`, 'Idempotency-Key': `standalone-gate-cleanup-${conversationId}` }, data: {},
      })
      assert.equal(response.status(), 200, 'Synthetic handover cleanup failed')
    })
    app.checks.directOwnerHandoverRequest = true
    await page.screenshot({ path: resolve(output, `app-${index}-human-pending.png`) })
    const closePanel = page.getByRole('button', { name: 'Close human support panel' })
    if (await closePanel.isVisible()) await closePanel.click()
    const pendingResponse = page.waitForResponse(response => response.request().method() === 'POST' && /\/conversations\/[^/]+\/messages$/.test(new URL(response.url()).pathname))
    await page.locator('textarea').fill('Synthetic verification message for the human channel; no booking or lead requested.')
    await page.locator('textarea').press('Enter')
    const pending = await pendingResponse
    assert.equal(pending.status(), 202)
    const pendingBody = await pending.json()
    assert(!pendingBody.metadata?.timing?.pipelineTotalDurationMs, 'Pending human message ran AI orchestration')
    app.checks.humanPendingMessageDoesNotRunAi = true
    await page.evaluate(() => {
      const key = Object.keys(sessionStorage).find(key => key.startsWith('maxmode_public_runtime_session_credential_v2:'))
      const credential = JSON.parse(sessionStorage.getItem(key))
      credential.expiresAt = new Date(Date.now() + 10_000).toISOString()
      sessionStorage.setItem(key, JSON.stringify(credential))
    })
    const renewalResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/public/chat/session/renew')
    await page.reload()
    const renewal = await renewalResponse
    assert.equal(renewal.status(), 200)
    assert.equal((await renewal.json()).sessionId, credential.sessionId)
    await page.locator('textarea').waitFor({ state: 'visible' })
    token = await page.evaluate(() => {
      const key = Object.keys(sessionStorage).find(key => key.startsWith('maxmode_public_runtime_session_credential_v2:'))
      return JSON.parse(sessionStorage.getItem(key)).token
    })
    const headers = { Origin: origin, Authorization: `Bearer ${token}` }
    const control = await page.request.get(`${runtimeOrigin}/api/chat/me/conversations/${conversationId}/control`, { headers })
    assert.equal(control.status(), 200)
    const view = await control.json()
    assert.equal(view.conversationId, conversationId)
    assert.equal(view.controlState, 'HUMAN_PENDING')
    app.checks.realAnonymousRenewalPreservedHandoverAndConversation = true
    const handoverId = view.handoverId
    assert(handoverId, 'Control readback omitted handover identity')
    await page.getByRole('button', { name: 'Connecting', exact: true }).click()
    const cancellationResponse = page.waitForResponse(response => new URL(response.url()).pathname.endsWith(`/handover-requests/${handoverId}/cancel`))
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel request', exact: true }).click()
    const cancelled = await cancellationResponse
    assert.equal(cancelled.status(), 200)
    const restored = await cancelled.json()
    assert.equal(restored.controlState, 'AI_ACTIVE')
    cleaned = true
    app.checks.explicitOwnerCancellationRestoredAi = true
    await page.close()
  }
} catch (error) {
  report.errors.push(error.message)
  process.exitCode = 1
} finally {
  for (const cleanup of cleanups) {
    try { await cleanup() } catch (error) { report.errors.push(error.message); process.exitCode = 1 }
  }
  await browser.close()
  await writeFile(resolve(output, 'report.json'), `${JSON.stringify(report, null, 2)}\n`)
  console.log(JSON.stringify(report, null, 2))
}
