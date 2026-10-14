import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { chromium } from 'playwright'

const origin = process.env.WORKSPACE_AUTH_ORIGIN
assert(origin && process.env.WORKSPACE_AUTH_CREDENTIAL_FILE, 'Set a reviewed app origin and a private synthetic-user credential file.')
const credentials = JSON.parse(await readFile(process.env.WORKSPACE_AUTH_CREDENTIAL_FILE, 'utf8'))
const output = resolve(process.env.WORKSPACE_AUTH_OUTPUT || '/private/tmp/loomai-engage-auth-live-gate')
await mkdir(output, { recursive: true })
const report = { checkedAt: new Date().toISOString(), origin, checks: {}, errors: [] }
const browser = await chromium.launch()
let runtimeToken = null
let providerToken = null
let runtimeOrigin = null
let posts = 0
let cleanupAnonymousHandover = null
try {
  report.stage = 'anonymous-query'
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.setDefaultTimeout(120_000)
  page.on('request', request => {
    if (/\/api\/chat\/me\/conversations\/[^/]+\/messages$/.test(new URL(request.url()).pathname) && request.method() === 'POST') posts++
  })
  page.on('response', async response => {
    if (new URL(response.url()).pathname === '/api/public/auth/runtime-token' && response.status() === 200) {
      runtimeToken = (await response.json()).accessToken
      runtimeOrigin = new URL(response.url()).origin
    }
  })
  await page.goto(origin)
  await page.locator('textarea').waitFor({ state: 'visible' })
  await page.waitForFunction(()=>Object.keys(sessionStorage).some(key=>key.startsWith('maxmode_public_runtime_session_credential_v2:')))
  const anonymousResponse = await send(page, 'Which current electric vehicles are under GBP 40,000?')
  assert.equal(anonymousResponse.success, true)
  await page.evaluate(() => window.MaxMode.attachItem({type:'current-page',data:{id:'auth-boundary-page',title:'Authentication boundary reference',content:'Synthetic attachment for identity transition verification.',metadata:{attachmentKind:'CURRENT_PAGE',capturedCharacters:59}}}))
  await page.waitForFunction(() => Object.keys(sessionStorage).filter(key => key.startsWith('maxmode_widget_state:')).some(key=>JSON.parse(sessionStorage.getItem(key)).attachedItems?.some(item=>item.data.id==='auth-boundary-page')))
  const anonymous = await state(page, 'anonymous')
  const anonymousOwner = await page.evaluate(() => {
    const key = Object.keys(sessionStorage).find(key => key.startsWith('maxmode_public_runtime_session_credential_v2:'))
    return JSON.parse(sessionStorage.getItem(key)).token
  })
  await page.getByRole('button', { name: 'Talk to a person', exact: true }).first().click()
  const transferResponse = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/handover-requests') && response.request().method() === 'POST')
  await page.getByRole('dialog', { name: 'Talk to a person' }).getByRole('button', { name: 'Continue anonymously', exact: true }).click()
  const transfer = await transferResponse
  assert.equal(transfer.status(), 202)
  const episode = (await transfer.json()).conversation
  assert.equal(episode.controlState, 'HUMAN_PENDING')
  const originalRuntime = new URL(transfer.url()).origin
  cleanupAnonymousHandover = async () => {
    const cancelled = await page.request.post(`${originalRuntime}/api/chat/me/conversations/${anonymous.conversationId}/handover-requests/${episode.handoverId}/cancel`, {
      headers: { Origin: origin, Authorization: `Bearer ${anonymousOwner}`, 'Idempotency-Key': `auth-boundary-cleanup-${episode.handoverId}` }, data: {},
    })
    assert.equal(cancelled.status(), 200, 'Original anonymous handover cleanup failed')
  }
  const postsBeforeLogin = posts
  report.stage = 'failed-login'
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Email').fill(credentials.email)
  await dialog.getByLabel('Password').fill('deliberately-invalid-password')
  await dialog.getByRole('button',{name:'Sign in',exact:true}).click()
  await page.locator('#auth-error').waitFor({state:'visible'})
  assert.equal(posts, postsBeforeLogin)
  assert.equal((await state(page, 'anonymous')).conversationId, anonymous.conversationId)
  report.checks.failedLoginPreservedAnonymousConversation = true
  report.stage = 'provider-login-and-broker'
  await dialog.getByLabel('Password').fill(credentials.password)
  const brokerResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/public/auth/runtime-token')
  await dialog.getByRole('button',{name:'Sign in',exact:true}).click()
  const broker = await brokerResponse
  assert.equal(broker.status(), 200, 'A genuine provider identity was rejected by the broker')
  const brokerBody = await broker.json()
  runtimeToken = brokerBody.accessToken
  runtimeOrigin = new URL(broker.url()).origin
  assert.equal(brokerBody.subjectId, credentials.userId)
  assert.deepEqual([...brokerBody.grantedScopes].sort(), [
    'chat:query','chat:suggestions','chat:conversations','chat:messages',
    'chat:handover:request','chat:handover:control','chat:handover:read',
  ].sort())
  await page.locator('textarea').waitFor({state:'visible'})
  const authenticated = await state(page, 'authenticated:')
  assert.notEqual(authenticated.conversationId, anonymous.conversationId)
  assert.equal(authenticated.attachmentCount, 0)
  assert.equal(posts, postsBeforeLogin, 'Login replayed the anonymous request')
  report.checks.realEs256ProviderTokenAccepted = true
  report.checks.loginClearedAnonymousAttachments = true
  report.checks.loginDidNotReplayRequest = true
  report.stage = 'ownership-and-signature'
  const priorConversation = await page.request.get(`${runtimeOrigin}/api/chat/me/conversations/${anonymous.conversationId}`, {headers:{Origin:origin,Authorization:`Bearer ${runtimeToken}`}})
  assert([403,404].includes(priorConversation.status()), `Authenticated owner could access an anonymous conversation: HTTP ${priorConversation.status()}`)
  report.checks.anonymousConversationNotTransferred = true
  report.checks.activeAnonymousHandoverOwnershipNotTransferred = true
  await cleanupAnonymousHandover()
  cleanupAnonymousHandover = null
  providerToken = await page.evaluate(() => {
    const key=Object.keys(sessionStorage).find(key=>key.startsWith('loomai_engage_identity:'))
    return JSON.parse(sessionStorage.getItem(key)).access_token
  })
  const parts = providerToken.split('.')
  parts[2] = (parts[2][0] === 'A' ? 'B' : 'A') + parts[2].slice(1)
  const invalid = await page.request.post(`${runtimeOrigin}/api/public/auth/runtime-token`, {
    headers:{Origin:origin,Authorization:`Bearer ${parts.join('.')}`},data:{schemaVersion:'loomai-workspace-runtime-token-request-v1'},
  })
  assert.equal(invalid.status(), 401)
  report.checks.tamperedProviderSignatureRejected = true
  const deniedOrigin = await page.request.post(`${runtimeOrigin}/api/public/auth/runtime-token`, {
    headers:{Origin:'https://unregistered.example',Authorization:`Bearer ${providerToken}`},data:{schemaVersion:'loomai-workspace-runtime-token-request-v1'},
  })
  assert([401,403].includes(deniedOrigin.status()))
  report.checks.unregisteredOriginRejected = true
  const operatorOnly = await page.request.get(`${runtimeOrigin}/api/admin/conversation-handover/overview`, {
    headers: { Origin: origin, Authorization: `Bearer ${runtimeToken}` },
  })
  assert([401, 403].includes(operatorOnly.status()))
  report.checks.signedInOwnerCannotUseOperatorApi = true
  report.stage = 'authenticated-query'
  const answer = await send(page, 'What is the dealership delivery policy?')
  assert.equal(answer.success,true)
  assert.equal(answer.metadata?.authenticated,true)
  report.checks.authenticatedQueryUsedDirectRuntime = true
  const authenticatedControl = await page.request.get(`${runtimeOrigin}/api/chat/me/conversations/${answer.conversationId}/control`, {
    headers: { Origin: origin, Authorization: `Bearer ${runtimeToken}` },
  })
  assert.equal(authenticatedControl.status(), 200)
  assert.equal((await authenticatedControl.json()).controlState, 'AI_ACTIVE')
  report.checks.authenticatedConversationDidNotInheritAnonymousHandover = true
  const beforeRefresh = await state(page, 'authenticated:')
  report.stage = 'provider-refresh'
  await page.evaluate(() => {
    const key=Object.keys(sessionStorage).find(key=>key.startsWith('loomai_engage_identity:'))
    const session=JSON.parse(sessionStorage.getItem(key))
    session.expires_at=Math.floor(Date.now()/1000)+10
    sessionStorage.setItem(key,JSON.stringify(session))
  })
  const refresh = page.waitForResponse(response=>response.url().includes('grant_type=refresh_token'))
  await page.reload()
  assert.equal((await refresh).status(),200)
  await page.locator('textarea').waitFor({state:'visible'})
  assert.equal((await state(page,'authenticated:')).conversationId,beforeRefresh.conversationId)
  report.checks.realProviderRefreshPreservedConversation = true
  report.stage = 'logout'
  await page.screenshot({path:resolve(output,'authenticated-desktop.png')})
  await page.evaluate(()=>window.LoomAIWorkspaceHost.signOut())
  await page.locator('textarea').waitFor({state:'visible'})
  assert.equal(await page.evaluate(()=>Object.keys(sessionStorage).some(key=>key.startsWith('loomai_engage_identity:'))),false)
  const signedOut = await state(page,'anonymous')
  assert.notEqual(signedOut.conversationId,beforeRefresh.conversationId)
  report.checks.logoutClearedPrivateBrowserState = true
  report.checks.runtimeOrigin = runtimeOrigin
  report.stage = 'completed'
} catch(error) {
  report.errors.push(error.message)
  process.exitCode=1
} finally {
  if (cleanupAnonymousHandover) {
    try { await cleanupAnonymousHandover() } catch (error) { report.errors.push(error.message); process.exitCode = 1 }
  }
  await browser.close()
  await writeFile(resolve(output,'report.json'),`${JSON.stringify(report,null,2)}\n`)
  console.log(JSON.stringify(report,null,2))
}

async function send(page,message) {
  const response = page.waitForResponse(response=>/\/api\/chat\/me\/conversations\/[^/]+\/messages$/.test(new URL(response.url()).pathname) && response.request().method()==='POST')
  const [received] = await Promise.all([
    response,
    (async()=>{
      await page.locator('textarea').fill(message)
      await page.locator('textarea').press('Enter')
    })(),
  ])
  assert.equal(received.status(),200)
  return received.json()
}

async function state(page,suffix) {
  return page.evaluate(suffix=>{
    const key=Object.keys(sessionStorage).find(key=>key.startsWith('maxmode_widget_state:') && key.includes(suffix))
    const value=key?JSON.parse(sessionStorage.getItem(key)):{}
    return {conversationId:value.conversationId,attachmentCount:value.attachedItems?.length || 0,messageCount:value.chatMessages?.length || 0}
  },suffix)
}
