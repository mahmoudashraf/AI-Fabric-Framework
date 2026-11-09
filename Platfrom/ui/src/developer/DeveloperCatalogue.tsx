import { Alert, Button, CircularProgress, Stack, Typography } from '@mui/material'
import CodeRounded from '@mui/icons-material/CodeRounded'
import { useQuery } from '@tanstack/react-query'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { experienceKitKeys, fetchExperienceKitCatalogue } from '../api/experienceKitsApi'
import { DeveloperStarterCards } from './DeveloperStarterCards'
import { isDeveloperKit } from './developerEntry'
import { useDeveloperExperience } from './DeveloperExperienceProvider'

export function ConsoleExperienceControl() {
  const { developer, selectExperience } = useDeveloperExperience()
  return <Stack direction="row" alignItems="center" justifyContent="space-between" gap={2} flexWrap="wrap" sx={{ mb: 2 }}>
    <Typography variant="body2" color="text.secondary">{developer ? 'Developer starters · Staging only' : 'Business kits'}</Typography>
    <Button size="small" startIcon={developer ? undefined : <CodeRounded />} onClick={() => selectExperience(developer ? 'CUSTOMER' : 'DEVELOPER')}>
      {developer ? 'Browse business kits' : 'Browse developer starters'}
    </Button>
  </Stack>
}

export function DeveloperCatalogue() {
  const customerId = usePlatformAuth().session?.customerId ?? ''
  const catalogue = useQuery({ queryKey: experienceKitKeys.catalogue(customerId), queryFn: ({ signal }) => fetchExperienceKitCatalogue(signal), enabled: Boolean(customerId), retry: false })
  if (catalogue.isPending) return <CircularProgress size={24} aria-label="Loading developer starters" />
  if (catalogue.isError) return <Alert severity="error" action={<Button onClick={() => void catalogue.refetch()}>Retry</Button>}>{catalogue.error.message}</Alert>
  const codes = catalogue.data.map(entry => entry.definition.code).filter(isDeveloperKit)
  return codes.length ? <DeveloperStarterCards availableCodes={codes} />
    : <Alert severity="info">No developer starter is available for this workspace. Refresh the available templates in My kits or contact support.</Alert>
}
