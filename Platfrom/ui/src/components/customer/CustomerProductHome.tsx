import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Card, CardContent, Chip, Divider, Stack, Typography } from '@mui/material'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import WidgetsOutlined from '@mui/icons-material/WidgetsOutlined'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded'
import AddRounded from '@mui/icons-material/AddRounded'
import type { AIWorkspaceInstallationSummary, CustomerDeploymentSummary, CustomerOfferSummary } from '../../api/platformApi'
import { appResource, deploymentResource, safeCustomerAppUrl, type CustomerResource } from './customerResourceModels'
import { activeProductDeployments, customerProducts, productSetupPath } from './customerProductModels'

export function CustomerProductHome({ offers, deployments, apps, errors, onInspect }: {
  offers: CustomerOfferSummary[]
  deployments: CustomerDeploymentSummary[]
  apps: AIWorkspaceInstallationSummary[]
  errors: string[]
  onInspect: (resource: CustomerResource) => void
}) {
  const products = customerProducts(offers, deployments, apps)
  const [historyOpen, setHistoryOpen] = useState(false)
  const activeApps = apps.filter(app => app.status === 'ACTIVE')
  const activeIssues = activeApps.flatMap(app => appResource(app).issues.map(issue => ({ app, issue })))
  const coveredDeployments = new Set(products.flatMap(product => activeProductDeployments(product).map(deployment => deployment.deploymentId)))
  const otherDeployments = deployments.filter(deployment => !coveredDeployments.has(deployment.deploymentId))
  const inactiveApps = apps.filter(app => app.status !== 'ACTIVE')
  return <Stack spacing={3}>
    {activeIssues.length > 0 ? <Stack spacing={1} aria-label="Active app issues">
      {activeIssues.map(({ app, issue }) => <Alert key={`${app.installationId}:${issue.code}`} severity={issue.severity}
        action={<Button onClick={() => onInspect(appResource(app))}>Inspect</Button>}><strong>{app.displayName}</strong>: {issue.message}</Alert>)}
    </Stack> : null}
    <Box>
      <Typography variant="h6" sx={{ mb: 2 }}>Your products</Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(2,minmax(0,1fr))' }, gap: 2 }}>
        {products.map(product => {
          const active = activeProductDeployments(product)
          const usableOffers = product.offers.filter(offer => offer.entitlement.status === 'ACTIVE')
          return <Card key={product.id} component="section" aria-label={`${product.name} product`}><CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" gap={1}>
              <AutoAwesomeOutlined color="primary" /><Chip size="small" variant="outlined" label={active.length ? 'In use' : usableOffers.length ? 'Ready to set up' : 'Review access'} />
            </Stack>
            <Typography variant="h5" sx={{ mt: 2, overflowWrap: 'anywhere' }}>{product.name}</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{product.description}</Typography>
            <Stack spacing={1.25} sx={{ mt: 2.5 }}>
              {active.map(deployment => <Box key={deployment.deploymentId} sx={{ borderTop: 1, borderColor: 'divider', pt: 1.5 }}>
                <Button onClick={() => onInspect(deploymentResource(deployment, product.offers.find(offer => offer.id === deployment.offerId)))} sx={{ justifyContent: 'flex-start', overflowWrap: 'anywhere', textAlign: 'left' }}>{deployment.name}</Button>
                <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center" sx={{ mt: 0.5 }}>
                  {product.apps.filter(app => app.deploymentId === deployment.deploymentId && app.status === 'ACTIVE').map(app => <Box key={app.installationId}>
                    <Button onClick={() => onInspect(appResource(app))}>{app.displayName}</Button>
                    {safeCustomerAppUrl(app.appUrl) ? <Button component="a" href={safeCustomerAppUrl(app.appUrl)!} target="_blank" rel="noopener noreferrer" startIcon={<OpenInNewRounded />}>Open app</Button> : null}
                  </Box>)}
                  {!product.apps.some(app => app.deploymentId === deployment.deploymentId && app.status === 'ACTIVE') ? <Typography variant="caption" color="text.secondary">No active Engage app assigned</Typography> : null}
                </Stack>
              </Box>)}
              {usableOffers.length === 1 && !active.length ? <Button component={RouterLink} to={productSetupPath(usableOffers[0])} variant="contained" startIcon={<AddRounded />} sx={{ alignSelf: 'flex-start' }}>Set up {product.name}</Button> : null}
            </Stack>
            {usableOffers.length > 1 || active.length > 0 ? <Accordion disableGutters elevation={0} sx={{ mt: 2, bgcolor: 'transparent', '&:before': { display: 'none' } }}>
              <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 0 }}><Typography variant="body2">Start another setup</Typography></AccordionSummary>
              <AccordionDetails sx={{ px: 0 }}><Stack spacing={1}>{usableOffers.map(offer => <Stack key={offer.id} direction="row" justifyContent="space-between" alignItems="center" gap={2}>
                <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{offer.name}</Typography><Button component={RouterLink} to={productSetupPath(offer)}>Open setup</Button>
              </Stack>)}</Stack></AccordionDetails>
            </Accordion> : null}
            {product.offers.some(offer => offer.entitlement.status !== 'ACTIVE') ? <Alert severity="warning" sx={{ mt: 2 }}>Some template access is no longer active. Existing resources remain available according to your permissions.</Alert> : null}
          </CardContent></Card>
        })}
        <Card component="section" aria-label="Engage Apps product"><CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="center"><WidgetsOutlined color="secondary" /><Chip size="small" variant="outlined" label={`${activeApps.length} active`} /></Stack>
          <Typography variant="h5" sx={{ mt: 2 }}>Engage Apps</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>Your branded AI workspace, on its own address or embedded in your website.</Typography>
          <Stack direction="row" gap={1} flexWrap="wrap" sx={{ mt: 2.5 }}><Button component={RouterLink} to="/ai-workspaces" variant="contained">Manage apps</Button><Button component={RouterLink} to="/ai-workspaces?view=manage" startIcon={<AddRounded />}>Create app</Button></Stack>
          {activeApps.filter(app => !products.some(product => product.apps.some(item => item.installationId === app.installationId))).map(app => <Box key={app.installationId} sx={{ mt: 2 }}><Button onClick={() => onInspect(appResource(app))}>{app.displayName}</Button>{safeCustomerAppUrl(app.appUrl) ? <Button component="a" href={safeCustomerAppUrl(app.appUrl)!} target="_blank" rel="noopener noreferrer" startIcon={<OpenInNewRounded />}>Open app</Button> : null}</Box>)}
        </CardContent></Card>
      </Box>
      {!products.length && !errors.length ? <Alert severity="info" sx={{ mt: 2 }}>No deployment template is currently available. <Button component={RouterLink} to="/deployments?view=setup&step=template">Review invitations</Button></Alert> : null}
    </Box>
    <Divider />
    <Accordion expanded={historyOpen} onChange={(_, open) => setHistoryOpen(open)} disableGutters elevation={0} sx={{ bgcolor: 'transparent', '&:before': { display: 'none' } }}>
      <AccordionSummary expandIcon={<ExpandMoreRounded />}><Typography variant="body2">Other deployments and app drafts ({otherDeployments.length + inactiveApps.length})</Typography></AccordionSummary>
      <AccordionDetails><Stack spacing={1}>
        {otherDeployments.map(deployment => <Stack key={deployment.deploymentId} direction="row" justifyContent="space-between" gap={2}><Button onClick={() => onInspect(deploymentResource(deployment, offers.find(offer => offer.id === deployment.offerId)))} sx={{ textAlign: 'left', overflowWrap: 'anywhere' }}>{deployment.name}</Button><Chip size="small" label={deployment.status} variant="outlined" /></Stack>)}
        {inactiveApps.map(app => <Stack key={app.installationId} direction="row" justifyContent="space-between" gap={2}><Button onClick={() => onInspect(appResource(app))} sx={{ textAlign: 'left', overflowWrap: 'anywhere' }}>{app.displayName}</Button><Chip size="small" label={app.status} variant="outlined" /></Stack>)}
        {!otherDeployments.length && !inactiveApps.length ? <Typography variant="body2" color="text.secondary">No additional resources</Typography> : null}
      </Stack></AccordionDetails>
    </Accordion>
  </Stack>
}
