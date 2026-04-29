import { Box, Button, Paper, Stack, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { PartnerCatalogEntry, PartnerSession } from '../api/schemas'
import { listCatalog } from '../api/catalog'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { DetailDrawer } from '../components/DetailDrawer'
import { EmptyState } from '../components/EmptyState'
import { PageHeader } from '../components/PageHeader'
import { TierBadge } from '../components/TierBadge'
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined'

export function IntelligenceCatalogPage({ session }: { session: PartnerSession }) {
  const { api } = useSupabaseAuth()
  const [selected, setSelected] = useState<PartnerCatalogEntry | null>(null)
  const catalogQuery = useQuery({ queryKey: ['catalog'], queryFn: () => listCatalog(api), enabled: !session.signupRequired })
  const entries = catalogQuery.data ?? []

  return (
    <>
      <PageHeader
        title="Intelligence catalog"
        subtitle="Partner-safe Free and Starter surfaces. Starter surfaces are read-only and exclude order lookup."
      />
      {entries.length === 0 && !catalogQuery.isLoading ? (
        <EmptyState
          icon={AutoAwesomeOutlinedIcon}
          title="Catalog unavailable"
          body="Complete workspace setup to view the partner catalog."
        />
      ) : (
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)', xl: 'repeat(3, 1fr)' }, gap: 2 }}>
          {entries.map((entry) => (
            <Paper key={entry.surfaceId} sx={{ p: 2 }}>
              <Stack spacing={1.25}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
                  <Box>
                    <Typography variant="h3">{entry.name}</Typography>
                    <Typography variant="caption" color="text.secondary">{entry.type}</Typography>
                  </Box>
                  <TierBadge tier={entry.tier} />
                </Stack>
                <Typography color="text.secondary">{entry.shopperProblem}</Typography>
                <Typography variant="caption" color="text.secondary">Placement</Typography>
                <Typography>{entry.storefrontPlacement}</Typography>
                <Button sx={{ alignSelf: 'flex-start' }} onClick={() => setSelected(entry)}>Review details</Button>
              </Stack>
            </Paper>
          ))}
        </Box>
      )}
      <DetailDrawer open={Boolean(selected)} title={selected?.name ?? 'Catalog surface'} onClose={() => setSelected(null)}>
        {selected ? (
          <Stack spacing={2}>
            <TierBadge tier={selected.tier} />
            <Section title="Launch-safe claim" items={[selected.launchSafeClaim]} />
            <Section title="Required source data" items={selected.requiredSourceData} />
            <Section title="Merchant setup" items={selected.merchantSetup} />
            <Section title="Verification steps" items={selected.verificationSteps} />
            <Section title="Failure signs" items={selected.failureSigns} />
            <Section title="Limitations" items={selected.limitations} />
            <Section title="Escalation evidence" items={selected.escalationEvidence} />
          </Stack>
        ) : null}
      </DetailDrawer>
    </>
  )
}

function Section({ title, items }: { title: string; items: string[] }) {
  return (
    <Box>
      <Typography variant="h3" sx={{ mb: 0.75 }}>{title}</Typography>
      <Stack component="ul" spacing={0.75} sx={{ pl: 3, m: 0 }}>
        {items.map((item) => (
          <Typography component="li" key={item}>{item}</Typography>
        ))}
      </Stack>
    </Box>
  )
}
