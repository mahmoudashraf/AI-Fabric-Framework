import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('../src/api/platformApi.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText

test('deployment operations status preserves authenticated transport and server-owned policy', async (suite) => {
  const originalWindow = globalThis.window
  const originalFetch = globalThis.fetch
  globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://operations.test' } }
  const api = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
  const calls = []
  let result
  let status = 200
  let networkError = null
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init })
    if (networkError) throw networkError
    return new Response(JSON.stringify(result), { status, headers: { 'Content-Type': 'application/json' } })
  }
  try {
    await suite.test('all effective modes are read-only values, not a client-side reservation or policy toggle', async () => {
      api.setStoredPlatformApiKey('fixture-staff-key')
      for (const mode of ['DISABLED', 'OBSERVE', 'ENFORCE']) {
        for (const providerUsageMode of ['FUNDED', 'TEAM_AUTHORIZED', 'CUSTOMER_KEY']) {
          result = { mode, manualDeploymentsAllowed: true, serviceConfigured: mode !== 'DISABLED',
            providerUsageMode, decisionSource: 'EXTERNAL_SERVICE' }
          const before = calls.length
          assert.deepEqual(await api.fetchDeploymentOperationsStatus(), result)
          assert.equal(calls.length, before + 1)
          const call = calls.at(-1)
          assert.equal(call.url, 'https://operations.test/api/deployment-operations/status')
          assert.equal(call.init.method ?? 'GET', 'GET')
          assert.equal(call.init.credentials, 'include')
          assert.equal(call.init.headers['X-PLATFORM-API-KEY'], 'fixture-staff-key')
          assert.equal(call.init.headers['Content-Type'], 'application/json')
          assert.equal(call.init.body, undefined)
        }
      }
    })

    await suite.test('customer sessions use the same authenticated cookie transport without a staff key', async () => {
      api.clearStoredPlatformApiKey()
      assert.deepEqual(await api.fetchDeploymentOperationsStatus(), result)
      const call = calls.at(-1)
      assert.equal(call.init.credentials, 'include')
      assert.equal(call.init.headers['X-PLATFORM-API-KEY'], undefined)
      assert.equal(call.init.body, undefined)
    })

    await suite.test('auth, missing-route and backend failures remain visible without legacy or allow-all fallback', async () => {
      for (const code of [401, 403, 404, 500, 503]) {
        status = code
        result = { message: `Failure ${code}`, errorCode: 'OPERATIONS_STATUS_UNAVAILABLE', success: false }
        const before = calls.length
        await assert.rejects(api.fetchDeploymentOperationsStatus(), error => error instanceof api.PlatformApiError
          && error.status === code && error.message === result.message
          && error.errorCode === result.errorCode && error.responseRejected === true)
        assert.equal(calls.length, before + 1)
        assert.equal(calls.at(-1).url, 'https://operations.test/api/deployment-operations/status')
      }
    })

    await suite.test('network failures propagate without requesting legacy admission', async () => {
      networkError = new TypeError('Network unavailable')
      const before = calls.length
      await assert.rejects(api.fetchDeploymentOperationsStatus(), error => error === networkError)
      assert.equal(calls.length, before + 1)
      networkError = null
    })

    await suite.test('a healthy service denial returned by core apply is not converted into success', async () => {
      status = 409
      result = { message: 'Deployment operations policy denied this apply.', errorCode: 'OPERATIONS_POLICY_DENIED', success: false }
      const command = { versionId: 'version-one', expectedEntitlementRevision: 2, operationKey: 'apply-once' }
      const before = calls.length
      await assert.rejects(api.applyCustomerDeploymentStagingVersion('offer/one', 'deployment/one', command),
        error => error instanceof api.PlatformApiError && error.status === 409 && error.errorCode === result.errorCode)
      assert.equal(calls.length, before + 1)
      const call = calls.at(-1)
      assert.equal(call.url, 'https://operations.test/api/customer-self-service/catalogue/offer%2Fone/deployments/deployment%2Fone/staging-releases')
      assert.equal(call.init.method, 'POST')
      assert.equal(call.init.credentials, 'include')
      assert.deepEqual(JSON.parse(call.init.body), command)
    })
  } finally {
    api.clearStoredPlatformApiKey()
    globalThis.window = originalWindow
    globalThis.fetch = originalFetch
  }
})
