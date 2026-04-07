import type { DeploymentIntegrationSummary } from '../api/platformApi'

export function integrationModeLabel(summary: DeploymentIntegrationSummary | null | undefined): string {
  if (!summary) {
    return 'Runtime posture'
  }
  switch (summary.preferredIntegrationMode) {
    case 'BACKEND_MEDIATED_PRIVATE_RUNTIME':
      return 'Private runtime'
    case 'PUBLIC_RUNTIME_BROWSER_TOKEN':
      return summary.anonymousBootstrapSupported ? 'Public runtime + bootstrap' : 'Public runtime token'
    case 'DIRECT_RUNTIME_COMPATIBILITY':
      return 'Compatibility mode'
    case 'NOT_APPLIED':
      return 'Not applied'
    default:
      return summary.preferredIntegrationMode.replace(/_/g, ' ').toLowerCase()
    }
}

export function integrationModeColor(
  summary: DeploymentIntegrationSummary | null | undefined,
): 'success' | 'warning' | 'default' {
  if (!summary) {
    return 'default'
  }
  switch (summary.preferredIntegrationMode) {
    case 'BACKEND_MEDIATED_PRIVATE_RUNTIME':
    case 'PUBLIC_RUNTIME_BROWSER_TOKEN':
      return 'success'
    case 'DIRECT_RUNTIME_COMPATIBILITY':
      return 'warning'
    default:
      return 'default'
  }
}

export function integrationAlertSeverity(
  summary: DeploymentIntegrationSummary | null | undefined,
): 'success' | 'warning' | 'info' {
  if (!summary) {
    return 'info'
  }
  switch (summary.preferredIntegrationMode) {
    case 'BACKEND_MEDIATED_PRIVATE_RUNTIME':
    case 'PUBLIC_RUNTIME_BROWSER_TOKEN':
      return 'success'
    case 'DIRECT_RUNTIME_COMPATIBILITY':
      return 'warning'
    default:
      return 'info'
  }
}

export function runtimeIntegrationDescription(
  runtimeBaseUrl: string | null | undefined,
  integration: DeploymentIntegrationSummary | null | undefined,
): string {
  if (!runtimeBaseUrl || runtimeBaseUrl.trim().length === 0) {
    return 'Runtime URL is assigned after apply.'
  }
  if (!integration) {
    return `${runtimeBaseUrl} Runtime exposure exists, but integration posture metadata is still loading.`
  }
  switch (integration.preferredIntegrationMode) {
    case 'BACKEND_MEDIATED_PRIVATE_RUNTIME':
      return `${runtimeBaseUrl} Preferred production mode is backend-mediated private runtime. Route customer traffic through your host or storefront backend and reserve direct runtime access for operator inspection and governed tooling.`
    case 'PUBLIC_RUNTIME_BROWSER_TOKEN':
      return `${runtimeBaseUrl} Runtime is prepared for signed browser-token access${integration.anonymousBootstrapSupported ? ' and anonymous bootstrap' : ''}. Use ${integration.publicRuntimeAuthorizationHeader ?? 'Authorization'}: ${(integration.publicRuntimeTokenScheme ?? 'Bearer')} <token>${integration.publicRuntimeTokenIssuerHint ? ` from issuer ${integration.publicRuntimeTokenIssuerHint}` : ''}${integration.publicRuntimeDefaultAudience ? ` with default audience ${integration.publicRuntimeDefaultAudience}` : ''}.`
    case 'DIRECT_RUNTIME_COMPATIBILITY':
      return `${runtimeBaseUrl} Runtime is still in direct compatibility posture. Plan migration to verified private-runtime or signed public-token mode before treating this as the long-term production ingress.`
    default:
      return `${runtimeBaseUrl} ${integration.guidance ?? 'Apply the deployment before integrating.'}`
  }
}

export function connectorIntegrationDescription(
  connectorBaseUrl: string | null | undefined,
  integration: DeploymentIntegrationSummary | null | undefined,
): string {
  if (!connectorBaseUrl || connectorBaseUrl.trim().length === 0) {
    return 'Connector service URL is assigned after apply.'
  }
  if (!integration) {
    return `${connectorBaseUrl} Treat the connector as an internal/operator service surface.`
  }
  return `${connectorBaseUrl} ${integration.connectorInternalOnly
    ? 'Connector remains internal-only. Config, status, summary, diagnostics, and admin reads should flow through runtime-backed operator APIs instead of direct customer integrations.'
    : 'Connector exposure is broader than the preferred posture and should be reviewed.'}`
}
