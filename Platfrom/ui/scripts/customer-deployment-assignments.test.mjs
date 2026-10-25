import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/customer/customerDeploymentAssignments.ts', import.meta.url), 'utf8')
const journalSource = await readFile(new URL('../src/components/customer/customerAssignmentConfigurationIntents.ts', import.meta.url), 'utf8')
const journalCode = ts.transpileModule(journalSource, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const journal = await import(`data:text/javascript;base64,${Buffer.from(journalCode).toString('base64')}`)
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } })
const loaded = { exports: {} }
new Function('exports', 'require', compiled.outputText)(loaded.exports, name => {
  assert.equal(name, './customerAssignmentConfigurationIntents'); return journal
})
const { selectableCustomerAssignments, canConfigureExistingCustomerAssignment, customerAssignmentConfigurationRequest,
  customerAssignmentDraftExpectations,
  configureExistingCustomerAssignment, assignmentConfigurationClaimKey, prepareKnownAssignmentReconciliation,
  confirmKnownAssignmentReconciliation } = loaded.exports
const consumer = (overrides = {}) => ({ consumerId: 'existing', customerId: 'customer', status: 'ACTIVE',
  boundDeploymentId: null, boundReleaseId: null, boundTargetProfileId: null, bindingRevision: 2, ...overrides })

test('an owned detached consumer can be reused without creating a duplicate or inventing a release binding', () => {
  const existing = consumer(), before = structuredClone(existing)
  assert.deepEqual(selectableCustomerAssignments([existing], 'customer', 'new-deployment'), [existing])
  assert.deepEqual(existing, before)
})

test('already bound assignments stay ahead of detached choices without changing the response order inside each group', () => {
  const detached = consumer(), bound = consumer({ consumerId: 'bound', boundDeploymentId: 'selected', boundReleaseId: 'release', boundTargetProfileId: 'target' })
  assert.deepEqual(selectableCustomerAssignments([detached, bound], 'customer', 'selected'), [bound, detached])
})

test('foreign, disabled, partially detached or other-deployment consumers are never eligible', () => {
  const invalid = [consumer({ customerId: 'foreign' }), consumer({ status: 'DISABLED' }),
    consumer({ boundDeploymentId: 'other' }), consumer({ boundReleaseId: 'retained' }), consumer({ boundTargetProfileId: 'retained' })]
  assert.deepEqual(selectableCustomerAssignments(invalid, 'customer', 'selected'), [])
  assert.deepEqual(selectableCustomerAssignments([consumer()], '', 'selected'), [])
  assert.deepEqual(selectableCustomerAssignments([consumer()], 'customer', ''), [])
})

const scope = () => ({ actorId: 'owner', authenticationMode: 'SESSION', customerId: 'customer',
  offer: { id: 'offer/one', fingerprint: 'offer-pin', entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 4 },
    targets: [{ id: 'staging', intent: 'CUSTOMER_STAGING', capabilities: ['APP_CONFIGURE'] }] },
  deployment: { deploymentId: 'new-deployment', tenantId: 'tenant', offerId: 'offer/one', offerFingerprint: 'offer-pin',
    bootstrapTargetProfileId: 'staging', status: 'DRAFT', activeVersionId: null }, consumer: consumer(),
  draft: { id: 'draft', deploymentId: 'new-deployment', status: 'DRAFT', revisionNumber: 1, updatedAt: '2026-10-20T00:00:00Z' } })
const workspaces = () => [{ id: 'awi-b', installationId: 'public-b', customerId: 'customer', consumerId: 'existing',
  status: 'DRAFT', rowVersion: 3, activatedAt: null, disabledAt: null },
{ id: 'awi-a', installationId: 'public-a', customerId: 'customer', consumerId: 'existing',
  status: 'DRAFT', rowVersion: 0, activatedAt: null, disabledAt: null }]
function fixture() {
  const captured = scope(), apps = workspaces(), calls = [], records = new Map()
  const storage = { getItem: key => records.get(key) ?? null, setItem: (key, value) => records.set(key, value),
    key: index => [...records.keys()][index] ?? null, get length() { return records.size } }
  let queue = Promise.resolve()
  const exclusive = (_, work) => { const next = queue.then(work); queue = next.catch(() => {}); return next }
  const freshIntents = () => journal.createAssignmentConfigurationIntents({ storage: () => storage, exclusive })
  const intents = freshIntents()
  const result = { ...captured.consumer, boundDeploymentId: captured.deployment.deploymentId,
    boundTargetProfileId: 'staging', bindingRevision: 3 }
  const api = { session: async () => ({ authenticated: true, role: 'CUSTOMER_ADMIN', actorId: 'owner', customerId: 'customer', authenticationMode: 'SESSION' }),
    offer: async () => captured.offer, deployments: async () => [captured.deployment],
    installations: async () => apps, consumers: async () => [captured.consumer],
    draft: async () => captured.draft,
    versions: async () => [], releases: async () => [], configure: async (...args) => { calls.push(args); return result } }
  const request = customerAssignmentConfigurationRequest(captured, apps, 'stable-configuration-key')
  return { captured, apps, calls, result, api, request, intents, freshIntents, records,
    intent: () => intents.read('customer', 'existing') }
}

test('configuration sends the complete sorted internal-ID DRAFT fence and exact five-field request', () => {
  const captured = scope(), apps = workspaces(), before = structuredClone(apps)
  apps.push({ ...apps[0], id: 'unrelated', consumerId: 'another', status: 'ACTIVE' })
  assert.deepEqual(customerAssignmentConfigurationRequest(captured, apps, 'stable-key'), {
    consumerId: 'existing', expectedBindingRevision: 2, targetProfileId: 'staging', operationKey: 'stable-key',
    expectedDraftWorkspaces: [{ installationEntityId: 'awi-a', expectedRowVersion: 0 }, { installationEntityId: 'awi-b', expectedRowVersion: 3 }],
  })
  assert.deepEqual(apps.slice(0, 2), before)
  assert.deepEqual(customerAssignmentDraftExpectations(captured, []), [])
})

test('configuration is pre-v1 only and rejects foreign, inactive, partial and unentitled scopes', () => {
  assert.equal(canConfigureExistingCustomerAssignment(scope(), [], []), true)
  assert.equal(canConfigureExistingCustomerAssignment(scope(), [{ id: 'v1' }], []), false)
  assert.equal(canConfigureExistingCustomerAssignment(scope(), [], [{ id: 'failed-release' }]), false)
  for (const edit of [s => { s.consumer.customerId = 'foreign' }, s => { s.consumer.status = 'DISABLED' },
    s => { s.consumer.boundTargetProfileId = 'old-target' }, s => { s.deployment.activeVersionId = 'v1' },
    s => { s.deployment.status = 'ACTIVE' }, s => { s.offer.fingerprint = 'changed' },
    s => { s.offer.targets[0].capabilities = [] }, s => { s.offer.entitlement.status = 'REVOKED' },
    s => { s.authenticationMode = 'API_KEY' }, s => { s.draft.status = 'PUBLISHED' }]) {
    const captured = scope(); edit(captured)
    assert.equal(canConfigureExistingCustomerAssignment(captured, [], []), false)
  }
})

test('no linked workspace may be silently omitted, activated, duplicated or missing its row fence', () => {
  for (const edit of [apps => { apps[0].status = 'ACTIVE' }, apps => { apps[0].activatedAt = '2026-10-20T00:00:00Z' },
    apps => { apps[0].disabledAt = '2026-10-20T00:00:00Z' }, apps => { apps[0].customerId = 'foreign' },
    apps => { delete apps[0].rowVersion }, apps => { apps[0].rowVersion = 0.5 }, apps => { apps.push(apps[0]) }]) {
    const apps = workspaces(); edit(apps)
    assert.throws(() => customerAssignmentConfigurationRequest(scope(), apps, 'stable-key'), /All linked workspaces/)
  }
})

test('fresh configuration uses the reviewed key and fences and accepts only an unactivated matching result', async () => {
  const f = fixture(), original = structuredClone(f.request)
  assert.equal(await configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.intents), f.result)
  assert.deepEqual(f.calls, [['offer/one', 'new-deployment', original]])
  assert.deepEqual(f.request, original)
  for (const edit of [r => { r.boundReleaseId = 'invented-release' }, r => { r.consumerId = 'other' },
    r => { r.boundTargetProfileId = 'other-target' }, r => { r.bindingRevision = 9 }]) {
    const invalid = fixture(); edit(invalid.result)
    await assert.rejects(configureExistingCustomerAssignment(invalid.api, invalid.captured, invalid.request, () => true, invalid.intents), /unconfirmed/)
    assert.equal(invalid.calls.length, 1)
    assert.equal(invalid.intent().status, 'UNKNOWN')
  }
})

test('fresh app-list, consumer, version or release drift denies before any configuration POST', async () => {
  for (const edit of [f => { f.apps[0].rowVersion++ }, f => { f.apps.pop() },
    f => { f.api.consumers = async () => [{ ...f.captured.consumer, boundDeploymentId: 'other' }] },
    f => { f.api.versions = async () => [{ id: 'new-v1' }] }, f => { f.api.releases = async () => [{ id: 'release' }] },
    f => { f.api.draft = async () => ({ ...f.captured.draft, revisionNumber: 2 }) }]) {
    const f = fixture(); edit(f)
    await assert.rejects(configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.intents), /changed/)
    assert.equal(f.calls.length, 0); assert.equal(f.intent().status, 'EMPTY')
  }
})

test('scope change during readback suppresses the POST without claiming a command', async () => {
  const f = fixture(); let finish, current = true
  f.api.installations = () => new Promise(resolve => { finish = resolve })
  const pending = configureExistingCustomerAssignment(f.api, f.captured, f.request, () => current, f.intents)
  current = false; finish(f.apps)
  await assert.rejects(pending, /changed/)
  assert.equal(f.calls.length, 0); assert.equal(f.intent().status, 'EMPTY')
})

test('concurrent dispatch and a lost response hold the exact consumer revision across target changes', async () => {
  const f = fixture(); let reject
  f.api.configure = (...args) => { f.calls.push(args); return new Promise((_, no) => { reject = no }) }
  const first = configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.intents)
  const second = configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.intents)
  const secondCheck = assert.rejects(second, /held/)
  await secondCheck
  assert.equal(f.calls.length, 1)
  reject(new TypeError('Lost response after acceptance'))
  await assert.rejects(first, /unconfirmed/)
  const otherScope = structuredClone(f.captured); otherScope.deployment.deploymentId = 'another-target-deployment'
  await assert.rejects(configureExistingCustomerAssignment(f.api, otherScope, f.request, () => true, f.intents), /held/)
  assert.equal(f.calls.length, 1)
})

test('actual configuration API posts only the normal scoped contract without changing live activation', async () => {
  const apiSource = await readFile(new URL('../src/api/platformApi.ts', import.meta.url), 'utf8')
  const output = ts.transpileModule(apiSource, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  const oldWindow = globalThis.window, oldFetch = globalThis.fetch, calls = []
  globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://configuration.invalid' } }
  const api = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}`)
  const f = fixture()
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init }); return new Response(JSON.stringify(f.result), { headers: { 'Content-Type': 'application/json' } })
  }
  try {
    await api.configureCustomerDeploymentStagingConsumer('offer/one', 'deployment/one', f.request)
    assert.equal(calls[0].url, 'https://configuration.invalid/api/customer-self-service/catalogue/offer%2Fone/deployments/deployment%2Fone/staging-consumer-configurations')
    assert.equal(calls[0].init.method, 'POST'); assert.equal(calls[0].init.credentials, 'include')
    assert.equal(calls[0].init.body, JSON.stringify(f.request))
    const body = JSON.parse(calls[0].init.body)
    assert.deepEqual(Object.keys(body).sort(), ['consumerId', 'expectedBindingRevision', 'expectedDraftWorkspaces', 'operationKey', 'targetProfileId'])
    assert.equal(body.targetProfileId, f.captured.deployment.bootstrapTargetProfileId)
    assert.ok(body.expectedDraftWorkspaces.every(item => Object.keys(item).sort().join(',') === 'expectedRowVersion,installationEntityId'))
    assert.equal(calls[0].init.headers['Idempotency-Key'], undefined)
    const activation = { consumerId: 'existing', releaseId: 'verified', expectedBindingRevision: 3, expectedEntitlementRevision: 4, operationKey: 'activate' }
    await api.activateCustomerDeploymentStagingConsumer('offer/one', 'deployment/one', activation)
    assert.ok(calls[1].url.endsWith('/staging-consumer-activations'))
    assert.equal(calls[1].init.body, JSON.stringify(activation))
  } finally {
    globalThis.fetch = oldFetch
    if (oldWindow === undefined) delete globalThis.window
    else globalThis.window = oldWindow
  }
})

test('structured setting denial requires explicit fresh-owner review before a new setup intent without any app edit', async () => {
  const f = fixture()
  f.api.configure = async (...args) => { f.calls.push(args); throw Object.assign(new Error('Not a semantic signal'),
    { status: 403, errorCode: 'SETTING_FORBIDDEN', responseRejected: true }) }
  const originalApps = structuredClone(f.apps)
  await assert.rejects(configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.intents), /SETTING_FORBIDDEN/)
  await assert.rejects(configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.freshIntents()), /held/)
  const prepared = await prepareKnownAssignmentReconciliation(f.api, f.captured, () => true, f.intents)
  assert.equal(f.intent().status, 'REJECTED', 'read-only preview never releases the intent')
  await confirmKnownAssignmentReconciliation(f.api, f.captured, prepared, () => true, f.intents)
  const reviewed = customerAssignmentConfigurationRequest(f.captured, f.apps, 'new-explicit-reviewed-fence')
  f.api.configure = async (...args) => { f.calls.push(args); return f.result }
  await configureExistingCustomerAssignment(f.api, f.captured, reviewed, () => true, f.freshIntents())
  assert.deepEqual(f.apps, originalApps, 'no impossible unbound-app update or rowVersion increment workaround')
  assert.deepEqual(f.intent().events.map(event => event.kind), ['ATTEMPT', 'REJECTED', 'REVIEWED', 'ATTEMPT', 'COMPLETED'])
  assert.equal(f.intent().events[0].operationKey, f.request.operationKey, 'original command stays retained')
  assert.equal(f.calls.length, 2)
})

test('plain or nonmatching HTTP errors never become known-no-effect from their text', async () => {
  for (const detail of [{ status: 403 }, { status: 403, errorCode: 'SETTING_FORBIDDEN' },
    { status: 403, errorCode: 'OTHER_CODE', responseRejected: true }, { status: 500, errorCode: 'SETTING_FORBIDDEN', responseRejected: true }]) {
    const f = fixture()
    f.api.configure = async (...args) => { f.calls.push(args); throw Object.assign(new Error('SETTING_FORBIDDEN'), detail) }
    await assert.rejects(configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.intents), /unconfirmed/)
    assert.equal(f.intent().status, 'UNKNOWN')
    await assert.rejects(prepareKnownAssignmentReconciliation(f.api, f.captured, () => true, f.intents), /UNKNOWN/)
    assert.equal(f.calls.length, 1)
  }
})

test('UNKNOWN survives app revision, consumer revision, target and actor changes in a remounted journal', async () => {
  const f = fixture()
  f.api.configure = async (...args) => { f.calls.push(args); throw new TypeError('Lost response') }
  await assert.rejects(configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.intents), /unconfirmed/)
  const oldKey = assignmentConfigurationClaimKey(f.captured, f.request.expectedDraftWorkspaces)
  f.apps[0].rowVersion++
  f.captured.consumer.bindingRevision++
  f.captured.actorId = 'another-owner'
  f.captured.deployment.deploymentId = 'another-deployment'
  f.captured.deployment.bootstrapTargetProfileId = 'another-target'
  assert.equal(assignmentConfigurationClaimKey(f.captured, []), oldKey)
  await assert.rejects(configureExistingCustomerAssignment(f.api, f.captured, { ...f.request, operationKey: 'never-send' },
    () => true, f.freshIntents()), /held/)
  assert.equal(f.calls.length, 1)
  assert.equal(f.intent().events.length, 1)
})

test('known rejection reconciliation rechecks owner, complete unchanged app fences, draft and zero versions/releases', async () => {
  for (const change of [f => { f.api.session = async () => ({ authenticated: true, role: 'CUSTOMER_ADMIN', actorId: 'foreign', customerId: 'customer', authenticationMode: 'SESSION' }) },
    f => { f.apps.pop() }, f => { f.apps[0].rowVersion++ }, f => { f.captured.consumer.bindingRevision++ },
    f => { f.api.versions = async () => [{ id: 'v1' }] }, f => { f.api.releases = async () => [{ id: 'release' }] },
    f => { f.api.draft = async () => ({ ...f.captured.draft, revisionNumber: 2 }) },
    f => { f.api.offer = async () => ({ ...f.captured.offer, fingerprint: 'foreign-offer-pin' }) }]) {
    const f = fixture()
    f.api.configure = async (...args) => { f.calls.push(args); throw Object.assign(new Error('Denied'),
      { status: 403, errorCode: 'SETTING_FORBIDDEN', responseRejected: true }) }
    await assert.rejects(configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.intents), /SETTING_FORBIDDEN/)
    change(f)
    await assert.rejects(prepareKnownAssignmentReconciliation(f.api, f.captured, () => true, f.freshIntents()), /changed|row fences/)
    assert.equal(f.intent().status, 'REJECTED')
    assert.equal(f.calls.length, 1)
  }
})

test('reconciliation preview alone cannot release, and stale confirmation cannot append REVIEWED', async () => {
  const f = fixture()
  f.api.configure = async (...args) => { f.calls.push(args); throw Object.assign(new Error('Denied'),
    { status: 403, errorCode: 'SETTING_FORBIDDEN', responseRejected: true }) }
  await assert.rejects(configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.intents), /SETTING_FORBIDDEN/)
  const preview = await prepareKnownAssignmentReconciliation(f.api, f.captured, () => true, f.intents)
  await assert.rejects(configureExistingCustomerAssignment(f.api, f.captured, { ...f.request, operationKey: 'do-not-send' }, () => true, f.freshIntents()), /held/)
  await assert.rejects(confirmKnownAssignmentReconciliation(f.api, f.captured, preview, () => false, f.intents), /changed/)
  assert.equal(f.intent().status, 'REJECTED')
  assert.equal(f.calls.length, 1)
})

test('legacy digest-only holds and corrupted outcome journals fail closed without fallback or deletion', async () => {
  const legacy = fixture()
  const key = 'platform.command-hold.v1:' + JSON.stringify(['CONFIGURE_EXISTING_ASSIGNMENT', 'customer', 'existing', 2, []])
  legacy.records.set(key, JSON.stringify({ schema: 1, scope: key, operationKey: 'old', commandSha256: 'a'.repeat(64), recordedAt: '2026-10-20T00:00:00Z' }))
  await assert.rejects(configureExistingCustomerAssignment(legacy.api, legacy.captured, legacy.request, () => true, legacy.intents), /protection/)
  assert.equal(legacy.records.size, 1); assert.equal(legacy.calls.length, 0)
  const f = fixture()
  f.api.configure = async (...args) => { f.calls.push(args); throw new TypeError('Lost') }
  await assert.rejects(configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.intents), /unconfirmed/)
  const [storedKey, raw] = [...f.records][0], changed = JSON.parse(raw)
  changed.events.push({ kind: 'REVIEWED', at: '2026-10-20T00:00:00Z', operationKey: f.request.operationKey, snapshot: changed.events[0].snapshot })
  const corrupt = JSON.stringify(changed); f.records.set(storedKey, corrupt)
  assert.throws(() => f.freshIntents().read('customer', 'existing'), /protection/)
  assert.equal(f.records.get(storedKey), corrupt)
})

test('existing-app setup retains saved app settings and sends only target and row fences', async () => {
  const f = fixture()
  f.captured.offer.workspaceProfiles = [{ id: 'reviewed-profile', label: 'Reviewed app settings',
    interaction: { serverReviewed: true }, theme: null, authentication: { serverReviewed: true } }]
  f.apps[0].interaction = { layout: 'MAX_MODE' }
  f.apps[0].theme = { brand: 'Customer branding' }
  const request = customerAssignmentConfigurationRequest(f.captured, f.apps, 'keep-original')
  assert.deepEqual(request.expectedDraftWorkspaces, [
    { installationEntityId: 'awi-a', expectedRowVersion: 0 },
    { installationEntityId: 'awi-b', expectedRowVersion: 3 },
  ])
  assert.equal(request.targetProfileId, 'staging')
  assert.equal(Object.hasOwn(request, 'expectedEntitlementRevision'), false)
  const before = structuredClone(f.apps)
  await configureExistingCustomerAssignment(f.api, f.captured, request, () => true, f.intents)
  assert.deepEqual(f.calls[0][2], request)
  assert.deepEqual(f.apps, before, 'the client never rewrites app names, slugs, origins or profile bodies')
})

test('obsolete request fields or a different target never reach the setup API', async () => {
  for (const edit of [request => { request.expectedEntitlementRevision = 4 },
    request => { request.expectedDraftWorkspaces[0].selectedWorkspaceProfileId = 'old-profile' },
    request => { request.targetProfileId = 'other-target' }]) {
    const f = fixture(); edit(f.request)
    await assert.rejects(configureExistingCustomerAssignment(f.api, f.captured, f.request, () => true, f.intents), /changed/)
    assert.equal(f.calls.length, 0)
    assert.equal(f.intent().status, 'EMPTY')
  }
})
