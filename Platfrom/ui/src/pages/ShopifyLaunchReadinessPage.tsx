import AppRegistrationRoundedIcon from '@mui/icons-material/AppRegistrationRounded'
import AssignmentTurnedInRoundedIcon from '@mui/icons-material/AssignmentTurnedInRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import LockRoundedIcon from '@mui/icons-material/LockRounded'
import RocketLaunchRoundedIcon from '@mui/icons-material/RocketLaunchRounded'
import SecurityRoundedIcon from '@mui/icons-material/SecurityRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  LinearProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { type ReactNode, useMemo } from 'react'
import {
  fetchPlatformVerificationReleaseGate,
  fetchPlatformVerificationSuiteRuns,
  fetchShopifyPackageProfiles,
  fetchShopifyReadinessAuditState,
  type PlatformVerificationReleaseGateSummary,
  type ShopifyCompanionPackageProfileSummary,
  type ShopifyReadinessAuditStateSummary,
} from '../api/platformApi'

type StatusColor = 'success' | 'warning' | 'error' | 'default'

type LaunchReadinessItem = {
  label: string
  status: string
  color: StatusColor
  owner: string
  evidence: string
  nextAction: string
}

function statusColor(status: string | null | undefined): StatusColor {
  switch ((status ?? '').toUpperCase()) {
    case 'READY':
    case 'PASSED':
    case 'DESIGN_PARTNER_READY':
    case 'TECHNICAL_READY':
    case 'PRIVATE_LISTING_READY':
      return 'success'
    case 'FAILED':
    case 'BLOCKED':
    case 'NOT_READY':
    case 'PUBLIC_BLOCKED':
      return 'error'
    case 'RUNNING':
    case 'QUEUED':
    case 'STALE':
    case 'PROOF_REQUIRED':
    case 'NEEDS_REVIEW':
      return 'warning'
    default:
      return 'default'
  }
}

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : 'not recorded'
}

function latestProductionProofStatus(releaseGate: PlatformVerificationReleaseGateSummary | undefined): string {
  const combined = JSON.stringify(releaseGate?.latestRun ?? {}).toLowerCase()
  if (combined.includes('production') && combined.includes('rollback') && combined.includes('staging')) {
    return releaseGate?.ready ? 'PASSED' : 'NEEDS_REVIEW'
  }
  return 'PROOF_REQUIRED'
}

function buildPackagePosture(profiles: ShopifyCompanionPackageProfileSummary[]): LaunchReadinessItem {
  const activeTiers = new Set(
    profiles
      .filter((profile) => profile.status === 'ACTIVE')
      .map((profile) => profile.tierKey),
  )
  const complete = ['FREE', 'STARTER', 'ELITE'].every((tier) => activeTiers.has(tier))
  return {
    label: 'Pricing and package catalog',
    status: complete ? 'READY' : 'NEEDS_REVIEW',
    color: complete ? 'success' : 'warning',
    owner: 'Platform product',
    evidence: complete
      ? 'Active Free, Starter, and Elite Shopify package profiles are present.'
      : 'One or more active Shopify package profiles are missing from the catalog.',
    nextAction: complete
      ? 'Keep package copy aligned with the active profile catalog.'
      : 'Review Shopify package profiles before promising the tier ladder.',
  }
}

function buildListingItems(
  profiles: ShopifyCompanionPackageProfileSummary[],
  audit: ShopifyReadinessAuditStateSummary | undefined,
  releaseGate: PlatformVerificationReleaseGateSummary | undefined,
): LaunchReadinessItem[] {
  const packagePosture = buildPackagePosture(profiles)
  const readinessDecision = audit?.decision ?? 'NOT_READY'
  const designPartnerReady = ['DESIGN_PARTNER_READY', 'TECHNICAL_READY'].includes(readinessDecision)
  const releaseReady = Boolean(releaseGate?.ready)
  const productionProofStatus = latestProductionProofStatus(releaseGate)
  return [
    packagePosture,
    {
      label: 'Design-partner package',
      status: designPartnerReady ? 'READY' : 'NEEDS_REVIEW',
      color: designPartnerReady ? 'success' : 'warning',
      owner: 'Product and partner ops',
      evidence: audit?.nextHandoff ?? 'Shopify readiness audit has not produced a design-partner handoff yet.',
      nextAction: designPartnerReady
        ? 'Use private/design-partner launch posture while public App Store remains gated.'
        : 'Run the Shopify readiness audit and resolve product-truth blockers.',
    },
    {
      label: 'Support packaging',
      status: 'READY',
      color: 'success',
      owner: 'Support ops',
      evidence: 'Merchant app exports support bundle, support runbook, App Review guide, screencast script, and launch dossier from live store posture.',
      nextAction: 'Refresh exports after every material package, surface, or support posture change.',
    },
    {
      label: 'App Store/private listing readiness',
      status: designPartnerReady && releaseReady ? 'PRIVATE_LISTING_READY' : 'NEEDS_REVIEW',
      color: designPartnerReady && releaseReady ? 'success' : 'warning',
      owner: 'Operator',
      evidence: releaseGate?.summaryMessage ?? 'Release gate has not been evaluated.',
      nextAction: designPartnerReady && releaseReady
        ? 'Private listing/design-partner posture is supportable. Do not mark public App Store ready yet.'
        : 'Run hosted/full release gate on staging and refresh private listing evidence.',
    },
    {
      label: 'Public App Store launch',
      status: productionProofStatus === 'PASSED' ? 'READY' : 'PUBLIC_BLOCKED',
      color: productionProofStatus === 'PASSED' ? 'success' : 'error',
      owner: 'Release owner',
      evidence: productionProofStatus === 'PASSED'
        ? 'Controlled production proof is recorded in release evidence.'
        : 'Controlled production proof, rollback/deactivation proof, protected-data posture, and support readiness must be recorded first.',
      nextAction: 'Schedule the intentional production-promotion proof. Keep the mutation behind the operator gate.',
    },
  ]
}

const EXTERNAL_GATE_ITEMS: LaunchReadinessItem[] = [
  {
    label: 'Protected customer data posture',
    status: 'PROOF_REQUIRED',
    color: 'warning',
    owner: 'Shopify app owner',
    evidence: 'Protected customer data access must match the App Store/private listing data-use copy.',
    nextAction: 'Keep claims limited to live-proven Customer Account and Checkout behavior.',
  },
  {
    label: 'Customer Account MCP',
    status: 'PROOF_REQUIRED',
    color: 'warning',
    owner: 'Shopify app owner',
    evidence: 'Requires a real staging customer login, bound-token tools/call, and per-store customer account domain configuration.',
    nextAction: 'Record customer login proof before marketing account/order capabilities.',
  },
  {
    label: 'Checkout MCP',
    status: 'PROOF_REQUIRED',
    color: 'warning',
    owner: 'Shopify app owner',
    evidence: 'Checkout credential posture is managed through Platform secrets; live proof stays gated by Shopify storefront and checkout readiness.',
    nextAction: 'Run only safe non-terminal checkout proof. Terminal checkout remains disabled until explicitly approved.',
  },
]

const CONTROLLED_PROOF_ITEMS: LaunchReadinessItem[] = [
  {
    label: 'Actual Go production mutation',
    status: 'PROOF_REQUIRED',
    color: 'warning',
    owner: 'Release owner',
    evidence: 'Normal staging verification does not run PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true.',
    nextAction: 'Execute through dtp-coolify-production during an intentional proof window.',
  },
  {
    label: 'Production provisioning verification',
    status: 'PROOF_REQUIRED',
    color: 'warning',
    owner: 'Platform ops',
    evidence: 'Production provisioning evidence must be attached after the intentional promotion.',
    nextAction: 'Verify production health and merchant-safe launch evidence after provisioning.',
  },
  {
    label: 'Rollback/deactivation proof',
    status: 'PROOF_REQUIRED',
    color: 'warning',
    owner: 'Platform ops',
    evidence: 'Merchant rollback request recording is proven on staging; live production rollback proof still needs an intentional run.',
    nextAction: 'Record rollback/deactivation evidence without exposing provider internals.',
  },
  {
    label: 'Failed promotion leaves staging untouched',
    status: 'PROOF_REQUIRED',
    color: 'warning',
    owner: 'Release owner',
    evidence: 'Failure-isolation proof must show staging remains untouched in a real promotion attempt.',
    nextAction: 'Run a controlled negative proof or production-equivalent profile proof before public launch.',
  },
]

export function ShopifyLaunchReadinessPage() {
  const queryClient = useQueryClient()
  const packageProfilesQuery = useQuery({
    queryKey: ['shopify-package-profiles', 'launch-readiness'],
    queryFn: () => fetchShopifyPackageProfiles(false),
  })
  const auditQuery = useQuery({
    queryKey: ['shopify-readiness-audit'],
    queryFn: fetchShopifyReadinessAuditState,
  })
  const releaseGateQuery = useQuery({
    queryKey: ['verification-suites', 'release-gate'],
    queryFn: fetchPlatformVerificationReleaseGate,
  })
  const suiteRunsQuery = useQuery({
    queryKey: ['verification-suites', 'runs'],
    queryFn: fetchPlatformVerificationSuiteRuns,
  })

  const items = useMemo(
    () => buildListingItems(packageProfilesQuery.data ?? [], auditQuery.data, releaseGateQuery.data),
    [auditQuery.data, packageProfilesQuery.data, releaseGateQuery.data],
  )
  const privateReady = items.some((item) => item.label === 'App Store/private listing readiness' && item.status === 'PRIVATE_LISTING_READY')
  const productionProofStatus = latestProductionProofStatus(releaseGateQuery.data)
  const publicReady = productionProofStatus === 'PASSED' && EXTERNAL_GATE_ITEMS.every((item) => item.status === 'READY')
  const latestRuns = (suiteRunsQuery.data ?? []).slice(0, 5)
  const loading = packageProfilesQuery.isLoading || auditQuery.isLoading || releaseGateQuery.isLoading

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="010.1 UI Launch Readiness" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800 }}>
          Shopify Launch Readiness
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 1120 }}>
          Operator-only view for App Store/private listing readiness, protected-data gates, release-gate evidence,
          controlled production proof, and 010_SELF_SERVICE_PRODUCTION_READY blockers.
        </Typography>
      </Box>

      {loading ? <LinearProgress /> : null}
      {packageProfilesQuery.isError ? <Alert severity="error">Failed to load Shopify package profiles.</Alert> : null}
      {auditQuery.isError ? <Alert severity="error">Failed to load Shopify readiness audit state.</Alert> : null}
      {releaseGateQuery.isError ? <Alert severity="error">Failed to load platform release gate.</Alert> : null}

      <Grid container spacing={2}>
        <Grid item xs={12} md={3}>
          <SummaryCard title="Private listing" value={privateReady ? 'Ready' : 'Needs evidence'} color={privateReady ? 'success' : 'warning'} detail="Design-partner launch posture only." />
        </Grid>
        <Grid item xs={12} md={3}>
          <SummaryCard title="Public App Store" value={publicReady ? 'Ready' : 'Blocked'} color={publicReady ? 'success' : 'error'} detail="Blocked until production and protected-data proof." />
        </Grid>
        <Grid item xs={12} md={3}>
          <SummaryCard title="Release gate" value={releaseGateQuery.data?.status ?? 'Not evaluated'} color={statusColor(releaseGateQuery.data?.status)} detail={releaseGateQuery.data?.summaryMessage ?? 'Run hosted/full staging release gate.'} />
        </Grid>
        <Grid item xs={12} md={3}>
          <SummaryCard title="Controlled proof" value={productionProofStatus} color={statusColor(productionProofStatus)} detail="Mutation remains operator-gated." />
        </Grid>
      </Grid>

      <SectionCard icon={<AppRegistrationRoundedIcon />} title="App Store/private listing readiness">
        <ReadinessTable items={items} />
      </SectionCard>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <SectionCard icon={<SecurityRoundedIcon />} title="Protected-data and external Shopify gates">
            <ReadinessTable items={EXTERNAL_GATE_ITEMS} compact />
          </SectionCard>
        </Grid>
        <Grid item xs={12} lg={6}>
          <SectionCard icon={<RocketLaunchRoundedIcon />} title="Controlled production proof">
            <Alert severity="warning" sx={{ mb: 2 }}>
              This page exposes proof state only. It does not run the production mutation. Use the release-gate script with
              PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true only during an intentional proof window.
            </Alert>
            <ReadinessTable items={CONTROLLED_PROOF_ITEMS} compact />
          </SectionCard>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <SectionCard icon={<FactCheckRoundedIcon />} title="Release-gate evidence">
            {releaseGateQuery.data ? (
              <Stack spacing={1.5}>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip label={releaseGateQuery.data.status} color={statusColor(releaseGateQuery.data.status)} variant="outlined" />
                  <Chip label={`Suite ${releaseGateQuery.data.suiteKey}`} variant="outlined" />
                  <Chip label={`Evaluated ${formatTimestamp(releaseGateQuery.data.evaluatedAt)}`} variant="outlined" />
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  {releaseGateQuery.data.summaryMessage}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Latest run {releaseGateQuery.data.latestRun?.id ?? 'not recorded'} · expires {formatTimestamp(releaseGateQuery.data.expiresAt)}
                </Typography>
              </Stack>
            ) : (
              <Alert severity="info">Release gate evidence has not loaded yet.</Alert>
            )}
          </SectionCard>
        </Grid>
        <Grid item xs={12} lg={6}>
          <SectionCard icon={<AssignmentTurnedInRoundedIcon />} title="Recent verification runs">
            {suiteRunsQuery.isLoading ? <LinearProgress /> : null}
            {suiteRunsQuery.isError ? <Alert severity="error">Failed to load verification runs.</Alert> : null}
            {latestRuns.length ? (
              <Stack spacing={1}>
                {latestRuns.map((run) => (
                  <Card key={run.id} variant="outlined">
                    <CardContent>
                      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} justifyContent="space-between">
                        <Box>
                          <Typography sx={{ fontWeight: 700 }}>{run.suiteLabel}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {run.id} · {formatTimestamp(run.createdAt)}
                          </Typography>
                        </Box>
                        <Chip label={run.status} color={statusColor(run.status)} variant="outlined" />
                      </Stack>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                        {run.summaryMessage}
                      </Typography>
                    </CardContent>
                  </Card>
                ))}
              </Stack>
            ) : (
              <Alert severity="info">No verification suite runs are recorded yet.</Alert>
            )}
          </SectionCard>
        </Grid>
      </Grid>

      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <Button variant="outlined" onClick={() => void queryClient.invalidateQueries()}>
          Refresh readiness evidence
        </Button>
      </Stack>
    </Stack>
  )
}

function SummaryCard({
  title,
  value,
  color = 'default',
  detail,
}: {
  title: string
  value: string
  color?: StatusColor
  detail: string
}) {
  return (
    <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
      <CardContent>
        <Stack spacing={1}>
          <Typography variant="caption" color="text.secondary">{title}</Typography>
          <Chip label={value} color={color} sx={{ alignSelf: 'flex-start', fontWeight: 700 }} />
          <Typography variant="body2" color="text.secondary">{detail}</Typography>
        </Stack>
      </CardContent>
    </Card>
  )
}

function SectionCard({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
      <CardContent>
        <Stack spacing={2}>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Box sx={{ width: 36, height: 36, borderRadius: 2, bgcolor: 'action.hover', display: 'grid', placeItems: 'center', color: 'primary.main' }}>
              {icon}
            </Box>
            <Typography variant="h6">{title}</Typography>
          </Stack>
          {children}
        </Stack>
      </CardContent>
    </Card>
  )
}

function ReadinessTable({ items, compact = false }: { items: LaunchReadinessItem[]; compact?: boolean }) {
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Gate</TableCell>
          <TableCell>Status</TableCell>
          {!compact ? <TableCell>Owner</TableCell> : null}
          <TableCell>Evidence</TableCell>
          <TableCell>Next action</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.label}>
            <TableCell>
              <Stack direction="row" spacing={1} alignItems="center">
                <LockRoundedIcon fontSize="small" color={item.color === 'success' ? 'success' : item.color === 'error' ? 'error' : 'warning'} />
                <Typography variant="subtitle2">{item.label}</Typography>
              </Stack>
            </TableCell>
            <TableCell><Chip size="small" label={item.status} color={item.color} variant="outlined" /></TableCell>
            {!compact ? <TableCell>{item.owner}</TableCell> : null}
            <TableCell><Typography variant="body2" color="text.secondary">{item.evidence}</Typography></TableCell>
            <TableCell><Typography variant="body2" color="text.secondary">{item.nextAction}</Typography></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
