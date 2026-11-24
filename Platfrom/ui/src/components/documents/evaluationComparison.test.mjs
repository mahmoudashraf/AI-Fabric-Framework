import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('./evaluationComparison.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } })
const { comparableEvaluation, evaluationVariantLabel, searchEvaluationContext, evaluationComparisonRuns, pairedEvaluationCases, scoredMetric, evaluationExecutionFacts } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
const fixture = { id: 'selected', createdAt: '2026-11-23T10:00:00Z', status: 'COMPLETED', comparisonFingerprint: 'same-source-models-rubric-questions-and-release', usage: [{ model: 'answer-model' }, { model: 'judge-model' }] }

test('variants may compare only under the same saved experiment and actual models', () => {
  const before = structuredClone(fixture)
  assert.equal(comparableEvaluation({ ...fixture, retrievalVariant: 'DENSE_BASELINE' }, { ...fixture, retrievalVariant: 'CONFIGURED' }), true)
  assert.equal(comparableEvaluation(fixture, { ...fixture, usage: [...fixture.usage, { model: 'judge-model' }] }), true)
  assert.deepEqual(fixture, before)
  assert.equal(Object.keys(evaluationVariantLabel).length, 3)
})
test('legacy, unknown or changed provenance cannot display a previous score as comparable', () => {
  for (const change of [{ comparisonFingerprint: undefined }, { comparisonFingerprint: 'changed-corpus' },
    { status: 'PARTIAL' }, { usage: [] }, { usage: [{ model: undefined }] }, { usage: [{ model: 'different-model' }] }]) {
    assert.equal(comparableEvaluation(fixture, { ...fixture, ...change }), false)
  }
})

test('three-way comparison keeps the selected historical variant and excludes changed or partial runs', () => {
  const run = (id, retrievalVariant, createdAt, extra = {}) => ({ ...fixture, id, retrievalVariant, createdAt, cases: [], ...extra })
  const selected = run('selected', 'CONFIGURED', '2026-11-23T10:00:00Z')
  const history = [run('newer-selected-type', 'CONFIGURED', '2026-11-23T11:00:00Z'),
    run('dense-old', 'DENSE_BASELINE', '2026-11-23T09:00:00Z'), run('dense-new', 'DENSE_BASELINE', '2026-11-23T11:00:00Z'),
    run('hybrid', 'CONFIGURED_WITHOUT_RERANKING', '2026-11-23T10:30:00Z'),
    run('partial', 'DENSE_BASELINE', '2026-11-23T12:00:00Z', { status: 'PARTIAL' }),
    run('other-release', 'CONFIGURED_WITHOUT_RERANKING', '2026-11-23T13:00:00Z', { comparisonFingerprint: 'other' })]
  const before = structuredClone(history)
  assert.deepEqual(evaluationComparisonRuns(selected, history).map(run => run.id), ['dense-new', 'hybrid', 'selected'])
  assert.deepEqual(history, before)
  assert.deepEqual(evaluationComparisonRuns({ ...selected, status: 'PARTIAL' }, history).map(run => run.id), ['selected'])
})

test('case pairing preserves changed questions and reference review; missing or invalid metrics are not zero', () => {
  const question = { id: 'q1', question: 'Return window?', referenceAnswer: '30 days', referenceReviewed: true }
  const rows = pairedEvaluationCases([{ cases: [question] }, { cases: [{ ...question, referenceReviewed: false }] },
    { cases: [{ ...question, question: 'Warranty period?' }] }])
  assert.equal(rows.length, 3)
  assert.deepEqual(rows[0].cases.map(Boolean), [true, false, false])
  assert.equal(scoredMetric({ status: 'SCORED', score: 0 }), 0)
  for (const metric of [undefined, { status: 'NOT_SCORED', score: .9 }, { status: 'SCORED', score: null },
    { status: 'SCORED', score: NaN }, { status: 'SCORED', score: 2 }]) assert.equal(scoredMetric(metric), null)
})

test('execution labels use actual typed receipts, never infer hybrid or reranking from the requested variant', () => {
  const item = (effectiveMode, reranking, extra = {}) => ({ retrieval: { diagnostics: { retrievalDiagnostics: { effectiveMode, reranking, ...extra } } } })
  assert.equal(evaluationExecutionFacts({ retrievalVariant: 'CONFIGURED' }).rankingLabel, 'Reranking status unavailable')
  assert.equal(evaluationExecutionFacts(item('DENSE', { used: false, status: 'OFF' })).searchLabel, 'Meaning')
  assert.equal(evaluationExecutionFacts(item('HYBRID', { used: false, status: 'REQUEST_DISABLED' })).rankingLabel, 'Reranking not requested')
  assert.equal(evaluationExecutionFacts(item('HYBRID', { used: true, billedUnits: { input_tokens: 327 } })).inputTokens, 327)
  assert.equal(evaluationExecutionFacts(item('HYBRID', { used: true })).inputTokens, null)
  assert.equal(evaluationExecutionFacts(item('HYBRID', { used: true, billedUnits: { input_tokens: -1 } })).inputTokens, null)
  const fallback = evaluationExecutionFacts(item('DENSE', { used: false, status: 'PRE_RERANK_FALLBACK' }))
  assert.equal(fallback.degraded, true)
  assert.equal(fallback.inputTokens, null)
  assert.match(fallback.rankingLabel, /search order retained/)
})

test('large comparison handoffs retain numeric findings and disclose omitted cases instead of requiring a smaller user prompt', () => {
  const cases = Array.from({ length: 5 }, (_, n) => ({ id: `q${n}`, question: 'q'.repeat(1000), answer: 'a'.repeat(2000),
    referenceAnswer: 'r'.repeat(4000), referenceReviewed: false, status: 'COMPLETED',
    retrieval: { diagnostics: { large: 'x'.repeat(40000) } },
    evaluatedExcerpts: [{ id: 'chunk', sourceId: 'owned', content: 'e'.repeat(8000) }],
    evaluation: { metrics: Object.fromEntries(Array.from({ length: 10 }, (_, n) => [`metric${n}`, { status: 'SCORED', score: .4, explanation: 'm'.repeat(8000) }])) } }))
  const run = { ...fixture, sourceId: 'owned', sourceVersion: 1, providerId: 'ragas', rubricVersion: 'metric-v1', releaseId: 'release', cases }
  const parsed = JSON.parse(searchEvaluationContext(run, { deploymentId: 'brain', targetProfileId: 'stage', releaseId: 'release' }, [
    { ...run, id: 'other', retrievalVariant: 'DENSE_BASELINE' }]))
  assert.equal(parsed.diagnosticsOmitted, true)
  assert.equal(parsed.explanationsTruncated, true)
  assert.equal(parsed.casesOmitted, 4)
  assert.equal(parsed.cases[0].metrics.metric0.score, .4)
  assert.ok(JSON.stringify(parsed).length <= 32000)
})

test('search assistance receives attributed actual findings, not secret metadata or fabricated review', () => {
  const run = { ...fixture, sourceId: 'owned-source', sourceVersion: 3, releaseId: 'old-release', providerId: 'ragas', rubricVersion: 'metric-v1',
    syntheticReferences: true, retrievalVariant: 'DENSE_BASELINE', cases: [{ question: 'Policy?', answer: 'Observed answer', referenceAnswer: 'Unreviewed reference',
      referenceReviewed: false, status: 'COMPLETED', retrieval: { diagnostics: { effectiveMode: 'DENSE' }, evidence: [{ metadata: { unused: 'DO_NOT_SHARE_RAW_METADATA' } }] },
      evaluatedExcerpts: [{ id: 'chunk-1', sourceId: 'owned-source', content: 'Approved excerpt '.repeat(100) }],
      evaluation: { metrics: { faithfulness: { status: 'SCORED', score: .5, explanation: 'Partly supported' } } } }] }
  const before = structuredClone(run)
  const content = searchEvaluationContext(run, { deploymentId: 'owned-brain', targetProfileId: 'stage', releaseId: 'new-release' })
  const observed = JSON.parse(content)
  assert.equal(observed.historicalRelease, true)
  assert.equal(observed.sampleIsPartial, true)
  assert.equal(observed.cases[0].referenceReviewed, false)
  assert.equal(observed.cases[0].metrics.faithfulness.score, .5)
  assert.equal(observed.cases[0].suppliedExcerpts[0].truncated, true)
  assert.equal(observed.cases[0].suppliedExcerpts[0].sourceId, 'owned-source')
  assert.equal(content.includes('DO_NOT_SHARE_RAW_METADATA'), false)
  const compared = JSON.parse(searchEvaluationContext(run, { deploymentId: 'owned-brain', targetProfileId: 'stage', releaseId: 'new-release' }, [
    { ...run, id: 'without-reranking', createdAt: '2026-11-23T09:00:00Z', retrievalVariant: 'CONFIGURED_WITHOUT_RERANKING' },
    { ...run, id: 'changed', createdAt: '2026-11-23T09:30:00Z', comparisonFingerprint: 'changed' },
  ].map(item => ({ ...item }))))
  assert.equal(compared.comparisons.length, 1)
  assert.equal(compared.comparisons[0].cases[0].metrics.faithfulness.score, .5)
  assert.deepEqual(run, before)
  run.cases[0].retrieval.diagnostics = { large: 'x'.repeat(40000) }
  const bounded = JSON.parse(searchEvaluationContext(run, { deploymentId: 'owned-brain', targetProfileId: 'stage', releaseId: 'new-release' }))
  assert.equal(bounded.diagnosticsOmitted, true)
  assert.equal(bounded.cases[0].metrics.faithfulness.score, .5)
})
