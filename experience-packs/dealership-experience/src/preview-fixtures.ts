/** Bounded fictional samples, owned by this pack rather than the generic widget. */
export function dealershipPreviewFixtures(context: Record<string, boolean> = {}) {
  const vehicles = [
    { presentationKey: 'preview-vehicle-1', stockId: 'SAMPLE-001', make: 'Example', model: 'City EV', year: 2025, registrationYear: 2025, priceGbp: 24500, mileage: 4200, fuelType: 'Electric', bodyType: 'Hatchback', availability: 'Available', lifecycleState: 'AVAILABLE', summary: 'Fictional compact electric car.', features: ['Parking sensors', 'Heated seats'], sourceLabel: 'Synthetic inventory' },
    { presentationKey: 'preview-vehicle-2', stockId: 'SAMPLE-002', make: 'Example', model: 'Family Hybrid', year: 2024, registrationYear: 2024, priceGbp: 27800, mileage: 8100, fuelType: 'Hybrid', bodyType: 'Estate', availability: 'Available', lifecycleState: 'AVAILABLE', summary: 'Fictional family hybrid.', features: ['Adaptive cruise control', 'Rear camera'], sourceLabel: 'Synthetic inventory' },
  ]
  const refs = vehicles.map(vehicle => ({ key: vehicle.presentationKey, label: `${vehicle.year} ${vehicle.make} ${vehicle.model}`, sourceMessageId: 'preview-results', sourceActionName: 'preview', safeData: vehicle }))
  const base = { actionName: 'preview', resultReferences: refs, context: { comparisonEnabled: true, testDriveEnabled: false, callbackEnabled: false } }
  const receipt = { receiptCode: 'PREVIEW-001', status: 'SUBMITTED', success: true, vehicle: 'Example City EV', message: 'Synthetic receipt. No dealership request was submitted.' }
  return {
    userMessage: 'Which vehicles could suit my needs?',
    answer: 'These fictional inventory samples show how this workspace presents current facts and sources. No live stock lookup was performed.',
    confirmation: 'Confirm this sample dealership request?',
    documents: [{ id: 'preview-stock-source', title: 'Sample stock evidence', type: 'document', content: 'Synthetic dealership evidence. This is not live inventory.', messageId: 'preview-answer' }],
    attachment: { type: 'document', contextLabel: 'Example City EV', data: { id: 'preview-vehicle-context', title: 'Example City EV', name: 'Example City EV', content: 'Fictional vehicle context, not a customer record.' } },
    fallback: { status: 'Sample inventory response', summary: 'Unregistered results use the generic widget renderer.' },
    receipt: context.testDriveEnabled !== false || context.callbackEnabled !== false ? receipt : undefined,
    presentations: [
      { ...base, rendererId: 'loomai.vehicle-inventory.v1', elementName: 'loomai-dealership-inventory', schemaVersion: 'loomai.vehicle-list.v1', presentationData: { items: vehicles, count: vehicles.length, dataNotice: 'Synthetic preview inventory' } },
      { ...base, rendererId: 'loomai.vehicle-detail.v1', elementName: 'loomai-dealership-vehicle-detail', schemaVersion: 'loomai.vehicle-detail.v1', presentationData: { vehicle: vehicles[0], dataNotice: 'Synthetic preview details' } },
      { ...base, rendererId: 'loomai.vehicle-comparison.v1', elementName: 'loomai-dealership-vehicle-comparison', schemaVersion: 'loomai.vehicle-comparison.v1', presentationData: { vehicles, count: vehicles.length } },
      ...(['test-drive', 'callback'] as const).filter(kind => context[kind === 'callback' ? 'callbackEnabled' : 'testDriveEnabled'] !== false).map(kind => ({
        ...base, context: { ...base.context, requestKind: kind }, fixtureId: `${kind}-receipt`, sample: 'receipt' as const,
        rendererId: 'loomai.dealership-request-receipt.v1', elementName: 'loomai-dealership-request-receipt', schemaVersion: 'loomai.dealership-request-receipt.v1', presentationData: receipt,
      })),
    ],
  }
}
