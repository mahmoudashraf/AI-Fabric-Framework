import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createRequire(import.meta.url)
async function load(file, dependencies = {}) {
  const exports = {}
  new Function('require', 'exports', ts.transpileModule(await readFile(new URL(file, import.meta.url), 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText)(name => name in dependencies ? dependencies[name] : require(name), exports)
  return exports
}
const profiles = await load('../src/aiBehaviourProfiles.ts')
const { buildChangePreview: preview, previewHeadline: headline, sectionPreviewBaseline } = await load('../src/brain/brainChangePreview.ts', { '../aiBehaviourProfiles': profiles })
const { BrainChangeComparison } = await load('../src/brain/BrainChangeComparison.tsx', {
  'react-router-dom': { Link: ({ to, children, ...props }) => React.createElement('a', { ...props, href: to }, children) },
})
const catalogue = { groups: [{ key: 'tone', label: 'Tone', options: [{ id: 'professional', label: 'Professional' }, { id: 'warm', label: 'Warm & consultative' }] }] }
const prompt = (presetId, instructions) => ({ aiBehaviour: { schemaVersion: 'loomai-ai-behaviour-v1', tone: { presetId, instructions } } })

test('tone comparison comes from exact metadata and catalogue, not inferred prompt meaning', () => {
  const result = preview('promptConfig', prompt('professional', 'Be formal.'), prompt('warm', 'Ask a useful follow-up.'), 'applied', catalogue)
  assert.equal(result.title, 'How your AI replies')
  assert.equal(headline(result), 'Professional → Warm & consultative')
  assert.equal(result.values[1].proposed, 'Ask a useful follow-up.')
  assert.equal(result.editor, 'responses')
})
test('unknown profile labels are not invented; changed IDs still remain a visible change', () => {
  const result = preview('promptConfig', prompt('a', 'same'), prompt('b', 'same'), 'applied')
  assert.equal(result.values[0].current, 'Saved profile (label unavailable)')
  assert.equal(result.values.length, 1)
  assert.ok(result.fieldChanges.some(row => row.path.endsWith('presetId')))
})
test('known editable instructions can be compared but arbitrary prompts and secrets are not projected', () => {
  const result = preview('promptConfig', { systemPrompt: 'Previous instructions', secret: 'OLD_SECRET' },
    { systemPrompt: 'New instructions', secret: 'NEW_SECRET', prompt: 'UNKNOWN_PROMPT' }, 'applied')
  assert.equal(result.values[0].label, 'General instructions')
  assert.equal(result.values[0].proposed, 'New instructions')
  assert.ok(!JSON.stringify(result).includes('NEW_SECRET'))
  assert.ok(!JSON.stringify(result).includes('UNKNOWN_PROMPT'))
})
test('new setup never calls configured entries removals or invents a current release', () => {
  const result = preview('actionsConfig', undefined, { actions: [{ name: 'find', displayName: 'Find stock' }] }, 'new')
  assert.equal(result.values[0].current, 'Not applied')
  assert.equal(result.values[0].kind, 'Added')
  assert.equal(headline(result), '+ Find stock')
})
test('missing applied evidence cannot invent additions or field changes', () => {
  const result = preview('promptConfig', undefined, prompt('warm', 'Ask.'), 'unavailable', catalogue)
  assert.equal(result.values[0].current, 'Unavailable')
  assert.equal(headline(result), 'Saved draft: Tone')
  assert.deepEqual(result.fieldChanges, [])
})
test('action additions, removals, renames and confirmation settings use stable identities', () => {
  const result = preview('actionsConfig', { actions: [{ name: 'old', displayName: 'Old action' }, { name: 'keep', displayName: 'Callback', requiresConfirmation: false }] },
    { actions: [{ name: 'new', displayName: 'Request a test drive' }, { name: 'keep', displayName: 'Request a callback', requiresConfirmation: true, token: 'SECRET' }] }, 'applied')
  assert.equal(result.values.find(row => row.id === 'item:old').kind, 'Removed')
  assert.equal(result.values.find(row => row.id === 'item:new').kind, 'Added')
  assert.equal(result.values.find(row => row.id === 'name:keep').current, 'Callback')
  assert.equal(result.values.find(row => row.id === 'keep:requiresConfirmation').proposed, 'Required')
  assert.ok(!JSON.stringify(result).includes('SECRET'))
})
test('duplicate or absent list IDs are not guessed or paired by position', () => {
  for (const rows of [[{}, {}], [{ name: 'same' }, { name: 'same' }]]) {
    const result = preview('actionsConfig', { actions: [] }, { actions: rows }, 'applied')
    assert.deepEqual(result.values.map(row => row.id), ['list'])
    assert.equal(result.values[0].proposed, '2 configured')
  }
})
test('source changes describe configuration, not claims that uploaded documents are indexed', () => {
  const result = preview('knowledgeSourceConfig', { sources: [] }, { sources: [{ sourceId: 'policies', displayName: 'Delivery policies', accessKey: 'HIDDEN_KEY' }] }, 'applied')
  assert.equal(result.values[0].label, 'Delivery policies')
  assert.match(result.impact, /does not prove/)
  assert.ok(!JSON.stringify(result).includes('HIDDEN_KEY'))
})
test('unknown settings expose changed paths without raw connection or credential values', () => {
  const result = preview('routingConfig', { routes: { auth: 'OLD_TOKEN' } }, { routes: { auth: 'NEW_TOKEN' } }, 'applied')
  assert.equal(result.values.length, 0)
  assert.deepEqual(result.fieldChanges, [{ path: '$.routes.auth', change: 'Changed' }])
  assert.ok(!JSON.stringify(result).includes('NEW_TOKEN'))
})
test('same scalar counts do not disguise list modifications and canonical key ordering is stable', () => {
  const result = preview('actionsConfig', { actions: [{ displayName: 'Old' }] }, { actions: [{ displayName: 'New' }] }, 'applied')
  assert.equal(result.values.length, 1)
  assert.equal(result.values[0].kind, 'Changed')
  assert.ok(result.fieldChanges.length)
  assert.deepEqual(preview('shellConfig', { a: 1, b: 2 }, { b: 2, a: 1 }, 'applied').fieldChanges, [])
})
test('long instructions stay readable in details without a truncated before-after headline', () => {
  const result = preview('promptConfig', { systemPrompt: 'Old' }, { systemPrompt: 'Word '.repeat(1000) }, 'applied')
  assert.equal(headline(result), 'General instructions updated')
  assert.equal(result.values[0].proposed.length, 5000)
})
test('detail view contains comparisons and edit link, never publication or write controls', () => {
  const result = preview('promptConfig', prompt('professional', 'Old'), prompt('warm', 'New'), 'applied', catalogue)
  const html = renderToStaticMarkup(React.createElement(BrainChangeComparison, { preview: result, currentLabel: 'Production v3', proposedLabel: 'Saved draft r5',
    href: section => `/smart-brains/brain-1/${section}?targetProfileId=production`, canEdit: true }))
  assert.match(html, /Current/); assert.match(html, /Proposed/)
  assert.match(html, /not a simulated AI answer/)
  assert.match(html, /responses\?targetProfileId=production/)
  assert.ok(!html.includes('Publish version'))
})

test('generated prompt defaults are not presented as removal and verified authored inputs avoid false changes', () => {
  const current = { answerGenerationPrompt: 'Expanded runtime guidance', aiBehaviour: { schemaVersion: 'loomai-ai-behaviour-v1',
    sourcePrompts: { answerGenerationPrompt: 'Write grounded answers' }, expandedPrompts: { answerGenerationPrompt: 'Expanded runtime guidance' } } }
  const same = preview('promptConfig', current, { answerGenerationPrompt: 'Write grounded answers' }, 'applied')
  assert.equal(same.values.length, 0)
  const reset = preview('promptConfig', current, {}, 'applied')
  assert.equal(reset.values[0].proposed, 'Generated/default guidance on publish')
  assert.equal(reset.values[0].kind, 'Changed')
  const unverified = preview('promptConfig', { ...current, answerGenerationPrompt: 'Changed after expansion' }, {}, 'applied')
  assert.equal(unverified.values[0].current, 'Changed after expansion')
})
test('malformed action flags and access modes do not expose arbitrary string values', () => {
  const result = preview('actionsConfig', { actions: [{name:'read',accessMode:'READ',requiresConfirmation:true}] },
    { actions: [{name:'read',accessMode:'SECRET_MODE',requiresConfirmation:'SECRET_REF',anonymousAllowed:'TOKEN_VALUE'}] }, 'applied')
  assert.ok(!JSON.stringify(result).includes('SECRET_MODE'))
  assert.ok(!JSON.stringify(result).includes('SECRET_REF'))
  assert.ok(!JSON.stringify(result).includes('TOKEN_VALUE'))
  const mode = preview('actionsConfig', {actions:[{name:'read',accessMode:'READ'}]}, {actions:[{name:'read',accessMode:'WRITE_ONLY'}]}, 'applied')
  assert.equal(mode.values.find(row=>row.id==='read:accessMode').proposed,'Write')
})
test('canonical source IDs and entity dictionaries produce named additions and removals', () => {
  const source = preview('knowledgeSourceConfig', {sources:[{id:'old',attributionLabel:'Old policies'}]}, {sources:[{id:'new',attributionLabel:'New policies'}]}, 'applied')
  assert.equal(source.values.find(row=>row.id==='item:old').kind,'Removed')
  assert.equal(source.values.find(row=>row.id==='item:new').label,'New policies')
  const entities = preview('entityConfig', {'ai-entities':{old:{indexing:{enabled:true}}}}, {'ai-entities':{document:{indexing:{enabled:true}}}}, 'applied')
  assert.equal(entities.values.find(row=>row.id==='item:document').label,'document')
  assert.equal(entities.values.find(row=>row.id==='item:old').kind,'Removed')
})
test('unsupported behaviour has no misleading prompt edit link', () => {
  assert.equal(preview('behaviorConfig',{}, {enabled:true},'applied').editor,null)
})
test('a missing manifest section stays unavailable even if a server summary knows that area changed', () => {
  assert.equal(sectionPreviewBaseline({versionId:'v3'},'promptConfig','applied'),'unavailable')
  assert.equal(sectionPreviewBaseline({promptConfig:{}},'promptConfig','applied'),'applied')
  assert.equal(sectionPreviewBaseline(undefined,'promptConfig','new'),'new')
  assert.equal(sectionPreviewBaseline({promptConfig:{}},'promptConfig','unavailable'),'unavailable')
})
