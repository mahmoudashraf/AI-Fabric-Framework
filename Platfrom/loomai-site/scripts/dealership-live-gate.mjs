import { chromium } from 'playwright'

const origin = normalizeOrigin(process.env.DEALERSHIP_DEMO_ORIGIN || 'https://loomai.pro')
const timeout = Number(process.env.DEALERSHIP_DEMO_GATE_TIMEOUT_MS || 90_000)
const syntheticEmail = 'loomai-final-gate@invalid.example'
const syntheticName = 'LoomAI Final Gate'
const actionPrompt = `My name is ${syntheticName} and my email is ${syntheticEmail}. Request a test drive for the Aster E1.`

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  reducedMotion: 'reduce',
})
const page = await context.newPage()
page.setDefaultTimeout(timeout)

const failures = []
const queryResponses = []
const suggestionResponses = []

page.on('pageerror', (error) => failures.push({ type: 'pageerror', message: error.message }))
page.on('response', async (response) => {
  const url = response.url()
  const request = response.request()
  if (url.includes('/api/chat/me/') && response.status() >= 400) {
    failures.push({ type: 'http', status: response.status(), path: new URL(url).pathname })
  }
  if (url.endsWith('/api/chat/me/query')) {
    queryResponses.push({
      status: response.status(),
      request: safeRequestBody(request),
      response: await safeJson(response),
    })
  }
  if (url.endsWith('/api/chat/me/suggestions')) {
    suggestionResponses.push({ status: response.status() })
  }
})

try {
  await page.goto(`${origin}/demos/dealership-ai`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.vehicle-card')
  await page.waitForFunction(
    () => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready',
  )

  const vehicleCount = await page.locator('.vehicle-card').count()
  assert(vehicleCount > 0, 'The dealership inventory rendered no vehicles.')

  const sessionRenewal = await page.evaluate(async () => {
    const siteConfig = await fetch('/runtime-config/dealership-demo.json', { cache: 'no-store' }).then((response) => response.json())
    const descriptor = await fetch(`${siteConfig.apiBaseUrl}/api/public/runtime-descriptor`, { cache: 'no-store' }).then((response) => response.json())
    const bootstrapUrl = new URL(descriptor.runtimeRoutes.bootstrapUrl, descriptor.chatBaseUrl).toString()
    const renewUrl = new URL(descriptor.runtimeRoutes.renewUrl, descriptor.chatBaseUrl).toString()
    const initial = await fetch(bootstrapUrl, { method: 'POST', headers: { 'Content-Type': 'application/json' } }).then((response) => response.json())
    const renewed = await fetch(renewUrl, {
      method: 'POST',
      headers: { Authorization: `Bearer ${initial.token}`, 'Content-Type': 'application/json' },
    }).then((response) => response.json())
    return { sameSession: initial.sessionId === renewed.sessionId, sessionIdPresent: Boolean(renewed.sessionId) }
  })
  assert(sessionRenewal.sameSession && sessionRenewal.sessionIdPresent, 'Anonymous session renewal changed the runtime-owned identity.')

  const informationalResponse = page.waitForResponse((response) => {
    if (!response.url().endsWith('/api/chat/me/query')) return false
    return response.request().postDataJSON()?.query?.startsWith('Tell me whether the 2025 Aster E1')
  })
  await page.locator('[data-card-ask]').first().click()
  const informational = await informationalResponse
  assert(informational.ok(), `The grounded vehicle query returned HTTP ${informational.status()}.`)

  const actionResponse = page.waitForResponse((response) => {
    if (!response.url().endsWith('/api/chat/me/query')) return false
    return response.request().postDataJSON()?.query === actionPrompt
  })
  await page.evaluate((prompt) => {
    window.MaxMode.sendMessage(prompt, { mode: 'executor', open: true })
  }, actionPrompt)
  const proposedAction = await actionResponse
  assert(proposedAction.ok(), `The governed action query returned HTTP ${proposedAction.status()}.`)

  const confirmButton = page.getByRole('button', { name: 'Confirm', exact: true })
  await confirmButton.waitFor()
  const confirmationResponse = page.waitForResponse((response) => {
    if (!response.url().endsWith('/api/chat/me/query')) return false
    return response.request().postDataJSON()?.query === 'Yes, confirm'
  })
  await confirmButton.click()
  const confirmedAction = await confirmationResponse
  assert(confirmedAction.ok(), `The action confirmation returned HTTP ${confirmedAction.status()}.`)
  await page.getByText('Test-drive request created.', { exact: true }).first().waitFor()

  await page.waitForTimeout(1_500)

  const [informationalCall, actionCall, confirmationCall] = selectExpectedCalls(queryResponses, actionPrompt)
  assert(informationalCall.request.mode === 'executor', 'The vehicle query did not use executor mode.')
  assert(
    informationalCall.request.attachments?.some(
      (attachment) => attachment.id === 'veh-aster-e1' && attachment.vectorSpace === 'dealer-vehicle',
    ),
    'The vehicle query did not include its deployment-authorized dealer-vehicle attachment.',
  )
  assert(actionCall.request.mode === 'executor', 'The action proposal did not use executor mode.')
  assert(confirmationCall.request.mode === 'executor', 'The confirmation did not preserve executor mode.')

  const retrieval = responseEvidence(informationalCall.response)
  assert(retrieval.sourceCount > 0 || retrieval.documentCount > 0, 'The vehicle answer had no indexed evidence.')
  assert(suggestionResponses.length > 0, 'The runtime did not issue a contextual suggestions request.')
  assert(suggestionResponses.every(({ status }) => status >= 200 && status < 300), 'A suggestions request failed.')
  assert(failures.length === 0, `Browser/runtime failures were observed: ${JSON.stringify(failures)}`)

  process.stdout.write(`${JSON.stringify({
    status: 'PASS',
    origin,
    viewport: '390x844',
    vehicleCount,
    sessionRenewal,
    retrieval,
    action: responseEvidence(actionCall.response),
    confirmation: responseEvidence(confirmationCall.response),
    suggestionRequests: suggestionResponses.length,
    failures,
  }, null, 2)}\n`)
} finally {
  await context.close()
  await browser.close()
}

function selectExpectedCalls(responses, prompt) {
  const informational = responses.find(({ request }) => request.query?.startsWith('Tell me whether the 2025 Aster E1'))
  const action = responses.find(({ request }) => request.query === prompt)
  const confirmation = responses.find(({ request }) => request.query === 'Yes, confirm')
  assert(informational, 'The informational response was not captured.')
  assert(action, 'The action proposal response was not captured.')
  assert(confirmation, 'The action confirmation response was not captured.')
  return [informational, action, confirmation]
}

function responseEvidence(value) {
  return {
    providerRequestId: findScalar(value, 'providerRequestId'),
    conversationId: findScalar(value, 'conversationId'),
    sourceCount: findLargestArray(value, 'sources'),
    documentCount: findLargestArray(value, 'documents'),
    action: findScalar(value, 'action'),
    status: findScalar(value, 'status'),
  }
}

function findScalar(value, key) {
  if (!value || typeof value !== 'object') return null
  if (typeof value[key] === 'string' || typeof value[key] === 'number') return value[key]
  for (const child of Object.values(value)) {
    const found = findScalar(child, key)
    if (found !== null) return found
  }
  return null
}

function findLargestArray(value, key) {
  if (!value || typeof value !== 'object') return 0
  let count = Array.isArray(value[key]) ? value[key].length : 0
  for (const child of Object.values(value)) {
    count = Math.max(count, findLargestArray(child, key))
  }
  return count
}

function safeRequestBody(request) {
  try {
    return request.postDataJSON() || {}
  } catch {
    return {}
  }
}

async function safeJson(response) {
  try {
    return await response.json()
  } catch {
    return {}
  }
}

function normalizeOrigin(value) {
  return new URL(value).origin
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}
