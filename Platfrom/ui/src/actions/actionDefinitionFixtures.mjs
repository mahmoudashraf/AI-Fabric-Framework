// Connector 0.8.46 fields; trusted input mirrors dealership_get_vehicle's deployed source contract.
export const actionCatalogueFixture = {
  actions: [{
    name: 'lookup_order', displayName: 'Look up an order', description: 'Read the current order status.', category: 'orders',
    accessMode: 'READ', anonymousAllowed: false, requiresConfirmation: false,
    confirmationMessage: 'Look up this order?', groundingEligible: true,
    groundingVectorSpaces: [], readActionResolutionEligible: true, resultPresentationHint: 'DEFAULT',
    terminal: false, builtInModuleId: 'orders', provenance: { sourceType: 'ACTION_CATALOG', publisher: 'fixture' },
    llmFacts: { objects: [{ sourcePath: 'order', target: 'order', includeFields: ['status'] }] },
    postPolicies: [{ type: 'webhook', targetRef: 'audit', eventType: 'order.read' }],
    adapterType: 'connector-http', execution: { customAdapterSetting: true },
    route: { method: 'POST', path: '/orders/lookup', request: { body: { id: '{{params.orderReference}}' } }, response: { result: { order: '{{body.order}}' } } },
    futureActionField: { keep: ['all', { nested: true }] },
    params: [{
      name: 'orderReference', type: 'STRING', description: 'Trusted order reference.', required: true,
      visibility: 'INTERNAL', askUser: false, sensitive: true,
      resolveFrom: { source: 'ATTACHMENT_METADATA', metadataKeys: ['lookupValue', 'id'] },
      evidenceBound: true, evidenceKeys: ['lookupValue', 'id'], evidenceFallbackPolicy: 'CLARIFY',
      defaultValue: 'order-example', allowedValues: ['order-example', 'order-two'],
      pattern: 'order-*', futureParamField: { keep: 1 },
    }, {
      name: 'limit', type: 'INTEGER', description: 'Result limit.', required: false,
      min: 1, max: 24, defaultValue: 10,
    }, {
      name: 'filters', type: 'ARRAY', required: false, batchTargets: true,
      items: { type: 'OBJECT', properties: { status: { type: 'STRING', allowedValues: ['OPEN', 'CLOSED'] } }, requiredProperties: ['status'] },
      defaultValue: [{ status: 'OPEN' }], futureNestedSchema: { keep: true },
    }],
  }, { name: 'submit_callback', accessMode: 'WRITE_ONLY', requiresConfirmation: true,
    confirmationMessage: 'Send the callback request?', anonymousAllowed: true, params: [],
    futureSiblingField: { retained: true } }],
  webhookTargets: [{ id: 'audit', urlSecretRef: 'AUDIT_URL' }],
  confirmationInterceptors: [],
  mcpServers: [{ id: 'existing-server', url: 'https://example.invalid/mcp' }],
  futureRootField: { retained: ['root'] },
}
