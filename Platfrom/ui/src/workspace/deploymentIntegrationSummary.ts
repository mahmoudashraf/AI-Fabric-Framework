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
    case 'BACKEND_MEDIATED_PRIVATE_RUNTIME': {
      const policySuffix = integration.trustedBackendPlatformDefaultIssuerPolicy
        ? ' Trusted caller verification is enabled, but the accepted issuer policy still uses platform-managed first-party defaults. Set deployment security privateRuntimeAcceptedIssuers before onboarding an external storefront or customer backend.'
        : !integration.externalTrustedBackendIntegrationReady
          ? ' Trusted caller verification is enabled, but issuer/audience allowlists are not fully configured yet. Complete deployment security privateRuntimeAcceptedIssuers/privateRuntimeAcceptedAudiences before treating this as the long-term external production ingress.'
          : ' Trusted caller issuer/audience policy is explicitly configured for external host-backed integration.'
      return `${integration.backendMediatedRuntimeBaseUrl ?? runtimeBaseUrl} Preferred production mode is backend-mediated private runtime. Route customer traffic through your host or storefront backend${integration.trustedBackendAuthorizationHeader ? ` using ${integration.trustedBackendAuthorizationHeader} for trusted caller auth` : ''}, and reserve browser-direct runtime access for operator inspection and governed tooling.${policySuffix}`
    }
    case 'PUBLIC_RUNTIME_BROWSER_TOKEN':
      return `${integration.browserDirectChatBaseUrl ?? runtimeBaseUrl} Runtime is prepared for signed browser-token access${integration.anonymousBootstrapSupported ? ' and anonymous bootstrap' : ''}. Use ${integration.publicRuntimeAuthorizationHeader ?? 'Authorization'}: ${(integration.publicRuntimeTokenScheme ?? 'Bearer')} <token>${integration.publicRuntimeTokenIssuerHint ? ` from issuer ${integration.publicRuntimeTokenIssuerHint}` : ''}${integration.publicRuntimeDefaultAudience ? ` with default audience ${integration.publicRuntimeDefaultAudience}` : ''}${integration.publicRuntimeAcceptedIssuerPolicyConfigured || integration.publicRuntimeAcceptedAudiencePolicyConfigured ? '' : '. Accepted issuer/audience policy is not fully configured yet and should be tightened before production browser rollout'}.`
    case 'DIRECT_RUNTIME_COMPATIBILITY':
      return `${integration.browserDirectChatBaseUrl ?? runtimeBaseUrl} Runtime is still in direct compatibility posture. Plan migration to verified private-runtime or signed public-token mode before treating this as the long-term production ingress.`
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
    return `Internal connector URL: ${connectorBaseUrl}. Treat the connector as an internal/operator service surface.`
  }
  return `Internal connector URL: ${connectorBaseUrl}. ${integration.connectorInternalOnly
    ? `Connector remains internal-only. Config, status, summary, diagnostics, and admin reads should flow through runtime-backed operator APIs instead of direct customer integrations${integration.preferredConnectorReadProxyBaseUrl ? `, including the published runtime-backed connector read proxy at ${integration.preferredConnectorReadProxyBaseUrl}` : ''}.`
    : 'Connector exposure is broader than the preferred posture and should be reviewed.'}`
}
