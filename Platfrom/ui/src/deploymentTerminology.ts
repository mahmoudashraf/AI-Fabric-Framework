export type DeploymentTerminologyMode = 'smart-brain' | 'deployment'

// UI copy only. Set this to 'deployment' to restore the original terminology.
export const DEPLOYMENT_TERMINOLOGY: DeploymentTerminologyMode = 'smart-brain'

export function getDeploymentTerminology(mode: DeploymentTerminologyMode) {
  return mode === 'deployment'
    ? { singular: 'Deployment', plural: 'Deployments', lowercase: 'deployment', pluralLowercase: 'deployments' }
    : { singular: 'Smart Brain', plural: 'Smart Brains', lowercase: 'smart brain', pluralLowercase: 'smart brains' }
}

export const deploymentTerm = getDeploymentTerminology(DEPLOYMENT_TERMINOLOGY)

// Only known enum display labels belong here, never names, IDs or server messages.
export function deploymentEnumLabel(value: string, fallback: string = value): string {
  const labels: Record<string, string> = {
    DEPLOYMENT: deploymentTerm.singular,
    DEPLOYMENT_OVERRIDE: `${deploymentTerm.singular} override`,
    DEPLOYMENT_ONLY: `${deploymentTerm.singular} only`,
    DEPLOYMENT_VERIFICATION_RUN: `${deploymentTerm.singular} verification run`,
    DEPLOYMENT_ADMIN: `${deploymentTerm.singular} admin`,
    DEPLOYMENT_EDITOR: `${deploymentTerm.singular} editor`,
    DEPLOYMENT_OPERATOR: `${deploymentTerm.singular} operator`,
    DEPLOYMENT_VIEWER: `${deploymentTerm.singular} viewer`,
    DELETE_DEPLOYMENT: `Delete ${deploymentTerm.lowercase}`,
    // SMART_BRAIN is an existing behavior, distinct from the renamed resource.
    SMART_BRAIN: 'Event-driven',
  }
  return Object.prototype.hasOwnProperty.call(labels, value) ? labels[value] : fallback
}
