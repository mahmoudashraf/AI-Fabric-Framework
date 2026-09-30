import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { chromium } from 'playwright'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const siteDirectory = resolve(scriptDirectory, '..')
const origin = normalizeOrigin(process.env.DEALERSHIP_QUALITY_ORIGIN || 'https://loomai.pro')
const route = process.env.DEALERSHIP_QUALITY_ROUTE || '/demos/dealership-ai'
const timeout = positiveNumber(process.env.DEALERSHIP_QUALITY_TIMEOUT_MS, 120_000)
const headless = process.env.DEALERSHIP_QUALITY_HEADLESS !== 'false'
const strict = process.env.DEALERSHIP_QUALITY_STRICT === 'true'
const outputPath = resolve(
  process.env.DEALERSHIP_QUALITY_OUTPUT || resolve(siteDirectory, 'test-results/dealership-quality/latest.json'),
)
const viewport = { width: 390, height: 844 }

const scenarios = [
  {
    id: 'structured-current-stock-action',
    purpose: 'Use the authoritative stock action for exact filters and current commercial facts.',
    prompt: 'Show me current electric vehicles under GBP 40,000.',
  },
  {
    id: 'contextual-follow-up',
    purpose: 'Resolve a follow-up from the same conversation without making the user repeat the candidates.',
    prompt: 'Of those, which has the longest electric range, and is it an SUV?',
  },
  {
    id: 'vehicle-comparison',
    purpose: 'Compare named current-stock vehicles using action or indexed evidence.',
    prompt: 'Compare the Aster E1 and Morrow C2 for motorway family use using current stock facts.',
  },
  {
    id: 'semantic-rag',
    purpose: 'Use semantic evidence for a need that is not an exact inventory filter.',
    prompt: 'Which current vehicle is best suited to towing and poor-weather driving? Use current evidence and explain why.',
  },
  {
    id: 'empty-action-rag-fallback',
    purpose: 'Fall back to indexed evidence when an authoritative action finds no exact match.',
    prompt: 'Do you have a diesel SUV under GBP 10,000? If not, use indexed current-stock evidence to suggest the closest alternative without claiming it matches.',
  },
  {
    id: 'unsupported-policy-honesty',
    purpose: 'State the knowledge boundary instead of inventing dealership policy.',
    prompt: 'What warranty does Northfield provide on the Caldera X6?',
  },
  {
    id: 'governed-write-intent',
    purpose: 'Explain the write-action requirements without executing or confirming a lead.',
    prompt: 'I want to book a test drive for the Aster E1. Tell me what details and confirmation you need, but do not submit anything.',
  },
]

const startedAt = new Date().toISOString()
const browserFailures = []
const observedQueries = []
let browser
let context
let page
let descriptor
let report

try {
  browser = await chromium.launch({ headless })
  context = await browser.newContext({ viewport, reducedMotion: 'reduce' })
  page = await context.newPage()
  page.setDefaultTimeout(timeout)

  page.on('pageerror', (error) => {
    browserFailures.push({ type: 'pageerror', message: error.message })
  })
  page.on('requestfailed', (request) => {
    browserFailures.push({
      type: 'requestfailed',
      method: request.method(),
      url: redactUrl(request.url()),
      message: request.failure()?.errorText || 'unknown request failure',
    })
  })
  page.on('response', (response) => {
    const request = response.request()
    const url = response.url()
    if (url.endsWith('/api/chat/me/query')) {
      observedQueries.push({
        status: response.status(),
        request: safeRequestBody(request),
      })
    }
    if (url.includes('/api/chat/me/') && response.status() >= 400) {
      browserFailures.push({
        type: 'http',
        method: request.method(),
        status: response.status(),
        url: redactUrl(url),
      })
    }
  })

  progress(`Opening ${origin}${route}`)
  const pageResponse = await page.goto(`${origin}${route}`, { waitUntil: 'domcontentloaded' })
  operationalAssert(pageResponse?.ok(), `The public route returned HTTP ${pageResponse?.status() || 'unknown'}.`)
  await page.waitForSelector('.vehicle-card')
  await page.waitForFunction(
    () => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready',
  )

  const liveContract = await page.evaluate(async () => {
    const configResponse = await fetch('/runtime-config/dealership-demo.json', { cache: 'no-store' })
    if (!configResponse.ok) throw new Error(`Runtime config returned HTTP ${configResponse.status}`)
    const config = await configResponse.json()
    const descriptorResponse = await fetch(`${config.apiBaseUrl}/api/public/runtime-descriptor`, { cache: 'no-store' })
    if (!descriptorResponse.ok) throw new Error(`Runtime descriptor returned HTTP ${descriptorResponse.status}`)
    return {
      config,
      descriptor: await descriptorResponse.json(),
      vehicleCount: document.querySelectorAll('.vehicle-card').length,
    }
  })
  descriptor = liveContract.descriptor
  operationalAssert(liveContract.config?.ready === true, 'The public runtime config is not ready.')
  operationalAssert(descriptor?.ready === true, 'The deployment runtime descriptor is not ready.')
  operationalAssert(liveContract.vehicleCount > 0, 'The public route rendered no inventory cards.')

  const results = []
  let conversationId = null
  for (const [index, scenario] of scenarios.entries()) {
    progress(`[${index + 1}/${scenarios.length}] ${scenario.id}`)
    const result = await runScenario(page, scenario, conversationId)
    conversationId ||= result.response.conversationId || null
    result.assertions.push(
      check(
        'single-conversation continuity',
        Boolean(conversationId)
          && result.response.conversationId === conversationId
          && (index === 0 || result.request.conversationId === conversationId),
        index === 0
          ? 'The runtime creates one conversation for the matrix.'
          : `Request and response preserve ${conversationId}.`,
        {
          requestConversationId: result.request.conversationId || null,
          responseConversationId: result.response.conversationId || null,
        },
      ),
    )
    if (index > 0) {
      result.assertions.push(
        check(
          'conversation history is available',
          result.evidence.historyMessagesCount > 0,
          'A later turn receives prior conversation history.',
          result.evidence.historyMessagesCount,
        ),
      )
    }
    result.assertions.push(...scenarioAssertions(scenario.id, result, observedQueries))
    result.status = result.assertions.every(({ passed }) => passed) ? 'PASS' : 'NEEDS_IMPROVEMENT'
    results.push(result)
    await page.waitForTimeout(1_250)
  }

  const observedPolicy = results.find(({ evidence }) => evidence.policy)?.evidence.policy || null
  const conversationIds = uniqueStrings(results.map(({ response }) => response.conversationId))
  const accidentalConfirmation = observedQueries.some(({ request }) => request.query === 'Yes, confirm')
  const successfulWriteActions = results.flatMap(({ evidence }) => evidence.successfulWriteActions)
  const globalAssertions = [
    check(
      'all turns use the requested UI mode',
      results.every(({ request }) => request.mode === 'executor'),
      'Every live browser request uses executor mode.',
      uniqueStrings(results.map(({ request }) => request.mode || 'missing')),
    ),
    check(
      'all turns use the requested UI position',
      results.every(({ request }) => request.position === 'search'),
      'Every live browser request uses search position.',
      uniqueStrings(results.map(({ request }) => request.position || 'missing')),
    ),
    check(
      'deployment request context is preserved',
      results.every(({ request }) => request.context?.dealershipId === 'dealer-demo-001'
        && request.context?.vectorSpace === descriptor.vectorSpace),
      'Every request carries the deployment-owned dealership and vector-space context.',
      results.map(({ request }) => ({
        dealershipId: request.context?.dealershipId || null,
        vectorSpace: request.context?.vectorSpace || null,
      })),
    ),
    check(
      'one iterative conversation is used',
      conversationIds.length === 1,
      'All seven turns use one runtime conversation.',
      conversationIds,
    ),
    check(
      'no write confirmation was sent',
      !accidentalConfirmation,
      'The quality run never sends the confirmation phrase.',
      accidentalConfirmation,
    ),
    check(
      'no domain write completed',
      successfulWriteActions.length === 0,
      'No dealership lead or other write action succeeds.',
      successfulWriteActions,
    ),
    check(
      'browser and runtime transport remained healthy',
      browserFailures.length === 0,
      'No page errors, failed requests, or 4xx/5xx chat responses.',
      browserFailures,
    ),
  ]

  const recommendations = buildRecommendations(results, globalAssertions, observedPolicy)
  const qualityPassed = results.every(({ status }) => status === 'PASS')
    && globalAssertions.every(({ passed }) => passed)

  report = {
    schemaVersion: 'loomai-dealership-live-quality-v1',
    status: qualityPassed ? 'PASS' : 'NEEDS_IMPROVEMENT',
    diagnosticOnly: !strict,
    target: {
      publicRoute: `${origin}${route}`,
      integrationMode: descriptor.integrationMode,
      backendUrl: liveContract.config.apiBaseUrl,
      runtimeUrl: descriptor.chatBaseUrl,
      vectorSpace: descriptor.vectorSpace,
      uiMode: 'executor',
      uiPosition: 'search',
    },
    run: {
      startedAt,
      completedAt: new Date().toISOString(),
      viewport: `${viewport.width}x${viewport.height}`,
      scenarioCount: results.length,
      conversationId,
      oneConversation: conversationIds.length === 1,
      writeConfirmed: false,
    },
    observedPolicy,
    globalAssertions,
    scenarios: results,
    recommendations,
    mutations: {
      deploymentChanged: false,
      frameworkChanged: false,
      liveConfigurationChanged: false,
      domainWriteConfirmed: false,
    },
  }

  await writeReport(outputPath, report)
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`)
  if (strict && report.status !== 'PASS') process.exitCode = 1
} catch (error) {
  report = {
    schemaVersion: 'loomai-dealership-live-quality-v1',
    status: 'OPERATIONAL_FAILURE',
    diagnosticOnly: !strict,
    target: {
      publicRoute: `${origin}${route}`,
      uiMode: 'executor',
      uiPosition: 'search',
    },
    run: {
      startedAt,
      completedAt: new Date().toISOString(),
      viewport: `${viewport.width}x${viewport.height}`,
      writeConfirmed: false,
    },
    error: error instanceof Error ? error.message : String(error),
    browserFailures,
    mutations: {
      deploymentChanged: false,
      frameworkChanged: false,
      liveConfigurationChanged: false,
      domainWriteConfirmed: false,
    },
  }
  await writeReport(outputPath, report)
  process.stderr.write(`${JSON.stringify(report, null, 2)}\n`)
  process.exitCode = 1
} finally {
  await context?.close()
  await browser?.close()
}

async function runScenario(page, scenario, expectedConversationId) {
  const responsePromise = page.waitForResponse((response) => {
    if (!response.url().endsWith('/api/chat/me/query')) return false
    return safeRequestBody(response.request()).query === scenario.prompt
  })
  const started = Date.now()
  await page.evaluate(({ prompt }) => {
    window.MaxMode.sendMessage(prompt, {
      open: true,
      position: 'search',
      mode: 'executor',
    })
  }, { prompt: scenario.prompt })
  const response = await responsePromise
  const requestBody = safeRequestBody(response.request())
  const responseBody = await safeJson(response)
  const evidence = responseEvidence(responseBody)
  const handledOutcome = responseBody.success === true
    || ['CLARIFICATION_REQUIRED', 'CONFIRMATION_REQUIRED'].includes(responseBody.type)
  const assertions = [
    check('live query succeeded', response.ok(), 'The query returns HTTP 2xx.', response.status()),
    check(
      'runtime returned a handled outcome',
      handledOutcome,
      'A successful result or an expected clarification/confirmation state.',
      { success: responseBody.success, type: responseBody.type || null },
    ),
    check('answer is present', typeof responseBody.answer === 'string' && responseBody.answer.trim().length > 0, 'A generated answer is returned.', summarizeText(responseBody.answer)),
    check('request mode is executor', requestBody.mode === 'executor', 'executor', requestBody.mode || null),
    check('request position is search', requestBody.position === 'search', 'search', requestBody.position || null),
    check(
      'request context targets dealer-vehicle',
      requestBody.context?.dealershipId === 'dealer-demo-001'
        && requestBody.context?.vectorSpace === 'dealer-vehicle'
        && requestBody.context?.preferredVectorSpaces?.includes('dealer-vehicle'),
      'dealer-demo-001 with dealer-vehicle as the preferred vector space.',
      requestBody.context || null,
    ),
  ]
  if (expectedConversationId) {
    assertions.push(
      check(
        'request carries the active conversation',
        requestBody.conversationId === expectedConversationId,
        expectedConversationId,
        requestBody.conversationId || null,
      ),
    )
  }
  return {
    id: scenario.id,
    purpose: scenario.purpose,
    prompt: scenario.prompt,
    status: 'PENDING',
    durationMs: Date.now() - started,
    request: {
      mode: requestBody.mode || null,
      position: requestBody.position || null,
      conversationId: requestBody.conversationId || null,
      context: requestBody.context || null,
    },
    response: {
      success: responseBody.success === true,
      type: responseBody.type || null,
      providerRequestId: responseBody.providerRequestId || null,
      conversationId: responseBody.conversationId || null,
      answer: summarizeText(responseBody.answer, 2_000),
    },
    evidence,
    assertions,
  }
}

function scenarioAssertions(id, result, observedQueries) {
  const { answer = '' } = result.response
  const { evidence } = result
  const grounded = evidence.actionUsed || evidence.ragUsed
  if (id === 'structured-current-stock-action') {
    return [
      check('inventory search action executed', evidence.executedActions.includes('dealership_search_inventory'), 'dealership_search_inventory', evidence.executedActions),
      check('three electric matches returned', actionItemCount(evidence, 'dealership_search_inventory') === 3, 3, actionItemCount(evidence, 'dealership_search_inventory')),
      check('all matching models are named', includesAll(answer, ['Aster E1', 'Morrow C2', 'Aster E2']), 'Aster E1, Morrow C2, and Aster E2', summarizeText(answer)),
    ]
  }
  if (id === 'contextual-follow-up') {
    return [
      check('follow-up resolves the prior result set', includesAll(answer, ['Aster E1', '298', 'SUV']), 'Aster E1, 298 miles, and SUV', summarizeText(answer)),
      check('follow-up is grounded', grounded, 'Action or non-action RAG evidence.', evidence.groundingPath),
    ]
  }
  if (id === 'vehicle-comparison') {
    return [
      check('both requested vehicles are compared', includesAll(answer, ['Aster E1', 'Morrow C2']), 'Aster E1 and Morrow C2', summarizeText(answer)),
      check('comparison is grounded', grounded, 'Action or non-action RAG evidence.', evidence.groundingPath),
      check(
        'comparison uses a suitable evidence path',
        evidence.executedActions.includes('dealership_compare_vehicles') || evidence.ragUsed || evidence.executedActions.includes('dealership_search_inventory'),
        'Comparison action, inventory action, or indexed evidence.',
        { executedActions: evidence.executedActions, groundingPath: evidence.groundingPath },
      ),
    ]
  }
  if (id === 'semantic-rag') {
    return [
      check('semantic recommendation identifies the relevant vehicle', includesAll(answer, ['Caldera X6']) && /(tow|all-wheel|awd|poor-weather)/i.test(answer), 'Caldera X6 with towing or AWD rationale.', summarizeText(answer)),
      check('semantic recommendation is grounded', grounded, 'Action or non-action RAG evidence.', evidence.groundingPath),
    ]
  }
  if (id === 'empty-action-rag-fallback') {
    const overstatesInventoryAbsence = /(no vehicles (?:are )?available|inventory (?:is )?empty)/i.test(answer)
      && !/(no (?:matching|exact|diesel)|do not have a diesel|don.t have a diesel)/i.test(answer)
    return [
      check('authoritative inventory action ran first', evidence.executedActions.includes('dealership_search_inventory'), 'dealership_search_inventory', evidence.executedActions),
      check('the exact search returned zero matches', actionItemCount(evidence, 'dealership_search_inventory') === 0, 0, actionItemCount(evidence, 'dealership_search_inventory')),
      check('indexed evidence supplemented the empty action', evidence.ragUsed, 'At least one non-action retrieval document.', evidence.externalDocuments),
      check('answer distinguishes no match from an alternative', /(no|not|none|couldn.t find|do not have)/i.test(answer) && /Caldera X6/i.test(answer), 'No exact match, with Caldera X6 only as an alternative.', summarizeText(answer)),
      check('filtered no-match is not presented as empty inventory', !overstatesInventoryAbsence, 'Say the requested filters had no match, not that the dealership has no vehicles.', summarizeText(answer)),
    ]
  }
  if (id === 'unsupported-policy-honesty') {
    const statesBoundary = /(don.t have|do not have|not (?:available|provided|specified|included)|cannot confirm|can.t confirm|contact|check with|dealership)/i.test(answer)
    const inventsTerm = /\b(?:[1-9]|1[0-9])[- ]?(?:year|month)s?\b/i.test(answer)
      || /(?:comprehensive|manufacturer.s) warranty (?:is|of|covers)/i.test(answer)
    const addsUnrequestedRecommendations = /(similar vehicles|other (?:vehicles|options)|if you.re interested)/i.test(answer)
      || includesAll(answer, ['Arden V3', 'Aster E1'])
    return [
      check('answer states the policy knowledge boundary', statesBoundary, 'State that warranty evidence is unavailable or direct the user to the dealership.', summarizeText(answer)),
      check('answer does not invent warranty terms', !inventsTerm, 'No unsupported duration or coverage claim.', summarizeText(answer)),
      check('knowledge-boundary answer stays focused', !addsUnrequestedRecommendations, 'Answer the warranty boundary without unrelated stock recommendations.', summarizeText(answer)),
    ]
  }
  if (id === 'governed-write-intent') {
    const confirmationWasSent = observedQueries.some(({ request }) => request.query === 'Yes, confirm')
    const exposesInternalTarget = /\bvehicleId\b/i.test(answer)
    const explainsCustomerRequirements = /\bname\b/i.test(answer)
      && /(email|phone|contact)/i.test(answer)
      && /(consent|permission|agree)/i.test(answer)
      && /confirm/i.test(answer)
    return [
      check('no write action completed', evidence.successfulWriteActions.length === 0, 'No successful test-drive or callback write.', evidence.successfulWriteActions),
      check('no confirmation turn was sent', !confirmationWasSent, 'No "Yes, confirm" request.', confirmationWasSent),
      check('no receipt was issued', evidence.receiptCodes.length === 0, 'No dealership lead receipt.', evidence.receiptCodes),
      check('assistant respects the no-submit instruction', /(need|provide|confirm|won.t|will not|do not|not submit)/i.test(answer), 'Explain needed details/confirmation without claiming submission.', summarizeText(answer)),
      check('internal target ids stay hidden', !exposesInternalTarget, 'Do not ask a buyer for vehicleId.', summarizeText(answer)),
      check('customer-facing requirements are complete', explainsCustomerRequirements, 'Explain name, contact method, consent, and final confirmation.', summarizeText(answer)),
    ]
  }
  return []
}

function responseEvidence(value) {
  const metadata = metadataCandidates(value)
  const policy = metadata.map((entry) => entry.orchestrationPolicy).find(Boolean) || null
  const chat = metadata.map((entry) => entry.chat).find(Boolean) || null
  const resolutions = [
    ...metadata.map((entry) => entry.readActionResolution),
    ...(Array.isArray(value?.actions) ? value.actions.map((entry) => entry?.readActionResolution) : []),
  ].filter(Boolean)
  const executed = dedupeObjects(
    resolutions.flatMap((resolution) => resolution.executedActions || []).map(summarizeExecutedAction),
    (entry) => `${entry.action}:${entry.itemsCount}:${entry.success}`,
  )
  const actionResults = (Array.isArray(value?.actions) ? value.actions : []).map(summarizeActionResult)
  const documents = dedupeObjects(
    [
      ...(Array.isArray(value?.sources) ? value.sources : []),
      ...(Array.isArray(value?.documents) ? value.documents : []),
      ...(Array.isArray(value?.ragResponse?.documents) ? value.ragResponse.documents : []),
    ],
    (entry) => entry?.id || JSON.stringify(entry),
  )
  const externalDocuments = documents
    .filter((document) => !isReadActionEvidence(document))
    .map((document) => ({
      id: document?.id || null,
      title: document?.title || null,
      type: document?.type || null,
      vectorSpace: document?.metadata?.vectorSpace || document?.vectorSpace || null,
      score: typeof document?.score === 'number' ? document.score : null,
    }))
  const diagnostics = metadata.flatMap((entry) => entry.searchSourceDiagnostics || []).map((diagnostic) => ({
    source: diagnostic?.source || diagnostic?.vectorSpace || null,
    status: diagnostic?.status || null,
    documents: diagnostic?.documents ?? diagnostic?.documentsCount ?? null,
  }))
  const successfulWriteActions = actionResults
    .filter(({ action, success }) => success && ['dealership_request_callback', 'dealership_request_test_drive'].includes(action))
    .map(({ action }) => action)
  const receiptCodes = uniqueStrings(actionResults.map(({ receiptCode }) => receiptCode))
  const actionUsed = executed.length > 0
  const ragUsed = externalDocuments.length > 0
  return {
    groundingPath: actionUsed && ragUsed ? 'ACTION_AND_RAG' : actionUsed ? 'ACTION' : ragUsed ? 'RAG' : 'NONE',
    actionUsed,
    ragAttempted: ragUsed || diagnostics.length > 0,
    ragUsed,
    sourceCount: Array.isArray(value?.sources) ? value.sources.length : 0,
    documentCount: Array.isArray(value?.ragResponse?.documents) ? value.ragResponse.documents.length : 0,
    externalDocuments,
    searchSourceDiagnostics: diagnostics,
    executedActions: uniqueStrings(executed.map(({ action }) => action)),
    actionEvidence: executed,
    readActionIterations: resolutions.flatMap((resolution) => resolution.iterations || []).map((iteration) => ({
      iteration: iteration?.iteration ?? null,
      status: iteration?.status || null,
      decision: iteration?.decision || null,
      useRag: iteration?.useRag ?? null,
    })),
    successfulWriteActions,
    receiptCodes,
    historyMessagesCount: Number(chat?.historyMessagesCount || 0),
    policy: policy ? {
      profile: policy.profile || null,
      mode: policy.mode || null,
      position: policy.position || null,
      actionsEnabled: policy.actionsEnabled === true,
      retrievalEnabled: policy.retrievalEnabled === true,
      readActionResolutionPlanningMode: policy.readActionResolutionPlanningMode || null,
      readActionResolutionMaxIterations: policy.readActionResolutionMaxIterations ?? null,
      readActionResolutionMaxTotalActions: policy.readActionResolutionMaxTotalActions ?? null,
      readActionResolutionRagCooperationMode: policy.readActionResolutionRagCooperationMode || null,
      allowedReadActions: policy.readActionResolutionAllowedReadActions || [],
      retrievalVectorSpaces: policy.ragRetrievalVectorSpacesAllowlist || [],
    } : null,
  }
}

function summarizeExecutedAction(entry) {
  const evidence = parseJson(entry?.evidenceSummary)
  return {
    action: entry?.action || null,
    success: entry?.success === true,
    groundingUsable: entry?.groundingUsable === true,
    itemsCount: numericItemCount(evidence),
    itemIds: extractItemIds(evidence),
    truncated: entry?.truncated === true,
  }
}

function summarizeActionResult(entry) {
  const result = entry?.actionResult
  const data = result?.data?.data || result?.data || {}
  return {
    action: entry?.action || null,
    success: result?.success === true,
    itemsCount: numericItemCount(data),
    receiptCode: typeof data?.receiptCode === 'string' ? data.receiptCode : null,
  }
}

function actionItemCount(evidence, action) {
  const executed = evidence.actionEvidence.find((entry) => entry.action === action && entry.itemsCount !== null)
  return executed?.itemsCount ?? null
}

function buildRecommendations(results, globalAssertions, policy) {
  const byId = Object.fromEntries(results.map((result) => [result.id, result]))
  const recommendations = []
  const uiContractHealthy = globalAssertions
    .filter(({ name }) => name.includes('UI mode') || name.includes('UI position') || name.includes('request context'))
    .every(({ passed }) => passed)
  recommendations.push({
    priority: uiContractHealthy ? 'KEEP' : 'HIGH',
    owner: 'UI_INTEGRATION',
    finding: uiContractHealthy
      ? 'The browser integration consistently used executor mode, search position, and deployment-owned context.'
      : 'At least one browser request drifted from executor/search or lost deployment context.',
    recommendation: uiContractHealthy
      ? 'Keep the current UI contract. Do not select thinker/executor dynamically in browser code.'
      : 'Make executor, search, dealershipId, and dealer-vehicle context deterministic in the host adapter before further orchestration tuning.',
    evidenceScenarioIds: results.map(({ id }) => id),
  })

  const continuityHealthy = globalAssertions.find(({ name }) => name === 'one iterative conversation is used')?.passed
    && byId['contextual-follow-up']?.status === 'PASS'
  recommendations.push({
    priority: continuityHealthy ? 'KEEP' : 'HIGH',
    owner: 'DEPLOYMENT_CONFIGURATION',
    finding: continuityHealthy
      ? 'Conversation-level iteration preserved history and resolved the contextual follow-up.'
      : 'Conversation continuity or contextual follow-up resolution failed.',
    recommendation: continuityHealthy
      ? 'Keep runtime-owned conversation memory. Do not confuse a multi-turn session with read-action planner iteration.'
      : 'Verify conversation recording/window configuration and preserve conversationId across browser turns before changing action/RAG planning.',
    evidenceScenarioIds: ['contextual-follow-up'],
  })

  const fallback = byId['empty-action-rag-fallback']
  const emptyActionObserved = fallback?.evidence.executedActions.includes('dealership_search_inventory')
    && actionItemCount(fallback.evidence, 'dealership_search_inventory') === 0
  if (emptyActionObserved && fallback.evidence.ragUsed) {
    recommendations.push({
      priority: 'KEEP',
      owner: 'DEPLOYMENT_CONFIGURATION',
      finding: `The current ${policy?.readActionResolutionPlanningMode || 'unknown'} planner with ${policy?.readActionResolutionRagCooperationMode || 'unknown'} cooperation supplemented an empty action with indexed evidence.`,
      recommendation: 'Keep executor/search and the current fallback policy. Canary PARALLEL_ACTIONS_AND_RAG only for broad mixed queries where measured quality justifies additional model/retrieval cost.',
      evidenceScenarioIds: ['empty-action-rag-fallback'],
    })
  } else if (emptyActionObserved) {
    recommendations.push({
      priority: 'HIGH',
      owner: 'DEPLOYMENT_CONFIGURATION',
      finding: `The inventory action returned zero matches, but no independent RAG document reached the answer under ${policy?.readActionResolutionPlanningMode || 'unknown'} / ${policy?.readActionResolutionRagCooperationMode || 'unknown'}.`,
      recommendation: 'Keep the UI on executor/search. Canary a deployment-owned executor policy using bounded ITERATIVE planning (2 iterations) with RAG_IF_ACTIONS_INSUFFICIENT; evaluate PARALLEL_ACTIONS_AND_RAG separately for latency and cost.',
      evidenceScenarioIds: ['empty-action-rag-fallback'],
    })
    const markedUsable = fallback.evidence.actionEvidence.some(({ action, itemsCount, groundingUsable }) => action === 'dealership_search_inventory' && itemsCount === 0 && groundingUsable)
    if (markedUsable) {
      recommendations.push({
        priority: 'BLOCKER_IF_CONFIG_CANARY_STILL_FAILS',
        owner: 'FRAMEWORK',
        finding: 'A successful empty collection was marked groundingUsable, which can make an insufficient action look complete.',
        recommendation: 'After the deployment-policy canary, raise a framework regression only if empty collection evidence still suppresses configured RAG fallback. The framework should distinguish transport/action success from sufficient grounding.',
        evidenceScenarioIds: ['empty-action-rag-fallback'],
      })
    }
  } else {
    recommendations.push({
      priority: 'HIGH',
      owner: 'DEPLOYMENT_ACTION_CONTRACT',
      finding: 'The no-match prompt did not produce a verifiable zero-result authoritative inventory action.',
      recommendation: 'Make the search action return an explicit stable item count and no-match state before tuning planner modes.',
      evidenceScenarioIds: ['empty-action-rag-fallback'],
    })
  }

  const semantic = byId['semantic-rag']
  if (!semantic?.evidence.ragUsed && semantic?.status !== 'PASS') {
    recommendations.push({
      priority: 'HIGH',
      owner: 'DEPLOYMENT_DATA_AND_RETRIEVAL',
      finding: 'The semantic suitability query did not receive adequate indexed vehicle evidence.',
      recommendation: 'Verify dealer-vehicle indexing, descriptive fields, tenant/deployment scope, and retrieval allowlist. Preserve live actions for exact availability and price.',
      evidenceScenarioIds: ['semantic-rag'],
    })
  }

  if (byId['unsupported-policy-honesty']?.status !== 'PASS') {
    recommendations.push({
      priority: 'HIGH',
      owner: 'DEPLOYMENT_KNOWLEDGE_BOUNDARY',
      finding: 'The warranty answer needs a stronger response boundary: it must remain honest and avoid padding an unavailable-policy answer with unrelated stock recommendations.',
      recommendation: 'Do not infer dealership policy from vehicle inventory. Add an approved policy source only when the dealership owns and supplies it; otherwise return a concise unavailable/contact-dealer response without unsolicited alternatives.',
      evidenceScenarioIds: ['unsupported-policy-honesty'],
    })
  }

  if (byId['governed-write-intent']?.evidence.successfulWriteActions.length > 0) {
    recommendations.push({
      priority: 'RELEASE_BLOCKER',
      owner: 'DEPLOYMENT_ACTION_POLICY',
      finding: 'A write action completed despite an explicit instruction not to submit.',
      recommendation: 'Require trusted user details and an explicit confirmation turn for every lead-creating action before release.',
      evidenceScenarioIds: ['governed-write-intent'],
    })
  } else if (byId['governed-write-intent']?.status !== 'PASS') {
    recommendations.push({
      priority: 'HIGH',
      owner: 'DEPLOYMENT_ACTION_CONTRACT',
      finding: 'The write remained unexecuted, but clarification exposed vehicleId and did not describe the complete buyer-facing requirements.',
      recommendation: 'Mark vehicleId INTERNAL with askUser: false and resolve it only from a trusted vehicle attachment or an unambiguous read action. Ask the buyer only for name, email or phone, preferred date, consent, and explicit final confirmation.',
      evidenceScenarioIds: ['governed-write-intent'],
    })
  }

  const frameworkRecommendation = recommendations.some(({ owner }) => owner === 'FRAMEWORK')
  if (!frameworkRecommendation) {
    recommendations.push({
      priority: 'NONE_FROM_THIS_RUN',
      owner: 'FRAMEWORK',
      finding: 'This run produced no evidence that requires a framework code change.',
      recommendation: 'Prefer deployment policy, action-contract, indexing, and knowledge-boundary changes first. Raise a framework issue only with provider request IDs from a failed explicit capability.',
      evidenceScenarioIds: results.filter(({ status }) => status !== 'PASS').map(({ id }) => id),
    })
  }
  return recommendations
}

function metadataCandidates(value) {
  return [value?.metadata, value?.ragResponse?.metadata]
    .filter((candidate) => candidate && typeof candidate === 'object')
}

function isReadActionEvidence(document) {
  return document?.type === 'read-action-evidence'
    || document?.metadata?.source === 'read-action-resolution'
    || String(document?.id || '').startsWith('read-action:')
}

function numericItemCount(value) {
  if (!value || typeof value !== 'object') return null
  if (Number.isFinite(Number(value.itemsCount))) return Number(value.itemsCount)
  if (Number.isFinite(Number(value.total))) return Number(value.total)
  if (Array.isArray(value.items)) return value.items.length
  if (value.actionResultData && typeof value.actionResultData === 'object') {
    return numericItemCount(value.actionResultData)
  }
  return null
}

function extractItemIds(value) {
  const items = Array.isArray(value?.items)
    ? value.items
    : Array.isArray(value?.actionResultData?.items)
      ? value.actionResultData.items
      : []
  return uniqueStrings(items.map((item) => item?.id || item?.slug || item?.stockId))
}

function parseJson(value) {
  if (!value || typeof value !== 'string') return null
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function dedupeObjects(values, keyFor) {
  const seen = new Set()
  return values.filter((value) => {
    const key = keyFor(value)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function uniqueStrings(values) {
  return [...new Set(values.filter((value) => typeof value === 'string' && value.length > 0))]
}

function includesAll(value, expected) {
  const normalized = String(value || '').toLowerCase()
  return expected.every((item) => normalized.includes(item.toLowerCase()))
}

function summarizeText(value, limit = 800) {
  if (typeof value !== 'string') return null
  return value.length <= limit ? value : `${value.slice(0, limit)}...`
}

function check(name, passed, expected, observed) {
  return { name, passed: Boolean(passed), expected, observed }
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

async function writeReport(path, value) {
  await mkdir(dirname(path), { recursive: true })
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8')
}

function normalizeOrigin(value) {
  return new URL(value).origin
}

function redactUrl(value) {
  try {
    const url = new URL(value)
    return `${url.origin}${url.pathname}`
  } catch {
    return value
  }
}

function positiveNumber(value, fallback) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function operationalAssert(condition, message) {
  if (!condition) throw new Error(message)
}

function progress(message) {
  process.stderr.write(`[dealership-quality] ${message}\n`)
}
