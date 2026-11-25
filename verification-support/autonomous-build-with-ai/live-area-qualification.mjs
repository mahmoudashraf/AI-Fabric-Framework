import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { brainCases, configs } from './area-matrix.mjs'
import { resourceCases } from './resource-area-matrix.mjs'

process.umask(0o077)
const [environment, label] = process.argv.slice(2)
assert.ok(['staging', 'production'].includes(environment))
assert.match(label ?? '', /^[a-z0-9-]{1,50}$/)
const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const privateRoot = path.join(os.homedir(), '.codex/private')
const { chromium } = await import(path.join(repo, 'Platfrom/ui/node_modules/playwright/index.mjs'))
const { login, logout, platform } = await import(path.join(privateRoot, 'loomai-hybrid-retrieval-01073/live-operations.mjs'))
const { nativeRead } = await import('/private/tmp/loomai-01075-native-observe.mjs')
const root = path.join(privateRoot, 'loomai-build-ai-all-areas-20261125', environment + '-' + label)
assert.ok(!fs.existsSync(root), 'Reconcile the previous run; never silently replay its effects.')
fs.mkdirSync(root, { recursive: true, mode: 0o700 })
const save = (name, value) => fs.writeFileSync(path.join(root, name + '.private.json'), JSON.stringify(value, null, 2), { mode: 0o600 })
const ui = environment === 'production' ? 'https://console.loomai.pro' : 'https://loomai-platform-ui.46.224.145.148.sslip.io'
const base = environment === 'production' ? 'https://api.loomai.pro' : 'https://loomai-platform-backend.46.224.145.148.sslip.io'
const fixture = environment === 'production'
  ? JSON.parse(fs.readFileSync(path.join(privateRoot, 'loomai-onboarding-01049/signup-fixture.private.json')))
  : (await import(path.join(privateRoot, 'loomai-policy-template-20261113/customer.mjs'))).fixture
const selected = process.env.LOOMAI_AREA_CASES?.split(',')
const allCases = [...brainCases, ...resourceCases]
assert.ok(!selected || selected.every(id => allCases.some(c => c.id === id)))
const modes = process.env.LOOMAI_AREA_MODES?.split(',') ?? ['AUTONOMOUS', 'BOUNDED']
assert.ok(modes.every(m => ['AUTONOMOUS', 'BOUNDED'].includes(m)))
const report = { status: 'RUNNING', environment, label, framework: '0.8.72', entry: 'PUBLIC_CUSTOMER_API_WITH_NORMAL_UI_AUTH', cases: [], pageErrors: [], at: new Date().toISOString() }
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
page.setDefaultTimeout(45000)
page.on('pageerror', error => report.pageErrors.push(error.message))
let token, signedIn = false, staffSignedIn = false, stop = false
const safe = error => {
  let text = String(error?.message ?? error)
  for (const secret of [token, fixture.password]) if (secret) text = text.split(secret).join('[REDACTED]')
  return text
}
const customerApi = async (route, method = 'GET', body, statuses = [200], key) => {
  const r = await fetch(base + route, { method, redirect: 'error', signal: AbortSignal.timeout(120000),
    headers: { Authorization: 'Bearer ' + token, Origin: ui, Accept: 'application/json', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
  const text = await r.text(), value = text ? JSON.parse(text) : null
  if (!statuses.includes(r.status)) save('http-failure-' + Date.now(), { route, method, status: r.status, value })
  assert.ok(statuses.includes(r.status), `${method} ${route} returned ${r.status}`)
  return value
}
const staffApi = async (route, method = 'GET', body, statuses = [200], key) => {
  const result = await platform(environment, route, { method, body, allowStatuses: statuses, headers: key ? { 'Idempotency-Key': key } : {} })
  assert.ok(statuses.includes(result.status), `${method} ${route} returned ${result.status}`)
  return result.value
}
let activeApi = customerApi
const api = (...args) => activeApi(...args)
const openStates = ['QUEUED', 'RUNNING', 'YIELDED']
async function closeSession(id) {
  let current = await api('/api/configuration-assistant/sessions/' + id)
  if (current.goal && !current.goal.terminal) current = await api(`/api/configuration-assistant/sessions/${id}/goals/${current.goal.id}/control`, 'POST', { command: 'CANCEL' })
  if (current.activeTurnId) current = await api('/api/configuration-assistant/sessions/' + id + '/cancel', 'POST', { turnId: current.activeTurnId })
  await api('/api/configuration-assistant/sessions/' + id, 'DELETE', undefined, [204])
  await api('/api/configuration-assistant/sessions/' + id, 'GET', undefined, [404, 410])
}
async function removeBrain(id) {
  assert.equal((await api('/api/deployments/' + id + '/releases')).length, 0)
  await api('/api/deployments/' + id + '/archive', 'POST')
  const op = await api('/api/deployments/' + id, 'DELETE', { hardDelete: false, reason: 'Remove exact resource-free autonomous area qualification fixture.' }, [202])
  for (let i = 0; i < 60; i++) {
    const state = (await platform(environment, '/api/platform/notifications/deployment-deletions/' + op.id)).value
    if (state.status === 'SUCCEEDED') return
    assert.ok(!['FAILED', 'CANCELLED'].includes(state.status), 'Fixture deletion did not succeed.')
    await new Promise(r => setTimeout(r, 1000))
  }
  throw new Error('Fixture cleanup needs reconciliation.')
}
async function createBrain(name) {
  return api('/api/deployments', 'POST', { name: 'Autonomous area qualification ' + name + ' ' + label, environment: 'staging', templateId: 'custom-start-from-scratch', curatedModuleId: 'default', behaviorType: 'CONVERSATIONAL', vectorProvisioningMode: 'LOCAL_MANAGED' }, [201])
}
async function turn(c, session, requestedMode, prefix) {
  const executionMode = c.readOnly ? 'BOUNDED' : requestedMode
  const request = { query: requestedMode === 'AUTONOMOUS' ? c.auto : c.quick, command: 'PREPARE',
    executionMode, previewOnly: Boolean(c.readOnly), sourceIds: session.sources.map(s => s.id), operationIds: [],
    editorContext: c.editorContext ?? { section: c.task, selectedDefinitionIds: [] }, ...(executionMode === 'BOUNDED' ? { validationFeedback: { enabled: !c.readOnly, maxCorrections: 2 } } : {}) }
  save(prefix + '-request', request)
  session = await api(`/api/configuration-assistant/sessions/${session.id}/turns`, 'POST', request, [200], 'area-' + randomUUID())
  const goalId = executionMode === 'AUTONOMOUS' ? session.goal?.id : null
  if (executionMode === 'AUTONOMOUS') assert.ok(goalId, 'The actual request must create a native autonomous goal.')
  const turnId = session.activeTurnId ?? session.turns.at(-1)?.id, deadline = Date.now() + 300000
  while (Date.now() < deadline) {
    session = await api(`/api/configuration-assistant/sessions/${session.id}`)
    save(prefix + '-observed', session)
    if (executionMode === 'AUTONOMOUS') {
      assert.equal(session.goal.id, goalId)
      if (!openStates.includes(session.goal.state)) break
    } else if (!session.activeTurnId) break
    await new Promise(r => setTimeout(r, 1200))
  }
  save(prefix + '-session', session)
  const result = session.turns.find(t => t.id === turnId)
  assert.ok(result, 'The actual requested turn must be retained.')
  if (executionMode === 'AUTONOMOUS') {
    if (session.goal.reasonCode?.includes('BUDGET') || session.goal.reasonCode?.includes('FUNDING')) stop = true
    assert.equal(session.goal.profileId, c.profile)
    assert.equal(session.goal.state, 'COMPLETE', `Autonomous stopped: ${session.goal.reasonCode ?? session.goal.state}: ${session.goal.reasonMessage ?? ''}`)
    if (!c.readOnly) assert.ok(session.goal.operations.some(o => o.status === 'EXECUTED'), 'Completion must have an actual draft save receipt.')
  } else {
    assert.equal(session.activeTurnId, null)
    assert.equal(result.workflow?.selection?.profileId, c.profile)
    if (!c.readOnly) { assert.equal(result.status, 'SAVED'); assert.ok(result.operations?.some(o => o.status === 'EXECUTED')) }
    else assert.ok(!result.operations?.some(o => o.status === 'EXECUTED'))
  }
  return { session, executionMode, goalId, status: result.status, usage: executionMode === 'AUTONOMOUS' ? session.goal.usage : result.workflow?.usage }
}

try {
  const overview = nativeRead(environment, '/api/admin/overview'), budget = nativeRead(environment, '/api/admin/provider-budget')
  assert.equal(overview.status, 200); assert.equal(budget.status, 200)
  assert.equal(overview.value.aiFabricFrameworkVersion, report.framework)
  assert.equal(overview.value.workflowAssistance.autonomousEnabled, true)
  assert.equal(overview.value.workflowAssistance.profiles.length, 9)
  assert.equal(overview.value.inferenceProfile.generationModel, process.env.LOOMAI_EXPECTED_MODEL ?? 'gpt-5.4-mini-2026-03-17', 'Use the qualified cheaper candidate, never a silent Astra fallback.')
  report.model = overview.value.inferenceProfile.generationModel
  save('native-before', { overview, budget })
  await login(environment); staffSignedIn = true
  await page.goto(ui + '/sign-in')
  await page.getByRole('textbox', { name: /^Email/ }).fill(fixture.email)
  await page.getByLabel(/^Password/).fill(fixture.password)
  await page.locator('form').filter({ has: page.getByLabel(/^Password/) }).locator('button[type=submit]').click()
  await page.waitForURL(u => !u.pathname.startsWith('/sign-in'), { timeout: 60000 }); signedIn = true
  token = await page.evaluate(() => JSON.parse(localStorage.getItem('loomai-console-supabase')).access_token)
  const status = await api('/api/configuration-assistant/status')
  assert.equal(status.engine, 'CONFIGURED_BRAIN'); assert.equal(status.autonomousEnabled, true)
  await api('/api/configuration-assistant/sessions', 'POST', { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'loomai-configuration-assistant', task: 'BRAIN_SETUP' }, [403, 404])
  report.knownForeignAssistantDenied = true
  for (const c of allCases.filter(c => !selected || selected.includes(c.id))) {
    if (stop) break
    activeApi = c.auth === 'STAFF' ? staffApi : customerApi
    let brain, resource, session
    const row = { id: c.id, task: c.task, profile: c.profile, status: 'RUNNING', modes: [], cleanup: [] }
    try {
      if (c.prepare) {
        resource = await c.prepare({ api, privateRoot, save, uid: randomUUID().slice(0, 8), environment, ui, createBrain, removeBrain })
      } else {
        brain = await createBrain(c.id); save(c.id + '-fixture', brain)
        let draft = await api('/api/deployments/' + brain.id + '/draft')
        if (c.seed) { const values = structuredClone(configs(draft)); c.seed(values); draft = await api('/api/deployment-drafts/' + draft.id, 'PUT', values) }
        const quickBaseline = process.env.LOOMAI_QUICK_BASELINE
        if (quickBaseline) {
          assert.deepEqual(modes, ['BOUNDED'])
          assert.ok(path.resolve(quickBaseline).startsWith(path.join(privateRoot, 'loomai-build-ai-all-areas-20261125', environment + '-')))
          const baseline = JSON.parse(fs.readFileSync(path.join(quickBaseline, c.id + '-autonomous-after.private.json')))
          draft = await api('/api/deployment-drafts/' + draft.id, 'PUT', configs(baseline))
        }
        save(c.id + '-initial', draft)
        resource = { scope: { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: brain.id, task: c.task }, source: c.source,
          read: () => api('/api/deployments/' + brain.id + '/draft'),
          async assertIsolated() { assert.equal((await api('/api/deployments/' + brain.id + '/versions')).length, 0); assert.equal((await api('/api/deployments/' + brain.id + '/releases')).length, 0) } }
      }
      row.auth = c.auth ?? 'CUSTOMER'
      session = await api('/api/configuration-assistant/sessions', 'POST', resource.scope)
      if (resource.source) session = await api('/api/configuration-assistant/sessions/' + session.id + '/sources', 'POST', { title: 'Selected area documentation', mediaType: 'text/plain', content: resource.source })
      for (const mode of modes) {
        const item = { requestedMode: mode, status: 'RUNNING' }, prefix = c.id + '-' + mode.toLowerCase()
        try {
          const before = await resource.read(); save(prefix + '-before', before)
          const result = await turn({ ...c, editorContext: resource.editorContext }, session, mode, prefix); session = result.session
          const after = await resource.read(); save(prefix + '-after', after)
          c.verify(before, after, mode)
          await resource.assertIsolated()
          Object.assign(item, { status: 'PASS', executionMode: result.executionMode, goalId: result.goalId, turnStatus: result.status, usage: result.usage, requestedValuesVerified: true, noPublication: true })
        } catch (e) {
          item.status = 'FAIL'; item.error = safe(e); process.exitCode = 1
          try { save(prefix + '-after-failure', await resource.read()) } catch (readError) { item.readbackError = safe(readError) }
          const funding = nativeRead(environment, '/api/admin/provider-budget'); save(prefix + '-funding-at-failure', funding)
          const reservation = report.model === 'gpt-5.4-mini-2026-03-17' ? 336864000 : 2887680000
          if (funding.status === 200 && funding.value.effectiveLimits.costNanoUsd - funding.value.accountedUsage.costNanoUsd < reservation) { item.fundingHeadroomLow = true; stop = true }
        }
        row.modes.push(item); save('report', report)
        console.log(JSON.stringify({ environment, area: c.id, mode, ...item }))
        if (item.status !== 'PASS') break
      }
      row.status = row.modes.length === modes.length && row.modes.every(m => m.status === 'PASS') ? 'PASS' : 'FAIL'
    } catch (e) { row.status = 'FAIL'; row.error = safe(e); process.exitCode = 1 }
    finally {
      if (session) try { await closeSession(session.id); row.cleanup.push({ session: session.id, deleted: true }) } catch (e) { row.cleanup.push({ error: safe(e) }); process.exitCode = 1; stop = true }
      if (resource?.cleanup) try { row.cleanup.push(...await resource.cleanup()) } catch (e) { row.cleanup.push({ error: safe(e) }); process.exitCode = 1; stop = true }
      if (brain) try { await removeBrain(brain.id); row.cleanup.push({ brain: brain.id, deleted: true }) } catch (e) { row.cleanup.push({ error: safe(e) }); process.exitCode = 1; stop = true }
      report.cases.push(row); save('report', report)
      console.log(JSON.stringify({ environment, area: c.id, status: row.status, error: row.error, cleanup: row.cleanup }))
    }
  }
  activeApi = customerApi
  assert.ok(!stop && report.cases.every(c => c.status === 'PASS' && c.cleanup.every(x => x.deleted || x.cleaned)), 'Some areas remain unqualified; inspect actual failures.')
  assert.deepEqual(report.pageErrors, [])
  report.status = 'PASS'
} catch (e) { report.status = 'FAIL'; report.error = safe(e); process.exitCode = 1 }
finally {
  if (signedIn) try {
    await page.goto(ui + '/home'); await page.getByRole('button', { name: 'Sign out', exact: true }).click()
    await page.waitForURL(u => u.pathname.startsWith('/sign-in'))
    assert.equal(await page.evaluate(() => Boolean(localStorage.getItem('loomai-console-supabase'))), false)
    report.customerSignedOut = true
  } catch (e) { report.logoutError = safe(e); process.exitCode = 1 }
  token = null; await browser.close(); report.browserClosed = true
  if (staffSignedIn) try { await logout(environment); report.staffSignedOut = true } catch (e) { report.staffLogoutError = safe(e); process.exitCode = 1 }
  report.nativeAfter = nativeRead(environment, '/api/admin/provider-budget')
  report.at = new Date().toISOString(); save('report', report)
  console.log(JSON.stringify({ environment, status: report.status, root, error: report.error, customerSignedOut: report.customerSignedOut, staffSignedOut: report.staffSignedOut }))
}
