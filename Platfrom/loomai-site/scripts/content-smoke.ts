import {
  experiments,
  products,
  research,
  validateContentGraph,
} from '../src/data/content.ts'

const errors = validateContentGraph()

const assertUnique = (label: string, values: string[]) => {
  const seen = new Set<string>()
  for (const value of values) {
    if (seen.has(value)) {
      errors.push(`Duplicate ${label}: ${value}`)
    }
    seen.add(value)
  }
}

assertUnique('product slug', products.map((item) => item.slug))
assertUnique('experiment slug', experiments.map((item) => item.slug))
assertUnique('research slug', research.map((item) => item.slug))

for (const product of products) {
  if (product.links.source && !product.links.source.href.startsWith('https://github.com/Loom-AI-Labs/')) {
    errors.push(`${product.id} must link to its public Loom AI Labs source`)
  }
  if (product.licence === 'Apache 2.0' && !product.links.source) {
    errors.push(`${product.id} is open source and must expose its source repository`)
  }
  if (product.capabilities.length !== 5) {
    errors.push(`${product.id} must expose exactly five capability pillars`)
  }
}

for (const experiment of experiments) {
  if (!experiment.usesSyntheticData) {
    errors.push(`${experiment.id} needs an explicit reviewed non-synthetic data source`)
  }
  const launchHref = experiment.links.launch.href
  if (
    !launchHref.startsWith('https://ai-fabric.dev/demos/') &&
    !launchHref.startsWith('/demos/')
  ) {
    errors.push(`${experiment.id} launch link must use a reviewed public demo route`)
  }
  if (!experiment.lastVerified) {
    errors.push(`${experiment.id} is missing a verification date`)
  }
}

for (const item of research) {
  if (item.evidenceLevel === 'measured-evaluation') {
    if (
      item.implementationArtifacts.length === 0 ||
      item.keyObservations.length === 0 ||
      item.limitations.length === 0
    ) {
      errors.push(`${item.id} cannot claim measured evaluation without public evidence`)
    }
  }
}

if (errors.length > 0) {
  console.error(errors.join('\n'))
  process.exit(1)
}

console.log(
  `Content graph passed: ${products.length} products, ${experiments.length} experiments, ${research.length} research items`,
)
