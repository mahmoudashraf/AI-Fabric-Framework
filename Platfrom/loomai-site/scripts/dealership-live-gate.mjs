import { mkdir } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { chromium } from 'playwright'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const siteDirectory = resolve(scriptDirectory, '..')
const origin = normalizeOrigin(process.env.DEALERSHIP_DEMO_ORIGIN || 'https://loomai.pro')
const timeout = positiveNumber(process.env.DEALERSHIP_DEMO_GATE_TIMEOUT_MS, 120_000)
const staffUsername = requiredEnvironment('DEALERSHIP_STAFF_USERNAME')
const staffPassword = requiredEnvironment('DEALERSHIP_STAFF_PASSWORD')
const runId = new Date().toISOString().replace(/\D/g, '').slice(0, 14)
const syntheticName = `LoomAI Live Gate ${runId}`
const syntheticEmail = `loomai-live-gate+${runId}@loomai.pro`
const syntheticPhone = '+44 7700 900123'
const inventorySearchPrompt = 'Show current electric vehicles under GBP 40,000.'
const screenshotDirectory = resolve(siteDirectory, 'test-results/dealership-live')

await mkdir(screenshotDirectory, { recursive: true })

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({
  viewport: { width: 1440, height: 1000 },
  reducedMotion: 'reduce',
})
const page = await context.newPage()
page.setDefaultTimeout(timeout)

const failures = []
const queryResponses = []
const suggestionResponses = []
const forbiddenBrowserRequests = []

page.on('pageerror', (error) => failures.push({ type: 'pageerror', message: error.message }))
page.on('request', (request) => {
  if (!['fetch', 'xhr'].includes(request.resourceType())) return
  const url = request.url()
  if (url.includes('/api/internal/')
      || url.includes('-connector.')
      || url.includes('loomai-platform-backend.')
      || url.startsWith('https://api.loomai.pro/')) {
    forbiddenBrowserRequests.push(redactUrl(url))
  }
})
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

let staffPage
const receiptsToClean = []

try {
  staffPage = await openStaffWorkspace(context, origin, staffUsername, staffPassword, timeout)
  const baselineReceipts = await staffReceipts(staffPage)

  await page.goto(`${origin}/demos/dealership-ai`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.vehicle-card')
  await page.waitForFunction(
    () => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready',
  )

  const vehicleCount = await page.locator('.vehicle-card').count()
  assert(vehicleCount > 0, 'The dealership inventory rendered no vehicles.')

  const sessionRenewal = await verifyAnonymousRenewal(page)
  assert(sessionRenewal.sameSession && sessionRenewal.sessionIdPresent, 'Anonymous session renewal changed the runtime-owned identity.')

  const hostToolState = await openAndReadHostTools(page)
  const hostToolLabels = hostToolState.activeTools
  const maxModeView = page.locator('[data-max-mode-view]')
  const expectedHostTools = [
    'Search stock',
    'Electric cars',
    'Family options',
    'Compare cars',
  ]
  assert(
    sameStrings(expectedHostTools, hostToolLabels),
    `The hosted Browse stock tool set is incorrect: ${JSON.stringify(hostToolLabels)}`,
  )
  assert(
    sameStrings(hostToolState.groupLabels, ['Browse stock', 'This vehicle'])
      && hostToolState.activeScope === 'default'
      && hostToolState.contextualDisabled,
    `The hosted scoped-tool state is incorrect: ${JSON.stringify(hostToolState)}`,
  )

  const electricQuickAction = await clickHostToolAndWait(
    page,
    'Electric cars',
    'Show me electric cars in current stock.',
  )
  assert(electricQuickAction.ok(), `The Electric cars Max Mode tool returned HTTP ${electricQuickAction.status()}.`)

  const inventorySearch = await sendMessageAndWait(page, inventorySearchPrompt)
  assert(inventorySearch.response.ok(), `The inventory search returned HTTP ${inventorySearch.response.status()}.`)

  const inventoryPresentation = maxModeView.locator('loomai-dealership-inventory').last()
  await inventoryPresentation.waitFor({ state: 'attached' })
  const presentationEvidence = await inspectInventoryPresentation(inventoryPresentation)
  assertInventoryPresentation(presentationEvidence)

  const selectedVehicleLabel = presentationEvidence.references[0]?.label
  assert(selectedVehicleLabel, 'The inventory presentation exposed no selectable vehicle label.')

  const detailQuery = `Tell me about ${selectedVehicleLabel} using its current dealership facts.`
  const detailResponsePromise = waitForQueryResponse(page, (request) => safeRequestBody(request).query === detailQuery)
  await inventoryPresentation.getByRole('button', { name: 'Ask about this' }).first().click()
  const detailResponse = await detailResponsePromise
  assert(detailResponse.ok(), `The injected detail command returned HTTP ${detailResponse.status()}.`)
  const detailPresentation = maxModeView.locator('loomai-dealership-vehicle-detail').last()
  await detailPresentation.waitFor({ state: 'attached' })
  await detailPresentation.getByText('Vehicle details', { exact: true }).waitFor()

  const suitabilityQuery = `Is ${selectedVehicleLabel} suitable for everyday driving? Explain using current facts and identify unknowns.`
  const suitabilityResponsePromise = waitForQueryResponse(page, (request) => safeRequestBody(request).query === suitabilityQuery)
  await detailPresentation.getByRole('button', { name: 'Everyday suitability' }).click()
  const suitabilityResponse = await suitabilityResponsePromise
  assert(suitabilityResponse.ok(), `The grounded suitability follow-up returned HTTP ${suitabilityResponse.status()}.`)
  const suitabilityBody = await safeJson(suitabilityResponse)
  const suitabilityEvidence = responseEvidence(suitabilityBody)
  assert(
    suitabilityEvidence.sourceCount > 0 || suitabilityEvidence.documentCount > 0,
    'The grounded suitability follow-up returned no action or indexed evidence.',
  )

  const tradeoffsQuery = `Explain the important trade-offs for ${selectedVehicleLabel} using current dealership facts.`
  const tradeoffsResponsePromise = waitForQueryResponse(page, (request) => safeRequestBody(request).query === tradeoffsQuery)
  await detailPresentation.getByRole('button', { name: 'Explain trade-offs' }).click()
  const tradeoffsResponse = await tradeoffsResponsePromise
  assert(tradeoffsResponse.ok(), `The detail trade-offs control returned HTTP ${tradeoffsResponse.status()}.`)

  await detailPresentation.getByRole('button', { name: 'Keep in context' }).click()
  await detailPresentation.getByRole('button', { name: 'Remove context' }).waitFor()
  await detailPresentation.getByRole('button', { name: 'Remove context' }).click()
  await detailPresentation.getByRole('button', { name: 'Keep in context' }).waitFor()

  const inventoryCheckboxes = inventoryPresentation.getByRole('checkbox')
  assert(await inventoryCheckboxes.count() >= 2, 'The inventory presentation did not provide two comparison candidates.')
  await inventoryCheckboxes.nth(0).click()
  await inventoryCheckboxes.nth(1).click()
  const comparisonResponsePromise = waitForQueryResponse(
    page,
    (request) => safeRequestBody(request).query?.startsWith('Compare these selected current vehicles using dealership facts:'),
  )
  await inventoryPresentation.getByRole('button', { name: 'Compare selected' }).click()
  const comparisonResponse = await comparisonResponsePromise
  assert(comparisonResponse.ok(), `The injected comparison command returned HTTP ${comparisonResponse.status()}.`)
  const comparisonPresentation = maxModeView.locator('loomai-dealership-vehicle-comparison').last()
  await comparisonPresentation.waitFor({ state: 'attached' })
  await comparisonPresentation.getByText('Vehicle comparison', { exact: true }).waitFor()

  const comparisonSelect = comparisonPresentation.getByRole('button', { name: 'Select vehicle' }).first()
  await comparisonSelect.click()
  await comparisonPresentation.getByRole('button', { name: 'Selected' }).first().waitFor()
  await comparisonPresentation.getByRole('button', { name: 'Selected' }).first().click()
  await comparisonPresentation.getByRole('button', { name: 'Select vehicle' }).first().waitFor()

  const valueQuery = 'Compare the value trade-offs between these current vehicles. Ask for my priorities before naming a best option.'
  const valueResponsePromise = waitForQueryResponse(page, (request) => safeRequestBody(request).query === valueQuery)
  await comparisonPresentation.getByRole('button', { name: 'Compare value' }).click()
  const valueResponse = await valueResponsePromise
  assert(valueResponse.ok(), `The comparison follow-up returned HTTP ${valueResponse.status()}.`)

  const practicalityQuery = 'Compare the everyday practicality of these current vehicles using only available facts and clearly identify unknowns.'
  const practicalityResponsePromise = waitForQueryResponse(page, (request) => safeRequestBody(request).query === practicalityQuery)
  await comparisonPresentation.getByRole('button', { name: 'Compare practicality' }).click()
  const practicalityResponse = await practicalityResponsePromise
  assert(practicalityResponse.ok(), `The comparison practicality control returned HTTP ${practicalityResponse.status()}.`)

  const runningCostQuery = 'Explain likely running-cost trade-offs between these vehicles without inventing unavailable efficiency or finance figures.'
  const runningCostResponsePromise = waitForQueryResponse(page, (request) => safeRequestBody(request).query === runningCostQuery)
  await comparisonPresentation.getByRole('button', { name: 'Running-cost trade-offs' }).click()
  const runningCostResponse = await runningCostResponsePromise
  assert(runningCostResponse.ok(), `The running-cost trade-offs control returned HTTP ${runningCostResponse.status()}.`)

  const rejection = await runClarifiedWrite({
    page,
    trigger: () => inventoryPresentation.getByRole('button', { name: 'Request test drive' }).first().click(),
    initialQuery: `I would like to request a test drive for ${selectedVehicleLabel}.`,
    actionName: 'dealership_request_test_drive',
    values: { name: syntheticName, email: syntheticEmail, phone: syntheticPhone },
    decision: 'reject',
  })
  assert(rejection.cancelled, 'Rejecting the test-drive proposal did not return a cancelled result.')
  const receiptsAfterReject = await refreshStaffReceipts(staffPage)
  assert(
    sameStrings(baselineReceipts, receiptsAfterReject),
    'Rejecting the test-drive proposal unexpectedly created a staff-inbox request.',
  )

  const confirmedTestDrive = await runClarifiedWrite({
    page,
    trigger: () => detailPresentation.getByRole('button', { name: 'Request test drive' }).click(),
    initialQuery: `I would like to request a test drive for ${selectedVehicleLabel}.`,
    actionName: 'dealership_request_test_drive',
    values: { name: syntheticName, email: syntheticEmail, phone: syntheticPhone },
    decision: 'confirm',
  })
  assertReceipt(confirmedTestDrive, 'test-drive')
  receiptsToClean.push(confirmedTestDrive.receiptCode)

  const confirmedCallback = await runClarifiedWrite({
    page,
    trigger: () => inventoryPresentation.getByRole('button', { name: 'Request callback' }).first().click(),
    initialQuery: `I would like the dealership to call me about ${selectedVehicleLabel}.`,
    actionName: 'dealership_request_callback',
    values: { name: syntheticName, phone: syntheticPhone, consent: 'true' },
    decision: 'confirm',
  })
  assertReceipt(confirmedCallback, 'callback')
  receiptsToClean.push(confirmedCallback.receiptCode)

  const staffEvidence = []
  for (const receiptCode of receiptsToClean) {
    staffEvidence.push(await verifyStaffReceipt(staffPage, receiptCode, selectedVehicleLabel))
  }
  const finalReceipts = await staffReceipts(staffPage)
  for (const receiptCode of receiptsToClean) {
    assert(finalReceipts.filter((receipt) => receipt === receiptCode).length === 1, `Receipt ${receiptCode} was not persisted exactly once.`)
  }

  const contextualSuggestions = await verifyContextualSuggestions(page, detailPresentation)

  await page.screenshot({
    path: resolve(screenshotDirectory, 'meeting-demo-desktop.png'),
    fullPage: true,
    animations: 'disabled',
  })
  await page.setViewportSize({ width: 390, height: 844 })
  const comparisonSurface = maxModeView.locator('[data-max-mode-action-presentation="loomai.vehicle-comparison.v1"]').last()
  await comparisonSurface.evaluate((element) => element.scrollIntoView({ block: 'start' }))
  assert(await comparisonPresentation.locator('.comparison-mobile').isVisible(), 'The hosted comparison did not switch to its mobile layout.')
  const mobileBox = await comparisonSurface.boundingBox()
  assert(mobileBox && mobileBox.width <= 390, `The hosted comparison exceeded the mobile viewport: ${JSON.stringify(mobileBox)}`)
  await page.screenshot({
    path: resolve(screenshotDirectory, 'meeting-demo-mobile.png'),
    animations: 'disabled',
  })

  await page.setViewportSize({ width: 1440, height: 1000 })
  const detailNavigation = await verifyDetailNavigationAndPageAttachment(page, inventoryPresentation, selectedVehicleLabel)

  const conversationIds = uniqueStrings(queryResponses.map(({ response }) => findScalar(response, 'conversationId')))
  assert(conversationIds.length === 1, `The meeting journey did not retain one conversation: ${JSON.stringify(conversationIds)}`)
  assert(suggestionResponses.length > 0, 'The runtime did not issue a contextual suggestions request.')
  assert(suggestionResponses.every(({ status }) => status >= 200 && status < 300), 'A suggestions request failed.')
  assert(forbiddenBrowserRequests.length === 0, `The browser called a protected provider/control-plane route: ${JSON.stringify(forbiddenBrowserRequests)}`)
  assert(failures.length === 0, `Browser/runtime failures were observed: ${JSON.stringify(failures)}`)

  const staffCleanup = []
  for (const receiptCode of receiptsToClean) {
    staffCleanup.push(await cancelStaffReceipt(staffPage, receiptCode))
  }
  receiptsToClean.length = 0

  process.stdout.write(`${JSON.stringify({
    status: 'PASS',
    origin,
    deploymentJourney: {
      conversationId: conversationIds[0],
      queryCount: queryResponses.length,
      hostToolLabels,
      hostToolState,
      clickedHostTool: 'Electric cars',
      vehicleCount,
      sessionRenewal,
      contextualSuggestions,
    },
    presentations: {
      inventory: {
        actionName: presentationEvidence.actionName,
        rendererId: presentationEvidence.rendererId,
        schemaVersion: presentationEvidence.schemaVersion,
        renderedCardCount: presentationEvidence.renderedCardCount,
        filterLabels: presentationEvidence.filterLabels,
      },
      detail: 'loomai.vehicle-detail.v1',
      comparison: 'loomai.vehicle-comparison.v1',
    },
    groundedFollowUp: suitabilityEvidence,
    writes: {
      rejectedTestDrive: { cancelled: rejection.cancelled },
      confirmedTestDrive: safeReceiptEvidence(confirmedTestDrive),
      confirmedCallback: safeReceiptEvidence(confirmedCallback),
    },
    staffReadback: staffEvidence,
    staffCleanup,
    suggestionRequests: suggestionResponses.length,
    detailNavigation,
    forbiddenBrowserRequests,
    failures,
    screenshots: [
      resolve(screenshotDirectory, 'meeting-demo-desktop.png'),
      resolve(screenshotDirectory, 'meeting-demo-mobile.png'),
    ],
  }, null, 2)}\n`)
} catch (error) {
  process.stderr.write(`${JSON.stringify({
    status: 'FAIL',
    error: error instanceof Error ? error.message : String(error),
    observedQueries: queryResponses.map(({ status, request, response }) => ({
      status,
      query: request?.query || null,
      type: findScalar(response, 'type'),
      providerRequestId: findScalar(response, 'providerRequestId'),
      ...responseEvidence(response),
    })),
    forbiddenBrowserRequests,
    failures,
  }, null, 2)}\n`)
  throw error
} finally {
  if (staffPage) {
    for (const receiptCode of receiptsToClean) {
      await cancelStaffReceipt(staffPage, receiptCode).catch(() => undefined)
    }
  }
  await context.close()
  await browser.close()
}

async function verifyAnonymousRenewal(browserPage) {
  return browserPage.evaluate(async () => {
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
}

async function openAndReadHostTools(browserPage) {
  await browserPage.evaluate(() => window.MaxMode.open({ position: 'search', mode: 'executor' }))
  await browserPage.getByRole('button', { name: 'Close MAX Mode' }).waitFor()
  const view = browserPage.locator('[data-max-mode-view]')
  const groupLabels = await view.locator('[data-max-mode-tool-scope]').evaluateAll(
    (elements) => elements.map((element) => element.textContent?.trim()).filter(Boolean),
  )
  const activeTools = await view.locator('[data-max-mode-quick-action]').evaluateAll(
    (elements) => elements.map((element) => element.getAttribute('data-max-mode-quick-action')).filter(Boolean),
  )
  const defaultScope = view.locator('[data-max-mode-tool-scope="default"]')
  const contextualScope = view.locator('[data-max-mode-tool-scope="contextual"]')
  return {
    groupLabels,
    activeTools,
    activeScope: (await defaultScope.getAttribute('aria-selected')) === 'true' ? 'default' : 'contextual',
    contextualDisabled: await contextualScope.isDisabled(),
  }
}

async function sendMessageAndWait(browserPage, query) {
  const responsePromise = waitForQueryResponse(browserPage, (request) => safeRequestBody(request).query === query)
  await browserPage.evaluate((message) => {
    window.MaxMode.sendMessage(message, { mode: 'executor', position: 'search', open: true })
  }, query)
  return { response: await responsePromise }
}

async function clickHostToolAndWait(browserPage, label, query) {
  const responsePromise = waitForQueryResponse(browserPage, (request) => safeRequestBody(request).query === query)
  await browserPage.locator('[data-max-mode-view]').getByRole('button', { name: label, exact: true }).click()
  return responsePromise
}

async function verifyDetailNavigationAndPageAttachment(browserPage, inventoryPresentation, expectedVehicleLabel) {
  await inventoryPresentation.getByRole('button', { name: 'View details' }).first().click()
  await browserPage.waitForURL(/\/demos\/dealership-ai\/vehicles\/[^/?#]+$/)
  await browserPage.waitForFunction(
    () => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready',
  )
  await browserPage.getByRole('heading', { level: 1, name: expectedVehicleLabel, exact: true }).waitFor()

  const attach = browserPage.getByRole('button', { name: 'Attach current page' })
  await attach.click()
  const chip = browserPage.locator('[data-max-mode-current-page-chip]').filter({ hasText: expectedVehicleLabel })
  await chip.waitFor()
  const remove = browserPage.getByRole('button', { name: new RegExp(`Remove attached page: ${escapeRegex(expectedVehicleLabel)}`, 'i') })
  await remove.click()
  await chip.waitFor({ state: 'detached' })

  return {
    path: new URL(browserPage.url()).pathname,
    title: expectedVehicleLabel,
    currentPageAttachRemove: true,
  }
}

async function verifyContextualSuggestions(browserPage, detailPresentation) {
  const responsePromise = browserPage.waitForResponse((response) => response.url().endsWith('/api/chat/me/suggestions'))
  await detailPresentation.getByRole('button', { name: 'Keep in context' }).click()
  await detailPresentation.getByRole('button', { name: 'Remove context' }).waitFor()
  const response = await responsePromise
  assert(response.ok(), `Contextual suggestions returned HTTP ${response.status()}.`)
  const body = await safeJson(response)
  const suggestions = Array.isArray(body?.suggestions)
    ? body.suggestions.filter((value) => typeof value === 'string' && value.trim().length > 0)
    : []
  assert(suggestions.length > 0, 'The contextual suggestions response contained no usable suggestions.')
  await browserPage.getByText('Generating smart suggestions...', { exact: true }).waitFor({ state: 'detached' })
  const dismissSuggestions = browserPage
    .locator('[data-max-mode-view]')
    .getByRole('button', { name: 'Dismiss suggestions' })
    .last()
  if (await dismissSuggestions.isVisible().catch(() => false)) {
    await dismissSuggestions.click()
  }
  const removeContext = detailPresentation.getByRole('button', { name: 'Remove context' })
  await removeContext.evaluate((element) => element.scrollIntoView({ block: 'center', inline: 'nearest' }))
  await removeContext.click()
  await detailPresentation.getByRole('button', { name: 'Keep in context' }).waitFor()
  const restoredSuggestionsDismiss = browserPage
    .locator('[data-max-mode-view]')
    .getByRole('button', { name: 'Dismiss suggestions' })
    .last()
  if (await restoredSuggestionsDismiss.isVisible().catch(() => false)) {
    await restoredSuggestionsDismiss.click()
    await restoredSuggestionsDismiss.waitFor({ state: 'hidden' })
  }
  return { count: suggestions.length, status: response.status() }
}

async function waitForQueryResponse(browserPage, predicate) {
  const response = await browserPage.waitForResponse((response) => (
    response.url().endsWith('/api/chat/me/query') && predicate(response.request())
  ))
  await response.finished()
  await browserPage.waitForTimeout(50)
  await browserPage
    .locator('[data-max-mode-view]')
    .getByText('AI is thinking...', { exact: true })
    .waitFor({ state: 'detached', timeout })
  return response
}

async function runClarifiedWrite({ page: browserPage, trigger, initialQuery, actionName, values, decision }) {
  const initialResponsePromise = waitForQueryResponse(
    browserPage,
    (request) => safeRequestBody(request).query === initialQuery,
  )
  await trigger()
  const initialResponse = await initialResponsePromise
  assert(initialResponse.ok(), `${actionName} entry returned HTTP ${initialResponse.status()}.`)
  const initialBody = await safeJson(initialResponse)
  const entryType = findScalar(initialBody, 'type')
  assert(
    ['CLARIFICATION_REQUIRED', 'CONFIRMATION_REQUIRED'].includes(entryType),
    `${actionName} entered neither clarification nor confirmation from its injected CTA.`,
  )

  if (entryType === 'CLARIFICATION_REQUIRED') {
    const providedParameters = findRecord(initialBody, 'providedParameters') || {}
    await browserPage.getByRole('button', { name: 'Submit & Proceed' }).last().waitFor()

    for (const [field, value] of Object.entries(values)) {
      const input = browserPage.getByPlaceholder(`Enter ${humanizeField(field).toLowerCase()}...`).last()
      if (await input.isVisible().catch(() => false)) {
        await input.fill(value)
        continue
      }
      assert(
        String(providedParameters[field] ?? '').trim() === String(value).trim(),
        `${actionName} field ${field} was neither editable nor already provided with the expected value.`,
      )
    }

    const clarificationPrefix = `Proceed with ${actionName.replaceAll('_', ' ')} using:`
    const clarificationResponsePromise = waitForQueryResponse(
      browserPage,
      (request) => safeRequestBody(request).query?.startsWith(clarificationPrefix),
    )
    await browserPage.getByRole('button', { name: 'Submit & Proceed' }).last().click()
    const clarificationResponse = await clarificationResponsePromise
    assert(clarificationResponse.ok(), `${actionName} clarification returned HTTP ${clarificationResponse.status()}.`)
    const clarificationBody = await safeJson(clarificationResponse)
    assert(findScalar(clarificationBody, 'type') === 'CONFIRMATION_REQUIRED', `${actionName} did not enter final confirmation.`)
  }

  const confirmationQuery = decision === 'confirm' ? 'Yes, confirm' : 'No, cancel'
  const decisionResponsePromise = waitForQueryResponse(
    browserPage,
    (request) => safeRequestBody(request).query === confirmationQuery,
  )
  await browserPage.getByRole('button', { name: decision === 'confirm' ? 'Confirm' : 'Reject', exact: true }).last().click()
  const decisionResponse = await decisionResponsePromise
  assert(decisionResponse.ok(), `${actionName} ${decision} returned HTTP ${decisionResponse.status()}.`)
  const decisionBody = await safeJson(decisionResponse)
  if (decision === 'reject') {
    await browserPage.getByText('Rejected', { exact: true }).last().waitFor()
    const evidence = responseEvidence(decisionBody)
    assert(evidence.executedActions.length === 0, `${actionName} executed after the user rejected it.`)
    return {
      cancelled: true,
      entryType,
      ...evidence,
    }
  }

  await browserPage.getByText('Confirmed', { exact: true }).last().waitFor()
  return {
    entryType,
    ...responseEvidence(decisionBody),
    ...confirmationEvidence(decisionBody),
  }
}

async function inspectInventoryPresentation(inventoryPresentation) {
  return inventoryPresentation.evaluate((element) => {
    const presentation = element.presentation || {}
    const root = element.shadowRoot
    const labels = (selector) => [...(root?.querySelectorAll(selector) || [])]
      .map((candidate) => candidate.textContent?.trim())
      .filter(Boolean)
    const presentationData = presentation.presentationData || {}
    const references = Array.isArray(presentation.resultReferences) ? presentation.resultReferences : []
    return {
      actionName: presentation.actionName,
      rendererId: presentation.rendererId,
      schemaVersion: presentation.schemaVersion,
      projectedItemCount: Array.isArray(presentationData.items) ? presentationData.items.length : 0,
      renderedCardCount: root?.querySelectorAll('.vehicle-card').length || 0,
      filterLabels: labels('.filter-pills span'),
      buttonLabels: labels('button'),
      referenceCount: references.length,
      references: references.map((reference) => ({ label: reference.label, lookupValue: reference.lookupValue })),
      referencesHaveProvenance: references.every((reference) => (
        reference.sourceActionName === 'dealership_search_inventory'
          && typeof reference.sourceMessageId === 'string'
          && reference.sourceMessageId.length > 0
          && typeof reference.lookupValue === 'string'
          && reference.lookupValue.length > 0
      )),
      forbiddenProjectionFieldsPresent: ['content', 'errors', 'warnings'].some((field) => field in presentationData),
    }
  })
}

function assertInventoryPresentation(evidence) {
  assert(evidence.actionName === 'dealership_search_inventory', `The inventory surface was bound to ${evidence.actionName || 'no action'}.`)
  assert(
    evidence.rendererId === 'loomai.vehicle-inventory.v1' && evidence.schemaVersion === 'loomai.vehicle-list.v1',
    `The exact inventory renderer contract was not selected: ${JSON.stringify(evidence)}.`,
  )
  assert(
    evidence.projectedItemCount >= 2
      && evidence.projectedItemCount <= 12
      && evidence.renderedCardCount === evidence.projectedItemCount,
    `The bounded inventory projection did not render at least two complete records: ${JSON.stringify(evidence)}.`,
  )
  assert(
    ['Fuel: Electric', 'Up to £40,000'].every((label) => evidence.filterLabels.includes(label)),
    `The inventory surface did not expose the applied filters: ${JSON.stringify(evidence.filterLabels)}.`,
  )
  assert(
    ['View details', 'Ask about this', 'Keep in context', 'Request test drive', 'Request callback', 'Compare selected']
      .every((label) => evidence.buttonLabels.includes(label)),
    `The inventory surface is missing safe host commands: ${JSON.stringify(evidence.buttonLabels)}.`,
  )
  assert(
    evidence.referenceCount === evidence.projectedItemCount && evidence.referencesHaveProvenance,
    `The result references lost action/message provenance: ${JSON.stringify(evidence)}.`,
  )
  assert(!evidence.forbiddenProjectionFieldsPresent, 'The host projection exposed raw transport fields to the renderer.')
}

async function openStaffWorkspace(browserContext, siteOrigin, username, password, defaultTimeout) {
  const browserPage = await browserContext.newPage()
  browserPage.setDefaultTimeout(defaultTimeout)
  await browserPage.goto(`${siteOrigin}/demos/dealership-ai/staff`, { waitUntil: 'networkidle' })
  const loginPanel = browserPage.locator('[data-login-panel]')
  if (await loginPanel.isVisible()) {
    await browserPage.locator('input[name="username"]').fill(username)
    await browserPage.locator('input[name="password"]').fill(password)
    const leadsResponse = browserPage.waitForResponse((response) => response.url().includes('/api/staff/leads?limit=50'))
    await browserPage.getByRole('button', { name: 'Sign in' }).click()
    const response = await leadsResponse
    assert(response.ok(), `Staff login/readback returned HTTP ${response.status()}.`)
    await waitForStaffInboxRender(browserPage, response)
  }
  await browserPage.locator('[data-staff-workspace]').waitFor({ state: 'visible' })
  await browserPage.locator('[data-lead-table-body] tr').first().waitFor()
  return browserPage
}

async function staffReceipts(browserPage) {
  return browserPage.locator('[data-lead-receipt]').evaluateAll(
    (elements) => elements.map((element) => element.textContent?.trim()).filter(Boolean),
  )
}

async function refreshStaffReceipts(browserPage) {
  const responsePromise = browserPage.waitForResponse((response) => response.url().includes('/api/staff/leads?limit=50'))
  await browserPage.locator('[data-refresh-workspace]').click()
  const response = await responsePromise
  assert(response.ok(), `Staff inbox refresh returned HTTP ${response.status()}.`)
  await waitForStaffInboxRender(browserPage, response)
  return staffReceipts(browserPage)
}

async function waitForStaffInboxRender(browserPage, response) {
  const body = await safeJson(response)
  const expectedItems = Array.isArray(body?.items)
    ? body.items.map((item) => ({ receiptCode: item?.receiptCode, status: item?.status }))
    : null
  assert(expectedItems !== null, 'Staff inbox response did not contain an items array.')
  await browserPage.waitForFunction(
    (expected) => {
      const rows = [...document.querySelectorAll('[data-lead-table-body] tr')]
      const receipts = [...document.querySelectorAll('[data-lead-receipt]')]
      if (receipts.length !== expected.length) return false
      return expected.every((item) => rows.some((row) => (
        row.querySelector('[data-lead-receipt]')?.textContent?.trim() === item.receiptCode
        && row.querySelector('[data-lead-status]')?.getAttribute('data-status') === item.status
      )))
    },
    expectedItems,
  )
}

async function verifyStaffReceipt(browserPage, receiptCode, expectedVehicle) {
  await refreshStaffReceipts(browserPage)
  const row = browserPage.locator('[data-lead-table-body] tr').filter({ hasText: receiptCode })
  assert(await row.count() === 1, `Staff inbox did not contain exactly one ${receiptCode} row.`)
  const rowText = (await row.textContent()) || ''
  assert(rowText.includes(expectedVehicle), `Staff receipt ${receiptCode} was not associated with ${expectedVehicle}.`)
  await row.locator('[data-open-lead]').click()
  const dialog = browserPage.locator('[data-lead-dialog]')
  await dialog.waitFor({ state: 'visible' })
  await dialog.getByText(receiptCode, { exact: true }).waitFor()
  const detailText = (await dialog.textContent()) || ''
  assert(detailText.includes(syntheticName), `Staff receipt ${receiptCode} did not reveal the confirmed synthetic contact after authentication.`)
  await dialog.locator('[data-close-lead]').click()
  await dialog.waitFor({ state: 'hidden' })
  return { receiptCode, persistedExactlyOnce: true, vehicle: expectedVehicle, contactReadback: true }
}

async function cancelStaffReceipt(browserPage, receiptCode) {
  await refreshStaffReceipts(browserPage)
  const row = browserPage.locator('[data-lead-table-body] tr').filter({ hasText: receiptCode })
  assert(await row.count() === 1, `Cleanup could not find exactly one ${receiptCode} row.`)
  await row.locator('[data-open-lead]').click()
  const dialog = browserPage.locator('[data-lead-dialog]')
  await dialog.waitFor({ state: 'visible' })
  await dialog.locator('select[name="status"]').selectOption('CANCELLED')
  const responsePromise = browserPage.waitForResponse((response) => (
    response.request().method() === 'PATCH' && response.url().includes('/api/staff/leads/')
  ))
  const refreshResponsePromise = browserPage.waitForResponse((response) => (
    response.request().method() === 'GET' && response.url().includes('/api/staff/leads?limit=50')
  ))
  await dialog.getByRole('button', { name: 'Save status' }).click()
  const response = await responsePromise
  assert(response.ok(), `Cleanup for ${receiptCode} returned HTTP ${response.status()}.`)
  const body = await safeJson(response)
  assert(findScalar(body, 'status') === 'CANCELLED', `Cleanup for ${receiptCode} did not return CANCELLED.`)
  await dialog.waitFor({ state: 'hidden' })
  const refreshResponse = await refreshResponsePromise
  assert(refreshResponse.ok(), `Post-cleanup inbox refresh for ${receiptCode} returned HTTP ${refreshResponse.status()}.`)
  await waitForStaffInboxRender(browserPage, refreshResponse)
  const updated = browserPage.locator('[data-lead-table-body] tr').filter({ hasText: receiptCode })
  assert(
    await updated.locator('[data-lead-status]').getAttribute('data-status') === 'CANCELLED',
    `Cleanup for ${receiptCode} was not visible in the staff inbox.`,
  )
  return { receiptCode, status: 'CANCELLED' }
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
  const action = findFirstActionResult(value)
  const actionResult = action?.actionResult
  const data = actionResult?.data?.data || actionResult?.data || {}
  return {
    actionSuccess: actionResult?.success === true,
    receiptCode: typeof data.receiptCode === 'string' ? data.receiptCode : null,
    actionStatus: typeof data.status === 'string' ? data.status : null,
  }
}

function findFirstActionResult(value) {
  if (!value || typeof value !== 'object') return null
  if (Array.isArray(value.actions) && value.actions.length > 0) return value.actions[0]
  for (const child of Object.values(value)) {
    const found = findFirstActionResult(child)
    if (found) return found
  }
  return null
}

function assertReceipt(evidence, label) {
  assert(evidence.actionSuccess, `The confirmed ${label} action did not report success.`)
  assert(/^NFM-[A-Z0-9]+$/.test(evidence.receiptCode || ''), `The confirmed ${label} action returned no receipt.`)
  assert(evidence.actionStatus === 'NEW', `The confirmed ${label} action did not enter the staff inbox as NEW.`)
}

function safeReceiptEvidence(evidence) {
  return {
    entryType: evidence.entryType,
    providerRequestId: evidence.providerRequestId,
    conversationId: evidence.conversationId,
    actionSuccess: evidence.actionSuccess,
    receiptCode: evidence.receiptCode,
    actionStatus: evidence.actionStatus,
  }
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

function findRecord(value, key) {
  if (!value || typeof value !== 'object') return null
  if (value[key] && typeof value[key] === 'object' && !Array.isArray(value[key])) return value[key]
  for (const child of Object.values(value)) {
    const found = findRecord(child, key)
    if (found) return found
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

function humanizeField(value) {
  return value.replace(/([A-Z])/g, ' $1').replace(/^./, (character) => character.toUpperCase()).trim()
}

function sameStrings(left, right) {
  return JSON.stringify([...left].sort()) === JSON.stringify([...right].sort())
}

function redactUrl(value) {
  const url = new URL(value)
  return `${url.origin}${url.pathname}`
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function normalizeOrigin(value) {
  return new URL(value).origin
}

function positiveNumber(value, fallback) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function requiredEnvironment(name) {
  const value = process.env[name]?.trim()
  if (!value) throw new Error(`${name} is required for protected staff-inbox readback.`)
  return value
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}
