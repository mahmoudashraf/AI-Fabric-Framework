import { zodResolver } from '@hookform/resolvers/zod'
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Controller, useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { createClientImplementation, fetchEligibleStores } from '../api/implementations'
import type { PartnerEligibleStore, PartnerSession } from '../api/schemas'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { AccessGuard } from '../auth/AccessGuard'
import { PageHeader } from '../components/PageHeader'
import { useState } from 'react'

const formSchema = z.object({
  clientName: z.string().min(2, 'Client name is required.'),
  contactEmail: z.string().email('Enter a valid email.').optional().or(z.literal('')),
  storeConnectionId: z.string().min(1, 'Choose an installed Shopify store.'),
  vertical: z.string().optional(),
  knownIntegrationsText: z.string().optional(),
  notes: z.string().optional(),
})

type ImplementationForm = z.infer<typeof formSchema>

export function NewImplementationPage({ session }: { session: PartnerSession }) {
  const canCreate = session.permissions.includes('IMPLEMENTATION_CREATE')
  return (
    <AccessGuard hasAccess={canCreate} message="Your partner role cannot create implementation requests.">
      <NewImplementationForm />
    </AccessGuard>
  )
}

function NewImplementationForm() {
  const navigate = useNavigate()
  const { api } = useSupabaseAuth()
  const [storeQuery, setStoreQuery] = useState('')
  const [selectedStore, setSelectedStore] = useState<PartnerEligibleStore | null>(null)
  const form = useForm<ImplementationForm>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      clientName: '',
      contactEmail: '',
      storeConnectionId: '',
      vertical: '',
      knownIntegrationsText: '',
      notes: '',
    },
  })
  const eligibleStoresQuery = useQuery({
    queryKey: ['eligible-stores', storeQuery],
    queryFn: () => fetchEligibleStores(api, storeQuery),
    staleTime: 30_000,
  })
  const mutation = useMutation({
    mutationFn: (values: ImplementationForm) =>
      createClientImplementation(api, {
        clientName: values.clientName,
        contactEmail: values.contactEmail || undefined,
        storeConnectionId: values.storeConnectionId,
        vertical: values.vertical || undefined,
        knownIntegrations: values.knownIntegrationsText?.split(',').map((item) => item.trim()).filter(Boolean) ?? [],
        notes: values.notes || undefined,
      }),
    onSuccess: (implementation) => navigate(`/implementations/${implementation.id}`),
  })

  return (
    <>
      <PageHeader
        title="New implementation"
        subtitle="Create a full-access implementation request for an installed Shopify store. The merchant approves access inside Shopify admin."
        breadcrumbs={[{ label: 'Dashboard', to: '/' }, { label: 'New implementation' }]}
      />
      <Paper sx={{ p: 3, maxWidth: 860 }}>
        <Stack component="form" spacing={2.25} onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
          {mutation.isError ? <Alert severity="error">{mutation.error instanceof Error ? mutation.error.message : 'Implementation request failed.'}</Alert> : null}
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
            <TextField label="Client name" {...form.register('clientName')} error={Boolean(form.formState.errors.clientName)} helperText={form.formState.errors.clientName?.message} />
            <TextField label="Contact email" type="email" {...form.register('contactEmail')} error={Boolean(form.formState.errors.contactEmail)} helperText={form.formState.errors.contactEmail?.message} />
            <Controller
              control={form.control}
              name="storeConnectionId"
              render={({ field }) => (
                <Autocomplete
                  options={eligibleStoresQuery.data ?? []}
                  value={selectedStore}
                  inputValue={storeQuery}
                  loading={eligibleStoresQuery.isLoading}
                  onInputChange={(_, value) => setStoreQuery(value)}
                  onChange={(_, value) => {
                    setSelectedStore(value)
                    field.onChange(value?.storeConnectionId ?? '')
                  }}
                  getOptionLabel={(option) => `${option.displayName} · ${option.shopDomain}`}
                  isOptionEqualToValue={(option, value) => option.storeConnectionId === value.storeConnectionId}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="Installed Shopify store"
                      error={Boolean(form.formState.errors.storeConnectionId)}
                      helperText={form.formState.errors.storeConnectionId?.message ?? 'Only installed stores without active or pending partner access appear.'}
                    />
                  )}
                  renderOption={(props, option) => (
                    <Box component="li" {...props} key={option.storeConnectionId}>
                      <Stack spacing={0.25}>
                        <Typography fontWeight={700}>{option.displayName}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {option.shopDomain} · {option.readinessStatus} · {option.widgetStatus}
                        </Typography>
                      </Stack>
                    </Box>
                  )}
                />
              )}
            />
            <TextField label="Vertical" {...form.register('vertical')} placeholder="Fashion, electronics, health/beauty" />
            <TextField label="Known integrations" {...form.register('knownIntegrationsText')} placeholder="Reviews app, page builder" />
          </Box>
          <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 2 }}>
            <Typography variant="h3">Store configured access</Typography>
            <Typography color="text.secondary" sx={{ mt: 0.5 }}>
              The request uses the merchant store configuration. Tier changes stay with the merchant in Shopify.
            </Typography>
            <Stack direction="row" gap={1} flexWrap="wrap" sx={{ mt: 1.25 }}>
              {(selectedStore?.enabledSurfaces?.length ? selectedStore.enabledSurfaces : ['Select a store']).map((surface) => (
                <Chip key={surface} label={surface} size="small" />
              ))}
            </Stack>
          </Box>
          <TextField label="Notes" minRows={4} multiline {...form.register('notes')} />
          <Stack direction="row" justifyContent="flex-end" spacing={1}>
            <Button onClick={() => navigate('/')} disabled={mutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={mutation.isPending}>Create request</Button>
          </Stack>
        </Stack>
      </Paper>
    </>
  )
}
