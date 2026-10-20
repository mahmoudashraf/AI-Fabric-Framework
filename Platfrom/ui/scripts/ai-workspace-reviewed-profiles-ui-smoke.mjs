import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer } from 'vite'

const root = fileURLToPath(new URL('..', import.meta.url))
const server = await createServer({
  root, logLevel: 'error', server: { host: '127.0.0.1', port: 4395, strictPort: true },
})
await server.listen()
const base = server.resolvedUrls.local[0]
const browser = await chromium.launch({ headless: true })

function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`
  if (value !== null && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`
  }
  return JSON.stringify(value)
}
const pin = (kind, value) => `${kind}:${createHash('sha256').update(canonical(value)).digest('hex')}`
const source = {
  id: 'owned-app', installationId: 'owned-app', customerId: 'customer-one', consumerId: 'support',
  displayName: 'Reviewed support app', status: 'DISABLED', experiencePackCode: 'generic-workspace',
  experiencePackVersion: '1.0.0', connectionMode: 'public-runtime-authenticated',
  connectionProfileCode: 'runtime-authenticated-broker', connectionProfileVersion: '1.0.0',
  connectionConfiguration: {}, allowedOrigins: ['https://support.apps.example.test'],
  configuration: {
    app: { assistantLabel: 'Support AI' }, page: { rootSelector: '#support', maxChars: 3000 },
    toolGroups: {
      initialScope: 'default',
      default: { label: 'Support', tools: [{ label: 'Search knowledge', query: 'Search approved support knowledge',
        position: 'search', mode: 'executor', icon: 'search' }] },
      contextual: { label: 'Current context', tools: [{ label: 'Summarize', query: 'Summarize the attached context',
        position: 'search', mode: 'executor', icon: 'documents' }] },
    },
    requestContext: { product: 'support' }, theme: { primaryColor: '#176d61', darkMode: true },
  },
  deliveryMode: 'standalone', appSlug: 'support', appUrl: 'https://support.apps.example.test',
  interactionPolicy: 'login-required', authProfileCode: 'supabase', authProfileVersion: '1.0.0',
  authConfiguration: { projectUrl: 'https://auth.example.test', publishableKey: 'sb_publishable_UI_FIXTURE', methods: ['email-password', 'magic-link'] },
  domains: [], deploymentId: 'deployment-one', releaseId: 'release-one', assignmentRevision: '1',
  ready: false, readinessChecks: [], rowVersion: 9,
}
const { theme, ...configuration } = source.configuration
const offer = {
  id: 'support-offer', name: 'Support experience', fingerprint: 'offer-fingerprint',
  entitlement: { customerId: 'customer-one', status: 'ACTIVE', revision: 1 },
  targets: [{ id: 'staging-target', capabilities: ['APP_CONFIGURE', 'AUTH_CONFIGURE', 'DOMAIN_CONFIGURE'] }],
  workspaceProfiles: [],
  settings: [
    { setting: 'APP_TITLE', type: 'TEXT', maxLength: 120, allowedValues: [] },
    { setting: 'INTERACTION_PROFILE_ID', type: 'CHOICE', maxLength: 100, allowedValues: [pin('workspace', {
      packCode: source.experiencePackCode, packVersion: source.experiencePackVersion,
      connectionMode: 'PUBLIC_RUNTIME_AUTHENTICATED', connectionProfileCode: source.connectionProfileCode,
      connectionProfileVersion: source.connectionProfileVersion, deliveryMode: 'STANDALONE',
      connectionConfiguration: {}, configuration,
    })] },
    { setting: 'THEME_PROFILE_ID', type: 'CHOICE', maxLength: 100, allowedValues: [pin('theme', theme)] },
    { setting: 'AUTH_PROFILE_ID', type: 'CHOICE', maxLength: 100, allowedValues: [pin('auth', {
      profileCode: source.authProfileCode, profileVersion: source.authProfileVersion,
      interactionPolicy: 'LOGIN_REQUIRED', configuration: source.authConfiguration,
    })] },
  ],
}

const publishedProfile = {
  id: 'launch-experience', label: 'Reviewed launch experience',
  interaction: {
    packCode: source.experiencePackCode, packVersion: source.experiencePackVersion,
    connectionMode: 'PUBLIC_RUNTIME_ANONYMOUS', connectionProfileCode: 'runtime-anonymous-direct',
    connectionProfileVersion: source.connectionProfileVersion, deliveryMode: 'STANDALONE',
    connectionConfiguration: {}, configuration,
  },
  theme,
  authentication: {
    profileCode: source.authProfileCode, profileVersion: source.authProfileVersion,
    interactionPolicy: 'PUBLIC_WITH_PROTECTED_CAPABILITIES', configuration: source.authConfiguration,
  },
}

async function open({ role = 'CUSTOMER_ADMIN', empty = false, denyCatalogue = false, staleOffer = false,
  includeUnreviewed = false, publishProfiles = false, tamperedProfile = false, noTheme = false, nestedTheme = false,
  invalidDirectConfig = false, preRelease = false, loseFirstSave = false } = {}) {
  const context = await browser.newContext()
  const page = await context.newPage()
  page.setDefaultTimeout(30_000)
  await page.route(`${base}runtime-config.js`, route => route.fulfill({
    contentType: 'application/javascript',
    body: `window.__PLATFORM_RUNTIME_CONFIG__ = ${JSON.stringify({ apiBaseUrl: base.replace(/\/$/, '') })}`,
  }))
  const calls = []
  const errors = []
  let firstSave = null
  page.on('pageerror', error => errors.push(error.message))
  const consumer = {
    consumerId: 'support', customerId: 'customer-one', displayName: 'Support', status: 'ACTIVE',
    boundDeploymentId: 'deployment-one', boundTargetProfileId: 'staging-target', boundReleaseId: preRelease ? null : 'release-one',
  }
  await page.route(`${base}api/**`, async route => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    calls.push({ path, method, body: route.request().postDataJSON(), headers: route.request().headers() })
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/platform/auth/session') return json({
      enabled: true, authenticated: true, authenticationMode: 'SESSION', actorId: 'owner', role,
      customerId: 'customer-one', customerName: 'Customer One', canManageCustomers: true,
      canManageUsers: role === 'PLATFORM_ADMIN', canManageUserDirectory: true,
    })
    if (path === '/api/platform/customers') return json([{ id: 'customer-one', name: 'Customer One', consumers: [consumer] }])
    if (path === '/api/platform/ai-workspaces/catalog') return json({
      assetsReady: true, experiencePacks: [{ code: 'generic-workspace', version: '1.3.0', name: 'Generic', enabled: true }],
      connectionProfiles: [{ code: 'runtime-anonymous-direct', mode: 'public-runtime-anonymous', version: '1.0.0', name: 'Anonymous', enabled: true }],
      authenticationProfiles: [{ code: 'supabase', version: '1.0.0', name: 'Supabase', enabled: true }],
    })
    if (path === '/api/customer-self-service/catalogue') {
      const currentOffer = structuredClone(offer)
      if (publishProfiles) {
        currentOffer.workspaceProfiles = [structuredClone(publishedProfile)]
        if (noTheme) currentOffer.workspaceProfiles[0].theme = null
        const published = currentOffer.workspaceProfiles[0]
        if (nestedTheme) published.interaction.configuration.theme = structuredClone(published.theme)
        if (invalidDirectConfig) published.interaction.connectionConfiguration = { ignored: true }
        for (const [setting, kind, payload] of [
          ['INTERACTION_PROFILE_ID', 'workspace', published.interaction],
          ['THEME_PROFILE_ID', 'theme', published.theme],
          ['AUTH_PROFILE_ID', 'auth', published.authentication],
        ]) currentOffer.settings.find(rule => rule.setting === setting).allowedValues.push(pin(kind, payload))
        if (tamperedProfile) currentOffer.workspaceProfiles[0].theme.darkMode = false
      }
      return denyCatalogue ? json({ message: 'Review catalogue unavailable' }, 503) : json([currentOffer])
    }
    if (path === '/api/customer-self-service/deployments') return json([{
      deploymentId: 'deployment-one', offerId: 'support-offer',
      offerFingerprint: staleOffer ? 'different-fingerprint' : offer.fingerprint,
    }])
    const installations = '/api/platform/customers/customer-one/ai-workspace-installations'
    if (path === installations && method === 'GET') return json(empty ? [] : [source, ...(includeUnreviewed ? [{
      ...source, installationId: 'unreviewed-app', displayName: 'Unreviewed app',
      configuration: { ...source.configuration, page: { rootSelector: '#changed' } },
    }] : [])])
    if ((path === installations || path === `${installations}/owned-app`) && ['POST', 'PUT'].includes(method)) {
      const command = { key: route.request().headers()['idempotency-key'], body: route.request().postDataJSON() }
      if (loseFirstSave) {
        if (!firstSave) {
          firstSave = command
          return json({ message: 'Simulated response loss after acceptance' }, 503)
        }
        assert.deepEqual(command, firstSave, 'The retry must preserve both the request and its operation key')
      }
      return json({ ...source, ...route.request().postDataJSON() })
    }
    errors.push(`Unhandled API request: ${method} ${path}`)
    return json({ message: 'Unexpected test request' }, 404)
  })
  await page.goto(`${base}ai-workspaces`, { waitUntil: 'networkidle' })
  try {
    await page.getByRole('heading', { name: 'AI Workspaces', exact: true }).waitFor()
  } catch (error) {
    throw new Error(`${error.message}\nPage: ${await page.locator('body').innerText()}\nErrors: ${errors.join('; ')}\nCalls: ${JSON.stringify(calls)}`)
  }
  return { context, page, calls, errors }
}

try {
  for (const command of ['edit', 'create']) {
    const { context, page, calls, errors } = await open()
    try {
      if (command === 'edit') await page.getByLabel('Edit', { exact: true }).getByRole('button').click()
      else await page.getByRole('button', { name: 'Create AI App', exact: true }).click()
      const selector = page.getByRole('combobox', { name: 'Reviewed app configuration' })
      await selector.waitFor()
      await page.getByRole('textbox', { name: 'App slug', exact: true }).waitFor()
      assert.equal(await page.getByRole('combobox', { name: 'Experience pack', exact: true }).count(), 0)
      assert.equal(await page.getByRole('textbox', { name: 'Supabase project URL', exact: true }).count(), 0)
      await page.getByRole('textbox', { name: 'Workspace name' }).fill('My reviewed support app')
      await page.getByRole('textbox', { name: 'App slug', exact: true }).fill('my-support')
      await page.getByRole('button', { name: 'Save draft' }).click()
      await page.getByText('AI Workspace My reviewed support app saved.').waitFor()
      const request = calls.find(call => call.method === (command === 'edit' ? 'PUT' : 'POST'))
      assert.ok(request)
      assert.equal(request.body.experiencePackVersion, '1.0.0', 'Do not silently upgrade to catalogue 1.3.0')
      assert.equal(request.body.rowVersion, command === 'edit' ? 9 : undefined)
      assert.deepEqual(request.body.configuration, source.configuration)
      assert.deepEqual(request.body.authConfiguration, source.authConfiguration)
      assert.equal(request.body.consumerId, 'support')
      assert.ok(request.headers['idempotency-key'])
      assert.deepEqual(errors, [])
      console.log(`Passed: customer ${command} preserves reviewed configuration`)
    } finally { await context.close() }
  }

  for (const { noTheme, loseFirstSave } of [
    { noTheme: false, loseFirstSave: false }, { noTheme: true, loseFirstSave: false },
    { noTheme: false, loseFirstSave: true },
  ]) {
    const firstApp = await open({ empty: true, publishProfiles: true, noTheme, preRelease: true, loseFirstSave })
    try {
      await firstApp.page.getByRole('button', { name: 'Create AI App', exact: true }).click()
      const selector = firstApp.page.getByRole('combobox', { name: 'Reviewed app configuration' })
      await selector.waitFor()
      await firstApp.page.getByRole('textbox', { name: 'App slug', exact: true }).waitFor()
      assert.match(await selector.innerText(), /Reviewed launch experience/)
      await firstApp.page.getByRole('textbox', { name: 'Workspace name' }).fill('First support app')
      await firstApp.page.getByRole('textbox', { name: 'App slug', exact: true }).fill('first-support')
      if (!noTheme && !loseFirstSave) {
        const screenshots = '/private/tmp/01036-reviewed-profile-ui'
        await mkdir(screenshots, { recursive: true })
        await firstApp.page.waitForFunction(() => [...document.querySelectorAll('button')]
          .some(button => button.textContent === 'Save draft' && !button.disabled))
        await firstApp.page.screenshot({ path: `${screenshots}/desktop.png`, fullPage: true, animations: 'disabled' })
        await firstApp.page.setViewportSize({ width: 390, height: 844 })
        await firstApp.page.screenshot({ path: `${screenshots}/mobile.png`, fullPage: true, animations: 'disabled' })
        const overflow = await firstApp.page.evaluate(() => ({
          width: innerWidth, scrollWidth: document.documentElement.scrollWidth,
          elements: [...document.querySelectorAll('body *')].filter(element => {
            const bounds = element.getBoundingClientRect()
            return bounds.width > innerWidth && getComputedStyle(element).position !== 'fixed'
          }).slice(0, 8).map(element => ({ tag: element.tagName, className: element.className,
            width: element.getBoundingClientRect().width })),
        }))
        assert.equal(overflow.scrollWidth <= overflow.width, true,
          `The first-app editor must not overflow the mobile viewport: ${JSON.stringify(overflow)}`)
        await firstApp.page.setViewportSize({ width: 1280, height: 720 })
      }
      await firstApp.page.getByRole('button', { name: 'Save draft' }).click()
      if (loseFirstSave) {
        await firstApp.page.getByText('Simulated response loss after acceptance').waitFor()
        await firstApp.page.getByRole('button', { name: 'Save draft' }).click()
      }
      await firstApp.page.getByText('AI Workspace First support app saved.').waitFor()
      const request = firstApp.calls.find(call => call.method === 'POST')
      assert.ok(request)
      assert.equal(request.body.connectionMode, 'public-runtime-anonymous')
      assert.equal(request.body.connectionProfileCode, 'runtime-anonymous-direct')
      assert.equal(request.body.interactionPolicy, 'public-with-protected-capabilities')
      assert.equal(request.body.experiencePackVersion, source.experiencePackVersion)
      assert.equal(request.body.rowVersion, undefined)
      assert.equal(request.body.consumerId, 'support')
      assert.equal(request.body.appSlug, 'first-support')
      assert.deepEqual(request.body.configuration, noTheme ? { ...source.configuration, theme: null } : source.configuration)
      assert.deepEqual(request.body.connectionConfiguration, {})
      assert.deepEqual(request.body.authConfiguration, source.authConfiguration)
      assert.equal(pin('auth', {
        profileCode: request.body.authProfileCode, profileVersion: request.body.authProfileVersion,
        interactionPolicy: 'PUBLIC_WITH_PROTECTED_CAPABILITIES', configuration: request.body.authConfiguration,
      }), pin('auth', publishedProfile.authentication))
      assert.ok(request.headers['idempotency-key'])
      assert.deepEqual(firstApp.errors, [])
      if (!noTheme && !loseFirstSave) {
        await firstApp.page.setViewportSize({ width: 390, height: 844 })
        await firstApp.page.getByRole('button', { name: 'Open navigation' }).click()
        const appLink = firstApp.page.getByRole('link', { name: 'My Engage Apps', exact: true })
        await appLink.waitFor({ state: 'visible' })
        assert.equal(await firstApp.page.getByRole('link', { name: 'Providers', exact: true }).count(), 0)
        await firstApp.page.keyboard.press('Escape')
        await appLink.waitFor({ state: 'hidden' })
      }
      console.log(`Passed: first app uses the published offer profile before runtime activation, with zero installations (null theme: ${noTheme})`)
    } finally { await firstApp.context.close() }
  }

  for (const scenario of [{ empty: true }, { empty: true, publishProfiles: true, tamperedProfile: true },
    { empty: true, publishProfiles: true, nestedTheme: true },
    { empty: true, publishProfiles: true, invalidDirectConfig: true }, { denyCatalogue: true }, { staleOffer: true }]) {
    const { context, page, calls, errors } = await open(scenario)
    try {
      await page.getByRole('button', { name: 'Create AI App', exact: true }).click()
      if (scenario.empty) await page.getByText(/No valid reviewed app configuration is available/).waitFor()
      else if (scenario.denyCatalogue) await page.getByText('Review catalogue unavailable').waitFor()
      else await page.getByText(/needs a current reviewed offer/).waitFor()
      assert.equal(await page.getByRole('button', { name: 'Save draft' }).isDisabled(), true)
      assert.equal(calls.some(call => ['POST', 'PUT'].includes(call.method)), false)
      assert.deepEqual(errors, [])
      console.log(`Passed: blocked configuration ${JSON.stringify(scenario)}`)
    } catch (error) {
      throw new Error(`${error.message}\nPage: ${await page.locator('body').innerText()}\nErrors: ${errors.join('; ')}\nCalls: ${JSON.stringify(calls.map(({ path, method }) => ({ path, method })))}`)
    } finally { await context.close() }
  }

  const unreviewed = await open({ includeUnreviewed: true })
  try {
    await unreviewed.page.getByRole('row').filter({ hasText: 'Unreviewed app' })
      .getByLabel('Edit', { exact: true }).getByRole('button').click()
    await unreviewed.page.getByText(/Select a reviewed configuration explicitly/).waitFor()
    assert.equal(await unreviewed.page.getByRole('button', { name: 'Save draft' }).isDisabled(), true)
    await unreviewed.page.getByRole('combobox', { name: 'Reviewed app configuration' }).click()
    await unreviewed.page.getByRole('option', { name: /Reviewed support app/ }).click()
    assert.equal(await unreviewed.page.getByRole('button', { name: 'Save draft' }).isDisabled(), false)
    assert.equal(unreviewed.calls.some(call => ['POST', 'PUT'].includes(call.method)), false)
    assert.deepEqual(unreviewed.errors, [])
    console.log('Passed: no implicit replacement of an unreviewed saved configuration')
  } finally { await unreviewed.context.close() }

  const { context, page, calls, errors } = await open({ role: 'PLATFORM_ADMIN' })
  try {
    await page.getByRole('button', { name: 'Create AI App', exact: true }).click()
    await page.getByRole('combobox', { name: /Experience pack/ }).waitFor()
    await page.getByRole('combobox', { name: /Connection mode/ }).waitFor()
    await page.getByRole('textbox', { name: 'Supabase project URL', exact: true }).waitFor()
    assert.equal(await page.getByRole('combobox', { name: 'Reviewed app configuration' }).count(), 0)
    assert.equal(calls.some(call => call.path.startsWith('/api/customer-self-service/')), false)
    assert.deepEqual(errors, [])
  } catch (error) {
    throw new Error(`${error.message}\nStaff page: ${await page.locator('body').innerText()}\nErrors: ${errors.join('; ')}`)
  } finally { await context.close() }
  console.log('Reviewed workspace UI: 13 scenarios passed (owned edit/create, pre-runtime first app, stable-key retry after response loss, invalid profiles, API error, offer drift, explicit replacement, staff editor). Local mocks only.')
} finally {
  await browser.close()
  await server.close()
}
