import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { isAbsolute, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { parseArgs } from 'node:util'

const HELP = `Usage:
  node hosted-dealership-workspace-journey.mjs --scope /private/scope.json --root /private/NEW-run
    [--mode read-only|full] [--credentials /private/customer.json]
    [--operator-key /private/operator.json] [--operations /private/operations.mjs]
    [--document-evidence /private/registered-sources.json]
    [--allow-one-synthetic-receipt] [--headed]

Default: read-only (compound stock/policy question, reload, basic mobile composer).
Full: also protected-action sign-in/cancel, real login, vehicle selection and Basic Inbox handover.
Full requires credentials, operator-key, operations and scope.receiptStore.
Only --allow-one-synthetic-receipt permits the signed-in proposal and one UI confirmation.
Every run needs a NEW private output directory. Failed runs are never resumed/replayed.
--self-test checks local guards only; --help does not open a browser or contact a service.
See hosted-dealership-workspace-journey.README.md for the exact scope contract.
`
const MAX_SENDS = 10
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const hash = value => createHash('sha256').update(value).digest('hex')
const check = (condition, code) => { if (!condition) throw new Error(code) }
const nonblank = value => typeof value === 'string' && value.trim() === value && value.length > 0
const readJson = path => JSON.parse(readFileSync(path, 'utf8'))
const settled = promise => promise.then(value => ({ value }), error => ({ error }))
const unwrap = result => { if (result.error) throw result.error; return result.value }

export function httpsOrigin(value) {
  check(nonblank(value), 'HTTPS_ORIGIN_REQUIRED')
  const url = new URL(value)
  check(url.protocol === 'https:' && !url.username && !url.password
    && url.pathname === '/' && !url.search && !url.hash, 'HTTPS_ORIGIN_REQUIRED')
  return url.origin
}

export function validateScope(input, mode, sourceIds = input.sourceIds) {
  check(['read-only', 'full'].includes(mode), 'MODE_NOT_SUPPORTED')
  for (const name of ['customerId', 'tenantId', 'deploymentId', 'releaseId', 'installationId', 'assignmentRevision']) {
    check(nonblank(input[name]), `SCOPE_${name}_REQUIRED`)
  }
  for (const name of ['customerId', 'tenantId', 'deploymentId', 'releaseId']) {
    check(/^[a-zA-Z0-9_-]{1,100}$/.test(input[name]), `SCOPE_${name}_INVALID`)
  }
  check(/^awi_pub_[a-f0-9]{32}$/.test(input.installationId), 'INSTALLATION_ID_INVALID')
  check(!input.runtimeBaseUrl && !input.runtimeUrl, 'RUNTIME_MUST_BE_DISCOVERED_FROM_BROWSER_MANIFEST')
  check(Array.isArray(sourceIds) && sourceIds.length > 0 && sourceIds.every(nonblank)
    && new Set(sourceIds).size === sourceIds.length, 'REGISTERED_DOCUMENT_SOURCE_IDS_REQUIRED')
  const scope = { ...input, appUrl: httpsOrigin(input.appUrl), sourceIds: [...sourceIds] }
  if (mode === 'full') {
    scope.inboxUrl = httpsOrigin(input.inboxUrl)
    check(nonblank(input.vehicleLabel), 'UI_VEHICLE_LABEL_REQUIRED')
    check(scope.inboxUrl !== scope.appUrl, 'SEPARATE_BASIC_INBOX_ORIGIN_REQUIRED')
    check(input.receiptStore, 'EXTERNAL_RECEIPT_PROOF_REQUIRED')
  }
  if (input.receiptStore) {
    check(['staging', 'production'].includes(input.receiptStore.environment), 'RECEIPT_SSH_ENVIRONMENT_INVALID')
    for (const name of ['container', 'user', 'database']) {
      check(/^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,100}$/.test(input.receiptStore[name]), `RECEIPT_${name}_INVALID`)
    }
  }
  return scope
}

export function policyCitations(body, sourceIds) {
  const allowed = new Set(sourceIds)
  const documents = new Map((body.ragResponse?.documents ?? []).map(document => [document.id, document]))
  const citations = (body.sources ?? []).flatMap(source => {
    const document = source.documentId ? documents.get(source.documentId) : source
    if (!document || !allowed.has(document.metadata?._aiDocumentSourceId)) return []
    if (source.documentId && (source.vectorSpace !== document.metadata?.vectorSpace
      || source.sourceId !== document.metadata?.knowledgeSourceId)) return []
    return [{ id: document.id, sourceId: document.metadata._aiDocumentSourceId, title: document.title }]
  })
  check(citations.length > 0, 'ACTUAL_REGISTERED_DOCUMENT_CITATION_REQUIRED')
  return citations
}

export function redact(value, secrets = []) {
  if (Array.isArray(value)) return value.map(item => redact(item, secrets))
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [
    key, /^(?:password|api[-_]?key|publishableKey|access[-_]?token|refresh[-_]?token|id[-_]?token|authorization|cookie|secret|token)$/i.test(key)
      ? '[REDACTED]' : redact(item, secrets),
  ]))
  if (typeof value !== 'string') return value
  return secrets.filter(secret => typeof secret === 'string' && secret.length > 3)
    .reduce((text, secret) => text.split(secret).join('[REDACTED]'), value)
    .replace(/\bBearer\s+[^\s"<>]+/gi, 'Bearer [REDACTED]')
    .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g, '[REDACTED_JWT]')
}

export function receiptSql(scope) {
  const literal = value => "'" + value.replaceAll("'", "''") + "'"
  return `BEGIN READ ONLY; SET LOCAL statement_timeout = '5s';
SELECT COALESCE(json_agg(row_to_json(proof) ORDER BY proof.receipt_code), '[]'::json)
FROM (SELECT receipt_code, action_type, vehicle_id, created_at
 FROM dealership_lead_request
 WHERE receiver_customer_id = ${literal(scope.customerId)}
 AND receiver_tenant_id = ${literal(scope.tenantId)}
 AND receiver_deployment_id = ${literal(scope.deploymentId)}) proof; COMMIT;`
}

export function assertReceiptDelta(before, after, expectedDelta, expectedCode) {
  const actual = new Map(after.map(row => [row.receipt_code, row]))
  for (const row of before) assert.deepEqual(actual.get(row.receipt_code), row, 'PREEXISTING_RECEIPT_CHANGED')
  const old = new Set(before.map(row => row.receipt_code))
  const created = after.filter(row => !old.has(row.receipt_code))
  check(created.length === expectedDelta && after.length === before.length + expectedDelta, 'EXTERNAL_RECEIPT_DELTA_INVALID')
  if (expectedCode) check(created[0]?.receipt_code === expectedCode, 'EXTERNAL_RECEIPT_CODE_MISMATCH')
  return created
}

function discoveredRoute(manifest, name, conversationId) {
  let path = manifest.connection.routes[name]
  check(nonblank(path), `MANIFEST_ROUTE_${name}_REQUIRED`)
  if (conversationId) path = path.replace('{conversationId}', encodeURIComponent(conversationId))
  const url = new URL(path, manifest.connection.runtimeBaseUrl + '/')
  check(url.origin === httpsOrigin(manifest.connection.runtimeBaseUrl), 'ROUTE_ESCAPES_DISCOVERED_RUNTIME')
  return url
}

function createDiscovery(page, scope, save, fail) {
  let bootstrap, manifest, sessionSeen = false, failure, sequence = 0
  const pending = new Set()
  page.on('response', response => {
    const url = new URL(response.url())
    const kind = url.pathname === '/api/public/ai-workspace/apps/bootstrap' ? 'bootstrap'
      : url.pathname === `/api/public/ai-workspace/installations/${scope.installationId}/manifest` ? 'manifest'
        : manifest && url.href === new URL(manifest.connection.anonymousBootstrap.url, manifest.connection.runtimeBaseUrl + '/').href
          ? 'session' : null
    if (!kind) return
    const index = ++sequence
    const task = (async () => {
      const raw = await response.text()
      let body
      try { body = JSON.parse(raw) } catch { save(`discovery-${index}.private.json`, { kind, status: response.status(), raw }); throw new Error('DISCOVERY_RESPONSE_NOT_JSON') }
      save(`discovery-${index}.private.json`, { kind, url: url.href, status: response.status(), body })
      check(response.status() === 200, 'PUBLIC_DISCOVERY_FAILED')
      if (kind !== 'session') {
        check(body.installationId === scope.installationId && body.delivery?.mode === 'standalone'
          && httpsOrigin(body.delivery.appUrl) === scope.appUrl, 'PUBLIC_APP_IDENTITY_MISMATCH')
      }
      if (kind === 'bootstrap') {
        check(body.schemaVersion === 'loomai-ai-workspace-app-bootstrap-v1', 'BOOTSTRAP_SCHEMA_MISMATCH')
        check(body.interactionPolicy === 'public-with-protected-capabilities', 'PROTECTED_CAPABILITY_APP_REQUIRED')
        check(body.authentication?.profileCode === 'supabase', 'SUPABASE_APP_REQUIRED')
        check(new URL(body.installer.url).origin === url.origin, 'INSTALLER_ORIGIN_MISMATCH')
        if (bootstrap) check(bootstrap.origin === url.origin, 'BOOTSTRAP_ORIGIN_CHANGED')
        bootstrap = { ...body, origin: url.origin }
      } else if (kind === 'manifest') {
        check(body.schemaVersion === 'loomai-ai-workspace-installation-v2'
          && body.assignmentRevision === scope.assignmentRevision
          && body.connection?.mode === 'public-runtime-anonymous', 'PUBLIC_ASSIGNMENT_MISMATCH')
        httpsOrigin(body.connection.runtimeBaseUrl)
        for (const name of ['queryUrl', 'conversationMessageUrlTemplate', 'suggestionsUrl', 'handoverRequestUrlTemplate']) discoveredRoute(body, name)
        for (const endpoint of [body.connection.anonymousBootstrap.url, body.connection.anonymousBootstrap.renewUrl,
          body.connection.credentialBroker?.url]) {
          check(nonblank(endpoint), 'RUNTIME_AUTH_ROUTE_REQUIRED')
          check(new URL(endpoint, body.connection.runtimeBaseUrl + '/').origin === httpsOrigin(body.connection.runtimeBaseUrl), 'AUTH_ROUTE_ESCAPES_RUNTIME')
        }
        if (manifest) check(manifest.connection.runtimeBaseUrl === body.connection.runtimeBaseUrl, 'RUNTIME_CHANGED_WITHIN_RUN')
        manifest = { ...body, origin: url.origin }
      } else {
        for (const name of ['customerId', 'tenantId', 'deploymentId']) check(body[name] === scope[name], 'ANONYMOUS_RUNTIME_SCOPE_MISMATCH')
        check(body.success === true && nonblank(body.sessionId), 'ANONYMOUS_RUNTIME_SESSION_REQUIRED')
        sessionSeen = true
      }
      if (bootstrap && manifest) {
        check(bootstrap.origin === manifest.origin, 'MANIFEST_PLATFORM_ORIGIN_MISMATCH')
        check(manifest.authentication?.interactionPolicy === bootstrap.interactionPolicy
          && manifest.authentication.profileCode === 'supabase', 'MANIFEST_AUTHENTICATION_MISMATCH')
      }
    })().catch(error => { failure = error; fail(error.message) })
    pending.add(task)
    void task.finally(() => pending.delete(task))
  })
  async function ready(requireSession = false) {
    const deadline = Date.now() + 90_000
    while (!(bootstrap && manifest && (!requireSession || sessionSeen))) {
      if (failure) throw failure
      check(Date.now() < deadline, 'NORMAL_PUBLIC_DISCOVERY_NOT_OBSERVED')
      await sleep(25)
    }
    if (failure) throw failure
    return { bootstrap, manifest }
  }
  return { ready, current: () => ({ bootstrap, manifest }), assert: () => { if (failure) throw failure },
    flush: async () => { while (pending.size) await Promise.allSettled([...pending]) } }
}

export async function runJourney(args) {
  process.umask(0o077)
  const mode = args.mode ?? 'read-only'
  for (const name of ['scope', 'root']) check(isAbsolute(args[name] ?? ''), `ABSOLUTE_${name}_PATH_REQUIRED`)
  for (const name of ['credentials', 'operator-key', 'operations', 'document-evidence']) {
    if (args[name]) check(isAbsolute(args[name]), `ABSOLUTE_${name}_PATH_REQUIRED`)
  }
  if (mode === 'full') for (const name of ['credentials', 'operator-key', 'operations']) check(isAbsolute(args[name] ?? ''), `ABSOLUTE_${name}_PATH_REQUIRED`)
  check(!args['allow-one-synthetic-receipt'] || mode === 'full', 'BUSINESS_WRITE_REQUIRES_FULL_MODE')
  const input = readJson(args.scope)
  let sources = input.sourceIds
  if (args['document-evidence']) {
    const evidence = readJson(args['document-evidence'])
    for (const name of ['customerId', 'tenantId', 'deploymentId', 'releaseId']) check(evidence[name] === input[name], 'DOCUMENT_EVIDENCE_SCOPE_MISMATCH')
    sources = evidence.sourceIds
    if (input.sourceIds) assert.deepEqual([...input.sourceIds].sort(), [...sources].sort(), 'DOCUMENT_SOURCE_IDS_DISAGREE')
  }
  const scope = validateScope(input, mode, sources)
  const credentials = args.credentials ? readJson(args.credentials) : null
  const operator = args['operator-key'] ? readJson(args['operator-key']) : null
  const operatorKey = operator?.apiKey ?? operator?.value?.apiKey
  if (mode === 'full') {
    check(nonblank(credentials?.email) && nonblank(credentials?.password) && nonblank(credentials?.userId), 'CUSTOMER_EMAIL_PASSWORD_USER_ID_REQUIRED')
    if (credentials.canaryCustomerId) check(credentials.canaryCustomerId === scope.customerId, 'CREDENTIAL_CUSTOMER_SCOPE_MISMATCH')
    check(nonblank(operatorKey), 'ONE_TIME_OPERATOR_KEY_REQUIRED')
    if (operator.deploymentId) check(operator.deploymentId === scope.deploymentId, 'OPERATOR_CREDENTIAL_SCOPE_MISMATCH')
  }
  if (args['allow-one-synthetic-receipt']) {
    const request = scope.testDrive
    check(request && nonblank(request.name) && nonblank(request.phone) && /^\d{4}-\d{2}-\d{2}$/.test(request.date), 'EXPLICIT_SYNTHETIC_TEST_DRIVE_DETAILS_REQUIRED')
    check(Date.parse(request.date + 'T23:59:59Z') >= Date.now(), 'TEST_DRIVE_DATE_IS_IN_THE_PAST')
  }
  check(!scope.receiptStore || args.operations, 'OPERATIONS_MODULE_REQUIRED_FOR_RECEIPT_PROOF')
  const secrets = [credentials?.password, operatorKey]
  mkdirSync(args.root, { mode: 0o700 }) // Exclusive directory creation is the durable no-replay gate.
  const save = (name, value) => writeFileSync(resolve(args.root, name), JSON.stringify(redact(value, secrets), null, 2) + '\n', { flag: 'wx', mode: 0o600 })
  const report = { startedAt: new Date().toISOString(), mode, status: 'RUNNING', scope,
    userSends: 0, aiSends: 0, humanSends: 0, automaticSuggestions: 0, checks: {}, blocked: [],
    businessWriteAllowed: args['allow-one-synthetic-receipt'] === true, confirmationClicks: 0, clarificationCount: 0,
    semanticAnswerReview: 'REQUIRED: inspect captured answers and screenshots; technical assertions are not semantic approval.' }
  save('intent.private.json', { at: report.startedAt, scope, mode, businessWriteAllowed: report.businessWriteAllowed,
    maxUserSends: MAX_SENDS, scopeSha256: hash(readFileSync(args.scope)), meaning: 'One-shot intent, not proof of completion or zero effect.' })
  let browser, page, operatorPage, discovery, stage = 'load-local-dependencies', fatal, expected, handoverExpected = false
  let passwordExpected = false, brokerExpected = false, operatorExpected, receiptBefore, receiptAfter, receiptReads = 0, commandSequence = 0
  const fail = code => { fatal ??= code }
  const requireHealthy = () => { if (fatal) throw new Error(fatal); discovery?.assert() }
  const watch = (target, predicate, timeout = 180_000) => settled(target.waitForResponse(predicate, { timeout }))
  const snap = (name, target = page) => target.screenshot({ path: resolve(args.root, name + '.png'), fullPage: true,
    mask: [target.locator('#auth-email'), target.locator('#auth-password'), target.locator('#operator-key'), target.locator('#intake')] })
  let operations
  async function receipts(label) {
    if (!scope.receiptStore) return null
    const store = scope.receiptStore
    const command = `sudo -n docker exec ${store.container} psql -X -q -v ON_ERROR_STOP=1 -U ${store.user} -d ${store.database} -At -c ${operations.quote(receiptSql(scope))}`
    const rows = JSON.parse(String(await operations.ssh(store.environment, command, undefined, 30_000)).trim())
    check(Array.isArray(rows) && rows.every(row => nonblank(row.receipt_code)), 'EXTERNAL_RECEIPT_READ_INVALID')
    check(new Set(rows.map(row => row.receipt_code)).size === rows.length, 'DUPLICATE_EXTERNAL_RECEIPT_CODE')
    save(`receipt-${++receiptReads}-${label}.private.json`, { at: new Date().toISOString(), scope: {
      customerId: scope.customerId, tenantId: scope.tenantId, deploymentId: scope.deploymentId }, rows, readOnly: true })
    return rows
  }
  function unchangedReceipts(rows) {
    if (receiptBefore) assertReceiptDelta(receiptBefore, rows, 0)
  }
  function isQuery(url, body) {
    const manifest = discovery.current().manifest
    if (!manifest || url.origin !== httpsOrigin(manifest.connection.runtimeBaseUrl)) return false
    if (url.href === discoveredRoute(manifest, 'queryUrl').href) return true
    return nonblank(body?.conversationId) && url.href === discoveredRoute(manifest, 'conversationMessageUrlTemplate', body.conversationId).href
  }
  async function responseBody(response, label) {
    const raw = await response.text()
    let body
    try { body = JSON.parse(raw) } catch { save(label + '.private.json', { status: response.status(), raw }); throw new Error('ACTUAL_RESPONSE_NOT_JSON') }
    save(label + '.private.json', { status: response.status(), path: new URL(response.url()).pathname, body })
    return body
  }
  async function send(query, label, { human = false, click } = {}) {
    requireHealthy()
    check(!expected && report.userSends < MAX_SENDS, 'USER_SEND_LIMIT_OR_OUTSTANDING_MESSAGE')
    expected = { query, human, label }
    const waiting = watch(page, response => response.request().method() === 'POST'
      && isQuery(new URL(response.url()), response.request().postDataJSON())
      && response.request().postDataJSON()?.query === query)
    if (click) await click()
    else { const composer = page.locator('textarea').first(); await composer.fill(query); await composer.press('Enter') }
    const response = unwrap(await waiting)
    const body = await responseBody(response, label)
    requireHealthy()
    check(!expected, 'EXPECTED_USER_MESSAGE_NOT_DISPATCHED')
    check(body && typeof body === 'object', 'CHAT_RESPONSE_OBJECT_REQUIRED')
    // Do not replace ERROR/budget/access-denied responses with success, even when optional metadata is blank.
    check(response.status() < 500, 'ACTUAL_RUNTIME_SERVER_ERROR')
    return { status: response.status(), body }
  }
  function assertAnswer(result, authenticated) {
    check(result.status === 200 && result.body.success === true && result.body.type !== 'ERROR', 'ACTUAL_CHAT_RESPONSE_NOT_SUCCESSFUL')
    check(result.body.metadata?.authenticated === authenticated, 'CHAT_IDENTITY_MISMATCH')
    check(nonblank(result.body.conversationId), 'ACTUAL_CONVERSATION_ID_REQUIRED')
    check(typeof result.body.answer === 'string' && result.body.answer.trim(), 'ACTUAL_ANSWER_REQUIRED')
  }
  async function clarifyOnce(result, query, label) {
    if (result.status !== 200 || result.body.type !== 'CLARIFICATION_REQUIRED') return result
    check(report.clarificationCount === 0, 'ONLY_ONE_EXPLICIT_CLARIFICATION_ALLOWED')
    const conversationId = result.body.conversationId
    check(nonblank(conversationId), 'CLARIFICATION_CONVERSATION_REQUIRED')
    report.clarificationCount++
    report.clarification = { phase: stage, conversationId, explicitTestUserFollowUp: true }
    const clarified = await send(query, label)
    check(clarified.body.conversationId === conversationId, 'CLARIFICATION_CONVERSATION_CHANGED')
    return clarified
  }
  async function compoundQuestion() {
    let result = await send('Which current electric vehicles under GBP 40,000 are suitable for a family, and what are the approved dealership delivery and warranty policies? Use current stock and indexed dealership documents. This is read-only.', 'compound-stock-and-policy')
    result = await clarifyOnce(result, 'I am looking across your inventory, not referring to one specific vehicle. Please search for electric family cars under GBP 40,000 and include the approved dealership delivery and warranty policies. This is read-only.', 'explicit-compound-user-clarification')
    assertAnswer(result, false)
    report.compoundPolicyCitations = policyCitations(result.body, scope.sourceIds)
    await page.locator('loomai-dealership-inventory article.vehicle-card').first().waitFor({ state: 'visible' })
    report.checks.compoundInventoryAndRegisteredPolicyCitation = true
    return result.body.conversationId
  }
  async function operatorCommand(path, label, click, text) {
    requireHealthy()
    check(!operatorExpected, 'OUTSTANDING_OPERATOR_COMMAND')
    operatorExpected = { path, text }
    save(`operator-${label}-intent.private.json`, { at: new Date().toISOString(), path, handoverId: report.handoverId })
    const waiting = watch(operatorPage, response => response.request().method() === 'POST'
      && new URL(response.url()).origin === scope.inboxUrl && new URL(response.url()).pathname === path)
    await click()
    const response = unwrap(await waiting)
    const body = await responseBody(response, 'operator-' + label)
    check(response.status() === 200 && body.accepted === true, 'OPERATOR_COMMAND_NOT_ACCEPTED')
    requireHealthy()
    return body
  }
  try {
    if (scope.receiptStore) {
      operations = await import(pathToFileURL(resolve(args.operations)).href)
      check(typeof operations.ssh === 'function' && typeof operations.quote === 'function', 'PRIVATE_OPERATIONS_MODULE_CONTRACT_INVALID')
      receiptBefore = await receipts('before')
      report.businessCountBefore = receiptBefore.length
    }
    const { chromium } = createRequire(new URL('../../max-mode-widget/package.json', import.meta.url))('playwright')
    browser = await chromium.launch({ headless: !args.headed })
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' })
    page = await context.newPage(); page.setDefaultTimeout(60_000)
    discovery = createDiscovery(page, scope, save, fail)
    page.on('pageerror', error => { save(`browser-error-${Date.now()}-${++commandSequence}.private.json`, { message: error.message }); fail('ACTUAL_BROWSER_PAGE_ERROR') })
    await context.route('**/*', async route => {
      const request = route.request(), url = new URL(request.url())
      if (['GET', 'HEAD', 'OPTIONS'].includes(request.method())) return route.continue()
      try {
        requireHealthy()
        check(request.method() === 'POST', 'UNEXPECTED_CUSTOMER_MUTATION_METHOD')
        const { bootstrap, manifest } = await discovery.ready()
        const runtime = httpsOrigin(manifest.connection.runtimeBaseUrl)
        const body = request.postDataJSON()
        if (isQuery(url, body)) {
          check(expected && body.query === expected.query, 'UNEXPECTED_OR_REPLAYED_CHAT_MESSAGE')
          check(!Object.hasOwn(body, 'confirmed') && !Object.hasOwn(body, 'confirmation'), 'HIDDEN_CONFIRMATION_NOT_ALLOWED')
          check(report.userSends < MAX_SENDS, 'USER_SEND_LIMIT_EXCEEDED')
          const intent = expected; expected = null
          report.userSends++; if (intent.human) report.humanSends++; else report.aiSends++
          const canonical = url.href !== discoveredRoute(manifest, 'queryUrl').href
          const key = request.headers()['idempotency-key']
          check(canonical && nonblank(key), 'CANONICAL_MESSAGE_AND_IDEMPOTENCY_KEY_REQUIRED')
          save(`dispatch-${report.userSends}.private.json`, { at: new Date().toISOString(), label: intent.label,
            path: url.pathname, body, human: intent.human, canonical, idempotencyKeySha256: key ? hash(key) : null,
            meaning: 'Dispatch intent; an absent response does not prove zero effect.' })
        } else if (url.href === discoveredRoute(manifest, 'suggestionsUrl').href) {
          check(++report.automaticSuggestions <= 10, 'AUTOMATIC_SUGGESTION_LIMIT_EXCEEDED')
        } else if (url.origin === runtime && [manifest.connection.anonymousBootstrap.url, manifest.connection.anonymousBootstrap.renewUrl]
          .some(endpoint => new URL(endpoint, runtime + '/').href === url.href)) {
          report.anonymousSessionRequests = (report.anonymousSessionRequests ?? 0) + 1
          check(report.anonymousSessionRequests <= 6, 'ANONYMOUS_BOOTSTRAP_LOOP')
        } else if (url.href === new URL(manifest.connection.credentialBroker.url, runtime + '/').href) {
          check(brokerExpected, 'UNEXPECTED_CREDENTIAL_BROKER_REQUEST')
          report.brokerRequests = (report.brokerRequests ?? 0) + 1
          check(report.brokerRequests <= 6, 'CREDENTIAL_BROKER_LOOP')
        } else if (report.signedConversationId && url.href === discoveredRoute(manifest, 'handoverRequestUrlTemplate', report.signedConversationId).href) {
          check(handoverExpected && body.continueAnonymously === true, 'UNEXPECTED_HANDOVER_COMMAND')
          handoverExpected = false
          save('handover-dispatch-intent.private.json', { at: new Date().toISOString(), path: url.pathname, body })
          report.handoverMayNeedManualFollowUp = true
        } else {
          const project = httpsOrigin(bootstrap.authentication.configuration.projectUrl)
          check(mode === 'full' && url.origin === project && url.pathname === '/auth/v1/token'
            && url.searchParams.get('grant_type') === 'password' && passwordExpected, 'UNEXPECTED_CUSTOMER_MUTATION')
          check(body.email === credentials.email && body.password === credentials.password, 'LOGIN_INPUT_MISMATCH')
          passwordExpected = false
        }
        return route.continue()
      } catch (error) {
        fail(error.message); report.blocked.push({ origin: url.origin, path: url.pathname, method: request.method(), reason: error.message })
        return route.abort('blockedbyclient')
      }
    })
    stage = 'normal-public-discovery'
    await page.goto(scope.appUrl, { waitUntil: 'domcontentloaded' })
    await page.locator('textarea').first().waitFor({ state: 'visible' })
    const { bootstrap, manifest } = await discovery.ready(true)
    report.discoveredRuntime = manifest.connection.runtimeBaseUrl
    report.checks.normalPublicBootstrapManifestAndScopedSession = true
    stage = 'compound-inventory-and-policy'
    report.anonymousConversationId = await compoundQuestion()
    unchangedReceipts(await receipts('after-compound'))
    await snap('01-compound-inventory-and-policy')
    stage = 'reload-without-replay'
    const messages = () => page.locator('[data-message-id]').evaluateAll(nodes => nodes.map(node => ({ id: node.getAttribute('data-message-id'), text: node.textContent })))
    const beforeMessages = await messages()
    check(beforeMessages.length >= 2, 'VISIBLE_CONVERSATION_REQUIRED')
    const sendsBeforeReload = report.userSends
    await page.reload({ waitUntil: 'domcontentloaded' })
    await page.locator('textarea').first().waitFor({ state: 'visible' })
    await page.locator('[data-message-id]').nth(beforeMessages.length - 1).waitFor({ state: 'visible' })
    assert.deepEqual(await messages(), beforeMessages, 'RELOAD_DID_NOT_RESTORE_EXACT_CONVERSATION')
    check(report.userSends === sendsBeforeReload, 'RELOAD_REPLAYED_QUERY')
    report.checks.reloadRestoresConversationWithoutReplay = true
    if (mode === 'full') {
      const project = httpsOrigin(bootstrap.authentication.configuration.projectUrl)
      if (credentials.projectRef) check(new URL(project).hostname.split('.')[0] === credentials.projectRef, 'SUPABASE_PROJECT_REF_MISMATCH')
      if (credentials.projectUrl) check(httpsOrigin(credentials.projectUrl) === project, 'SUPABASE_PROJECT_URL_MISMATCH')
      const details = scope.testDrive ?? { name: 'Synthetic Journey Buyer', phone: '07700900900', date: new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10) }
      const requestText = `Please submit a test-drive request for the ${scope.vehicleLabel}. My name is ${details.name}, email ${credentials.email}, phone ${details.phone}. Preferred date ${details.date}. This is a synthetic demonstration request.`
      stage = 'protected-action-requires-login'
      const challenge = await send(requestText, 'anonymous-protected-action')
      check(challenge.status < 500 && challenge.body.conversationId === report.anonymousConversationId, 'PROTECTED_CHALLENGE_CONVERSATION_MISMATCH')
      const requirement = challenge.body.authenticationRequirement
      check(requirement?.schemaVersion === 'loomai-workspace-authentication-requirement-v1'
        && requirement.reasonCode === 'AUTHENTICATION_REQUIRED' && requirement.retryPolicy === 'EXPLICIT_NEW_REQUEST'
        && requirement.capability === 'dealership_request_test_drive', 'STRUCTURED_ACTION_TIME_SIGN_IN_REQUIRED')
      unchangedReceipts(await receipts('after-anonymous-challenge'))
      await page.locator('#auth-dialog[open]').waitFor({ state: 'visible' }); await snap('02-action-time-sign-in')
      const beforeLogin = report.userSends
      await page.locator('#auth-close').click()
      await page.locator('#auth-dialog').waitFor({ state: 'hidden' })
      check(await page.evaluate(() => window.LoomAIWorkspaceHost?.identity?.status) === 'anonymous', 'CANCELLED_LOGIN_CHANGED_IDENTITY')
      requireHealthy(); check(report.userSends === beforeLogin, 'CANCELLED_LOGIN_REPLAYED_ACTION')
      report.checks.protectedActionChallengeAndCancelledLogin = true
      stage = 'real-supabase-login-no-automatic-replay'
      await page.getByRole('button', { name: 'Sign in', exact: true }).click()
      await page.getByLabel('Email', { exact: true }).fill(credentials.email)
      await page.getByLabel('Password', { exact: true }).fill(credentials.password)
      passwordExpected = true; brokerExpected = true
      const loginWait = watch(page, response => new URL(response.url()).origin === project
        && new URL(response.url()).pathname === '/auth/v1/token' && response.request().method() === 'POST')
      const brokerWait = watch(page, response => response.request().method() === 'POST'
        && response.url() === new URL(manifest.connection.credentialBroker.url, manifest.connection.runtimeBaseUrl + '/').href)
      await page.locator('#password-sign-in').click()
      const login = unwrap(await loginWait)
      check(login.status() === 200, 'REAL_SUPABASE_PASSWORD_LOGIN_FAILED')
      const broker = unwrap(await brokerWait)
      const brokerBody = await broker.json()
      check(broker.status() === 200 && brokerBody.subjectId === credentials.userId, 'AUTHENTICATED_RUNTIME_SUBJECT_MISMATCH')
      await page.waitForFunction(id => window.LoomAIWorkspaceHost?.identity?.status === 'authenticated'
        && window.LoomAIWorkspaceHost.identity.subjectKey === id, credentials.userId)
      await page.locator('textarea').first().waitFor({ state: 'visible' })
      requireHealthy(); check(report.userSends === beforeLogin, 'LOGIN_AUTOMATICALLY_REPLAYED_ACTION')
      unchangedReceipts(await receipts('after-login'))
      report.checks.realSupabaseLoginWithoutAutomaticReplay = true
      stage = 'signed-in-ui-vehicle-selection'
      let stock = await send('Search the current dealership inventory for electric SUVs with automatic transmission, price under GBP 40,000 and mileage under 20,000 miles. Show the matching current stock. This is read-only.', 'signed-stock-selection')
      stock = await clarifyOnce(stock, 'I do not have a specific vehicle selected. Please list the available electric SUVs under GBP 40,000 and under 20,000 miles. This is read-only.', 'explicit-stock-user-clarification')
      assertAnswer(stock, true)
      check(stock.body.conversationId !== report.anonymousConversationId, 'AUTHENTICATED_CONVERSATION_MUST_BE_ISOLATED')
      report.signedConversationId = stock.body.conversationId
      const vehicle = page.locator('loomai-dealership-inventory article.vehicle-card').filter({ has: page.getByRole('heading', { name: scope.vehicleLabel, exact: true }) }).last()
      await vehicle.waitFor({ state: 'visible' })
      await vehicle.getByRole('button', { name: 'Keep in context', exact: true }).click()
      await vehicle.getByRole('button', { name: 'Remove context', exact: true }).waitFor({ state: 'visible' })
      report.checks.vehicleSelectedThroughActualUi = true
      unchangedReceipts(await receipts('after-vehicle-selection'))
      if (report.businessWriteAllowed) {
        stage = 'explicit-signed-in-action-proposal'
        const proposed = await send(requestText, 'signed-protected-action-proposal')
        check(proposed.status === 200 && proposed.body.metadata?.authenticated === true
          && proposed.body.conversationId === report.signedConversationId && proposed.body.type === 'CONFIRMATION_REQUIRED', 'REVIEWABLE_ACTION_CONFIRMATION_REQUIRED')
        unchangedReceipts(await receipts('before-confirmation'))
        await snap('03-explicit-action-confirmation')
        stage = 'one-explicit-synthetic-confirmation'
        save('synthetic-receipt-confirmation-intent.private.json', { at: new Date().toISOString(),
          conversationId: report.signedConversationId, expectedExternalReceiptDelta: 1, explicitCliAllowance: true })
        const confirmed = await send('Yes, confirm', 'confirmed-synthetic-action', { click: async () => {
          check(report.confirmationClicks === 0, 'SECOND_CONFIRMATION_PROHIBITED'); report.confirmationClicks++
          await page.getByRole('button', { name: 'Confirm', exact: true }).last().click()
        } })
        assertAnswer(confirmed, true)
        check(confirmed.body.conversationId === report.signedConversationId, 'CONFIRMATION_CONVERSATION_MISMATCH')
        receiptAfter = await receipts('after-confirmation')
        const created = assertReceiptDelta(receiptBefore, receiptAfter, 1)
        const receipt = page.locator('loomai-dealership-request-receipt [data-dealership-request-receipt="test-drive"], [data-max-mode-generic-action-result]')
          .filter({ hasText: created[0].receipt_code }).last()
        await receipt.waitFor({ state: 'visible' })
        check((await receipt.innerText()).includes(created[0].receipt_code), 'UI_RECEIPT_DOES_NOT_MATCH_EXTERNAL_RECORD')
        check(confirmed.body.actions?.some(action => action.action === 'dealership_request_test_drive' && action.actionResult?.success === true
          && action.actionResult.data?.receipt?.receiptCode === created[0].receipt_code), 'SUCCESSFUL_ACTION_RECEIPT_REQUIRED')
        report.receiptPresentation = await receipt.getAttribute('data-max-mode-generic-action-result') != null ? 'GENERIC_STRUCTURED' : 'CUSTOM'
        report.receiptCode = created[0].receipt_code
        report.checks.oneExplicitConfirmationAndOneExternalReceipt = true
        await snap('04-synthetic-receipt')
      } else report.checks.signedInBusinessWrite = 'NOT_RUN: no explicit receipt allowance'
      stage = 'widget-human-handover'
      await page.getByRole('button', { name: 'Talk to a person', exact: true }).first().click()
      const dialog = page.getByRole('dialog', { name: 'Talk to a person', exact: true })
      await dialog.waitFor({ state: 'visible' })
      const anonymous = dialog.getByRole('button', { name: 'Continue anonymously', exact: true })
      check(await anonymous.isVisible(), 'ANONYMOUS_HANDOVER_INTAKE_NOT_AVAILABLE')
      handoverExpected = true
      const handoverWait = watch(page, response => response.request().method() === 'POST'
        && response.url() === discoveredRoute(discovery.current().manifest, 'handoverRequestUrlTemplate', report.signedConversationId).href)
      await anonymous.click()
      const admitted = unwrap(await handoverWait), admission = await responseBody(admitted, 'handover-admission')
      check(admitted.status() === 202 && admission.conversation?.controlState === 'HUMAN_PENDING'
        && nonblank(admission.conversation.handoverId), 'ACTUAL_HUMAN_PENDING_ADMISSION_REQUIRED')
      report.handoverId = admission.conversation.handoverId
      const close = page.getByRole('button', { name: 'Close human support panel', exact: true })
      if (await close.isVisible()) await close.click()
      const humanText = `Synthetic customer journey ${scope.deploymentId}: please clarify the delivery arrangements with me.`
      const human = await send(humanText, 'human-bound-user-message', { human: true })
      check(human.status === 202 && human.body.type === 'HUMAN_MESSAGE_ACCEPTED'
        && human.body.conversationId === report.signedConversationId, 'HUMAN_MESSAGE_MUST_BYPASS_AI')
      report.checks.humanMessageBypassesAi = true
      stage = 'basic-inbox-operator-login'
      const operatorContext = await browser.newContext({ viewport: { width: 1200, height: 1000 }, serviceWorkers: 'block' })
      operatorPage = await operatorContext.newPage(); operatorPage.setDefaultTimeout(60_000)
      await operatorContext.route('**/*', async route => {
        const request = route.request(), url = new URL(request.url())
        if (['GET', 'HEAD', 'OPTIONS'].includes(request.method())) return route.continue()
        try {
          check(!fatal && request.method() === 'POST' && url.origin === scope.inboxUrl
            && operatorExpected?.path === url.pathname, 'UNEXPECTED_OPERATOR_MUTATION')
          const body = request.postDataJSON()
          check(nonblank(body.idempotencyKey), 'OPERATOR_IDEMPOTENCY_KEY_REQUIRED')
          if (operatorExpected.text) check(body.text === operatorExpected.text, 'OPERATOR_REPLY_CONTENT_MISMATCH')
          operatorExpected = null
          return route.continue()
        } catch (error) {
          fail(error.message); report.blocked.push({ origin: url.origin, path: url.pathname, method: request.method(), reason: error.message })
          return route.abort('blockedbyclient')
        }
      })
      await operatorPage.goto(scope.inboxUrl, { waitUntil: 'domcontentloaded' })
      await operatorPage.getByLabel('Operator API key', { exact: true }).fill(operatorKey)
      const operatorMe = watch(operatorPage, response => new URL(response.url()).origin === scope.inboxUrl && new URL(response.url()).pathname === '/api/operator/me')
      await operatorPage.getByRole('button', { name: 'Open inbox', exact: true }).click()
      const me = unwrap(await operatorMe)
      check(me.status() === 200, 'REAL_OPERATOR_LOGIN_FAILED')
      check(nonblank((await me.json()).operatorId), 'OPERATOR_IDENTITY_REQUIRED')
      stage = 'select-exact-handover-in-inbox'
      let queue = [], polls = 0
      const queueDeadline = Date.now() + 45_000
      while (!queue.some(row => row.handoverId === report.handoverId)) {
        check(Date.now() < queueDeadline, 'EXACT_HANDOVER_NOT_DELIVERED_TO_BASIC_INBOX')
        const waiting = watch(operatorPage, response => response.request().method() === 'GET'
          && new URL(response.url()).origin === scope.inboxUrl && new URL(response.url()).pathname === '/api/operator/conversations',
        Math.max(1, queueDeadline - Date.now()))
        await operatorPage.locator('#refresh').click()
        const response = unwrap(await waiting)
        check(response.status() === 200, 'INBOX_QUEUE_READ_FAILED'); queue = await response.json(); polls++
        check(Array.isArray(queue), 'INBOX_QUEUE_CONTRACT_MISMATCH')
        if (!queue.some(row => row.handoverId === report.handoverId)) await sleep(1000)
      }
      const index = queue.findIndex(row => row.handoverId === report.handoverId)
      save('exact-inbox-queue-entry.private.json', { polls, item: queue[index] })
      check(nonblank(queue[index].externalConversationRef), 'INBOX_EXTERNAL_CONVERSATION_REFERENCE_REQUIRED')
      const detailPath = `/api/operator/conversations/${encodeURIComponent(report.handoverId)}`
      const detailWait = watch(operatorPage, response => response.request().method() === 'GET'
        && new URL(response.url()).origin === scope.inboxUrl && new URL(response.url()).pathname === detailPath)
      await operatorPage.locator('#queue .queue-item').nth(index).click()
      const detailResponse = unwrap(await detailWait), detail = await responseBody(detailResponse, 'selected-inbox-conversation')
      check(detailResponse.status() === 200 && detail.conversation?.handoverId === report.handoverId
        && detail.conversation.externalConversationRef === queue[index].externalConversationRef, 'WRONG_INBOX_CONVERSATION_SELECTED')
      stage = 'operator-claims-and-replies'
      await operatorCommand(`${detailPath}/claim`, 'claim', () => operatorPage.locator('#claim').click())
      await operatorPage.locator('#reply').waitFor({ state: 'visible' })
      await operatorPage.waitForFunction(() => !document.querySelector('#reply').disabled)
      await operatorPage.getByText(humanText, { exact: true }).waitFor({ state: 'visible' })
      const reply = `Hello from the synthetic journey operator (${scope.deploymentId}). I can help with delivery; the approved dealership delivery policy applies.`
      await operatorPage.locator('#reply').fill(reply)
      await operatorCommand(`${detailPath}/messages`, 'reply', () => operatorPage.locator('#reply-form button[type=submit]').click(), reply)
      await page.getByText(reply, { exact: true }).first().waitFor({ state: 'visible' })
      report.checks.actualInboxReplyArrivesAsynchronouslyInWorkspace = true
      await snap('05-human-reply'); await snap('06-basic-inbox', operatorPage)
      stage = 'operator-returns-conversation-to-ai'
      await operatorCommand(`${detailPath}/return-to-ai`, 'return-ai', () => operatorPage.locator('#return-ai').click())
      await page.getByRole('button', { name: 'Talk to a person', exact: true }).first().waitFor({ state: 'visible' })
      report.checks.authorizedOperatorReturnsConversationToAi = true
      report.handoverMayNeedManualFollowUp = false
      stage = 'ai-resumes-with-registered-policy-citation'
      const resumed = await send('What is the approved dealership warranty policy? Use the indexed dealership policy and cite it. This is read-only; do not create a request.', 'ai-resumed-policy-answer')
      assertAnswer(resumed, true)
      check(resumed.body.conversationId === report.signedConversationId, 'AI_RESUME_LOST_CONVERSATION')
      report.resumedPolicyCitations = policyCitations(resumed.body, scope.sourceIds)
      report.checks.aiResumesWithActualRegisteredPolicyCitation = true
    }
    stage = 'basic-mobile-composer'
    await page.setViewportSize({ width: 390, height: 844 })
    const composer = page.locator('textarea').first()
    await composer.scrollIntoViewIfNeeded(); await composer.focus()
    const box = await composer.boundingBox()
    check(box && box.x >= 0 && box.y >= 0 && box.x + box.width <= 391 && box.y + box.height <= 845, 'MOBILE_COMPOSER_NOT_IN_VIEWPORT')
    check(await composer.isEditable(), 'MOBILE_COMPOSER_NOT_EDITABLE')
    await snap('07-mobile-composer')
    report.checks.basicMobileComposerFitsAndIsEditable = true
    receiptAfter = await receipts('final')
    if (receiptBefore) {
      if (report.businessWriteAllowed) {
        check(report.confirmationClicks === 1, 'FINAL_CONFIRMATION_COUNT_INVALID')
        assertReceiptDelta(receiptBefore, receiptAfter, 1, report.receiptCode)
      } else unchangedReceipts(receiptAfter)
    }
    requireHealthy(); check(report.userSends <= MAX_SENDS && !expected && !operatorExpected && !handoverExpected, 'OUTSTANDING_INTENT_OR_SEND_LIMIT')
    report.status = 'PASS_TECHNICAL_PENDING_ANSWER_REVIEW'
  } catch (error) {
    report.status = 'NOT_ACCEPTED'
    report.failure = { stage, message: redact(String(error.message), secrets).slice(0, 2000) }
    if (page) await snap('stopped-workspace').catch(() => {})
    if (operatorPage) await snap('stopped-inbox', operatorPage).catch(() => {})
  } finally {
    if (discovery) await discovery.flush()
    if (browser) await browser.close().catch(() => { report.status = 'NOT_ACCEPTED'; report.browserCloseFailure = true })
    if (discovery) await discovery.flush()
    if (operations && receiptBefore) {
      try { receiptAfter = await receipts('after-browser-close'); report.businessCountAfter = receiptAfter.length }
      catch (error) { report.status = 'NOT_ACCEPTED'; report.receiptReadFailure = redact(error.message, secrets) }
      if (receiptAfter) {
        try { assertReceiptDelta(receiptBefore, receiptAfter, report.checks.oneExplicitConfirmationAndOneExternalReceipt ? 1 : 0, report.receiptCode) }
        catch { report.status = 'NOT_ACCEPTED'; report.manualBusinessEffectReviewRequired = true }
      }
    }
    if (fatal) { report.status = 'NOT_ACCEPTED'; report.fatal = fatal }
    if (report.confirmationClicks && !report.checks.oneExplicitConfirmationAndOneExternalReceipt) {
      report.manualBusinessEffectReviewRequired = true
    }
    report.finishedAt = new Date().toISOString()
    save('report.private.json', report)
    console.log(JSON.stringify({ status: report.status, mode, stage, root: args.root, userSends: report.userSends,
      checks: report.checks, clarificationCount: report.clarificationCount,
      failure: report.failure, handoverMayNeedManualFollowUp: report.handoverMayNeedManualFollowUp ?? false }))
  }
  return report.status === 'PASS_TECHNICAL_PENDING_ANSWER_REVIEW' ? 0 : 1
}

export function selfTest() {
  const scope = validateScope({ customerId: 'cus-fixture', tenantId: 'ten-fixture', deploymentId: 'dep-fixture',
    releaseId: 'rel-fixture', installationId: 'awi_pub_' + 'a'.repeat(32), assignmentRevision: 'revision-fixture',
    appUrl: 'https://fixture.example/', sourceIds: ['doc-fixture'] }, 'read-only')
  assert.equal(scope.appUrl, 'https://fixture.example')
  assert.throws(() => httpsOrigin('https://user:password@fixture.example'), /HTTPS_ORIGIN_REQUIRED/)
  assert.throws(() => validateScope({ ...scope, runtimeBaseUrl: 'https://guessed.example' }, 'read-only'), /RUNTIME_MUST_BE_DISCOVERED/)
  assert.throws(() => validateScope({ ...scope, sourceIds: [] }, 'read-only'), /SOURCE_IDS_REQUIRED/)
  assert.throws(() => validateScope(scope, 'full'), /HTTPS_ORIGIN_REQUIRED/)
  const body = { sources: [{ id: 'chunk', metadata: { _aiDocumentSourceId: 'doc-fixture' } }] }
  assert.equal(policyCitations(body, scope.sourceIds)[0].sourceId, 'doc-fixture')
  assert.throws(() => policyCitations(body, ['doc-other']), /ACTUAL_REGISTERED_DOCUMENT_CITATION_REQUIRED/)
  const projected = { sources: [{ documentId: 'chunk', sourceId: 'approved-knowledge', vectorSpace: 'document' }],
    ragResponse: { documents: [{ id: 'chunk', metadata: { _aiDocumentSourceId: 'doc-fixture',
      knowledgeSourceId: 'approved-knowledge', vectorSpace: 'document' } }] } }
  assert.equal(policyCitations(projected, scope.sourceIds)[0].sourceId, 'doc-fixture')
  assert.throws(() => policyCitations({ ...projected, sources: [{ ...projected.sources[0], documentId: 'other' }] }, scope.sourceIds), /ACTUAL_REGISTERED_DOCUMENT_CITATION_REQUIRED/)
  assert.throws(() => policyCitations({ ...projected, sources: [{ ...projected.sources[0], sourceId: 'foreign-knowledge' }] }, scope.sourceIds), /ACTUAL_REGISTERED_DOCUMENT_CITATION_REQUIRED/)
  assert.deepEqual(redact({ password: 'topsecret', apiKey: 'key', nested: { message: 'topsecret' }, error: 'ACTUAL_ERROR' }, ['topsecret']),
    { password: '[REDACTED]', apiKey: '[REDACTED]', nested: { message: '[REDACTED]' }, error: 'ACTUAL_ERROR' })
  const sql = receiptSql(scope)
  assert.ok(sql.startsWith('BEGIN READ ONLY;') && sql.includes("receiver_customer_id = 'cus-fixture'")
    && sql.includes("receiver_tenant_id = 'ten-fixture'") && sql.includes("receiver_deployment_id = 'dep-fixture'"))
  const old = [{ receipt_code: 'prior', action_type: 'test-drive' }]
  const created = { receipt_code: 'new', action_type: 'test-drive' }
  assertReceiptDelta(old, old, 0)
  assertReceiptDelta(old, [...old, created], 1, 'new')
  assert.throws(() => assertReceiptDelta(old, [...old, created], 0), /RECEIPT_DELTA_INVALID/)
  assert.throws(() => assertReceiptDelta(old, [{ ...old[0], action_type: 'changed' }], 0), /PREEXISTING_RECEIPT_CHANGED/)
  console.log('PASS: local scope, citation, redaction, read-only SQL and receipt-delta guards; no browser/network actions.')
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { values: args } = parseArgs({ options: {
    ...Object.fromEntries(['scope', 'root', 'mode', 'credentials', 'operator-key', 'operations', 'document-evidence'].map(key => [key, { type: 'string' }])),
    ...Object.fromEntries(['allow-one-synthetic-receipt', 'headed', 'help', 'self-test'].map(key => [key, { type: 'boolean', default: false }])),
  } })
  if (args.help) console.log(HELP)
  else if (args['self-test']) selfTest()
  else runJourney(args).then(code => { process.exitCode = code }).catch(error => {
    console.error('Journey could not start; check private input files and the unused output path.', error.code ?? error.name)
    process.exitCode = 1
  })
}
