import assert from 'node:assert/strict'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { parseArgs } from 'node:util'
const { chromium } = createRequire(new URL('../../../Platfrom/ui/package.json', import.meta.url))('playwright')
const { values: args } = parseArgs({ options: Object.fromEntries(
  ['mode', 'root', 'credentials', 'deployment', 'app', 'customer', 'offer', 'staging', 'production', 'attempt']
    .map(key => [key, { type: 'string' }])) })
for (const key of ['mode', 'root', 'credentials', 'deployment', 'app', 'customer', 'offer', 'staging', 'production']) assert.ok(args[key], `Missing --${key}`)
if (args.attempt) assert.match(args.attempt, /^[a-zA-Z0-9_-]{1,32}$/)
const commandRecord = args.mode + (args.attempt ? `-${args.attempt}` : '')
assert.ok(['inspect', 'publish', 'stage', 'observe-stage', 'bind-stage', 'plan-production', 'production',
  'observe-production', 'refresh-readiness', 'activate-production', 'verify-stage', 'verify-production'].includes(args.mode))
process.umask(0o077)
mkdirSync(args.root, { recursive: true, mode: 0o700 })
const save = (name, value) => writeFileSync(`${args.root}/${name}.json`, JSON.stringify(value, null, 2) + '\n', { flag: 'wx', mode: 0o600 })
const read = name => JSON.parse(readFileSync(`${args.root}/${name}.json`, 'utf8'))
const stamp = `${args.mode}-${Date.now()}`
const api = 'https://api.loomai.pro', base = 'https://console.loomai.pro'
const path = `/api/customer-self-service/catalogue/${args.offer}/deployments/${args.deployment}`
const appPath = `/api/platform/customers/${args.customer}/ai-workspace-installations/${args.app}`
const report = { startedAt: new Date().toISOString(), mode: args.mode, status: 'RUNNING', calls: [], blocked: [], errors: [] }
const browser = await chromium.launch()
let page, context, state, phase = 'login'
const allowedCommand = (request, url) => {
  if (['/api/platform/auth/login', '/api/platform/auth/logout', '/api/platform/preferences'].includes(url.pathname)) return true
  if (url.pathname === `/api/customer-self-service/catalogue/${args.offer}/targets/${args.staging}/placement-evidence`
    || url.pathname === `/api/customer-self-service/catalogue/${args.offer}/targets/${args.production}/placement-evidence`) return true
  if (['verify-stage', 'verify-production'].includes(args.mode)
    && url.pathname === `/api/deployments/${args.deployment}/verification-runs/recheck`) {
    return url.searchParams.get('releaseId') === state[args.mode === 'verify-stage' ? 'stagingRequestedReleaseId' : 'productionRequestedReleaseId']
  }
  const body = request.postDataJSON()
  const operations = { stage: 'APPLY', 'bind-stage': 'ACTIVATE', 'plan-production': 'PROMOTION_PLAN',
    production: 'PROMOTION_APPLY', 'refresh-readiness': 'ACTIVATE', 'activate-production': 'ACTIVATE' }
  if (url.pathname === path + '/admission-reservations') {
    assert.ok(body.operation === operations[args.mode] || (args.mode === 'production' && body.operation === 'PROMOTION_PLAN'))
    assert.equal(body.targetProfileId, ['stage', 'bind-stage'].includes(args.mode) ? args.staging : args.production)
    assert.equal(body.versionId, state.versionId)
    return true
  }
  if (args.mode === 'publish' && url.pathname === path + '/versions') {
    assert.equal(body.targetProfileId, args.staging); return true
  }
  if (args.mode === 'stage' && url.pathname === path + '/staging-releases') {
    assert.equal(body.versionId, state.versionId); return true
  }
  if (args.mode === 'bind-stage' && url.pathname === path + '/staging-consumer-activations') {
    assert.equal(body.consumerId, state.consumerId); assert.equal(body.releaseId, state.stagingReleaseId); return true
  }
  const commands = { 'plan-production': 'plan', production: 'production-apply', 'activate-production': 'activate-production-consumer' }
  if (commands[args.mode] && (url.pathname === `/api/deployments/${args.deployment}/practical-promotion/${commands[args.mode]}`
    || (args.mode === 'production' && url.pathname === `/api/deployments/${args.deployment}/practical-promotion/plan`))) {
    assert.equal(body.consumerId, state.consumerId)
    if (args.mode === 'activate-production') assert.equal(body.productionReleaseId, state.productionReleaseId)
    else assert.equal(body.stagingReleaseId, state.stagingReleaseId)
    return true
  }
  return false
}
try {
  context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' })
  await context.route('**/*', route => {
    const request = route.request(), url = new URL(request.url())
    if (['GET', 'HEAD', 'OPTIONS'].includes(request.method())) return route.continue()
    try {
      assert.equal(url.origin, api); assert.ok(allowedCommand(request, url))
      report.calls.push({ method: request.method(), path: url.pathname })
      return route.continue()
    } catch {
      report.blocked.push({ method: request.method(), path: url.pathname }); return route.abort('blockedbyclient')
    }
  })
  page = await context.newPage(); page.setDefaultTimeout(45000)
  page.on('pageerror', error => report.errors.push(error.message))
  const get = async requestPath => {
    const response = await context.request.get(api + requestPath)
    assert.equal(response.status(), 200, requestPath)
    return response.json()
  }
  await page.goto(base + '/home', { waitUntil: 'networkidle' })
  const owner = JSON.parse(readFileSync(args.credentials, 'utf8'))
  await page.getByLabel('Email', { exact: true }).fill(owner.email)
  await page.getByLabel('Password', { exact: true }).fill(owner.password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.getByRole('heading', { name: 'Your products', exact: true }).waitFor()
  const session = await get('/api/platform/auth/session')
  assert.equal(session.role, 'CUSTOMER_ADMIN'); assert.equal(session.customerId, args.customer)
  const app = await get(appPath)
  if (['inspect', 'publish', 'stage', 'observe-stage', 'bind-stage', 'verify-stage'].includes(args.mode)) {
    assert.ok(['DRAFT', 'ACTIVE'].includes(app.status))
  } else assert.equal(app.status, 'ACTIVE')
  assert.equal(app.deploymentId, args.deployment)
  const deployments = await get('/api/customer-self-service/deployments')
  const deployment = deployments.find(item => item.deploymentId === args.deployment)
  assert.equal(deployment.offerId, args.offer)
  const offers = await get('/api/customer-self-service/catalogue')
  const offer = offers.find(item => item.id === args.offer)
  assert.equal(offer.fingerprint, deployment.offerFingerprint)
  assert.equal(offer.entitlement.customerId, args.customer); assert.equal(offer.entitlement.status, 'ACTIVE')
  const customers = await get('/api/platform/customers')
  const consumer = customers.find(item => item.id === args.customer)?.consumers.find(item => item.consumerId === app.consumerId)
  assert.equal(consumer.boundDeploymentId, args.deployment)
  if (!existsSync(`${args.root}/state.json`)) {
    assert.equal(args.mode, 'inspect')
    save('app-before.private', app)
    save('state', { customerId: args.customer, deploymentId: args.deployment, offerId: args.offer,
      consumerId: consumer.consumerId, originalReleaseId: app.releaseId, originalRowVersion: app.rowVersion,
      appUrl: app.appUrl, initialBindingRevision: consumer.bindingRevision })
  }
  state = read('state')
  assert.equal(state.consumerId, app.consumerId); assert.equal(state.deploymentId, args.deployment)
  phase = 'open-product-setup'
  await page.goto(`${base}/deployments?resourceKind=deployment&resourceId=${args.deployment}&deploymentId=${args.deployment}`, { waitUntil: 'networkidle' })
  const tasks = page.getByRole('region', { name: 'Product setup and operations', exact: true })
  await tasks.waitFor()
  const production = ['plan-production', 'production', 'observe-production', 'refresh-readiness', 'activate-production', 'verify-production'].includes(args.mode)
  await tasks.getByRole('link', { name: production ? 'Review go-live' : 'Test in customer staging', exact: true }).click()
  await page.getByRole('heading', { name: production ? 'Production promotion' : 'Staging release', exact: true }).waitFor()
  await page.waitForLoadState('networkidle')
  const observe = async target => {
    const rows = await get(`/api/deployments/${args.deployment}/releases`)
    const requestedId = state[target === args.staging ? 'stagingRequestedReleaseId' : 'productionRequestedReleaseId']
    const match = rows.filter(item => item.targetProfileId === target && item.deploymentVersionId === state.versionId
      && (!requestedId || item.id === requestedId))
    assert.equal(match.length, 1, 'One exact new-version release is required')
    const release = match[0]
    save(`${stamp}-release.private`, release)
    return release
  }
  const buttons = async () => page.getByRole('button').evaluateAll(items => items.filter(item => item.offsetParent)
    .map(item => ({ label: item.innerText, disabled: item.disabled })))
  if (args.mode === 'inspect') {
    report.buttons = await buttons()
    report.text = await page.locator('main').innerText()
    const draft = await get(`/api/deployments/${args.deployment}/draft`)
    save(`${stamp}-draft.private`, draft)
    report.draft = { id: draft.id, status: draft.status, revisionNumber: draft.revisionNumber }
    report.currentVersionIds = (await get(`/api/deployments/${args.deployment}/versions`)).map(item => item.id)
    report.admissions = (await get(path + `/admission-reservations?targetProfileId=${args.staging}`))
      .map(({ id, state, expiryState, expiresAt }) => ({ id, state, expiryState, expiresAt }))
    report.retirements = (await get(path + `/retirements?targetProfileId=${args.staging}`))
      .map(({ id, state, phase, blockers }) => ({ id, state, phase, blockers }))
  } else if (['verify-stage', 'verify-production'].includes(args.mode)) {
    const release = await observe(args.mode === 'verify-stage' ? args.staging : args.production)
    assert.equal(release.provisioningStatus, 'ACTIVE')
    phase = 'verify-exact-release'
    assert.equal(existsSync(`${args.root}/${args.mode}.intent.json`), false, 'Consumed verification: inspect its original result')
    const button = page.getByRole('button', { name: args.mode === 'verify-stage' ? 'Verify staging release' : 'Verify production release', exact: true })
    assert.ok(await button.isEnabled())
    save(`${args.mode}.intent`, { at: new Date().toISOString(), releaseId: release.id, rebuild: false, replay: false })
    const pending = page.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname === `/api/deployments/${args.deployment}/verification-runs/recheck`
      && new URL(response.url()).searchParams.get('releaseId') === release.id, { timeout: 180000 })
    await button.click()
    const response = await pending, body = await response.json()
    save(`${args.mode}.response.private`, { status: response.status(), body })
    assert.ok([200, 201].includes(response.status())); assert.equal(body.releaseId, release.id)
    assert.equal(body.deploymentId, args.deployment); assert.equal(body.deploymentVersionId, state.versionId)
    report.verification = { id: body.id, status: body.status, summaryMessage: body.summaryMessage }
    assert.equal(body.status, 'PASSED', 'Verification failures remain visible; do not rebuild or bypass')
  } else if (args.mode.startsWith('observe-')) {
    phase = 'observe-exact-release'
    const target = args.mode === 'observe-stage' ? args.staging : args.production
    const release = await observe(target)
    report.release = { id: release.id, status: release.status, verificationStatus: release.verificationStatus,
      provisioningStatus: release.provisioningStatus, step: release.currentStepKey, error: release.errorMessage }
    if (release.status === 'APPLIED_VERIFIED' && release.verificationStatus === 'PASSED') {
      const key = args.mode === 'observe-stage' ? 'stagingReleaseId' : 'productionReleaseId'
      save(`${stamp}-state`, { ...state, [key]: release.id })
      writeFileSync(`${args.root}/state.json`, JSON.stringify({ ...state, [key]: release.id }, null, 2) + '\n', { mode: 0o600 })
    }
  } else {
    const labels = { publish: 'Publish current configuration', stage: 'Deploy to customer staging',
      'bind-stage': 'Use latest verified staging release', 'plan-production': 'Review production candidate',
      production: 'Deploy production candidate', 'refresh-readiness': 'Refresh production readiness',
      'activate-production': 'Activate production' }
    if (args.mode === 'bind-stage') {
      await page.getByRole('tab', { name: 'Configure', exact: true }).click()
      await page.getByRole('heading', { name: 'Engage assignment', exact: true }).waitFor()
    }
    phase = `command-${args.mode}`
    const button = page.getByRole('button', { name: labels[args.mode], exact: true })
    if (args.mode === 'production' && !(await button.isEnabled())) {
      save(`${commandRecord}-review.intent`, { at: new Date().toISOString(), stagingReleaseId: state.stagingReleaseId, replay: false })
      const reviewed = page.waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).pathname === `/api/deployments/${args.deployment}/practical-promotion/plan`)
      await page.getByRole('button', { name: 'Review production candidate', exact: true }).click()
      const response = await reviewed, body = await response.json()
      save(`${commandRecord}-review.response.private`, { status: response.status(), body })
      assert.ok(response.ok()); assert.equal(body.status, 'READY')
    }
    if (args.mode === 'activate-production' && !(await button.isEnabled())) {
      save(`${commandRecord}-readiness.intent`, { at: new Date().toISOString(), productionReleaseId: state.productionReleaseId, replay: false })
      const pending = page.waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).pathname === path + '/admission-reservations')
      await page.getByRole('button', { name: 'Refresh production readiness', exact: true }).click()
      const response = await pending, body = await response.json()
      save(`${commandRecord}-readiness.response.private`, { status: response.status(), body })
      assert.ok(response.ok())
      await button.click({ trial: true })
    }
    report.buttons = await buttons()
    assert.ok(await button.isEnabled(), 'The UI command is blocked; inspect recorded buttons and screenshot')
    const intent = `${commandRecord}.intent`
    assert.equal(existsSync(`${args.root}/${intent}.json`), false, 'CONSUMED: reconcile the original command, do not replay')
    save(intent, { at: new Date().toISOString(), mode: args.mode, state, replay: false })
    const finalPaths = { publish: path + '/versions', stage: path + '/staging-releases',
      'bind-stage': path + '/staging-consumer-activations', 'plan-production': `/api/deployments/${args.deployment}/practical-promotion/plan`,
      production: `/api/deployments/${args.deployment}/practical-promotion/production-apply`,
      'activate-production': `/api/deployments/${args.deployment}/practical-promotion/activate-production-consumer`,
      'refresh-readiness': path + '/admission-reservations' }
    const responsePromise = page.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname === finalPaths[args.mode])
    await button.click()
    const response = await responsePromise
    const body = await response.json()
    save(`${commandRecord}.response.private`, { status: response.status(), body })
    report.command = { status: response.status(), id: body.id ?? body.versionId, code: body.code ?? body.errorCode,
      message: body.message, statusName: body.status }
    assert.ok(response.ok(), `${args.mode} failed with ${response.status()} ${body.code ?? body.errorCode ?? body.message ?? ''}`)
    if (args.mode === 'publish') {
      assert.ok(typeof body.versionId === 'string' && body.versionId.length > 0)
      state.versionId = body.versionId
      writeFileSync(`${args.root}/state.json`, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 })
    }
    if (args.mode === 'stage' || args.mode === 'production') {
      const releaseId = args.mode === 'stage' ? body.id : body.productionReleaseId
      assert.ok(typeof releaseId === 'string' && releaseId.length > 0)
      state[args.mode === 'stage' ? 'stagingRequestedReleaseId' : 'productionRequestedReleaseId'] = releaseId
      writeFileSync(`${args.root}/state.json`, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 })
    }
    await page.waitForLoadState('networkidle')
  }
  await page.screenshot({ path: `${args.root}/${stamp}.png`, fullPage: true })
  report.appAfter = (({ status, releaseId, rowVersion, ready, assignmentRevision }) => ({ status, releaseId, rowVersion, ready, assignmentRevision }))(await get(appPath))
  assert.deepEqual(report.blocked, []); assert.deepEqual(report.errors, [])
  report.status = 'PASS'
} catch (error) {
  report.status = 'STOPPED_NOT_ACCEPTED'; report.phase = phase; report.error = error.message; process.exitCode = 1
  if (page) {
    await page.screenshot({ path: `${args.root}/${stamp}-failure.png`, fullPage: true }).catch(() => {})
    report.text = (await page.locator('body').innerText().catch(() => '')).slice(-16000)
  }
} finally {
  await browser.close(); report.finishedAt = new Date().toISOString(); save(`${stamp}-report.private`, report)
  const { text, ...summary } = report
  console.log(JSON.stringify({ ...summary, evidenceRoot: args.root }))
}
