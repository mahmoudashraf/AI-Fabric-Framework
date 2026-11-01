import assert from 'node:assert/strict'
import test from 'node:test'

globalThis.window = {
  location: { pathname: '/' },
  dispatchEvent() {},
}
globalThis.document = {
  querySelector() { return null },
}
if (typeof globalThis.CustomEvent === 'undefined') {
  globalThis.CustomEvent = class CustomEvent {
    constructor(type, options = {}) {
      this.type = type
      this.detail = options.detail
    }
  }
}

const { mountInstallation } = await import('../dist/dealership-experience.esm.js')

test('standalone delivery opens the full workspace and preserves host capabilities', async () => {
  const fixture = createFixture('standalone', false)

  const controller = await mountInstallation(fixture.context)

  assert.equal(fixture.openCalls(), 1)
  assert.equal(fixture.config().host.companionDock, false)
  assert.equal(fixture.config().host.showCloseButton, false)
  assert.deepEqual(fixture.config().host.authentication, { enabled: true })
  assert.equal(fixture.config().host.actionPresentation.mappings[0].rendererContext.detailNavigation, 'query')
  controller.destroy()
})

test('embedded delivery retains docked behavior without forcing the workspace open', async () => {
  const fixture = createFixture('embedded', true)

  const controller = await mountInstallation(fixture.context)

  assert.equal(fixture.openCalls(), 0)
  assert.equal(fixture.config().host.companionDock, true)
  assert.equal(fixture.config().host.showCloseButton, true)
  controller.destroy()
})

test('standalone detail queries do not inherit a host website base path', async () => {
  const fixture = createFixture('standalone', false)
  fixture.context.configuration.presentation = {
    detailBasePath: '/vehicles/', detailSlugs: { 'STOCK-1': 'example-car' },
  }
  const controller = await mountInstallation(fixture.context)
  assert.equal(fixture.config().host.actionPresentation.mappings[0].rendererContext.detailNavigation, 'query')
  controller.destroy()
})

test('an explicit website detail configuration is preserved for either delivery mode', async () => {
  for (const deliveryMode of ['embedded', 'standalone']) {
    const fixture = createFixture(deliveryMode, true)
    fixture.context.configuration.presentation = {
      detailNavigation: 'website', detailBasePath: '/vehicles/',
      detailSlugs: { 'STOCK-1': 'example-car' },
    }
    const controller = await mountInstallation(fixture.context)
    assert.equal(fixture.config().host.actionPresentation.mappings[0].rendererContext.detailNavigation, 'website')
    controller.destroy()
  }
})

function createFixture(deliveryMode, showCloseButton) {
  let initialized
  let openCalls = 0
  const maxMode = {
    init(config) { initialized = config },
    open() { openCalls += 1 },
    close() {},
    attachItem() {},
    sendMessage() {},
    destroy() {},
  }
  return {
    context: {
      installationId: 'awi_pub_delivery_contract',
      manifestRevision: 'manifest-1',
      assignmentRevision: 'assignment-1',
      connectionMode: 'public-runtime-anonymous',
      deliveryMode,
      configuration: {
        page: { kind: 'inventory', rootSelector: 'main', contextLabel: 'Current page' },
        dealer: { id: 'dealer-1', assistantLabel: 'Dealer AI' },
      },
      widgetConfig: {
        apiConfig: {},
        host: {
          showCloseButton,
          authentication: { enabled: true },
        },
      },
      maxMode,
      async refreshAssignment() {},
    },
    config: () => initialized,
    openCalls: () => openCalls,
  }
}
