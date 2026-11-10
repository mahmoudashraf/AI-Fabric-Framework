import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { dirname, relative, resolve, sep } from 'node:path'
import { parseArgs } from 'node:util'
import { fileURLToPath } from 'node:url'

// Hosted proof only: no mock responses, no publishing/applying/provisioning and no personal accounts.
// baseline/read never save drafts or infer. edit requires a Main-selected disposable Brain.
// execute additionally requires a private case plan, --allow-paid and --max-executions.
// Main owns the README, deployed-source proof, real-runtime setup and paid query matrix.
const { values: args } = parseArgs({ options: {
  run: { type: 'boolean' }, help: { type: 'boolean' }, mode: { type: 'string', default: 'baseline' },
  environment: { type: 'string', default: 'staging' }, output: { type: 'string' },
  'auth-registry': { type: 'string' }, 'auth-index': { type: 'string', default: '1' },
  'auth-mode': { type: 'string', default: 'customer' }, 'brain-id': { type: 'string' }, target: { type: 'string' },
  'expected-customer-id': { type: 'string' }, plan: { type: 'string' },
  'create-draft': { type: 'boolean' },
  'require-task-summary': { type: 'boolean' },
  'allow-draft-write': { type: 'boolean' }, 'allow-paid': { type: 'boolean' },
  'max-executions': { type: 'string', default: '0' }, 'require-readonly': { type: 'boolean' },
} })
if (args.help) {
  console.log(`node verification-support/managed-tasks/qualify-ui.mjs --run --mode baseline|read|edit|execute
  --environment staging|production --output <NEW directory OUTSIDE repository>
  --auth-registry <private auth registry> [--auth-index 1] [--auth-mode customer|staff]
  [--brain-id <Main canary>] [--target <exact target profile>] [--expected-customer-id <owner>]
  [--plan <private JSON>] [--allow-draft-write] [--allow-paid --max-executions <1..10>]
  [--create-draft (edit mode only; new configuration-only Brain, archived afterward)]
  [--require-readonly]
  [--require-task-summary (after Main deploys the narrow Updates fix)]
Private plan: {resources:{knowledge:[{id,label}],readActions:[{id,label}]},
  executions:[{name,taskCode,input,expectedStatus,expectedFailureCode?,expectedConfigurationVersion?,expectedTaskConfigurationHash?}]}.
No fixture APIs, deployment lifecycle writes, automatic inference retries or credential output.`)
  process.exit(0)
}
assert.ok(args.run, 'Explicit --run is required.')
assert.ok(['baseline', 'read', 'edit', 'execute'].includes(args.mode), 'Unknown mode.')
assert.ok(['staging', 'production'].includes(args.environment), 'Unknown environment.')
assert.ok(['customer', 'staff'].includes(args['auth-mode']), 'Unknown authentication mode.')
assert.ok(args.output && args['auth-registry'], 'A new private output directory and private auth registry are required.')
const root = fileURLToPath(new URL('../..', import.meta.url)), output = resolve(args.output)
const outputRelation = relative(root, output)
assert.ok(outputRelation === '..' || outputRelation.startsWith('..' + sep), 'Private captures must remain outside the repository.')
const maxExecutions = Number(args['max-executions'])
assert.ok(Number.isInteger(maxExecutions) && maxExecutions >= 0 && maxExecutions <= 10)
if (args['create-draft']) assert.ok(args.mode === 'edit' && !args['brain-id'], 'Create-draft is only for an independent edit canary, not Main\'s runtime Brain.')
if (args.mode !== 'baseline') assert.ok((args['brain-id'] || args['create-draft']) && args.target, 'Main must supply the exact canary Brain and target, or authorize a separate edit draft.')
if (args.mode === 'edit') assert.ok(args['allow-draft-write'] && args['expected-customer-id'], 'Draft writes need explicit permission and the expected customer.')
if (args.mode === 'execute') assert.ok(args['allow-paid'] && maxExecutions > 0 && args.plan && args['expected-customer-id'], 'Paid execution must be explicitly bounded by Main.')
if (args.mode !== 'execute') assert.equal(args['allow-paid'] ?? false, false, 'Paid calls are allowed only in execute mode.')
await mkdir(dirname(output), { recursive: true, mode: 0o700 })
await mkdir(output, { mode: 0o700 })
const registry = JSON.parse(await readFile(resolve(args['auth-registry']), 'utf8'))
const credential = registry.credentials?.[Number(args['auth-index'])]
assert.ok(credential?.email && credential?.password, 'The selected fixture credential is incomplete.')
assert.ok(credential.email.includes('+') && /fixture/i.test(credential.source ?? ''), 'Use an explicitly recorded dedicated fixture account, not a personal/bootstrap account.')
const plan = args.plan ? JSON.parse(await readFile(resolve(args.plan), 'utf8')) : {}
if (args.mode === 'execute') assert.ok(Array.isArray(plan.executions) && plan.executions.length > 0 && plan.executions.length <= maxExecutions, 'Execution cases must fit the explicit allowance.')
const ui = args.environment === 'production' ? 'https://console.loomai.pro' : 'https://loomai-platform-ui.46.224.145.148.sslip.io'
const api = args.environment === 'production' ? 'https://api.loomai.pro' : 'https://loomai-platform-backend.46.224.145.148.sslip.io'
const require = createRequire(new URL('../../Platfrom/ui/package.json', import.meta.url))
const { chromium } = require('playwright'), lossless = require('lossless-json')
const parse = source => lossless.parse(source, undefined, { parseNumber: value => {
  const n = Number(value)
  return /^-?(0|[1-9]\d*)$/.test(value) && Number.isSafeInteger(n) && !Object.is(n, -0) ? n : new lossless.LosslessNumber(value)
} })
const stringify = value => lossless.stringify(value, undefined, 2)
const identity = value => value === undefined ? 'undefined' : Array.isArray(value) ? `[${value.map(identity).join(',')}]`
  : value !== null && typeof value === 'object' && !lossless.isLosslessNumber(value)
    ? `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${identity(value[key])}`).join(',')}}` : stringify(value)
const report = { environment: args.environment, mode: args.mode, startedAt: new Date().toISOString(), status: 'IN_PROGRESS',
  cases: [], skipped: [], errors: [], consoleErrors: [], consoleWarnings: [], guards: [], cleanup: [], assets: [], requests: [], inferenceCount: 0 }
const secretValues = [credential.password]
const redact = value => [...secretValues, credential.email].reduce((s, value) => value ? s.split(value).join('[REDACTED]') : s, String(value))
const saveReport = () => writeFile(resolve(output, 'report.private.json'), JSON.stringify(report, null, 2), { mode: 0o600 })
const privateJson = (name, value) => writeFile(resolve(output, name), stringify(value), { mode: 0o600 })
const browser = await chromium.launch(), context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const page = await context.newPage(); page.setDefaultTimeout(30000)
let token, authenticated = false, brainId = args['brain-id'], workspace, originalDraft, createdCode, createdBrain = false, plannedDraftWrite = null, plannedExecution = null
const assetReads = []
page.on('pageerror', error => report.errors.push(redact(error.message)))
page.on('console', message => {
  if (message.type() === 'error') report.consoleErrors.push(redact(message.text()))
  if (message.type() === 'warning') report.consoleWarnings.push(redact(message.text()))
})
page.on('dialog', async dialog => {
  if (dialog.type() === 'beforeunload') await dialog.accept()
  else { report.errors.push(`Unexpected native ${dialog.type()} dialog.`); await dialog.dismiss() }
})
page.on('response', response => {
  const url = new URL(response.url())
  if (url.origin === ui && url.pathname.startsWith('/assets/') && url.pathname.endsWith('.js')) assetReads.push((async () => {
    try { report.assets.push({ path: url.pathname, status: response.status(), sha256: createHash('sha256').update(await response.body()).digest('hex') }) } catch { /* Closed-page responses are not deployment proof. */ }
  })())
})
// An abort guard is not a fixture: allowed requests reach the real host unchanged.
// Unexpected first-party writes are blocked before dispatch and make the run fail.
await context.route(`${api}/api/**`, async route => {
  try {
  const request = route.request(), url = new URL(request.url()), method = request.method()
  report.requests.push({ method, path: url.pathname, targetProfileId: url.searchParams.get('targetProfileId') })
  if (['GET', 'HEAD', 'OPTIONS'].includes(method) || ['/api/platform/auth/login', '/api/platform/auth/logout'].includes(url.pathname)) return route.continue()
  if (method === 'PUT' && url.pathname === '/api/platform/preferences') {
    report.presentationPreferenceWrites = (report.presentationPreferenceWrites ?? 0) + 1
    return route.continue()
  }
  if (plannedDraftWrite && method === 'PUT' && url.pathname === plannedDraftWrite) {
    plannedDraftWrite = null
    const body = parse(request.postData())
    assert.deepEqual(Object.keys(body), ['behaviorConfig'], 'Task saves must not write other draft sections.')
    assert.ok(body.behaviorConfig?.managedTasks)
    return route.continue()
  }
  const expectedPath = plannedExecution ? `/api/deployments/${encodeURIComponent(brainId)}/tasks/${encodeURIComponent(plannedExecution.taskCode)}/execute` : null
  if (args.mode === 'execute' && args['allow-paid'] && method === 'POST' && url.pathname === expectedPath
    && url.searchParams.get('targetProfileId') === args.target && report.inferenceCount < maxExecutions) {
    const body = parse(request.postData())
    assert.equal(identity(body.input), identity(plannedExecution.input), 'Only the exact Main-approved task input may execute.')
    plannedExecution = null; report.inferenceCount++
    return route.continue()
  }
  report.guards.push({ method, path: url.pathname }); await route.abort('blockedbyclient')
  } catch (error) {
    report.guards.push({ method: route.request().method(), path: new URL(route.request().url()).pathname, message: redact(error.message) })
    await route.abort('blockedbyclient').catch(() => {})
  }
})
async function read(path, statuses = [200]) {
  const response = await context.request.get(api + path, { headers: token ? { Authorization: `Bearer ${token}` } : {}, timeout: 45000 })
  assert.ok(statuses.includes(response.status()), `GET ${path.split('?')[0]}: HTTP ${response.status()}`)
  const text = await response.text()
  return text ? parse(text) : null
}
async function configurationOnlyCommand(path, body, statuses = [200]) {
  assert.ok(args.mode === 'edit' && args['create-draft'] && args['allow-draft-write'])
  assert.ok(path === '/api/deployments' || createdBrain && path === `/api/deployments/${brainId}/archive`, 'Only create/archive of this empty UI canary is allowed.')
  const response = await context.request.post(api + path, { headers: token ? { Authorization: `Bearer ${token}` } : {},
    ...(body ? { data: body } : {}), timeout: 45000 })
  assert.ok(statuses.includes(response.status()), `Configuration-only command returned HTTP ${response.status()}.`)
  const text = await response.text()
  return text ? parse(text) : null
}
const brainUrl = (section = 'tasks', code = '') => `${ui}/smart-brains/${encodeURIComponent(brainId)}/${section}${code ? `/${encodeURIComponent(code)}` : ''}${args.target ? `?${new URLSearchParams({ targetProfileId: args.target })}` : ''}`
async function capture(name) {
  await page.evaluate(() => Promise.all(document.getAnimations().filter(a => a.playState === 'running' && Number.isFinite(a.effect?.getTiming().iterations)).map(a => a.finished.catch(() => {}))))
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Horizontal page overflow.')
  const visible = await page.locator('body').innerText()
  const formValues = await page.locator('main input,main textarea,pre').evaluateAll(nodes => nodes.map(node => node.value ?? node.textContent ?? '').join('\n'))
  for (const secret of secretValues) if (secret && secret.length > 6) assert.ok(!visible.includes(secret) && !formValues.includes(secret), 'A known authentication value appeared in the authenticated page.')
  await page.screenshot({ path: resolve(output, `${name}.png`), fullPage: true })
}
async function openTask(code) {
  await page.goto(brainUrl('tasks', code), { waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: 'Managed Tasks', exact: true }).waitFor()
  await page.getByRole('switch', { name: 'Enable Managed Tasks', exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: 'Build with AI', exact: true }).count(), 0)
}
async function saveDraft() {
  plannedDraftWrite = `/api/deployment-drafts/${encodeURIComponent(originalDraft.id)}`
  // Capture the exact path before the one-shot dispatch guard consumes it.
  const path = plannedDraftWrite
  const response = await Promise.all([page.waitForResponse(response => new URL(response.url()).pathname === path && response.request().method() === 'PUT'),
    page.locator('footer').getByRole('button', { name: 'Save draft', exact: true }).click()]).then(([response]) => response)
  assert.ok(response.ok(), `UI draft save returned HTTP ${response.status()}.`)
  await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
  return read(`/api/deployments/${brainId}/draft`)
}
async function chooseTab(name) { await page.getByRole('tab', { name, exact: true }).click() }
async function setAdvanced(label, source) {
  const section = page.getByRole('region', { name: `${label} fields`, exact: true })
  await section.getByRole('switch', { name: 'Advanced JSON', exact: true }).check()
  await section.getByRole('textbox', { name: `${label} schema JSON`, exact: true }).fill(source)
}
async function waitAppliedRead() {
  const response = await page.waitForResponse(response => new URL(response.url()).pathname === `/api/deployments/${brainId}/tasks/applied`, { timeout: 30000 })
  assert.ok(response.ok(), `Applied task metadata returned HTTP ${response.status()}.`)
  return parse(await response.text())
}
try {
  await page.goto(ui + (args['auth-mode'] === 'staff' ? '/staff/sign-in' : '/sign-in'), { waitUntil: 'domcontentloaded' })
  await page.locator('input[type=email]').fill(credential.email)
  await page.locator('input[type=password]').fill(credential.password)
  await page.locator('button[type=submit]').click()
  await page.waitForURL(url => !url.pathname.includes('sign-in'), { timeout: 60000 })
  const tokens = await page.evaluate(() => {
    try { const state = JSON.parse(localStorage.getItem('loomai-console-supabase') ?? 'null'); return { access: state?.access_token, refresh: state?.refresh_token } } catch { return {} }
  })
  token = tokens.access; secretValues.push(tokens.access, tokens.refresh)
  const session = await read('/api/platform/auth/session')
  assert.equal(session.authenticated, true)
  if (args['auth-mode'] === 'customer') assert.equal(session.role, 'CUSTOMER_ADMIN')
  else assert.ok(['OPERATOR', 'ADMIN'].includes(session.role), 'A genuine dedicated staff fixture is required.')
  if (args['expected-customer-id'] && args['auth-mode'] === 'customer') assert.equal(session.customerId, args['expected-customer-id'])
  authenticated = true
  report.identity = { role: session.role, customerId: session.customerId, authenticationMode: session.authenticationMode, registryIndex: Number(args['auth-index']) }
  report.cases.push('real-dedicated-fixture-sign-in-and-server-session')
  if (args['create-draft']) {
    assert.equal(session.role, 'CUSTOMER_ADMIN')
    const created = await configurationOnlyCommand('/api/deployments', {
      name: `Managed Tasks UI qualification ${args.environment} ${new Date().toISOString()}`,
      environment: 'staging', templateId: 'custom-start-from-scratch', behaviorType: 'CONVERSATIONAL', vectorProvisioningMode: 'LOCAL_MANAGED',
    }, [201])
    brainId = created.id; createdBrain = true; report.brainId = brainId
    await saveReport()
    assert.deepEqual(await read(`/api/deployments/${brainId}/versions`), [])
    assert.deepEqual(await read(`/api/deployments/${brainId}/releases`), [])
    report.cases.push('separate-configuration-only-edit-brain-with-no-infrastructure')
  }
  const deployments = await read('/api/deployments?includeArchived=false')
  assert.ok(Array.isArray(deployments))
  if (!brainId && args.mode === 'baseline') brainId = deployments.find(item => !item.archivedAt)?.id
  report.brainId = brainId ?? null
  if (!brainId) {
    await page.goto(ui + '/deployments', { waitUntil: 'domcontentloaded' })
    await capture('baseline-resource-list')
    report.skipped.push('No active owned Brain is available; Main must provide the canary before feature checks.')
  } else {
    workspace = await read(`/api/deployments/${brainId}/workspace`)
    assert.equal(workspace.deployment.id, brainId)
    if (args['auth-mode'] === 'customer') assert.equal(workspace.deployment.binding?.customerId, session.customerId, 'Brain must belong to the dedicated fixture customer.')
    if (args['expected-customer-id']) assert.equal(workspace.deployment.binding?.customerId, args['expected-customer-id'])
    report.access = workspace.access
    await page.goto(brainUrl(''), { waitUntil: 'domcontentloaded' })
    await page.getByRole('navigation', { name: 'Smart Brain navigation', exact: true }).waitFor()
    await capture('baseline-brain-shell')
    report.cases.push('real-owned-brain-navigation-and-workspace-access')
    if (args.mode === 'baseline') {
      report.managedTasksNavigationVisible = await page.getByRole('link', { name: 'Managed Tasks', exact: true }).count() > 0
      report.skipped.push('Baseline only: no task mutations, applied summary requests or inference.')
    } else {
      originalDraft = await read(`/api/deployments/${brainId}/draft`)
      await privateJson('draft-before.private.json', originalDraft)
      const versionsBefore = await read(`/api/deployments/${brainId}/versions`), releasesBefore = await read(`/api/deployments/${brainId}/releases`)
      const beforeMetadataReads = report.requests.filter(r => r.path.endsWith('/tasks/applied')).length
      const initialCode = plan.taskCode ?? originalDraft.behaviorConfig?.managedTasks?.tasks?.[0]?.code
      await openTask(initialCode)
      assert.equal(report.requests.filter(r => r.path.endsWith('/tasks/applied')).length, beforeMetadataReads, 'Outcome mount must not read applied task metadata.')
      report.cases.push('real-task-route-and-no-on-mount-summary-or-inference')
      if (args['require-readonly']) {
        assert.equal(workspace.access.canEdit, false, 'This assignment is not read-only; do not infer read-only proof from an admin.')
        assert.equal(await page.getByRole('button', { name: 'Add task', exact: true }).isDisabled(), true)
        assert.equal(await page.locator('footer').getByRole('button', { name: 'Save draft', exact: true }).count(), 0)
        if (initialCode) assert.equal(await page.getByRole('textbox', { name: 'Task name', exact: false }).isDisabled(), true)
        report.cases.push('real-read-only-assignment-has-no-draft-controls')
      }
      if (args.mode === 'edit') {
        assert.equal(workspace.access.canEdit, true)
        assert.ok((originalDraft.behaviorConfig?.managedTasks?.tasks?.length ?? 0) < 16, 'Leave one task slot for the temporary UI proof.')
        createdCode = `ui-proof-${Date.now().toString(36)}`
        await page.getByRole('button', { name: 'Add task', exact: true }).click()
        await page.getByRole('textbox', { name: 'Task name', exact: false }).fill('UI verification task')
        await page.getByRole('textbox', { name: 'Task code', exact: false }).fill(createdCode)
        await page.getByRole('textbox', { name: 'Purpose', exact: false }).fill('Verify hosted task configuration using synthetic input only.')
        await page.getByRole('textbox', { name: 'Instructions', exact: false }).fill('Summarise the supplied message and state any missing information. Return an answer object.')
        const inputSchema = { type: 'object', properties: { message: { type: 'string', minLength: 2, description: 'Synthetic request.' }, details: { $ref: '#/$defs/detail' } },
          $defs: { detail: { type: 'object', properties: { note: { type: 'string' } }, additionalProperties: false } }, required: ['message'], additionalProperties: false }
        await setAdvanced('Input', stringify(inputSchema))
        const fields = page.getByRole('region', { name: 'Input fields', exact: true })
        await fields.getByRole('switch', { name: 'Advanced JSON', exact: true }).uncheck()
        await fields.getByRole('textbox', { name: 'Input field name', exact: true }).fill('includeDetails')
        await fields.getByRole('combobox', { name: 'Input field type', exact: false }).click()
        await page.getByRole('option', { name: 'boolean', exact: true }).click()
        await fields.getByRole('button', { name: 'Add field', exact: true }).click()
        const expectedSchema = { ...inputSchema, properties: { ...inputSchema.properties, includeDetails: { type: 'boolean' } } }
        await page.getByRole('switch', { name: 'Enable Managed Tasks', exact: true }).check()
        let saved = await saveDraft(), actual = saved.behaviorConfig.managedTasks.tasks.find(task => task.code === createdCode)
        assert.equal(identity(actual.inputSchema), identity(expectedSchema))
        const { managedTasks: _beforeTasks, ...beforeBehavior } = originalDraft.behaviorConfig ?? {}
        const { managedTasks: _afterTasks, ...afterBehavior } = saved.behaviorConfig
        assert.equal(identity(afterBehavior), identity(beforeBehavior), 'Unrelated behaviour was changed.')
        assert.equal(identity(saved.actionsConfig), identity(originalDraft.actionsConfig))
        report.cases.push('real-ui-add-save-and-advanced-schema-constraints-preserved')
        await openTask(createdCode)
        assert.equal(await page.getByRole('textbox', { name: 'Task name', exact: false }).inputValue(), 'UI verification task')
        await setAdvanced('Input', stringify(actual.inputSchema))
        assert.equal(identity(parse(await page.getByRole('textbox', { name: 'Input schema JSON', exact: true }).inputValue())), identity(expectedSchema))
        await chooseTab('Resources')
        await page.getByRole('heading', { name: 'Read actions', exact: false }).waitFor()
        for (const [kind, changes] of [['knowledge', plan.resources?.knowledge ?? []], ['readActions', plan.resources?.readActions ?? []]]) {
          for (const choice of changes) {
            assert.ok(typeof choice.id === 'string' && typeof choice.label === 'string')
            await page.getByRole('checkbox', { name: choice.label, exact: true }).check()
          }
        }
        if ((plan.resources?.knowledge?.length ?? 0) + (plan.resources?.readActions?.length ?? 0) > 0) {
          saved = await saveDraft(); actual = saved.behaviorConfig.managedTasks.tasks.find(task => task.code === createdCode)
          assert.deepEqual(actual.vectorSpaces, plan.resources.knowledge?.map(item => item.id) ?? [])
          assert.deepEqual(actual.readActions, plan.resources.readActions?.map(item => item.id) ?? [])
          report.cases.push('real-owned-resource-selection-saved-with-exact-identifiers')
        } else report.skipped.push('Resource catalogue opened; no resource mutation was requested in the private plan.')
        await chooseTab('Outcome')
        await setAdvanced('Output', '{unfinished')
        assert.equal(await page.locator('footer').getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
        await page.getByRole('combobox', { name: 'Return an answer as', exact: false }).click()
        await page.getByRole('option', { name: 'Text', exact: true }).click()
        saved = await saveDraft(); actual = saved.behaviorConfig.managedTasks.tasks.find(task => task.code === createdCode)
        assert.equal(actual.outputMode, 'TEXT'); assert.equal('outputSchema' in actual, false)
        report.cases.push('invalid-json-buffer-blocks-save-and-TEXT-omits-output-schema')
        await page.getByRole('textbox', { name: 'Task name', exact: false }).fill('Unsaved local task title')
        await page.getByRole('link', { name: 'Responses', exact: true }).click()
        await page.getByRole('dialog', { name: 'Leave unsaved changes?', exact: true }).waitFor()
        await page.getByRole('button', { name: 'Keep editing', exact: true }).click()
        await page.locator('footer').getByRole('button', { name: 'Discard', exact: true }).click()
        await page.waitForFunction(() => document.querySelector('input[value="UI verification task"]'))
        await chooseTab('Try')
        assert.equal(await page.getByRole('button', { name: 'Run task', exact: true }).isDisabled(), true)
        await capture('newer-draft-gate')
        report.cases.push('real-unsaved-navigation-discard-and-unapplied-task-gate')
        await page.goto(brainUrl('updates'), { waitUntil: 'domcontentloaded' })
        await page.getByRole('heading', { name: 'Updates', exact: true }).waitFor()
        const behaviorRow = page.getByRole('button', { name: 'Preview How conversations run', exact: true })
        const hasSummary = (await behaviorRow.innerText()).includes('Managed Tasks')
        if (args['require-task-summary']) assert.ok(hasSummary, 'The serving Updates UI must render the Managed Tasks summary.')
        if (!hasSummary) report.skipped.push('Rendered Managed Tasks natural summary awaits the narrow UI-fix rollout; generic comparison is still checked.')
        await behaviorRow.click()
        const comparison = page.locator('[data-change-preview]')
        await comparison.waitFor()
        await comparison.getByRole('button', { name: 'Technical details', exact: true }).click()
        const comparisonText = await comparison.innerText()
        const existingApplied = releasesBefore.some(item => item.targetProfileId === args.target && item.provisioningStatus === 'ACTIVE')
        if (existingApplied) assert.ok(comparisonText.includes('managedTasks'), 'Task changes must be visible in the existing version comparison.')
        else assert.ok(comparisonText.includes('New setup.'), 'An empty configuration-only Brain must not invent an applied comparison.')
        await capture('updates-task-changes')
        report.cases.push('updates-comparison-reuses-existing-release-workflow-without-publishing')
        await openTask(createdCode)
      }
      if (args.mode === 'execute') {
        assert.equal(workspace.access.canOperate, true)
        for (const execution of plan.executions) {
          assert.ok(execution.taskCode && execution.name && execution.input && typeof execution.input === 'object' && !Array.isArray(execution.input))
          await openTask(execution.taskCode)
          const appliedPromise = waitAppliedRead()
          await chooseTab('Try')
          const applied = await appliedPromise
          if (execution.expectedConfigurationVersion) assert.equal(applied.configurationVersion, execution.expectedConfigurationVersion)
          if (execution.expectedTaskConfigurationHash) assert.equal(applied.taskConfigurationHash, execution.expectedTaskConfigurationHash)
          assert.ok(applied.tasks.some(task => task.code === execution.taskCode))
          await page.getByRole('textbox', { name: 'Task input JSON', exact: true }).fill(stringify(execution.input))
          const run = page.getByRole('button', { name: 'Run task', exact: true })
          if (await run.isDisabled()) {
            const reason = (await page.getByRole('alert').allInnerTexts()).join(' | ')
            report.blockedExecution = { taskCode: execution.taskCode, configurationVersion: applied.configurationVersion,
              taskConfigurationHash: applied.taskConfigurationHash, reason, dispatched: false }
            assert.fail(`Run task is disabled before dispatch: ${reason || 'No visible reason supplied.'}`)
          }
          const path = `/api/deployments/${brainId}/tasks/${encodeURIComponent(execution.taskCode)}/execute`
          plannedExecution = execution
          const [response] = await Promise.all([
            page.waitForResponse(response => new URL(response.url()).pathname === path && response.request().method() === 'POST', { timeout: 145000 }),
            run.click(),
          ])
          const body = parse(await response.text())
          assert.equal(body.taskCode, execution.taskCode); assert.equal(body.status, execution.expectedStatus)
          assert.equal(body.sideEffectsExecuted, false); assert.equal(typeof body.runId, 'string')
          if (execution.expectedFailureCode) assert.equal(body.failure?.code, execution.expectedFailureCode)
          await page.getByRole('region', { name: 'Task result', exact: true }).getByText(body.status, { exact: true }).waitFor()
          if (execution.expectedFailureCode) assert.ok((await page.getByRole('region', { name: 'Task result', exact: true }).innerText()).includes(execution.expectedFailureCode))
          await privateJson(`execution-${report.inferenceCount}.private.json`, { name: execution.name, httpStatus: response.status(), result: body })
          await capture(`execution-${report.inferenceCount}`)
          report.cases.push(`real-explicit-task-execution:${execution.name}`)
        }
      }
      const inspectCode = createdCode ?? initialCode
      if (inspectCode) {
        await openTask(inspectCode)
        await chooseTab('Resources')
        await page.getByRole('heading', { name: 'Read actions', exact: false }).waitFor()
        await capture('resources-desktop')
        const metadataBeforeUse = report.requests.filter(r => r.path.endsWith('/tasks/applied')).length
        const hasAppliedRelease = releasesBefore.some(item => item.targetProfileId === args.target && item.provisioningStatus === 'ACTIVE'
          && ['APPLIED_VERIFIED', 'VERIFYING', 'APPLIED_VERIFICATION_FAILED'].includes(item.status))
        const appliedRead = hasAppliedRelease ? waitAppliedRead() : null
        await chooseTab('Use in your app')
        if (appliedRead) await appliedRead
        await page.locator('pre').waitFor()
        const snippet = await page.locator('pre').first().innerText()
        assert.ok(snippet.includes('/api/tasks/v1/tasks/'))
        assert.ok(snippet.includes('X-AIFABRIC-RUNTIME-API-KEY: <private-runtime-api-key>'))
        assert.ok(snippet.includes('X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer <private-runtime-token>'))
        assert.ok(!snippet.includes("--header 'Authorization:"))
        if (hasAppliedRelease) assert.ok(report.requests.filter(r => r.path.endsWith('/tasks/applied')).length > metadataBeforeUse)
        await capture('use-api-desktop')
        report.cases.push('private-auth-api-example-and-applied-summary-only-on-selected-runtime-view')
        for (const theme of ['light', 'dark']) {
          const desired = theme === 'light' ? 'dark' : 'light'
          if (await page.locator(`[data-customer-theme="${desired}"]`).count()) await page.getByRole('button', { name: 'Switch color theme', exact: true }).click()
          await page.setViewportSize({ width: 1440, height: 1000 })
          await chooseTab('Outcome'); await capture(`tasks-desktop-${theme}`)
          await page.setViewportSize({ width: 390, height: 844 })
          await page.getByRole('button', { name: 'Open Smart Brain navigation', exact: true }).click()
          await page.getByRole('link', { name: 'Managed Tasks', exact: true }).waitFor()
          await page.keyboard.press('Escape')
          await chooseTab('Try')
          if (args['require-readonly'] && workspace.access.canOperate === false) assert.equal(await page.getByRole('button', { name: 'Run task', exact: true }).isDisabled(), true)
          await capture(`tasks-mobile-${theme}`)
        }
        report.cases.push('real-desktop-mobile-light-dark-navigation-and-known-auth-value-leak-checks')
      } else report.skipped.push('No configured task exists; field and runtime-view checks require a Main-provided canary task.')
      const afterVersions = await read(`/api/deployments/${brainId}/versions`), afterReleases = await read(`/api/deployments/${brainId}/releases`)
      assert.equal(identity(afterVersions), identity(versionsBefore), 'The UI runner must not publish versions.')
      assert.equal(identity(afterReleases), identity(releasesBefore), 'The UI runner must not apply or change releases.')
      if (args.mode !== 'edit') assert.equal(identity(await read(`/api/deployments/${brainId}/draft`)), identity(originalDraft), 'Read/execute mode changed the draft.')
    }
  }
  assert.deepEqual(report.guards, [], 'An unplanned write was attempted.')
  assert.deepEqual(report.errors, [], 'Browser page errors were captured.')
  if (args.mode !== 'execute') assert.equal(report.inferenceCount, 0)
  report.status = 'PASS'
} catch (error) {
  report.status = 'FAIL'; report.failure = redact(error.message)
  if (authenticated) await page.screenshot({ path: resolve(output, 'failure.png'), fullPage: true }).catch(() => {})
} finally {
  if (createdCode && originalDraft) {
    try {
      await page.setViewportSize({ width: 1440, height: 1000 })
      const current = await read(`/api/deployments/${brainId}/draft`)
      if (current.behaviorConfig?.managedTasks?.tasks?.some(task => task.code === createdCode)) {
        await openTask(createdCode)
        await page.getByRole('button', { name: 'Remove task', exact: true }).click()
        await page.getByRole('dialog').getByRole('button', { name: 'Remove task', exact: true }).click()
        await page.getByRole('switch', { name: 'Enable Managed Tasks', exact: true }).setChecked(originalDraft.behaviorConfig?.managedTasks?.enabled === true)
        await saveDraft()
        const restored = await read(`/api/deployments/${brainId}/draft`)
        assert.equal(restored.behaviorConfig.managedTasks.tasks.some(task => task.code === createdCode), false)
        assert.equal(identity(restored.behaviorConfig.managedTasks.tasks), identity(originalDraft.behaviorConfig?.managedTasks?.tasks ?? []))
        report.cleanup.push('only-temporary-ui-task-removed-and-original-task-list-restored')
      } else report.cleanup.push('temporary-ui-task-was-not-persisted')
    } catch (error) { report.status = 'FAIL'; report.cleanup.push(redact(`Task cleanup requires Main: ${error.message}`)) }
  }
  if (createdBrain) {
    try {
      assert.deepEqual(await read(`/api/deployments/${brainId}/versions`), [])
      assert.deepEqual(await read(`/api/deployments/${brainId}/releases`), [])
      await configurationOnlyCommand(`/api/deployments/${brainId}/archive`)
      report.cleanup.push('empty-configuration-only-ui-brain-archived')
    } catch (error) { report.status = 'FAIL'; report.cleanup.push(redact(`Canary archive requires Main: ${error.message}`)) }
  }
  try {
    if (authenticated) {
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.goto(ui + '/home', { waitUntil: 'domcontentloaded' })
      await page.getByRole('button', { name: 'Sign out', exact: true }).click()
      await page.waitForURL(url => url.pathname === '/' || url.pathname.includes('sign-in'), { timeout: 30000 })
      assert.equal(await page.evaluate(() => localStorage.getItem('loomai-console-supabase')), null)
      report.cleanup.push('fixture-browser-signed-out')
    }
  } catch { report.status = 'FAIL'; report.cleanup.push('sign-out-not-confirmed') }
  report.finishedAt = new Date().toISOString()
  if (report.guards.length || report.errors.length) report.status = 'FAIL'
  await Promise.allSettled(assetReads)
  await saveReport(); await context.close(); await browser.close()
}
console.log(JSON.stringify({ status: report.status, environment: report.environment, mode: report.mode,
  brainId: report.brainId, output, cases: report.cases, skipped: report.skipped, inferenceCount: report.inferenceCount,
  guards: report.guards, errors: report.errors, consoleErrorCount: report.consoleErrors.length,
  consoleWarningCount: report.consoleWarnings.length, cleanup: report.cleanup, failure: report.failure }))
if (report.status !== 'PASS') process.exitCode = 1
