import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import {
  clickWithPointer,
  createRecording,
  hidePointer,
  installPresentationLayer,
  pointAt,
  showStage,
} from './demo-video-support.mjs'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(scriptDir, '../../..')
const outputPath = resolve(
  process.argv[2]
    || resolve(repoRoot, 'Final_Documentation/Demo_Videos/LOOMAI_DEALERSHIP_CONVERSATION_HANDOVER_DEMO.mp4'),
)
const dealershipUrl = process.env.DEALERSHIP_DEMO_URL?.trim() || 'https://loomai.pro/demos/dealership-ai'
const staffUrl = `${dealershipUrl.replace(/\/$/, '')}/staff`
const staffUsername = process.env.DEALERSHIP_DEMO_STAFF_USERNAME?.trim()
const staffPassword = process.env.DEALERSHIP_DEMO_STAFF_PASSWORD?.trim()

if (!staffUsername || !staffPassword) {
  throw new Error('DEALERSHIP_DEMO_STAFF_USERNAME and DEALERSHIP_DEMO_STAFF_PASSWORD are required.')
}

const recording = await createRecording(outputPath)
const { context, page, evidenceDir } = recording

try {
  await context.route('https://loomai.pro/**', async (route) => {
    const response = await route.fetch()
    const headers = { ...response.headers() }
    delete headers['content-security-policy']
    delete headers['x-frame-options']
    await route.fulfill({ response, headers })
  })

  await page.goto('https://loomai.pro/', { waitUntil: 'domcontentloaded' })
  await page.setContent(`<!doctype html>
    <html><head><meta charset="utf-8"><title>LoomAI dealership handover proof</title>
    <style>
      *{box-sizing:border-box}body{margin:0;background:#eef2f5;color:#102a37;font-family:Inter,system-ui,sans-serif;overflow:hidden}
      .demo-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px;height:100vh;padding:96px 10px 10px}
      .demo-pane{display:grid;grid-template-rows:38px 1fr;min-width:0;overflow:hidden;border:1px solid #bccbd1;border-radius:8px;background:#fff;box-shadow:0 10px 28px rgba(24,44,54,.13)}
      .demo-pane header{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #dce5e8;padding:0 13px;font-size:12px;font-weight:800}
      .demo-pane header span{color:#60747e;font-size:10px;font-weight:650}iframe{border:0;width:100%;height:100%;background:#fff}
    </style></head><body>
      <main class="demo-grid">
        <section class="demo-pane"><header>Customer AI Workspace <span>deployment-local visitor session</span></header><iframe name="visitor" src="${dealershipUrl}"></iframe></section>
        <section class="demo-pane"><header>Dealership Staff Inbox <span>server-protected operator session</span></header><iframe name="staff" src="${staffUrl}"></iframe></section>
      </main>
    </body></html>`, { waitUntil: 'domcontentloaded', timeout: 90_000 })

  await installPresentationLayer(page)
  const visitor = await waitForNamedFrame(page, 'visitor')
  const staff = await waitForNamedFrame(page, 'staff')

  await staff.locator('[data-login-form]').waitFor({ timeout: 30_000 })
  await visitor.waitForFunction(() => Boolean(window.MaxMode?.open), null, { timeout: 45_000 })
  const staffSecureRuntime = await staff.evaluate(() => (
    window.isSecureContext && typeof window.crypto?.randomUUID === 'function'
  ))
  if (!staffSecureRuntime) throw new Error('The embedded staff proof did not retain a secure browser context.')

  await showStage(
    page,
    'LoomAI dealership conversation handover',
    'A live customer AI Workspace and the dealership staff inbox are shown together. The browser never receives the operator credential.',
    4200,
  )
  await page.screenshot({ path: resolve(evidenceDir, '01-live-surfaces-ready.png') })

  await showStage(
    page,
    '1. Staff access stays behind the dealership backend',
    'The protected staff session calls a server-side BFF. Its conversation-channel operator key is never exposed to the browser.',
  )
  await staff.locator('input[name="username"]').fill(staffUsername)
  await staff.locator('input[name="password"]').fill(staffPassword)
  await clickWithPointer(page, staff.locator('[data-login-form] button[type="submit"]'), 'Sign in as dealership staff')
  await staff.locator('[data-staff-workspace]:not([hidden])').waitFor({ timeout: 30_000 })
  await staff.locator('[data-human-inbox]:not([hidden])').waitFor({ timeout: 30_000 })
  await staff.locator('[data-human-inbox]').scrollIntoViewIfNeeded()
  const existingHandoverIds = new Set(await staff.locator('[data-human-handover-id]').evaluateAll((items) => (
    items.map((item) => item.getAttribute('data-human-handover-id')).filter(Boolean)
  )))
  await page.screenshot({ path: resolve(evidenceDir, '02-protected-staff-inbox.png') })

  await showStage(
    page,
    '2. The customer starts with grounded vehicle discovery',
    'The same AI Workspace searches the dealership deployment first, so the eventual handover carries a useful conversation rather than an empty support request.',
  )
  await visitor.evaluate(() => window.MaxMode.open())
  const aiComposer = activeComposer(visitor)
  await aiComposer.waitFor({ timeout: 30_000 })
  await submitAiTurn(
    page,
    visitor,
    aiComposer,
    'Find electric cars priced below £35,000.',
    'Search current electric stock',
  )
  await page.screenshot({ path: resolve(evidenceDir, '03-grounded-vehicle-search.png') })

  await showStage(
    page,
    '3. The customer asks AI to transfer the conversation',
    'The model selects the deployment-owned request_conversation_handover action. There is no phrase parser and the dealership page does not call an operator API.',
  )
  await submitAiTurn(
    page,
    visitor,
    aiComposer,
    'Please transfer this conversation to a human member of the dealership team now.',
    'Ask AI for a human teammate',
    { expectAiMessage: false },
  )
  await visitor.getByText('Connecting to a person', { exact: true }).first().waitFor({ timeout: 45_000 })
  await page.screenshot({ path: resolve(evidenceDir, '04-ai-requested-handover.png') })

  await showStage(
    page,
    '4. The request arrives through the installed conversation channel',
    'The deployment persists the transition, pauses AI and dispatches the conversation to the Basic LoomAI Inbox provider.',
  )
  const handoverId = await waitForNewHandover(staff, existingHandoverIds)
  const queueItem = staff.locator(`[data-human-handover-id="${handoverId}"]`)
  await clickWithPointer(page, queueItem, 'Open the waiting customer')
  await staff.locator('[data-human-state]').getByText('Waiting', { exact: true }).waitFor({ timeout: 20_000 })
  await page.screenshot({ path: resolve(evidenceDir, '05-request-in-staff-inbox.png') })

  await showStage(
    page,
    '5. Dealership staff claims and replies',
    'Claim is an explicit authorized command. AI remains blocked while the human operator owns the conversation.',
  )
  await runStaffMutation(
    page,
    staff,
    staff.locator('[data-human-claim]'),
    'Claim the conversation',
    /\/api\/staff\/human-conversations\/[^/]+\/claim$/,
  )
  await staff.locator('[data-human-state]').getByText('Human active', { exact: true }).waitFor({ timeout: 60_000 })
  const humanReply = staff.locator('[data-human-reply-form] textarea[name="reply"]')
  await pointAt(page, humanReply, 'Write a dealership reply')
  await humanReply.fill('Hello Alex, this is Sara at Northfield Motor House. I have joined your conversation and can help directly.')
  await runStaffMutation(
    page,
    staff,
    staff.locator('[data-human-reply-form] button[type="submit"]'),
    'Send the human reply',
    /\/api\/staff\/human-conversations\/[^/]+\/messages$/,
  )
  await visitor.getByText('Hello Alex, this is Sara', { exact: false }).waitFor({ timeout: 30_000 })
  await page.screenshot({ path: resolve(evidenceDir, '06-human-reply-live.png') })

  await showStage(
    page,
    '6. Both sides continue in the same durable conversation',
    'Customer messages now travel through the human channel. No model response or AI action is permitted during HUMAN_ACTIVE.',
  )
  const visitorComposer = visitor.getByPlaceholder('Message human support...')
  await visitorComposer.fill('Thanks Sara. Please return me to the AI after this message.')
  await pointAt(page, visitorComposer, 'Send to the human operator')
  await visitorComposer.press('Enter')
  await staff.getByText('Please return me to the AI', { exact: false }).waitFor({ timeout: 30_000 })

  await showStage(
    page,
    '7. Staff explicitly returns response authority to AI',
    'The provider callback is validated by the deployment. The customer receives the committed state change through the owner-scoped event stream.',
  )
  await humanReply.fill('Of course. I am returning this conversation to the AI assistant now.')
  await runStaffMutation(
    page,
    staff,
    staff.locator('[data-human-reply-form] button[type="submit"]'),
    'Send the final human reply',
    /\/api\/staff\/human-conversations\/[^/]+\/messages$/,
  )
  await visitor.getByText('I am returning this conversation', { exact: false }).waitFor({ timeout: 30_000 })
  await runStaffMutation(
    page,
    staff,
    staff.locator('[data-human-return]'),
    'Return the conversation to AI',
    /\/api\/staff\/human-conversations\/[^/]+\/return-to-ai$/,
  )
  await visitor.getByRole('button', { name: 'Talk to a person' }).first().waitFor({ timeout: 30_000 })

  await showStage(
    page,
    '8. The same conversation continues with AI',
    'After the committed return, the deployment permits model orchestration again and keeps the earlier vehicle and human context in one conversation.',
  )
  await submitAiTurn(
    page,
    visitor,
    activeComposer(visitor),
    'I am back with AI. Search current vehicle stock again for electric cars priced below GBP 35,000.',
    'Verify AI is active again',
  )
  await page.screenshot({ path: resolve(evidenceDir, '07-ai-authority-restored.png') })

  await showStage(
    page,
    '9. The explicit UI control remains available',
    'The customer can bypass model selection and request a person through the generic Workspace button. Both paths commit the same governed transition.',
  )
  await staff.locator('[data-refresh-human]').click()
  await staff.waitForTimeout(500)
  const beforeButtonHandoverIds = new Set(await staff.locator('[data-human-handover-id]').evaluateAll((items) => (
    items.map((item) => item.getAttribute('data-human-handover-id')).filter(Boolean)
  )))
  const talkToPerson = visitor.getByRole('button', { name: 'Talk to a person' }).first()
  await clickWithPointer(page, talkToPerson, 'Use the explicit human-support control')
  const dialog = visitor.getByRole('dialog')
  await dialog.waitFor({ timeout: 15_000 })
  const anonymous = dialog.getByRole('button', { name: 'Continue anonymously' })
  await pointAt(page, anonymous, 'Continue without sharing contact details')
  const handoverResponsePromise = page.waitForResponse((response) => (
    response.request().method() === 'POST'
      && /\/api\/chat\/me\/conversations\/[^/]+\/handover-requests$/.test(new URL(response.url()).pathname)
  ), { timeout: 30_000 })
  await anonymous.evaluate((button) => button.click())
  const handoverResponse = await handoverResponsePromise
  const handoverAdmission = await assertOkResponse(handoverResponse, 'Dealership UI handover request')
  const secondHandoverId = String(handoverAdmission?.conversation?.handoverId || '')
  if (handoverAdmission?.outcome !== 'HANDOVER_REQUESTED' || !secondHandoverId) {
    throw new Error(`Dealership UI did not create a new handover: ${JSON.stringify(handoverAdmission)}`)
  }
  if (secondHandoverId === handoverId || beforeButtonHandoverIds.has(secondHandoverId)) {
    throw new Error(`Dealership UI replayed an earlier handover instead of creating a new episode: ${secondHandoverId}`)
  }
  await visitor.getByText('Connecting to a person', { exact: true }).first().waitFor({ timeout: 30_000 })
  await waitForHandover(staff, secondHandoverId)
  const secondQueueItem = staff.locator(`[data-human-handover-id="${secondHandoverId}"]`)
  await clickWithPointer(page, secondQueueItem, 'See the UI-requested handover in the inbox')
  await staff.locator('[data-human-state]').getByText('Waiting', { exact: true }).waitFor({ timeout: 20_000 })
  await hidePointer(page)
  await page.screenshot({ path: resolve(evidenceDir, '08-ui-button-handover.png') })

  await showStage(
    page,
    'Dealership proof passed',
    'Grounded search · AI-selected action · live human exchange · explicit AI return · direct UI handover',
    5600,
  )

  await coverForCleanup(page)
  await runStaffMutation(page, staff, staff.locator('[data-human-claim]'), 'Claim cleanup conversation', /\/api\/staff\/human-conversations\/[^/]+\/claim$/)
  await staff.locator('[data-human-state]').getByText('Human active', { exact: true }).waitFor({ timeout: 30_000 })
  await runStaffMutation(page, staff, staff.locator('[data-human-return]'), 'Return cleanup conversation to AI', /\/api\/staff\/human-conversations\/[^/]+\/return-to-ai$/)

  const result = await recording.finish()
  process.stdout.write(`${JSON.stringify(result)}\n`)
} catch (error) {
  await page.screenshot({ path: resolve(evidenceDir, 'FAILED.png'), fullPage: true }).catch(() => undefined)
  await recording.abort()
  throw error
}

async function submitAiTurn(page, visitor, composer, message, label, options = {}) {
  await pointAt(page, composer, label)
  await composer.fill(message)
  const responsePromise = page.waitForResponse((response) => (
    response.request().method() === 'POST'
      && isConversationTurnPath(new URL(response.url()).pathname)
  ), { timeout: 90_000 })
  await composer.press('Enter')
  const response = await responsePromise
  const payload = await assertOkResponse(response, label)
  await visitor.getByText(message, { exact: true }).waitFor({ timeout: 30_000 })
  if (options.expectAiMessage !== false) {
    const answerFragment = visibleAnswerFragment(payload)
    if (!answerFragment) throw new Error(`${label} returned no visible answer text.`)
    await visitor.getByText(answerFragment, { exact: false }).first().waitFor({ timeout: 60_000 })
  }
  return response
}

function activeComposer(frame) {
  return frame.locator('textarea:visible:not([disabled])').last()
}

function isConversationTurnPath(pathname) {
  return /\/api\/chat\/me\/query$/.test(pathname)
    || /\/api\/chat\/me\/conversations\/[^/]+\/messages$/.test(pathname)
}

async function waitForNamedFrame(page, name, timeout = 45_000) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    const frame = page.frame({ name })
    if (frame) return frame
    await page.waitForTimeout(150)
  }
  throw new Error(`Dealership demo frame ${name} did not initialize.`)
}

async function assertOkResponse(response, label) {
  const body = await response.text().catch(() => '')
  if (!response.ok()) throw new Error(`${label} failed (${response.status()}): ${body}`)
  let parsed
  try { parsed = JSON.parse(body) } catch { return undefined }
  if (parsed?.success === false) {
    throw new Error(`${label} returned an unsuccessful runtime result: ${body}`)
  }
  return parsed
}

function visibleAnswerFragment(payload) {
  const answer = String(payload?.answer || payload?.safeSummary || '')
  const firstLine = answer
    .split(/\r?\n/)
    .map((line) => line.replace(/[*_`#]/g, '').trim())
    .find(Boolean)
  return firstLine?.slice(0, 80) || ''
}

async function coverForCleanup(page) {
  await page.evaluate(() => {
    const cover = document.createElement('div')
    cover.style.cssText = 'position:fixed;inset:0;z-index:2147483646;background:#eef2f5'
    document.body.append(cover)
  })
}

async function waitForNewHandover(staff, existingIds) {
  const deadline = Date.now() + 45_000
  while (Date.now() < deadline) {
    await staff.locator('[data-refresh-human]').click()
    await staff.waitForTimeout(900)
    const ids = await staff.locator('[data-human-handover-id]').evaluateAll((items) => (
      items.map((item) => item.getAttribute('data-human-handover-id')).filter(Boolean)
    ))
    const created = ids.find((id) => !existingIds.has(id))
    if (created) return created
  }
  throw new Error('The new dealership handover did not reach the staff inbox.')
}

async function waitForHandover(staff, handoverId) {
  const deadline = Date.now() + 45_000
  while (Date.now() < deadline) {
    await staff.locator('[data-refresh-human]').click()
    await staff.waitForTimeout(900)
    if (await staff.locator(`[data-human-handover-id="${handoverId}"]`).count() > 0) return
  }
  throw new Error(`Dealership handover ${handoverId} did not reach the staff inbox.`)
}

async function runStaffMutation(page, staff, locator, label, pathPattern) {
  await pointAt(page, locator, label, 650)
  const responsePromise = page.waitForResponse((response) => (
    response.request().method() === 'POST' && pathPattern.test(new URL(response.url()).pathname)
  ), { timeout: 20_000 })
  await locator.evaluate((button) => button.click())
  const response = await responsePromise
  if (!response.ok()) {
    const body = await response.text().catch(() => '')
    throw new Error(`${label} failed (${response.status()}): ${body}`)
  }
  await staff.waitForTimeout(300)
  return response
}
