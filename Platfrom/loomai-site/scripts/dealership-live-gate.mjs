import { chromium } from 'playwright'

const origin = normalizeOrigin(process.env.DEALERSHIP_DEMO_ORIGIN || 'https://loomai.pro')
const timeout = Number(process.env.DEALERSHIP_DEMO_GATE_TIMEOUT_MS || 90_000)
const syntheticEmail = 'loomai-final-gate@loomai.pro'
const syntheticPhone = '+44 7700 900123'
const syntheticName = 'LoomAI Final Gate'
const inventorySearchPrompt = 'Search the current dealership inventory for electric SUVs under £35,000 and list the available matches.'
const actionPrompt = `My name is ${syntheticName}, my email is ${syntheticEmail}, and my phone is ${syntheticPhone}. Request a test drive for the Aster E1.`

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
  const capturedConfirmation = await waitForCapturedCall(
    queryResponses,
    ({ request }) => request.query === 'Yes, confirm',
  )
  const confirmedActionEvidence = confirmationEvidence(capturedConfirmation.response)
  assert(confirmedActionEvidence.actionSuccess, 'The confirmed dealership action did not report success.')
  assert(/^NFM-[A-Z0-9]+$/.test(confirmedActionEvidence.receiptCode || ''), 'The confirmed dealership action returned no receipt.')
  assert(confirmedActionEvidence.actionStatus === 'NEW', 'The confirmed dealership action did not enter the staff inbox as NEW.')
  await page.getByText('Confirmed', { exact: true }).last().waitFor()

  const inventorySearchResponse = page.waitForResponse((response) => {
    if (!response.url().endsWith('/api/chat/me/query')) return false
    return response.request().postDataJSON()?.query === inventorySearchPrompt
  })
  await page.evaluate((prompt) => {
    window.MaxMode.sendMessage(prompt, { mode: 'executor', open: true })
  }, inventorySearchPrompt)
  const inventorySearch = await inventorySearchResponse
  assert(inventorySearch.ok(), `The inventory search returned HTTP ${inventorySearch.status()}.`)

  await page.waitForTimeout(1_500)

  const [informationalCall, inventorySearchCall, actionCall, confirmationCall] = selectExpectedCalls(
    queryResponses,
    inventorySearchPrompt,
    actionPrompt,
  )
  assert(informationalCall.request.mode === 'executor', 'The vehicle query did not use executor mode.')
  assert(
    informationalCall.request.attachments?.some(
      (attachment) => attachment.id === 'veh-aster-e1' && attachment.vectorSpace === 'dealer-vehicle',
    ),
    'The vehicle query did not include its deployment-authorized dealer-vehicle attachment.',
  )
  assert(inventorySearchCall.request.mode === 'executor', 'The inventory search did not use executor mode.')
  assert(actionCall.request.mode === 'executor', 'The action proposal did not use executor mode.')
  assert(confirmationCall.request.mode === 'executor', 'The confirmation did not preserve executor mode.')

  const retrieval = responseEvidence(informationalCall.response)
  assert(retrieval.sourceCount > 0 || retrieval.documentCount > 0, 'The vehicle answer had no indexed evidence.')
  assert(
    retrieval.searchSourceStatuses.every((status) => status === 'SUCCEEDED'),
    `The vehicle retrieval source did not complete successfully: ${JSON.stringify(retrieval.searchSourceStatuses)}`,
  )
  assert(
    !retrieval.readActionStatuses.includes('FAILED'),
    `The vehicle query reported a failed read-action iteration: ${JSON.stringify(retrieval.readActionStatuses)}`,
  )
  const inventorySearchEvidence = responseEvidence(inventorySearchCall.response)
  assert(
    inventorySearchEvidence.executedActions.includes('dealership_search_inventory'),
    'The explicit inventory search did not execute the deployment-owned read action.',
  )
  assert(
    inventorySearchEvidence.sourceCount > 0 || inventorySearchEvidence.documentCount > 0,
    'The explicit inventory search returned no action evidence.',
  )
  assert(
    !inventorySearchEvidence.readActionStatuses.includes('FAILED'),
    `The explicit inventory search reported a failed read-action iteration: ${JSON.stringify(inventorySearchEvidence.readActionStatuses)}`,
  )
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
    inventorySearch: inventorySearchEvidence,
    action: responseEvidence(actionCall.response),
    confirmation: {
      ...responseEvidence(confirmationCall.response),
      ...confirmedActionEvidence,
    },
    suggestionRequests: suggestionResponses.length,
    failures,
  }, null, 2)}\n`)
} finally {
  await context.close()
  await browser.close()
}

function selectExpectedCalls(responses, searchPrompt, actionPrompt) {
  const informational = responses.find(({ request }) => request.query?.startsWith('Tell me whether the 2025 Aster E1'))
  const inventorySearch = responses.find(({ request }) => request.query === searchPrompt)
  const action = responses.find(({ request }) => request.query === actionPrompt)
  const confirmation = responses.find(({ request }) => request.query === 'Yes, confirm')
  assert(informational, 'The informational response was not captured.')
  assert(inventorySearch, 'The inventory search response was not captured.')
  assert(action, 'The action proposal response was not captured.')
  assert(confirmation, 'The action confirmation response was not captured.')
  return [informational, inventorySearch, action, confirmation]
}

function responseEvidence(value) {
  const metadata = metadataCandidates(value)
  return {
    providerRequestId: findScalar(value, 'providerRequestId'),
    conversationId: findScalar(value, 'conversationId'),
    sourceCount: findLargestArray(value, 'sources'),
    documentCount: findLargestArray(value, 'documents'),
    action: findScalar(value, 'action'),
    readActionStatuses: uniqueStrings(
      metadata.flatMap((entry) => entry?.readActionResolution?.iterations?.map((iteration) => iteration?.status) || []),
    ),
    searchSourceStatuses: uniqueStrings(
      metadata.flatMap((entry) => entry?.searchSourceDiagnostics?.map((diagnostic) => diagnostic?.status) || []),
    ),
    executedActions: uniqueStrings(
      metadata.flatMap((entry) => entry?.readActionResolution?.executedActions?.map((action) => action?.action) || []),
    ),
  }
}

function confirmationEvidence(value) {
  const action = Array.isArray(value?.actions) ? value.actions[0] : undefined
  const actionResult = action?.actionResult
  const data = actionResult?.data?.data || actionResult?.data || {}
  return {
    actionSuccess: actionResult?.success === true,
    receiptCode: typeof data.receiptCode === 'string' ? data.receiptCode : null,
    actionStatus: typeof data.status === 'string' ? data.status : null,
  }
}

async function waitForCapturedCall(responses, predicate, timeoutMs = 5_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const captured = responses.find(predicate)
    if (captured) return captured
    await new Promise((resolve) => setTimeout(resolve, 25))
  }
  throw new Error('The completed runtime response was not captured by the live gate.')
}

function metadataCandidates(value) {
  return [value?.metadata, value?.ragResponse?.metadata].filter((candidate) => candidate && typeof candidate === 'object')
}

function uniqueStrings(values) {
  return [...new Set(values.filter((value) => typeof value === 'string' && value.length > 0))]
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
