import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'
import { spawnSync } from 'node:child_process'

assert.equal(process.argv[2], '--run', 'Explicit --run is required: this records three real paid AI turns and creates one disposable draft.')
const root = resolve(process.argv[3] ?? '')
assert.ok(process.argv[3] && process.env.AUTHORING_RECORDING_AUTH_REGISTRY, 'Provide a new private output directory and external auth registry.')
await mkdir(root, { mode: 0o700 })
const ui = process.env.PLATFORM_UI_BASE_URL ?? 'https://console.loomai.pro'
const api = process.env.PLATFORM_BASE_URL ?? 'https://api.loomai.pro'
const registry = JSON.parse(await readFile(process.env.AUTHORING_RECORDING_AUTH_REGISTRY, 'utf8'))
const credential = registry.credentials[Number(process.env.AUTHORING_RECORDING_AUTH_INDEX ?? 1)]
assert.ok(credential?.email && credential?.password)
const { chromium } = createRequire(new URL('../../../Platfrom/ui/package.json', import.meta.url))('playwright')
const browser = await chromium.launch({ headless: true })
const auth = await browser.newContext({ viewport: { width: 1600, height: 1000 } })
const authPage = await auth.newPage()
const save = (name, value) => writeFile(resolve(root, `${name}.private.json`), JSON.stringify(value, null, 2), { mode: 0o600 })
let token, brain, context, page, video, started
const sessions = new Set(), requests = [], errors = [], chapters = [], turns = []
const take = { status: 'IN_PROGRESS', ui, scope: 'Real production UI and AI responses. New configuration-only draft. No publish, apply, indexing, assignment or business API execution.', chapters, turns, errors }
async function request(path, { method = 'GET', body, statuses = [200] } = {}) {
  const response = await fetch(api + path, { method, headers: { Authorization: `Bearer ${token}`, Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30000) })
  const text = await response.text(), value = text ? JSON.parse(text) : null
  assert.ok(statuses.includes(response.status), `${method} ${path}: ${response.status}`)
  return value
}
const pause = ms => page.waitForTimeout(ms)
const panel = () => page.getByRole('complementary', { name: 'Build with AI' })
async function click(locator) {
  await locator.scrollIntoViewIfNeeded()
  const box = await locator.boundingBox(); assert.ok(box)
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 16 })
  await pause(350); await locator.click(); await pause(550)
}
async function chapter(title, detail, narration) {
  const entry = { seconds: Number(((Date.now() - started) / 1000).toFixed(3)), title, detail, narration }
  chapters.push(entry); console.log(JSON.stringify({ chapter: chapters.length, title }))
  await save('progress', take)
}
async function ready() {
  await panel().getByRole('textbox', { name: 'Message', exact: true }).waitFor()
  await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
}
async function scrollConversation(top = true) {
  await panel().getByRole('log', { name: 'Conversation messages' }).evaluate((node, top) => { node.scrollTop = top ? 0 : node.scrollHeight }, top)
  await pause(500)
}
async function submit(label) {
  const response = page.waitForResponse(response => response.request().method() === 'POST' && /\/configuration-assistant\/sessions\/[^/]+\/turns$/.test(new URL(response.url()).pathname))
  await click(panel().getByRole('button', { name: label, exact: true }))
  let session = await (await response).json(); sessions.add(session.id)
  const id = session.activeTurnId ?? session.turns.at(-1).id, deadline = Date.now() + 150000
  while (session.activeTurnId && Date.now() < deadline) { await pause(900); session = await request(`/api/configuration-assistant/sessions/${session.id}`) }
  assert.equal(session.activeTurnId, null)
  const turn = session.turns.find(turn => turn.id === id); turns.push(turn); await save('turns', turns)
  assert.equal(turn.status, 'SAVED', 'Preserve failed takes rather than silently retrying a model decision.')
  assert.equal(turn.findings.errorCount, 0)
  await ready(); await scrollConversation()
  return { session, turn }
}
try {
  await authPage.goto(ui + '/sign-in', { waitUntil: 'domcontentloaded' })
  await authPage.locator('input[type=email]').fill(credential.email)
  await authPage.locator('input[type=password]').fill(credential.password)
  await authPage.locator('button[type=submit]').click()
  await authPage.waitForURL(url => !url.pathname.includes('sign-in'), { timeout: 45000 })
  token = await authPage.evaluate(() => JSON.parse(localStorage.getItem('loomai-console-supabase')).access_token)
  assert.equal((await request('/api/configuration-assistant/status')).ready, true)
  brain = await request('/api/deployments', { method: 'POST', statuses: [201], body: { name: 'Northfield API - Build with AI', environment: 'staging', templateId: 'custom-start-from-scratch', behaviorType: 'CONVERSATIONAL', vectorProvisioningMode: 'LOCAL_MANAGED' } })
  take.brainId = brain.id; await save('brain', brain)
  const initial = await request(`/api/deployments/${brain.id}/draft`); await save('initial-draft', initial)
  const storageState = await auth.storageState()
  context = await browser.newContext({ storageState, viewport: { width: 1600, height: 1000 }, recordVideo: { dir: resolve(root, 'raw'), size: { width: 1600, height: 1000 } } })
  await context.addInitScript(() => {
    const mount = () => {
      const style = document.createElement('style')
      style.textContent = '#recording-pointer{position:fixed;left:0;top:0;width:22px;height:30px;background:white;clip-path:polygon(8% 0,8% 86%,33% 65%,49% 100%,65% 92%,49% 60%,84% 60%);filter:drop-shadow(0 1px 2px black);pointer-events:none;z-index:2147483647;transform:translate(-50px,-50px)}#recording-ring{position:fixed;left:0;top:0;width:38px;height:38px;margin:-19px;border:3px solid #5ce5c8;border-radius:50%;opacity:0;pointer-events:none;z-index:2147483646}'
      document.head.append(style)
      const pointer = document.createElement('i'), ring = document.createElement('i'); pointer.id = 'recording-pointer'; ring.id = 'recording-ring'
      document.body.append(pointer, ring)
      document.addEventListener('pointermove', event => { pointer.style.transform = `translate(${event.clientX}px,${event.clientY}px)` }, true)
      document.addEventListener('pointerdown', event => { ring.style.transform = `translate(${event.clientX}px,${event.clientY}px)`; ring.animate([{ opacity: .9, scale: .6 }, { opacity: 0, scale: 1.5 }], { duration: 650 }) }, true)
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount, { once: true }); else mount()
  })
  started = Date.now(); take.startedAt = new Date(started).toISOString()
  page = await context.newPage(); page.setDefaultTimeout(30000); video = page.video()
  page.on('pageerror', error => errors.push(error.message))
  page.on('request', request => { if (request.method() === 'POST' && /\/configuration-assistant\/sessions\/[^/]+\/turns$/.test(new URL(request.url()).pathname)) requests.push(request.postDataJSON()) })
  page.on('response', async response => { if (response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/configuration-assistant/sessions' && response.ok()) { const session = await response.json().catch(() => null); if (session?.id) sessions.add(session.id) } })
  await page.goto(`${ui}/smart-brains/${brain.id}/capabilities`, { waitUntil: 'domcontentloaded' })
  await page.getByRole('tab', { name: 'Connected (0)', exact: true }).waitFor()
  await chapter('01  Start with an empty Smart Brain', 'A fresh configuration draft. Prepared before recording; no runtime is deployed.', 'This is the live LoomAI Console. We begin with a new, empty Smart Brain draft, created through the normal API before recording. There are no capabilities yet and no runtime is deployed. We will configure it using documentation, without writing JSON or a technical prompt.')
  await pause(6500)
  await click(page.getByRole('button', { name: 'Build with AI', exact: true })); await ready()
  await chapter('02  The assistant knows the page', 'Page-specific help, a clear preparation action, and optional bounded corrections.', 'Build with AI already knows we are on the Actions page and which Brain we are editing. The guidance explains the task. Preparation changes only the draft. Turn options show the bounded correction policy, with up to two corrections when actual checks identify errors. Opening the panel does not call the model.')
  await pause(3500)
  await click(panel().getByRole('button', { name: 'Turn options', exact: true }))
  assert.equal(await panel().getByRole('checkbox', { name: 'Let AI fix validation errors' }).isChecked(), true)
  await pause(4000); await click(panel().getByRole('button', { name: 'Turn options', exact: true }))
  await chapter('03  Share the API document', 'Upload a file, paste text or provide an allowed public documentation link.', 'Add the API documentation. This YAML file describes our fictional dealership demo, not the Auto Trader partner API. It covers stock search, vehicle details and comparison. The source can be reviewed and specific operations can be selected. Importing the document does not run its endpoints or trigger generation.')
  await click(panel().getByRole('button', { name: 'Add source', exact: true }))
  const sourceDialog = page.getByRole('dialog', { name: 'Add documentation' })
  const chooser = page.waitForEvent('filechooser'); await click(sourceDialog.getByRole('button', { name: 'Choose file', exact: true }))
  await (await chooser).setFiles(new URL('./dealership-api.yaml', import.meta.url).pathname)
  await pause(2300); await click(sourceDialog.getByRole('button', { name: 'Share source', exact: true })); await sourceDialog.waitFor({ state: 'hidden' })
  await ready(); assert.equal(requests.length, 0)
  await click(panel().getByRole('region', { name: 'Selected documents' }).getByRole('button', { name: 'dealership-api.yaml', exact: true }))
  await page.getByRole('dialog').getByText('Review dealership-api.yaml', { exact: true }).waitFor(); await pause(4500)
  await click(page.getByRole('button', { name: 'Close source review', exact: true }))
  await pause(1800)
  await chapter('04  Prepare actions without a message', 'Documentation selected. Empty message box. One explicit Prepare actions command.', 'There is no required prompt. The message box is empty. Click Prepare actions and the Platform supplies the task, current configuration, documentation and supported contracts automatically. This is a real model request. The selected bounded policy can feed actual validation findings back to the model; it does not publish the result.')
  assert.equal(await panel().getByRole('textbox', { name: 'Message', exact: true }).inputValue(), '')
  await pause(2800); const actions = await submit('Prepare actions')
  assert.equal(requests[0].command, 'PREPARE'); assert.equal(requests[0].query, '')
  const prepared = await request(`/api/deployments/${brain.id}/draft`); await save('prepared-draft', prepared)
  assert.equal(prepared.actionsConfig.actions.length, 3)
  const routes = Object.entries(prepared.routingConfig.actions)
  assert.deepEqual(routes.map(([, route]) => route.path).sort(), ['/api/public/vehicles', '/api/public/vehicles/by-reference', '/api/public/vehicles/compare'])
  for (const action of prepared.actionsConfig.actions) assert.equal(action.accessMode, 'READ')
  for (const field of ['entityConfig', 'providerConfig', 'securityConfig', 'promptConfig', 'knowledgeSourceConfig', 'shellConfig', 'marketplaceDatasetConfig', 'behaviorConfig']) assert.deepEqual(prepared[field], initial[field])
  await page.getByRole('tab', { name: 'Connected (3)', exact: true }).waitFor()
  await chapter('05  Review real draft results', 'Three READ actions and routes appear in the ordinary editor. Findings remain visible.', `The normal editor now contains three read-only actions and their documented routes. The actual configuration check has zero errors. Existing setup warnings stay visible. This preparation used ${actions.turn.attempts.length === 1 ? 'one model attempt' : `${actions.turn.attempts.length} model attempts`}. Saved does not mean deployed or connected to a real business system. You can inspect and refine the same draft using the normal forms.`)
  await pause(5500)
  const stockName = routes.find(([, route]) => route.path === '/api/public/vehicles')[0]
  const stockLabel = prepared.actionsConfig.actions.find(action => action.name === stockName).displayName
  await click(page.getByRole('list', { name: 'Connected capabilities' }).getByRole('link').filter({ hasText: stockLabel }).first())
  await pause(4500)
  await chapter('06  Refine with a short follow-up', 'A brief outcome request is enough. Existing routes and other actions are retained.', 'For a small change, use an ordinary short follow-up. Rename the stock search to Find current stock. The assistant already has the document and the configuration, so there is no need to repeat the API details or explain the schema. It saves the requested draft change and keeps the existing routing.')
  await click(panel().getByRole('textbox', { name: 'Message', exact: true }))
  await panel().getByRole('textbox', { name: 'Message', exact: true }).pressSequentially('Rename the stock search to Find current stock.', { delay: 55 })
  await pause(1500); await submit('Send')
  await panel().getByRole('article', { name: 'Assistant', exact: true }).nth(1).getByText('SAVED', { exact: true }).waitFor()
  await scrollConversation(false)
  const renamed = await request(`/api/deployments/${brain.id}/draft`)
  assert.equal(renamed.actionsConfig.actions.find(action => action.name === stockName).displayName, 'Find current stock')
  assert.deepEqual(renamed.routingConfig, prepared.routingConfig)
  await pause(4000)
  await chapter('07  Undo keeps the user in control', 'Restore the saved draft change. Nothing live is changed.', 'The saved change can be undone. This restores the previous action label through the same draft service. The conversation keeps an Undone receipt so the result is not confused with the current saved configuration. No published version or running application is changed.')
  await click(panel().getByRole('button', { name: 'Undo saved change', exact: true }))
  await panel().getByText('UNDONE', { exact: true }).waitFor(); await ready(); await scrollConversation(false)
  assert.deepEqual((await request(`/api/deployments/${brain.id}/draft`)).actionsConfig, prepared.actionsConfig)
  await pause(5000)
  await click(page.getByRole('link', { name: 'Responses', exact: true }))
  await click(page.getByRole('button', { name: 'Build with AI', exact: true })); await ready()
  await chapter('08  Guidance follows the task', 'The Responses page has its own command. Reuse this Brain\'s documents explicitly.', 'On the Responses page, the assistant offers different guidance and a Prepare responses command. The selected Brain stays the same. Brain documents lets us explicitly reuse the API file already shared on another configuration page, rather than uploading it again. Merely navigating or reusing a document does not start another paid request.')
  await pause(2500); await click(panel().getByRole('button', { name: 'Brain documents', exact: true }))
  const reuse = page.getByRole('dialog', { name: 'Add documentation' })
  await click(reuse.getByRole('combobox', { name: 'Available source' })); await click(page.getByRole('option').filter({ hasText: 'dealership-api.yaml' }).first())
  await pause(1700); await click(reuse.getByRole('button', { name: 'Reuse source', exact: true })); await reuse.waitFor({ state: 'hidden' }); await ready()
  assert.equal(requests.length, 2)
  await chapter('09  Configure from plain-language guidance', 'Paste a short style guide and an optional focus hint. No technical instruction required.', 'Documentation is not limited to an API schema. Paste a short response guide asking for a warm tone, brief grounded answers, and useful contextual next steps. Here we add a short focus hint to identify that guide, then choose Prepare responses. No JSON or configuration syntax is required. The draft still preserves the actions, routes and security settings.')
  await click(panel().getByRole('button', { name: 'Add source', exact: true }))
  const paste = page.getByRole('dialog', { name: 'Add documentation' })
  await click(paste.getByRole('tab', { name: 'Paste', exact: true }))
  await paste.getByLabel('Source title').fill('Northfield response guide')
  await paste.getByLabel('Documentation', { exact: true }).fill('Use a warm, welcoming tone. Keep answers brief and grounded in our approved information. Offer useful contextual next steps when appropriate. Preserve existing actions, routes and security settings.')
  await pause(4000); await click(paste.getByRole('button', { name: 'Share source', exact: true })); await paste.waitFor({ state: 'hidden' }); await ready()
  assert.equal(requests.length, 2)
  assert.equal(await panel().getByRole('textbox', { name: 'Message', exact: true }).inputValue(), '')
  await click(panel().getByRole('textbox', { name: 'Message', exact: true }))
  await panel().getByRole('textbox', { name: 'Message', exact: true }).pressSequentially('Use the Northfield response guide.', { delay: 65 })
  await pause(2200); await submit('Prepare responses')
  assert.equal(requests[2].command, 'PREPARE'); assert.equal(requests[2].query, 'Use the Northfield response guide.')
  const final = await request(`/api/deployments/${brain.id}/draft`); await save('final-draft', final)
  assert.deepEqual(final.actionsConfig, prepared.actionsConfig); assert.deepEqual(final.routingConfig, prepared.routingConfig)
  assert.deepEqual(final.securityConfig, initial.securityConfig); assert.deepEqual(final.providerConfig, initial.providerConfig)
  assert.notDeepEqual(final.promptConfig, initial.promptConfig)
  await pause(5500)
  await click(panel().getByRole('button', { name: 'Close assistant', exact: true })); await pause(5000)
  await chapter('10  Review before going live', 'Draft configuration is ready for review. Publishing, applying and connecting real services remain explicit.', 'The response controls now reflect the saved guidance. The three actions and their routes are still intact. Updates is the existing place to review and publish when you are ready. This demonstration made three explicit AI requests, not a background series of hidden calls. We have not published, deployed, indexed, assigned an app or executed a business API. Real connections and the remaining setup warnings must still be verified before going live.')
  await click(page.getByRole('link', { name: 'Updates', exact: true })); await pause(6500)
  assert.equal((await request(`/api/deployments/${brain.id}/versions`)).length, 0)
  assert.equal((await request(`/api/deployments/${brain.id}/releases`)).length, 0)
  assert.equal(requests.length, 3); assert.equal(errors.length, 0)
  await page.screenshot({ path: resolve(root, 'final-review.png') })
  take.status = 'PASS'; take.explicitTurns = requests.length; take.sessionIds = [...sessions]
} catch (error) {
  take.status = 'FAIL'; take.failure = error.message
  if (page) await page.screenshot({ path: resolve(root, 'failure.png') }).catch(() => {})
} finally {
  if (context) { take.video = await video.path(); await context.close(); context = null }
  if (take.video) {
    const result = spawnSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', take.video], { encoding: 'utf8' })
    assert.equal(result.status, 0); take.durationSeconds = Number(result.stdout.trim())
  }
  await save('take', take)
  const cleanup = []
  for (const id of sessions) { try { await request(`/api/configuration-assistant/sessions/${id}`, { method: 'DELETE', statuses: [204] }); await request(`/api/configuration-assistant/sessions/${id}`, { statuses: [404] }); cleanup.push({ sessionId: id, deleted: true }) } catch (error) { cleanup.push({ sessionId: id, error: error.message }) } }
  if (brain) { try { await request(`/api/deployments/${brain.id}/archive`, { method: 'POST' }); cleanup.push({ brainId: brain.id, archived: true }) } catch (error) { cleanup.push({ brainId: brain.id, error: error.message }) } }
  try { await authPage.goto(ui + '/home', { waitUntil: 'domcontentloaded' }); await authPage.getByRole('button', { name: 'Sign out', exact: true }).click(); await authPage.waitForURL(url => url.pathname.includes('sign-in') || url.pathname === '/'); take.signedOut = true } catch { take.signedOut = false }
  token = null; await auth.close(); await browser.close(); await save('cleanup', cleanup); await save('take', take)
  if (take.status === 'PASS') {
    const segments = chapters.map((chapter, index) => ({ start: chapter.seconds, end: index + 1 < chapters.length ? chapters[index + 1].seconds : take.durationSeconds - .1, title: chapter.title, detail: chapter.detail, narration: chapter.narration }))
    await save('edit-plan', { scope: take.scope, segments })
  }
  console.log(JSON.stringify({ status: take.status, failure: take.failure, root, durationSeconds: take.durationSeconds, turns: turns.length, errors, cleanup, signedOut: take.signedOut }))
  if (take.status !== 'PASS' || cleanup.some(item => item.error)) process.exitCode = 1
}
