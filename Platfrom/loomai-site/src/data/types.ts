export type LinkTarget = {
  label: string
  href: string
  external?: boolean
}

export type ContentKind = 'product' | 'experiment' | 'research'
export type ContentId = `${ContentKind}:${string}`

export type ContentRelation = {
  from: ContentId
  to: ContentId
  type: 'complements' | 'uses' | 'demonstrates' | 'tests' | 'informs' | 'explains' | 'related'
  label?: string
}

export type Product = {
  id: `product:${string}`
  slug: string
  name: string
  shortName: string
  layerLabel: string
  statusLabel: string
  version: string
  licence: string
  summary: string
  description: string
  problem: string
  value: string
  capabilities: Array<{
    name: string
    description: string
  }>
  owns: string[]
  hostOwns: string[]
  flow: string[]
  quickStart: {
    language: string
    code: string
  }
  compatibility: string[]
  links: {
    primary: LinkTarget
    source: LinkTarget
    documentation: LinkTarget
    releaseNotes?: LinkTarget
  }
  featured: boolean
  sortOrder: number
}

export type ExperimentCategory =
  | 'data-retrieval'
  | 'governed-actions'
  | 'privacy-security'
  | 'tenant-access'
  | 'adaptive-experience'

export type Experiment = {
  id: `experiment:${string}`
  slug: string
  title: string
  status: 'live' | 'preview' | 'archived'
  featured: boolean
  summary: string
  scenario: string
  hypothesis: string
  primaryCategory: ExperimentCategory
  categoryLabel: string
  capabilityTags: string[]
  domainTags: string[]
  relatedProductSlugs: string[]
  applicationControlBoundary: string
  observableProof: string[]
  notDemonstrated: string[]
  guidedSteps: string[]
  runtimeStack: string[]
  usesSyntheticData: boolean
  dataNotice: string
  knownLimitations: string[]
  frameworkVersion: string
  lastVerified: string
  screenshot: {
    src: string
    alt: string
  }
  links: {
    launch: LinkTarget
    source: LinkTarget
  }
  sortOrder: number
}

export type ResearchTheme =
  | 'data-consistency'
  | 'context-grounding'
  | 'actions-governance'
  | 'privacy-identity'
  | 'developer-experience'

export type EvidenceLevel =
  | 'design-rationale'
  | 'implemented-prototype'
  | 'reproducible-demo'
  | 'measured-evaluation'

export type ResearchArtifact = {
  id: `research:${string}`
  slug: string
  title: string
  researchQuestion: string
  abstract: string
  theme: ResearchTheme
  themeLabel: string
  maturityLabel: string
  artifactType: string
  evidenceLevel: EvidenceLevel
  evidenceLabel: string
  featured: boolean
  context: string
  proposedApproach: string[]
  implementation: string[]
  keyObservations: string[]
  limitations: string[]
  nextQuestions: string[]
  implementationArtifacts: LinkTarget[]
  linkedExperimentSlugs: string[]
  linkedProductSlugs: string[]
  frameworkVersion: string
  authorsOrContributors: string[]
  updatedAt: string
  readingTime: string
  sortOrder: number
}

export type NavigationItem = {
  label: string
  href: string
  activePatterns: string[]
}
