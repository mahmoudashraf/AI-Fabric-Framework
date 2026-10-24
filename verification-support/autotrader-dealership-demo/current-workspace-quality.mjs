import assert from 'node:assert/strict'
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { createRequire } from 'node:module'
import { parseArgs } from 'node:util'
import { resolve } from 'node:path'
import { redact } from '../customer-console-01045/hosted-dealership-workspace-journey.mjs'

const { values: args } = parseArgs({ options: Object.fromEntries(['root', 'scope', 'scenarios', 'only', 'manual-host']
  .map(key => [key, { type: 'string' }])) })
assert.ok(args.root && args.scope)
const root = resolve(args.root)
assert.ok(!existsSync(root), 'A quality run needs a new private output directory, not a replay')
process.umask(0o077); mkdirSync(root, { recursive: true, mode: 0o700 })
const scope = JSON.parse(readFileSync(args.scope, 'utf8'))
const manualHost = args['manual-host'] ? JSON.parse(readFileSync(args['manual-host'], 'utf8')) : null
const suite = JSON.parse(readFileSync(args.scenarios || new URL('./current-workspace-quality.scenarios.json', import.meta.url), 'utf8'))
const selected = args.only ? args.only.split(',') : null
const scenarios = selected ? suite.filter(item => selected.includes(item.id)) : suite
assert.ok(scenarios.length > 0 && scenarios.length <= 24)
if (selected) assert.equal(selected.length, scenarios.length, 'Every requested scenario must exist')
assert.equal(new URL(scope.appUrl).protocol, 'https:')
const report = { startedAt: new Date().toISOString(), appUrl: scope.appUrl, deploymentId: scope.deploymentId,
  releaseId: scope.releaseId, assignmentRevision: scope.assignmentRevision, scenarioCount: scenarios.length,
  status: 'RUNNING', settingsChanged: false, runtimeOrFrameworkCodeChanged: false,
  hostContract: manualHost ? 'MANUAL_HOST_PUBLISHED_ASSETS_LIVE_RUNTIME_NOT_PLATFORM_ASSIGNMENT_ACCEPTANCE' : 'LIVE_PLATFORM_INSTALLATION',
  semanticVerdicts: 'Pending human review against current source evidence, not legacy lexical assertions',
  sessions: [], results: [], blocked: [], errors: [], successfulWriteActions: [] }
const save = (name, value) => writeFileSync(`${root}/${name}.json`, JSON.stringify(value, null, 2) + '\n', { flag: 'wx', mode: 0o600 })
const { chromium } = createRequire(new URL('../../Platfrom/ui/package.json', import.meta.url))('playwright')
const browser = await chromium.launch()
let page, context, phase = 'start', queryCount = 0

function metadataRows(body) {
  const rows = [body?.metadata, body?.ragResponse?.metadata]
  for (const child of body?.results ?? body?.children ?? []) rows.push(...metadataRows(child))
  return rows.filter(row => row && typeof row === 'object')
}
function evidence(body) {
  const rows = metadataRows(body)
  const compound = rows.map(row => row.compoundEvidence).find(Boolean)
  const actions = body.actions ?? []
  const resolutions = [...rows.map(row => row.readActionResolution), ...actions.map(action => action.readActionResolution)].filter(Boolean)
  const documents = body.ragResponse?.documents ?? body.documents ?? []
  return {
    policy: rows.map(row => row.orchestrationPolicy).find(Boolean),
    extraction: rows.map(row => row.extractionDiagnostics).find(Boolean),
    routing: rows.flatMap(row => row.vectorSpaceRouting ?? []),
    chat: rows.map(row => row.chat).find(Boolean),
    targetResolution: rows.map(row => row.targetResolution).find(Boolean),
    readActionResolution: resolutions,
    actions: actions.map(action => ({ action: action.action, success: action.actionResult?.success,
      result: action.actionResult, postActionGeneration: action.postActionGeneration })),
    documents: documents.map(document => ({ id: document.id, title: document.title, content: document.content,
      score: document.score, metadata: document.metadata })),
    sources: body.sources ?? [], compound,
    compoundResults: Array.isArray(body.results) ? body.results : [],
    diagnostics: rows.flatMap(row => row.searchSourceDiagnostics ?? []),
    timing: rows.map(row => row.timing).find(Boolean),
  }
}
try {
  for (const sessionName of [...new Set(scenarios.map(item => item.session))]) {
    phase = sessionName
    context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block', locale: 'en-GB' })
    let manifest = manualHost?.manifest, expectedQuery, conversationId, rejectBlockedRequest, automaticSuggestions = 0, anonymousRequests = 0
    page = await context.newPage(); page.setDefaultTimeout(45000)
    page.on('pageerror', error => report.errors.push({ session: sessionName, message: error.message }))
    page.on('response', async response => {
      if (new URL(response.url()).pathname === `/api/public/ai-workspace/installations/${scope.installationId}/manifest` && response.ok()) {
        manifest = await response.json()
      }
    })
    await context.route('**/*', route => {
      const request = route.request(), url = new URL(request.url())
      if (manualHost && request.isNavigationRequest() && request.method() === 'GET' && url.href === scope.appUrl + '/') {
        return route.fulfill({ contentType: 'text/html', body: '<!doctype html><html><head><title>Manual live-runtime quality test</title></head><body><main id="main-content"></main></body></html>' })
      }
      if (['GET', 'HEAD', 'OPTIONS'].includes(request.method())) return route.continue()
      try {
        assert.equal(request.method(), 'POST'); assert.ok(manifest)
        assert.equal(url.origin, new URL(manifest.connection.runtimeBaseUrl).origin)
        const body = request.postDataJSON(), connection = manifest.connection
        const routeUrl = path => new URL(path, connection.runtimeBaseUrl).href
        if (typeof body?.query === 'string') {
          assert.equal(body.query, expectedQuery); assert.ok(!body.confirmed && !body.confirmation)
          const canonical = connection.routes.conversationMessageUrlTemplate
            ? routeUrl(connection.routes.conversationMessageUrlTemplate.replace('{conversationId}', encodeURIComponent(body.conversationId))) : null
          assert.ok(url.href === canonical || url.href === routeUrl(connection.routes.queryUrl))
          assert.ok(++queryCount <= scenarios.length); expectedQuery = null
        } else if ([connection.anonymousBootstrap.url, connection.anonymousBootstrap.renewUrl].some(path => routeUrl(path) === url.href)) {
          assert.ok(++anonymousRequests <= 4)
        } else if (connection.routes.suggestionsUrl && routeUrl(connection.routes.suggestionsUrl) === url.href) {
          assert.ok(++automaticSuggestions <= 10)
        } else throw new Error('Only the intended anonymous query and normal session routes are permitted')
        return route.continue()
      } catch (error) {
        report.blocked.push({ session: sessionName, method: request.method(), path: url.pathname, reason: error.message })
        rejectBlockedRequest?.(error)
        return route.abort('blockedbyclient')
      }
    })
    await page.goto(scope.appUrl, { waitUntil: 'domcontentloaded' })
    if (manualHost) {
      await page.evaluate(() => {
        window.LoomAIWorkspace = { registerExperiencePack(registration) {
          if (registration.code !== 'dealership' || registration.version !== '1.1.0') throw new Error('Unexpected test pack')
        } }
      })
      for (const asset of [manifest.workspace.asset, manifest.workspace.experiencePack]) {
        await page.evaluate(asset => new Promise((resolve, reject) => {
          const script = document.createElement('script')
          script.src = asset.url; script.integrity = asset.integrity; script.crossOrigin = 'anonymous'
          script.onload = resolve; script.onerror = () => reject(new Error('Published asset failed integrity/load verification'))
          document.head.append(script)
        }), asset)
      }
      await page.evaluate(async m => {
        const c = m.connection, r = c.routes, absolute = path => new URL(path, c.runtimeBaseUrl).href
        const template = path => absolute(path).replaceAll('%7BconversationId%7D', '{conversationId}')
        await window.LoomAIDealershipExperience.mountInstallation({
          installationId: m.installationId, manifestRevision: m.manifestRevision, assignmentRevision: m.assignmentRevision,
          connectionMode: c.mode, deliveryMode: 'standalone', interactionPolicy: 'public', configuration: m.workspace.configuration,
          maxMode: window.MaxMode, refreshAssignment: async () => {},
          widgetConfig: { integrationMode: c.mode, storageNamespace: 'manual-quality-live-runtime',
            host: { showCloseButton: false }, apiConfig: { chatBaseUrl: c.runtimeBaseUrl, fetchCredentials: 'omit',
              runtimeRoutes: { chatQueryUrl: absolute(r.queryUrl), suggestionsUrl: absolute(r.suggestionsUrl),
                authContextUrl: absolute(r.authContextUrl), shellConfigUrl: absolute(r.shellConfigUrl),
                conversationsUrl: absolute(r.conversationsUrl), conversationItemUrlTemplate: template(r.conversationItemUrlTemplate),
                ...(r.conversationMessageUrlTemplate ? { conversationMessageUrlTemplate: template(r.conversationMessageUrlTemplate) } : {}) },
              runtimeAuth: { bootstrapUrl: absolute(c.anonymousBootstrap.url), renewUrl: absolute(c.anonymousBootstrap.renewUrl),
                authContextUrl: absolute(r.authContextUrl), probeAuthContextOnOpen: true }, probeShellConfigOnOpen: true } },
        })
      }, manifest)
    }
    await page.locator('textarea').first().waitFor()
    assert.ok(manifest); assert.equal(manifest.installationId, scope.installationId)
    assert.equal(manifest.assignmentRevision, scope.assignmentRevision)
    assert.equal(manifest.connection.mode, 'public-runtime-anonymous')
    save(`manifest-${sessionName}.private`, manifest)
    report.sessions.push({ name: sessionName, runtime: manifest.connection.runtimeBaseUrl, viewport: '1440x1000' })
    for (const scenario of scenarios.filter(item => item.session === sessionName)) {
      phase = scenario.id
      console.log(`Running ${report.results.length + 1}/${scenarios.length}: ${scenario.id}`)
      expectedQuery = scenario.prompt
      save(`${scenario.id}.intent`, { at: new Date().toISOString(), sessionName, query: expectedQuery, confirmations: false, settingsChanged: false })
      const pending = Promise.race([page.waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).origin === new URL(manifest.connection.runtimeBaseUrl).origin
        && response.request().postDataJSON()?.query === scenario.prompt, { timeout: 180000 }),
        new Promise((_, reject) => { rejectBlockedRequest = reject })])
      const start = Date.now()
      await page.locator('textarea').first().fill(scenario.prompt)
      await page.locator('textarea').first().press('Enter')
      const response = await pending, body = await response.json(), request = response.request().postDataJSON()
      assert.equal(expectedQuery, null)
      save(`${scenario.id}.exchange.private`, { at: new Date().toISOString(), status: response.status(), request, response: body })
      const proof = evidence(body)
      const current = { id: scenario.id, session: sessionName, prompt: scenario.prompt, httpStatus: response.status(),
        durationMs: Date.now() - start, type: body.type, success: body.success, fallbackReason: body.fallbackReason,
        errorCode: body.errorCode, answer: body.answer, conversationId: body.conversationId,
        request: { mode: request.mode, position: request.position, conversationId: request.conversationId,
          attachmentCount: request.attachments?.length ?? 0, context: request.context },
        continuity: !conversationId || body.conversationId === conversationId, evidence: proof,
        semanticVerdict: 'PENDING_REVIEW' }
      report.results.push(redact(current))
      assert.ok(response.ok(), `Query HTTP ${response.status()}; retain the failed run rather than continue`)
      if (body.conversationId) conversationId ||= body.conversationId
      const writes = (body.actions ?? []).filter(action => ['dealership_request_test_drive', 'dealership_request_callback'].includes(action.action)
        && action.actionResult?.success === true)
      report.successfulWriteActions.push(...writes.map(action => ({ scenario: scenario.id, action: action.action })))
      assert.equal(writes.length, 0, 'A business write occurred; stop the diagnostic run')
      await page.waitForTimeout(1250)
      if (body.type === 'COMPOUND_HANDLED' && Array.isArray(body.results)) {
        for (const child of body.results) {
          if (typeof child.answer === 'string' && child.answer.trim()) {
            await page.getByText(child.answer, { exact: true }).last().waitFor()
          }
        }
        report.results[report.results.length - 1].compoundResultsRendered = body.results.length
      }
      const closeAuth = page.locator('#auth-close')
      if (await page.locator('#auth-dialog[open]').isVisible()) await closeAuth.click()
      await page.screenshot({ path: `${root}/${scenario.id}.png`, fullPage: true })
      save(`progress-${String(report.results.length).padStart(2, '0')}`, { results: report.results })
      console.log(JSON.stringify({ id: scenario.id, status: response.status(), type: body.type, success: body.success,
        seconds: Math.round(current.durationMs / 100) / 10, actions: proof.actions.map(action => action.action),
        documents: proof.documents.map(document => ({ title: document.title, vectorSpace: document.metadata?.vectorSpace })) }))
      assert.ok(!new Set(['PROVIDER_BUDGET_INTERRUPTED', 'PROVIDER_BUDGET_EXCEEDED', 'PROVIDER_BUDGET_UNAVAILABLE'])
        .has(body.fallbackReason), 'Provider allowance interrupted this run; retain its evidence and do not spend requests replaying denials')
      assert.ok(response.status() < 500, 'Runtime server failure; preserve this run rather than retry')
      assert.ok(![402, 429].includes(response.status()), 'Provider or request allowance refused; stop rather than replay')
      if (scenario.after === 'pin-first-vehicle') {
        const card = page.locator('loomai-dealership-inventory article.vehicle-card').first()
        await card.getByRole('button', { name: 'Keep in context', exact: true }).click()
        await card.getByRole('button', { name: 'Remove context', exact: true }).waitFor()
        save('selected-context.private', { title: await card.getByRole('heading').first().innerText(), selectedThroughUi: true })
      }
      if (scenario.after === 'remove-selected-vehicle') {
        const remove = page.getByRole('button', { name: 'Remove context', exact: true })
        assert.ok(await remove.count() > 0, 'Actual selection removal control required')
        await remove.first().click()
      }
    }
    await context.close(); context = null
  }
  assert.deepEqual(report.errors, []); assert.deepEqual(report.blocked, [])
  assert.equal(queryCount, scenarios.length); assert.ok(report.results.every(result => result.continuity))
  report.status = 'CAPTURED_PENDING_ANSWER_REVIEW'
} catch (error) {
  report.status = 'STOPPED_NOT_ACCEPTED'; report.failure = { phase, message: error.message }
  await page?.screenshot({ path: `${root}/stopped.png`, fullPage: true }).catch(() => {})
  process.exitCode = 1
} finally {
  if (context) await context.close()
  await browser.close()
  report.completedAt = new Date().toISOString(); save('report.actual', report)
  console.log(JSON.stringify({ status: report.status, count: report.results.length, failure: report.failure, root }))
}
