import type { DeploymentTargetReference, DocumentEvaluationRun, DocumentEvaluationVariant } from '../../api/platformApi'

export const evaluationVariantLabel: Record<DocumentEvaluationVariant, string> = {
  CONFIGURED: 'Current search settings',
  DENSE_BASELINE: 'Vector-only baseline',
  CONFIGURED_WITHOUT_RERANKING: 'Current search, without reranking',
}

export function comparableEvaluation(a: DocumentEvaluationRun, b: DocumentEvaluationRun): boolean {
  if (!a.comparisonFingerprint || a.comparisonFingerprint !== b.comparisonFingerprint
    || a.status !== 'COMPLETED' || b.status !== 'COMPLETED') return false
  const models = (run: DocumentEvaluationRun) => {
    if (!run.usage.length || run.usage.some(item => !item.model)) return null
    return JSON.stringify([...new Set(run.usage.map(item => `${item.kind ?? 'GENERATION'}:${item.provider ?? ''}:${item.model}`))].sort())
  }
  const actual = models(a)
  return actual !== null && actual === models(b)
}

export function evaluationComparisonRuns(selected: DocumentEvaluationRun, history: DocumentEvaluationRun[]): DocumentEvaluationRun[] {
  const latest = new Map<DocumentEvaluationVariant, DocumentEvaluationRun>()
  for (const run of [...history, selected].sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id))) {
    const variant = run.retrievalVariant ?? 'CONFIGURED'
    if (run.id !== selected.id && !comparableEvaluation(selected, run)) continue
    if (!latest.has(variant)) latest.set(variant, run)
  }
  // Keep the user's selected historical run, not a newer run of the same variant.
  latest.set(selected.retrievalVariant ?? 'CONFIGURED', selected)
  return (['DENSE_BASELINE', 'CONFIGURED_WITHOUT_RERANKING', 'CONFIGURED'] as const)
    .flatMap(variant => latest.has(variant) ? [latest.get(variant)!] : [])
}

export function pairedEvaluationCases(runs: DocumentEvaluationRun[]): {
  id: string; question: string; cases: (DocumentEvaluationRun['cases'][number] | undefined)[]
}[] {
  const questions = new Map<string, DocumentEvaluationRun['cases'][number]>()
  for (const run of runs) for (const item of run.cases) {
    const identity = JSON.stringify([item.id, item.question, item.referenceAnswer, item.referenceReviewed])
    if (!questions.has(identity)) questions.set(identity, item)
  }
  return [...questions.entries()].map(([identity, item]) => ({ id: identity, question: item.question,
    cases: runs.map(run => run.cases.find(candidate => JSON.stringify([
      candidate.id, candidate.question, candidate.referenceAnswer, candidate.referenceReviewed]) === identity)) }))
}

export function scoredMetric(metric: { status: string; score: number | null } | undefined): number | null {
  return metric?.status === 'SCORED' && typeof metric.score === 'number' && Number.isFinite(metric.score)
    && metric.score >= 0 && metric.score <= 1 ? metric.score : null
}

export function evaluationExecutionFacts(item: DocumentEvaluationRun['cases'][number]) {
  const record = (value: unknown): Record<string, unknown> => value != null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
  const diagnostics = record(record(item.retrieval?.diagnostics).retrievalDiagnostics)
  const ranking = record(diagnostics.reranking)
  const modes: Record<string, string> = { DENSE: 'Meaning', HYBRID: 'Words and meaning', KEYWORD: 'Words only',
    PARTIAL_HYBRID: 'Partly combined search', NONE: 'No eligible source' }
  const status = typeof ranking.status === 'string' ? ranking.status : ''
  const rankingLabel = ranking.used === true ? ranking.scoredCount === 0 ? 'Reranking ran; no scored candidates' : 'Reranking applied'
    : status === 'REQUEST_DISABLED' ? 'Reranking not requested'
      : status === 'OFF' ? 'Reranking off'
        : status === 'NO_CANDIDATE_TEXT' ? 'Reranking not run: no candidate text'
          : status === 'PRE_RERANK_FALLBACK' ? 'Reranking unavailable; search order retained' : 'Reranking status unavailable'
  const tokens = record(ranking.billedUnits).input_tokens
  return { searchLabel: modes[String(diagnostics.effectiveMode)] ?? 'Search mode unavailable', rankingLabel,
    degraded: diagnostics.degraded === true || status === 'PRE_RERANK_FALLBACK',
    inputTokens: ranking.used === true && typeof tokens === 'number' && Number.isSafeInteger(tokens) && tokens >= 0 ? tokens : null }
}

function observedCases(run: DocumentEvaluationRun) {
  return run.cases.slice(0, 3).map(item => ({
    question: item.question.slice(0, 1000), answer: item.answer?.slice(0, 1200), status: item.status,
    referenceAnswer: (item.referenceAnswer ?? '').slice(0, 1000), referenceReviewed: item.referenceReviewed,
    metrics: Object.fromEntries(Object.entries({ ...item.rankingMetrics, ...item.evaluation?.metrics }).map(([name, metric]) =>
      [name, { status: metric.status, score: metric.score, explanation: (metric.explanation ?? '').slice(0, 1000) }])),
    retrievalDiagnostics: item.retrieval?.diagnostics,
    suppliedExcerpts: item.evaluatedExcerpts?.slice(0, 2).map(excerpt => ({ id: excerpt.id, sourceId: excerpt.sourceId,
      content: excerpt.content.slice(0, 500), truncated: excerpt.content.length > 500 })),
  }))
}

export function searchEvaluationContext(run: DocumentEvaluationRun, target: DeploymentTargetReference, history: DocumentEvaluationRun[] = []): string {
  const cases = observedCases(run)
  const comparisons = evaluationComparisonRuns(run, history).filter(candidate => candidate.id !== run.id).map(candidate => ({
    id: candidate.id, status: candidate.status, retrievalVariant: candidate.retrievalVariant ?? 'CONFIGURED',
    providerId: candidate.providerId, rubricVersion: candidate.rubricVersion, modelBindings: candidate.modelBindings,
    comparisonFingerprint: candidate.comparisonFingerprint, modelCalls: candidate.modelCalls, cases: observedCases(candidate),
  }))
  const observation = { kind: 'SOURCE_SEARCH_EVALUATION', deploymentId: target.deploymentId, targetProfileId: target.targetProfileId,
    sourceId: run.sourceId, sourceVersion: run.sourceVersion, observedReleaseId: run.releaseId, selectedReleaseId: target.releaseId,
    historicalRelease: run.releaseId !== target.releaseId, status: run.status, retrievalVariant: run.retrievalVariant,
    engine: run.providerId, rubric: run.rubricVersion, modelBindings: run.modelBindings, comparisonFingerprint: run.comparisonFingerprint,
    syntheticReferences: run.syntheticReferences, sampleIsPartial: true, cases, comparisons }
  let content = JSON.stringify(observation)
  if (content.length > 31000) content = JSON.stringify({ ...observation, diagnosticsOmitted: true,
    cases: cases.map(({ retrievalDiagnostics: _diagnostics, ...item }) => item),
    comparisons: comparisons.map(candidate => ({ ...candidate,
      cases: candidate.cases.map(({ retrievalDiagnostics: _diagnostics, ...item }) => item) })) })
  if (content.length > 32000) content = JSON.stringify({ ...observation, comparisonDetailsOmitted: true, diagnosticsOmitted: true,
    comparisons: comparisons.map(({ cases: _cases, ...candidate }) => candidate),
    cases: cases.map(({ retrievalDiagnostics: _diagnostics, ...item }) => item) })
  if (content.length > 32000) content = JSON.stringify({ ...observation, comparisonDetailsOmitted: true, diagnosticsOmitted: true,
    casesOmitted: Math.max(0, run.cases.length - 1), explanationsTruncated: true,
    comparisons: comparisons.map(({ cases: _cases, ...candidate }) => candidate),
    cases: cases.slice(0, 1).map(({ retrievalDiagnostics: _diagnostics, ...item }) => ({ ...item,
      metrics: Object.fromEntries(Object.entries(item.metrics).map(([name, metric]) => [name, { ...metric, explanation: metric.explanation.slice(0, 200) }])),
      answer: item.answer?.slice(0, 800), suppliedExcerpts: item.suppliedExcerpts?.slice(0, 1) })) })
  if (content.length > 32000) throw new Error('Selected quality evidence is too large. Choose a smaller evaluation run.')
  return content
}
