import type { AIWorkspaceInstallationSummary, CustomerDeploymentSummary, CustomerOfferSummary } from '../../api/platformApi'

// Presentation contributions for reviewed experience-pack codes, never name inference.
const productLabels: Record<string, { name: string; description: string }> = {
  dealership: { name: 'Dealership AI', description: 'Inventory, dealership knowledge and customer enquiries.' },
  'generic-workspace': { name: 'AI workspace', description: 'An AI experience connected to your application.' },
  'shopify-shopping-workspace': { name: 'Shopify AI', description: 'Store products, policies and shopping assistance.' },
}

export type CustomerProduct = {
  id: string
  name: string
  description: string
  offers: CustomerOfferSummary[]
  deployments: CustomerDeploymentSummary[]
  apps: AIWorkspaceInstallationSummary[]
}

function offerProduct(offer: CustomerOfferSummary) {
  const packs = [...new Set(offer.workspaceProfiles.flatMap(profile =>
    typeof profile.interaction.packCode === 'string' && profile.interaction.packCode ? [profile.interaction.packCode] : []))]
  const pack = packs.length === 1 ? packs[0] : null
  return { id: pack ? `experience:${pack}` : `template:${offer.templateId}`,
    ...(pack && productLabels[pack] ? productLabels[pack] : { name: offer.name, description: 'Your reviewed AI experience.' }) }
}

export function customerProducts(offers: CustomerOfferSummary[], deployments: CustomerDeploymentSummary[], apps: AIWorkspaceInstallationSummary[]): CustomerProduct[] {
  const products = new Map<string, CustomerProduct>()
  for (const offer of offers) {
    const identity = offerProduct(offer)
    const product = products.get(identity.id) ?? { ...identity, offers: [], deployments: [], apps: [] }
    product.offers.push(offer)
    product.deployments.push(...deployments.filter(deployment => deployment.offerId === offer.id))
    products.set(identity.id, product)
  }
  for (const product of products.values()) {
    const ids = new Set(product.deployments.map(deployment => deployment.deploymentId))
    product.apps = apps.filter(app => app.deploymentId != null && ids.has(app.deploymentId))
  }
  return [...products.values()]
}

export function activeProductDeployments(product: CustomerProduct) {
  const assigned = new Set(product.apps.filter(app => app.status === 'ACTIVE').map(app => app.deploymentId))
  return product.deployments.filter(deployment => assigned.has(deployment.deploymentId) || deployment.status === 'ACTIVE')
}

export function productSetupPath(offer: CustomerOfferSummary) {
  return `/deployments?${new URLSearchParams({ view: 'setup', step: 'template', offerId: offer.id })}`
}
