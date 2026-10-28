function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown> : {}
}

export function hasApprovedAnonymousDocumentSource(manifest: unknown, datasetId: string): boolean {
  if (!datasetId) return false
  const contributions = record(record(manifest).contributions)
  const datasets = contributions.datasets
  const sources = contributions.knowledgeSources
  if (!Array.isArray(datasets) || !Array.isArray(sources)) return false
  const documentDataset = datasets.some(value => {
    const dataset = record(value)
    return dataset.datasetId === datasetId && dataset.ingestionMode === 'EXTERNAL_DOCUMENT_STORAGE'
  })
  return documentDataset && sources.some(value => {
    const source = record(value)
    const filters = record(source.filters)
    return source.datasetRef === datasetId
      && source.sourceType === 'deployment-private-vector'
      && Array.isArray(source.authModes) && source.authModes.includes('PUBLIC_RUNTIME_ANONYMOUS')
      && filters.datasetId === datasetId && filters.publicationStatus === 'PUBLIC_APPROVED'
  })
}
