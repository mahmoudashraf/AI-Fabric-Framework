import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import ts from 'typescript'

const source = readFileSync(new URL('../src/actionPresentation.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
  fileName: 'actionPresentation.ts',
}).outputText
const contract = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
const {
  presentationReferenceAttachmentId,
  resolveActionPresentation,
} = contract

const Renderer = () => null
const config = {
  renderers: [{
    id: 'test.inventory.v1',
    kind: 'react',
    component: Renderer,
    schemaVersions: ['test.vehicle-list.v1'],
  }],
  mappings: [{
    actionName: 'inventory_search',
    rendererId: 'test.inventory.v1',
    schemaVersion: 'test.vehicle-list.v1',
    projection: {
      fields: [{ sourcePath: 'total' }],
      collections: [{
        sourcePath: '_items',
        target: 'items',
        includeFields: ['stockId', 'name', 'price'],
        maxItems: 2,
        reference: {
          lookupField: 'stockId',
          labelFields: ['name'],
          scope: 'vehicle',
        },
      }],
    },
    rendererContext: {
      detailBasePath: '/vehicles/',
      apiToken: 'must-not-leak',
    },
  }],
}

const resolved = resolveActionPresentation({
  config,
  actionName: 'inventory_search',
  messageId: 'message-1',
  actionData: {
    total: 3,
    protectedToken: 'must-not-leak',
    _items: [
      { stockId: 'STK-1', name: 'First car', price: 10, internalId: 'secret-1' },
      { stockId: 'STK-2', name: 'Second car', price: 20, internalId: 'secret-2' },
      { stockId: 'STK-3', name: 'Third car', price: 30, internalId: 'secret-3' },
    ],
  },
})

assert.ok(resolved)
assert.equal(resolved.rendererId, 'test.inventory.v1')
assert.equal(resolved.presentationData.total, 3)
assert.equal(resolved.presentationData.protectedToken, undefined)
assert.equal(resolved.presentationData.items.length, 2)
assert.equal(resolved.presentationData.items[0].internalId, undefined)
assert.equal(resolved.context.detailBasePath, '/vehicles/')
assert.equal(resolved.context.apiToken, undefined)
assert.equal(resolved.resultReferences.length, 2)
assert.deepEqual(
  {
    label: resolved.resultReferences[0].label,
    lookupValue: resolved.resultReferences[0].lookupValue,
    sourceMessageId: resolved.resultReferences[0].sourceMessageId,
    sourceActionName: resolved.resultReferences[0].sourceActionName,
  },
  {
    label: 'First car',
    lookupValue: 'STK-1',
    sourceMessageId: 'message-1',
    sourceActionName: 'inventory_search',
  },
)
assert.equal(
  presentationReferenceAttachmentId(resolved.resultReferences[0]),
  'action-result:message-1:items-0',
)

assert.equal(resolveActionPresentation({
  config,
  actionName: 'unmapped_action',
  messageId: 'message-2',
  actionData: { _items: [] },
}), null)

assert.equal(resolveActionPresentation({
  config: {
    ...config,
    mappings: [{ ...config.mappings[0], schemaVersion: 'test.vehicle-list.v2' }],
  },
  actionName: 'inventory_search',
  messageId: 'message-3',
  actionData: { _items: [] },
}), null)

console.log('Action presentation contract smoke passed.')
