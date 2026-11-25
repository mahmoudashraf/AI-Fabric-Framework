import assert from 'node:assert/strict'

const doc = 'The public Office API is https://example.com. GET /api/products has no required inputs and returns {"products":[{"id":"p1","name":"Desk","priceGbp":250,"stock":5}]}. It has no side effects, no authentication and allows visitors without sign-in. Do not call the API during configuration.'
const action = { name: 'office_products', displayName: 'Office products', description: 'Read the public office catalogue.', category: 'catalogue', accessMode: 'READ', anonymousAllowed: true, requiresConfirmation: false, params: [] }
const route = { method: 'GET', url: 'https://example.com/api/products', response: { 'success-http-status': [200], result: { products: '{{body.products}}' } } }
const configs = draft => Object.fromEntries(Object.entries(draft).filter(([key]) => key.endsWith('Config')))
const equalExcept = (before, after, fields) => {
  for (const [field, value] of Object.entries(configs(before))) if (!fields.includes(field)) assert.deepEqual(after[field], value, field + ' changed outside requested scope')
}
const manager = { enabled: false, areas: [], resolutions: [{ id: 'setup', name: 'Setup', objective: 'Inspect setup', instructions: 'Original instructions', areaIds: [], allowParallel: false }], limits: { maxDecisions: 4, maxWorkerInvocations: 4, maxParallelWorkers: 1, deadlineSeconds: 60 } }

export const brainCases = [
  {
    id: 'actions', task: 'ACTIONS', profile: 'api', page: 'capabilities', fields: ['actionsConfig', 'routingConfig'], source: doc,
    auto: 'Prepare the documented public product lookup and save its draft. Preserve every unrelated setting.',
    quick: 'Rename the existing product lookup to Office catalogue. Preserve its exact endpoint, parameters and access rules. Save the draft.',
    verify(before, after, mode) {
      equalExcept(before, after, this.fields)
      assert.equal(after.actionsConfig.actions.length, 1)
      const a = after.actionsConfig.actions[0], r = after.routingConfig.actions[a.name]
      assert.equal(a.accessMode, 'READ'); assert.equal(a.anonymousAllowed, true); assert.notEqual(a.requiresConfirmation, true)
      assert.equal(r.url ?? new URL(r.path, after.routingConfig.connector?.upstream?.['base-url']).href, 'https://example.com/api/products')
      if (mode === 'BOUNDED') assert.equal(a.displayName, 'Office catalogue')
    },
  },
  {
    id: 'connections', task: 'CONNECTIONS', profile: 'api', page: 'capabilities', fields: ['routingConfig'],
    seed(draft) { draft.actionsConfig = { ...draft.actionsConfig, actions: [action] }; draft.routingConfig = { ...draft.routingConfig, actions: { office_products: route } } },
    source: 'The existing Office product API has moved. Its new public endpoint is GET https://example.com/v2/products. Response and authentication are unchanged. No live API call is requested.',
    auto: 'Update the existing office_products connection to the new documented endpoint. Keep the action and response mapping unchanged. Save the draft.',
    quick: 'Change only the office_products endpoint URL back to https://example.com/api/products. Save it without changing any other connection setting.',
    verify(before, after, mode) {
      equalExcept(before, after, this.fields)
      assert.equal(after.routingConfig.actions.office_products.url, mode === 'AUTONOMOUS' ? 'https://example.com/v2/products' : route.url)
      const expected = structuredClone(before.routingConfig); expected.actions.office_products.url = after.routingConfig.actions.office_products.url
      assert.deepEqual(after.routingConfig, expected)
    },
  },
  {
    id: 'knowledge', task: 'KNOWLEDGE', profile: 'data', page: 'knowledge', fields: ['knowledgeSourceConfig'],
    seed(draft) { draft.knowledgeSourceConfig.sources = [{ id: 'office-policy', displayName: 'Office policy', datasetRef: 'office-policy', future: { preserve: true } }, { id: 'keep-source', displayName: 'Unrelated source' }] },
    auto: 'Rename the existing office-policy knowledge source to Office approved policies. Do not create a source, install a plugin or change the dataset or permissions. Save this draft label only.',
    quick: 'Rename that same office-policy knowledge source to Office policy guide. Keep all other source settings and sources unchanged. Save the draft.',
    verify(before, after, mode) {
      equalExcept(before, after, this.fields)
      const expected = structuredClone(before.knowledgeSourceConfig); expected.sources[0].displayName = mode === 'AUTONOMOUS' ? 'Office approved policies' : 'Office policy guide'
      assert.deepEqual(after.knowledgeSourceConfig, expected)
    },
  },
  {
    id: 'responses', task: 'BEHAVIOUR', profile: 'responses', page: 'responses', fields: ['promptConfig'],
    auto: 'Use Warm tone and Brief answer length. Preserve answer breadth, next steps, prompts outside these presets and all other settings. Save the draft.',
    quick: 'Change only the tone preset to Consultative. Keep the Brief answer length and everything else. Save the draft.',
    verify(before, after, mode) {
      equalExcept(before, after, this.fields)
      assert.equal(after.promptConfig.aiBehaviour.tone.presetId, mode === 'AUTONOMOUS' ? 'warm' : 'consultative')
      assert.equal(after.promptConfig.aiBehaviour.answerLength.presetId, 'brief')
      for (const group of ['answerBreadth', 'nextSteps']) if (before.promptConfig.aiBehaviour?.[group]) assert.deepEqual(after.promptConfig.aiBehaviour[group], before.promptConfig.aiBehaviour[group])
    },
  },
  {
    id: 'models', task: 'MODELS', profile: 'models-search', page: 'models', fields: ['providerConfig'],
    auto: 'Set the generation output token limit to 1024. Preserve models, provider identities, credentials and every other setting. Save the draft.',
    quick: 'Set only the generation output token limit to 1536. Keep all other settings unchanged. Save the draft.',
    verify(before, after, mode) {
      equalExcept(before, after, this.fields)
      const field = Object.hasOwn(after.providerConfig, 'generationMaxTokens') ? 'generationMaxTokens' : 'openaiMaxTokens'
      assert.equal(Number(after.providerConfig[field]), mode === 'AUTONOMOUS' ? 1024 : 1536)
      const expected = structuredClone(before.providerConfig); expected[field] = after.providerConfig[field]
      assert.deepEqual(after.providerConfig, expected)
    },
  },
  {
    id: 'search', task: 'SEARCH', profile: 'models-search', page: 'knowledge/search', fields: ['providerConfig'],
    auto: 'Use Meaning search with 12 vector candidates, maximum 12 candidate results and 6 evidence items for AI. Preserve the context allowance. Keep reranking off and all other services and permissions unchanged. Save the draft.',
    quick: 'Change only Evidence items for AI to 4, keeping the 12 candidate limits and everything else. Save the draft.',
    verify(before, after, mode) {
      equalExcept(before, after, this.fields)
      const search = after.providerConfig.knowledgeRetrieval
      assert.equal(search.mode, 'DENSE'); assert.equal(search.vectorCandidates, 12); assert.equal(search.maxCandidates, 12)
      assert.equal(search.maxResults, mode === 'AUTONOMOUS' ? 6 : 4); assert.notEqual(search.reranking?.enabled, true)
      const expected = structuredClone(before.providerConfig); expected.knowledgeRetrieval = search
      assert.deepEqual(after.providerConfig, expected)
    },
  },
  {
    id: 'specialists', task: 'SPECIALISTS', profile: 'teams', page: 'specialists', fields: ['behaviorConfig'],
    seed(draft) { draft.behaviorConfig.specialistAssistance = structuredClone(manager) },
    auto: 'Change the existing Setup plan Manager instructions to: Explain verified facts and distinguish missing evidence. Preserve all other team settings, its disabled state and every other setting. Save the draft.',
    quick: 'Change only the existing Setup plan Manager instructions to: Explain verified facts briefly and identify missing evidence. Save the draft.',
    verify(before, after, mode) {
      equalExcept(before, after, this.fields)
      const expected = structuredClone(before.behaviorConfig)
      expected.specialistAssistance.resolutions[0].instructions = mode === 'AUTONOMOUS' ? 'Explain verified facts and distinguish missing evidence.' : 'Explain verified facts briefly and identify missing evidence.'
      assert.deepEqual(after.behaviorConfig, expected)
    },
  },
  {
    id: 'brain-setup', task: 'BRAIN_SETUP', profile: 'setup', page: 'overview', fields: ['actionsConfig', 'routingConfig', 'promptConfig'], source: doc,
    auto: 'Set up this Brain to help customers find office products through the documented public API. Use Warm tone and Brief answers. Save the supported draft settings, preserving provider credentials, security and every unrelated setting.',
    quick: 'Keep the configured product lookup, and change only the response tone to Consultative. Save the draft.',
    verify(before, after, mode) {
      equalExcept(before, after, this.fields)
      assert.equal(after.actionsConfig.actions.length, 1)
      assert.equal(after.promptConfig.aiBehaviour.tone.presetId, mode === 'AUTONOMOUS' ? 'warm' : 'consultative')
      assert.equal(after.promptConfig.aiBehaviour.answerLength.presetId, 'brief')
    },
  },
  {
    id: 'guidance', task: 'KIT', profile: 'guide', page: 'overview', fields: [], readOnly: true,
    auto: 'Explain how I should connect knowledge, test the Brain and publish it. Do not change settings.',
    quick: 'Explain where I can change response tone. Do not edit or publish anything.',
    verify(before, after) { assert.deepEqual(configs(after), configs(before)) },
  },
]

export { configs }
