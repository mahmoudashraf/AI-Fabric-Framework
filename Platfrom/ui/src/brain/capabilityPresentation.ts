import type { DeploymentMarketplaceInstallSummary } from '../api/platformApi'

export type Capability = {
  key: string
  label: string
  purpose: string
  action: Record<string, unknown> | null
  install: DeploymentMarketplaceInstallSummary | null
  kind: 'action' | 'knowledge' | 'integration'
}

export function capabilityAccess(action: Capability['action']) {
  return {
    signIn: action?.anonymousAllowed === true ? 'Anonymous allowed'
      : action?.anonymousAllowed === false ? 'Sign-in required' : 'Not declared',
    confirmation: action?.requiresConfirmation === true ? 'Confirmation required'
      : action?.requiresConfirmation === false ? 'Confirmation not required' : 'Confirmation not declared',
  }
}

export function capabilityMode(action: Capability['action']): string {
  switch (action?.accessMode) {
    case 'READ': return 'Read-only'
    case 'READ_WRITE': return 'Read and write'
    case 'WRITE_ONLY': return 'Write'
    default: return 'Access mode not declared'
  }
}

export function capabilityDraftStatus(row: Capability): { label: string; attention: boolean } {
  if (row.action?.enabled === false) return { label: 'Disabled', attention: true }
  if (row.install && row.install.status !== 'ENABLED') {
    return { label: row.install.status.replace(/[_-]/g, ' ') || 'Status unavailable', attention: true }
  }
  if (row.kind === 'action' && !row.action) return { label: 'Definition unavailable', attention: true }
  if (row.install && row.install.readinessStatus !== 'READY') {
    return { label: row.install.readinessStatus.replace(/[_-]/g, ' ') || 'Readiness unavailable', attention: true }
  }
  return { label: row.action ? 'Saved' : 'Installed', attention: false }
}
