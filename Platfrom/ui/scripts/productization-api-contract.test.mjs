import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('../src/api/platformApi.ts', import.meta.url), 'utf8')
const customerConsoleSource = await readFile(
  new URL('../src/components/customer/CustomerDeploymentConsole.tsx', import.meta.url),
  'utf8',
)
const compiled = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText

test('customer productization API contracts preserve command scope and transport', async (suite) => {
  const originalWindow = globalThis.window
  const originalFetch = globalThis.fetch
  globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://platform.test' } }
  const api = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
  api.setStoredPlatformApiKey('fixture-key')
  const calls = []
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init, body: init.body ? JSON.parse(init.body) : undefined })
    return new Response(JSON.stringify({ accepted: true }), { headers: { 'Content-Type': 'application/json' } })
  }
  const offer = 'offer/one'
  const deployment = 'deployment/one'
  const target = 'target/one'
  const base = 'https://platform.test'
  const offerPath = '/api/customer-self-service/catalogue/offer%2Fone'
  const deploymentPath = `${offerPath}/deployments/deployment%2Fone`
  const targetPath = '/api/deployments/deployment%2Fone/targets/target%2Fone'

  async function expectRequest(invoke, path, method = 'GET', payload, key) {
    const before = calls.length
    await invoke()
    assert.equal(calls.length, before + 1)
    const call = calls.at(-1)
    assert.equal(call.url, `${base}${path}`)
    assert.equal(call.init.method ?? 'GET', method)
    assert.equal(call.init.credentials, 'include')
    assert.equal(call.init.headers['Content-Type'], 'application/json')
    assert.equal(call.init.headers['X-PLATFORM-API-KEY'], 'fixture-key')
    assert.equal(call.init.headers['Idempotency-Key'], key)
    assert.deepEqual(call.body, payload)
    return call
  }

  try {
    await suite.test('catalogue, invitations, and portfolio use owner-scoped routes', async () => {
      await expectRequest(() => api.fetchCustomerSelfServiceCatalogue(), '/api/customer-self-service/catalogue')
      await expectRequest(() => api.fetchCustomerSelfServiceOffer(offer), offerPath)
      await expectRequest(() => api.fetchCustomerSelfServiceInvitations(), '/api/customer-self-service/invitations')
      await expectRequest(() => api.fetchCustomerDeploymentPortfolio(), '/api/customer-self-service/deployments')
      const command = { expectedRevision: 2, operationKey: 'accept-key', reason: 'Accept invitation' }
      await expectRequest(() => api.acceptCustomerSelfServiceInvitation('invitation/one', command),
        '/api/customer-self-service/invitations/invitation%2Fone/accept', 'POST', command)
    })

    await suite.test('configuration and admission carry server contract fields without header substitution', async () => {
      const bootstrap = { targetProfileId: target, name: 'My workspace', expectedEntitlementRevision: 2, operationKey: 'bootstrap-key' }
      await expectRequest(() => api.bootstrapCustomerDeployment(offer, bootstrap), `${offerPath}/deployments`, 'POST', bootstrap)
      const publish = { targetProfileId: target, expectedEntitlementRevision: 2, operationKey: 'publish-key' }
      await expectRequest(() => api.publishCustomerDeploymentVersion(offer, deployment, publish), `${deploymentPath}/versions`, 'POST', publish)
      const apply = { versionId: 'version', expectedEntitlementRevision: 2, operationKey: 'apply-key' }
      const applyCall = await expectRequest(() => api.applyCustomerDeploymentStagingVersion(offer, deployment, apply),
        `${deploymentPath}/staging-releases`, 'POST', apply)
      assert.equal('targetProfileId' in applyCall.body, false)
      assert.equal('sourceArtifactId' in applyCall.body, false)
      const activation = { consumerId: 'customer-app', releaseId: 'release', expectedBindingRevision: 0,
        expectedEntitlementRevision: 2, operationKey: 'activate-staging-key' }
      const activationCall = await expectRequest(
        () => api.activateCustomerDeploymentStagingConsumer(offer, deployment, activation),
        `${deploymentPath}/staging-consumer-activations`, 'POST', activation)
      assert.equal('targetProfileId' in activationCall.body, false)
      assert.equal('deploymentId' in activationCall.body, false)
      const createAssignment = { consumerId: 'new-app', displayName: 'New app', description: 'Customer app',
        releaseId: 'release', expectedEntitlementRevision: 2, operationKey: 'create-assignment-key' }
      const createAssignmentCall = await expectRequest(
        () => api.createCustomerDeploymentStagingConsumer(offer, deployment, createAssignment),
        `${deploymentPath}/staging-consumer-assignments`, 'POST', createAssignment)
      assert.equal('targetProfileId' in createAssignmentCall.body, false)
      assert.equal('deploymentId' in createAssignmentCall.body, false)
      await expectRequest(() => api.probeCustomerOfferTargetPlacement(offer, target), `${offerPath}/targets/target%2Fone/placement-evidence`, 'POST')
      const reserve = { targetProfileId: target, versionId: 'version', releaseId: 'release', operation: 'APPLY', operationKey: 'reserve-key', allocationId: 'allocation' }
      await expectRequest(() => api.reserveCustomerDeploymentAdmission(offer, deployment, reserve), `${deploymentPath}/admission-reservations`, 'POST', reserve)
      await expectRequest(() => api.fetchCustomerDeploymentAdmission(offer, deployment, 'reservation/one'), `${deploymentPath}/admission-reservations/reservation%2Fone`)
      await expectRequest(() => api.fetchCustomerDeploymentAdmissions(offer, deployment, target),
        `${deploymentPath}/admission-reservations?targetProfileId=target%2Fone`)
      const extend = { targetProfileId: target, allocationId: 'allocation', expectedRevision: 3, operationKey: 'extend-key', reason: 'Continue testing', keepAfterPromotion: true }
      await expectRequest(() => api.extendCustomerDeploymentStaging(offer, deployment, extend), `${deploymentPath}/staging-lifecycle/extensions`, 'POST', extend)
      await expectRequest(() => api.fetchCustomerAdmissionNotices(25), '/api/customer-self-service/admission/notices?limit=25')
    })

    await suite.test('creation sends the explicit AI-state hosting choice without database credentials', async () => {
      for (const aiStateStorageMode of ['MANAGED_POSTGRES', 'CUSTOMER_POSTGRES']) {
        const command = { targetProfileId: target, name: 'AI workspace', expectedEntitlementRevision: 2,
          operationKey: `bootstrap-${aiStateStorageMode}`, aiStateStorageMode }
        const call = await expectRequest(() => api.bootstrapCustomerDeployment(offer, command),
          `${offerPath}/deployments`, 'POST', command)
        assert.equal('jdbcUrl' in call.body, false)
        assert.equal('password' in call.body, false)
      }
    })

    await suite.test('external plugin binding uses exact owner, version, target and slot routes', async () => {
      const scope = { versionId: 'version/one', targetProfileId: target, bindingSlotId: 'stock', selectedProfileId: 'reviewed-stock' }
      const bind = { ...scope, approvalId: 'approval', expectedEntitlementRevision: 2, operationKey: 'bind-external',
        credentials: { apiKey: 'synthetic-private-value' } }
      await expectRequest(() => api.bindCustomerExternalIntegration(offer, deployment, bind),
        `${deploymentPath}/external-integrations/bind`, 'POST', bind)
      await expectRequest(() => api.fetchCustomerExternalIntegration(offer, deployment, scope),
        `${deploymentPath}/external-integrations?${new URLSearchParams(scope)}`)
      const revoke = { ...scope, bindingId: 'binding', expectedBindingRevision: 1,
        expectedEntitlementRevision: 2, operationKey: 'revoke-external' }
      await expectRequest(() => api.revokeCustomerExternalIntegration(offer, deployment, revoke),
        `${deploymentPath}/external-integrations/revoke`, 'POST', revoke)
    })

    await suite.test('customer AI-state credentials are sent only in the typed binding POST body', async () => {
      const bind = { targetProfileId: target, bindingSlotId: 'ai-state-database', expectedEntitlementRevision: 2,
        operationKey: 'bind-sql', connection: { versionId: 'version/one', host: 'db.example.test', port: 5432,
          databaseName: 'ai_state', runtimeSchema: 'runtime_state', connectorSchema: 'connector_state',
          username: 'ai_role', password: 'synthetic-private-value', migrationAuthorized: true } }
      const call = await expectRequest(() => api.bindCustomerAiStateStorage(offer, deployment, bind),
        `${deploymentPath}/ai-state-storage-binding`, 'POST', bind)
      assert.equal(call.url.includes(bind.connection.password), false)
      assert.equal('approvalReference' in call.body.connection, false)
      const scope = { targetProfileId: target, bindingSlotId: bind.bindingSlotId, versionId: bind.connection.versionId }
      await expectRequest(() => api.fetchCustomerAiStateStorage(offer, deployment, scope),
        `${deploymentPath}/ai-state-storage-binding?${new URLSearchParams(scope)}`)
    })

    await suite.test('go-live readiness is scoped to the exact owner candidate', async () => {
      await expectRequest(
        () => api.fetchCustomerDeploymentGoLiveReadiness(offer, deployment, 'release/one', 'consumer/one'),
        `${deploymentPath}/go-live-readiness?releaseId=release%2Fone&consumerId=consumer%2Fone`,
      )
    })

    await suite.test('customer retirement derives protected inventory behind an owner-scoped contract', async () => {
      const retirementPath = `${deploymentPath}/retirements`
      const plan = { targetProfileId: target, operationKey: 'retire-key', releaseIds: ['release'],
        drainDeadline: '2026-10-16T12:00:00Z', retention: { decision: 'RECLAIM', expiresAt: null, entitlementReference: null, backupDecisionReference: 'customer-source-rebuild' }, reason: 'Finished staging' }
      const planCall = await expectRequest(() => api.planCustomerTargetRetirement(offer, deployment, plan),
        `${retirementPath}/plan`, 'POST', plan)
      assert.equal('consumers' in planCall.body, false)
      await expectRequest(() => api.fetchCustomerTargetRetirements(offer, deployment, target),
        `${retirementPath}?targetProfileId=target%2Fone`)
      await expectRequest(() => api.fetchCustomerTargetRetirement(offer, deployment, 'operation/one'),
        `${retirementPath}/operation%2Fone`)
      const command = { requestFingerprint: 'planned-fingerprint' }
      for (const [name, route] of [['executeCustomerTargetRetirement', 'execute'], ['retryCustomerTargetRetirement', 'retry'], ['cancelCustomerTargetRetirement', 'cancel']]) {
        await expectRequest(() => api[name](offer, deployment, 'operation/one', command),
          `${retirementPath}/operation%2Fone/${route}`, 'POST', command)
      }
    })

    await suite.test('customer S3 binding uses the approved offer and explicit profile', async () => {
      const bind = { expectedEntitlementRevision: 2, operationKey: 'bind-key', targetProfileId: target,
        bindingSlotId: 'documents', selectedProfileId: 'reviewed-read-only', endpoint: 'https://s3.test',
        region: 'eu-north-1', bucket: 'fixture-bucket', prefix: 'fixture/', accessKey: 'fixture-access',
        secretKey: 'fixture-secret', pathStyleAccess: false, objectVersioningAvailable: false }
      await expectRequest(() => api.bindCustomerDocumentStorage(offer, deployment, bind), `${deploymentPath}/document-storage-binding`, 'POST', bind)
      const revoke = { expectedEntitlementRevision: 2, operationKey: 'revoke-key', targetProfileId: target, bindingSlotId: 'documents' }
      await expectRequest(() => api.revokeCustomerDocumentStorage(offer, deployment, revoke), `${deploymentPath}/document-storage-binding/revoke`, 'POST', revoke)
    })

    await suite.test('document and integration mutations preserve caller keys across retries', async () => {
      const scope = { deploymentId: deployment, targetProfileId: target, releaseId: 'release/one' }
      const basePath = `${targetPath}/releases/release%2Fone`
      const documentPath = `${basePath}/document-knowledge`
      const registration = { datasetId: 'policies', objectReference: 'delivery.txt', visibility: 'tenant' }
      for (let attempt = 0; attempt < 2; attempt += 1) {
        await expectRequest(() => api.registerDocumentSource(scope, registration, 'register-stable'), `${documentPath}/sources`, 'POST', registration, 'register-stable')
        for (const [name, route] of [['refreshDocumentSource', 'refresh'], ['indexDocumentSource', 'index'], ['reconcileDocumentSource', 'reconcile']]) {
          await expectRequest(() => api[name](scope, 'source/one', `${route}-stable`),
            `${documentPath}/sources/source%2Fone/${route}`, 'POST', {}, `${route}-stable`)
        }
        await expectRequest(() => api.removeDocumentIndex(scope, 'source/one', 'delete-stable'), `${documentPath}/sources/source%2Fone`, 'DELETE', undefined, 'delete-stable')
        await expectRequest(() => api.cleanupDocumentRetention(scope, 'cleanup-stable'), `${documentPath}/retention/cleanup`, 'POST', {}, 'cleanup-stable')
        await expectRequest(() => api.reconcileDeploymentIntegrationSource(scope, 'source/one', 'integration-stable'),
          `${basePath}/integrations/sources/source%2Fone/reconcile`, 'POST', {}, 'integration-stable')
        await expectRequest(() => api.replayDeploymentIntegrationWebhook(scope, 'source/one', 'event/one', 'replay-stable'),
          `${basePath}/integrations/webhooks/source%2Fone/events/event%2Fone/replay`, 'POST', {}, 'replay-stable')
        await expectRequest(() => api.retryConversationHandoverDispatch(scope, 'dispatch/one', 'retry-stable'),
          `${basePath}/integrations/conversation-handover/dispatches/dispatch%2Fone/retry`, 'POST', {}, 'retry-stable')
        await expectRequest(() => api.expireConversationHandover(scope, 'handover/one', 'expire-stable'),
          `${basePath}/integrations/conversation-handover/handovers/handover%2Fone/expire`, 'POST', {}, 'expire-stable')
      }
    })

    await suite.test('retirement plan and commands keep target and request fingerprint', async () => {
      const plan = { operationKey: 'retire-key', releaseIds: ['release'], consumers: [{ consumerEntityId: 'consumer', expectedBindingRevision: 4 }],
        drainDeadline: '2026-10-16T12:00:00Z', retention: { decision: 'RECLAIM', expiresAt: null, entitlementReference: null, backupDecisionReference: 'backup' }, reason: 'Finished staging' }
      await expectRequest(() => api.planTargetRetirement(deployment, target, plan), `${targetPath}/retirements/plan`, 'POST', plan)
      await expectRequest(() => api.fetchTargetRetirements(deployment, target), `${targetPath}/retirements`)
      await expectRequest(() => api.fetchTargetRetirement(deployment, target, 'operation/one'), `${targetPath}/retirements/operation%2Fone`)
      const command = { requestFingerprint: 'planned-fingerprint' }
      for (const [name, route] of [['executeTargetRetirement', 'execute'], ['retryTargetRetirement', 'retry'], ['cancelTargetRetirement', 'cancel']]) {
        await expectRequest(() => api[name](deployment, target, 'operation/one', command), `${targetPath}/retirements/operation%2Fone/${route}`, 'POST', command)
      }
    })

    await suite.test('managed inbox operators preserve revisions and use uncached credential transport', async () => {
      await expectRequest(() => api.fetchManagedDependencies(deployment, target), `${targetPath}/managed-dependencies`)
      const enroll = { slot: 'inbox', displayName: 'Owner', operationKey: 'enroll-key' }
      await expectRequest(() => api.enrollManagedDependencyOperator(deployment, target, enroll), `${targetPath}/managed-dependencies/operators`, 'POST', enroll)
      const command = { expectedCredentialRevision: 2, operationKey: 'operator-key' }
      for (const [name, route] of [['rotateManagedDependencyOperator', 'rotate'], ['revokeManagedDependencyOperator', 'revoke'], ['deliverManagedDependencyOperatorCredential', 'credential-delivery']]) {
        const call = await expectRequest(() => api[name](deployment, target, 'operator/one', command), `${targetPath}/managed-dependencies/operators/operator%2Fone/${route}`, 'POST', command)
        assert.equal(call.init.cache, 'no-store')
      }
      const before = calls.length
      globalThis.fetch = async () => { calls.push({ failed: true }); throw new TypeError('Connection lost') }
      await assert.rejects(api.deliverManagedDependencyOperatorCredential(deployment, target, 'operator/one', command), /Connection lost/)
      assert.equal(calls.length, before + 1, 'one-time credential delivery must not retry automatically')
      globalThis.fetch = originalFetch
    })

    await suite.test('existing workspace, storage, and apply commands preserve auth alongside operation headers', async () => {
      globalThis.fetch = async (url, init) => {
        calls.push({ url, init, body: init.body ? JSON.parse(init.body) : undefined })
        return new Response(JSON.stringify({ accepted: true }), { headers: { 'Content-Type': 'application/json' } })
      }
      const payload = { displayName: 'Fixture' }
      await expectRequest(() => api.createAIWorkspaceInstallation('customer', payload, 'create-key'), '/api/platform/customers/customer/ai-workspace-installations', 'POST', payload, 'create-key')
      await expectRequest(() => api.updateAIWorkspaceInstallation('customer', 'installation/one', payload, 'update-key'), '/api/platform/customers/customer/ai-workspace-installations/installation%2Fone', 'PUT', payload, 'update-key')
      for (const [name, suffix, method] of [['activateAIWorkspaceInstallation', '/activate', 'POST'], ['disableAIWorkspaceInstallation', '/disable', 'POST'], ['deleteAIWorkspaceInstallation', '', 'DELETE']]) {
        await expectRequest(() => api[name]('customer', 'installation/one', 'command-key'), `/api/platform/customers/customer/ai-workspace-installations/installation%2Fone${suffix}`, method, undefined, 'command-key')
      }
      await expectRequest(() => api.verifyAIWorkspaceDomain('customer', 'installation/one', 'workspace.test', 'verify-key'), '/api/platform/customers/customer/ai-workspace-installations/installation%2Fone/domains/workspace.test/verify', 'POST', undefined, 'verify-key')
      const binding = { targetProfileId: target, connectorType: 'S3_COMPATIBLE_OBJECT_STORAGE' }
      await expectRequest(() => api.upsertDocumentStorageBinding(deployment, binding, { operationKey: 'bind-key', bindingSlotId: 'documents/one', selectedProfileId: 'reviewed/one' }), '/api/deployments/deployment%2Fone/document-storage-bindings?bindingSlotId=documents%2Fone&selectedProfileId=reviewed%2Fone', 'POST', binding, 'bind-key')
      await expectRequest(() => api.deleteDocumentStorageBinding(deployment, 'binding/one', { operationKey: 'delete-key', bindingSlotId: 'documents/one' }), '/api/deployments/deployment%2Fone/document-storage-bindings/binding%2Fone?bindingSlotId=documents%2Fone', 'DELETE', undefined, 'delete-key')
      await expectRequest(() => api.applyDeploymentVersion('deployment', 'version', target, 'artifact', 'apply-key'), '/api/deployments/deployment/apply/version?targetProfileId=target%2Fone&sourceArtifactId=artifact', 'POST', undefined, 'apply-key')
      await expectRequest(() => api.applyDeploymentVersionWithApproval('deployment', 'version', 'approval', target, 'artifact', 'apply-approved-key'), '/api/deployments/deployment/apply/version?approvalId=approval&targetProfileId=target%2Fone&sourceArtifactId=artifact', 'POST', undefined, 'apply-approved-key')
    })
  } finally {
    globalThis.fetch = originalFetch
    if (originalWindow === undefined) delete globalThis.window
    else globalThis.window = originalWindow
  }
})

test('customer console keeps mutation keys stable and activation evidence-bound', () => {
  const ast = ts.createSourceFile('CustomerDeploymentConsole.tsx', customerConsoleSource, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  const consoleBody = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === 'CustomerDeploymentConsole')?.getText(ast)
  assert.ok(consoleBody)
  assert.match(customerConsoleSource, /useRef\(new Map<string, \{ identity: string; key: string \}>\(\)\)/)
  assert.match(customerConsoleSource, /existing\?\.identity === identity/)
  assert.equal((customerConsoleSource.match(/crypto\.randomUUID\(\)/g) ?? []).length, 4)
  assert.equal((consoleBody.match(/crypto\.randomUUID\(\)/g) ?? []).length, 1)
  assert.doesNotMatch(consoleBody, /operationKey:\s*crypto\.randomUUID\(\)/)
  for (const name of ['PreparedFailureReview', 'FailedApplyRetryReview', 'PostProvisionResumeReview']) {
    const body = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === name)?.getText(ast)
    assert.ok(body)
    assert.equal((body.match(/crypto\.randomUUID\(\)/g) ?? []).length, 1)
    assert.match(body, /request\.current \?\?= \{ releaseId: scope\.releaseId, operationKey: crypto\.randomUUID\(\)/)
  }
  assert.match(customerConsoleSource, /!goLiveReadiness\?\.activationReady/)
  assert.match(customerConsoleSource, /fetchCustomerDeploymentGoLiveReadiness/)
})
