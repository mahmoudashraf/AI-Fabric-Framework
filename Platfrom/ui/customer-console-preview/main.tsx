import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogContent, DialogTitle, Stack, TextField, Typography } from '@mui/material'
import { CustomerShellLayout } from '../src/layout/CustomerConsoleShell'
import type { PlatformAuthSessionSummary } from '../src/api/platformApi'

const fixtures = {
  dealership: { account: 'Northfield Motor House', deployment: 'Northfield assistant', app: 'Northfield AI', source: 'Vehicle inventory + dealership policies', connection: 'Advertiser-scoped simulator', access: 'Anonymous + protected-action sign-in' },
  shopify: { account: 'Willow & Finch', deployment: 'Store shopping assistant', app: 'Storefront AI workspace', source: 'Products + collections + store policies', connection: 'Shopify store installation', access: 'Existing private Shopify adapter' },
}
const params = new URLSearchParams(location.search)
const product = params.get('product') === 'shopify' ? 'shopify' : 'dealership'
const fixture = fixtures[product]
const session: PlatformAuthSessionSummary = {
  enabled: true, authenticated: true, actorId: 'preview-owner', displayName: 'Jamie Owner',
  role: params.get('role') ?? 'CUSTOMER_ADMIN', authenticationMode: params.get('auth') ?? 'SESSION',
  headerName: 'X-PLATFORM-API-KEY', sessionAuthEnabled: true, apiKeyAuthEnabled: true,
  customerId: 'preview-customer', customerName: fixture.account, customerSlug: 'preview',
  canManageUsers: false, canManageUserDirectory: false, canManageCustomers: false,
  canCreateCustomers: false, canManageSecrets: false, canOperateDeployments: false,
}

function PreviewPage() {
  const [detailOpen, setDetailOpen] = useState(false)
  const [name, setName] = useState(fixture.deployment)
  const pathname = useLocation().pathname
  const title = pathname === '/ai-workspaces' ? 'Engage apps' : pathname === '/document-knowledge' ? 'Knowledge & data' : pathname === '/integrations' ? 'Connections' : 'Deployments'
  return <Stack spacing={3}>
    <Box><Typography variant="h4">{title}</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>{fixture.account}</Typography></Box>
    <Alert severity="warning">Production prerequisites need review. No deployment or app assignment has changed.</Alert>
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
      {[{ name: fixture.deployment, kind: 'Deployment', detail: fixture.source }, { name: fixture.app, kind: 'Engage app', detail: fixture.access }].map(item => <Card key={item.kind}><CardContent>
        <Stack direction="row" justifyContent="space-between" spacing={1}><Typography fontWeight={600}>{item.name}</Typography><Chip size="small" label="Preview fixture" variant="outlined" /></Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{item.kind} / Customer staging</Typography>
        <Typography variant="body2" sx={{ mt: 2 }}>{item.detail}</Typography>
        <Button sx={{ mt: 2 }} onClick={() => setDetailOpen(true)}>Inspect preview</Button>
      </CardContent></Card>)}
    </Box>
    <TextField label="Unsaved name preview" value={name} onChange={event => setName(event.target.value)} helperText={fixture.connection} sx={{ maxWidth: 450 }} />
    <Dialog open={detailOpen} onClose={() => setDetailOpen(false)} maxWidth="sm" fullWidth>
      <DialogTitle>{fixture.deployment}</DialogTitle><DialogContent><Stack spacing={2}>
        <Typography>{fixture.source}</Typography><Typography color="text.secondary">{fixture.connection}</Typography>
        <Alert severity="info">Offline fixture. No credentials, model calls or live operations.</Alert>
        <Button onClick={() => setDetailOpen(false)}>Close preview</Button>
      </Stack></DialogContent>
    </Dialog>
  </Stack>
}

createRoot(document.getElementById('root')!).render(<React.StrictMode>
  <div style={{ background: '#24272d', color: '#f4f5f7', padding: '9px 16px', font: '12px/1.5 sans-serif', display: 'flex', flexWrap: 'wrap', gap: '10px 20px' }}>
    <strong>U1 IMPLEMENTATION PREVIEW</strong><span>Actual customer shell / Fictional data / Resource pages follow in U2</span>
    <a style={{ color: '#c4ceff' }} href="?product=dealership">Dealership fixture</a><a style={{ color: '#c4ceff' }} href="?product=shopify">Shopify fixture</a>
  </div>
  <MemoryRouter initialEntries={['/deployments']}>
    <CustomerShellLayout session={session} onSignOut={async () => { alert('Offline preview only. No live session exists.') }}
      buildWorkspacePath={path => `${path}?deploymentId=preview-owned`} deploymentName={fixture.deployment}>
      <PreviewPage />
    </CustomerShellLayout>
  </MemoryRouter>
</React.StrictMode>)
