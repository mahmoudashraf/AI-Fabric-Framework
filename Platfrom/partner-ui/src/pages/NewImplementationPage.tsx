import { zodResolver } from '@hookform/resolvers/zod'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  FormGroup,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { Controller, useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { createClientImplementation } from '../api/implementations'
import type { PartnerSession } from '../api/schemas'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { AccessGuard } from '../auth/AccessGuard'
import { PageHeader } from '../components/PageHeader'

const starterSurfaces = [
  { id: 'ai-search', label: 'AI search' },
  { id: 'product-insight', label: 'Product insight block' },
  { id: 'product-faq', label: 'Product FAQ' },
  { id: 'comparison', label: 'Comparison' },
  { id: 'policy-strip', label: 'Policy strip' },
  { id: 'contextual-pill', label: 'Contextual pill' },
  { id: 'read-only-chat', label: 'Read-only chat' },
]

const formSchema = z.object({
  clientName: z.string().min(2, 'Client name is required.'),
  contactEmail: z.string().email('Enter a valid email.').optional().or(z.literal('')),
  shopDomain: z.string().regex(/^[a-z0-9][a-z0-9-]*\.myshopify\.com$/, 'Use a myshopify.com domain.'),
  vertical: z.string().optional(),
  requestedTier: z.enum(['FREE', 'STARTER', 'ELITE']),
  requestedSurfaces: z.array(z.string()).min(1),
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
  const form = useForm<ImplementationForm>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      clientName: '',
      contactEmail: '',
      shopDomain: '',
      vertical: '',
      requestedTier: 'STARTER',
      requestedSurfaces: starterSurfaces.map((item) => item.id),
      knownIntegrationsText: '',
      notes: '',
    },
  })
  const tier = form.watch('requestedTier')
  const mutation = useMutation({
    mutationFn: (values: ImplementationForm) =>
      createClientImplementation(api, {
        clientName: values.clientName,
        contactEmail: values.contactEmail || undefined,
        shopDomain: values.shopDomain.toLowerCase(),
        vertical: values.vertical || undefined,
        requestedTier: values.requestedTier,
        requestedSurfaces: values.requestedTier === 'FREE' ? ['ai-search'] : values.requestedSurfaces,
        knownIntegrations: values.knownIntegrationsText?.split(',').map((item) => item.trim()).filter(Boolean) ?? [],
        notes: values.notes || undefined,
      }),
    onSuccess: (implementation) => navigate(`/implementations/${implementation.id}`),
  })

  return (
    <>
      <PageHeader
        title="New implementation"
        subtitle="Create a client implementation request and generate a merchant approval link for scoped store access."
        breadcrumbs={[{ label: 'Dashboard', to: '/' }, { label: 'New implementation' }]}
      />
      <Paper sx={{ p: 3, maxWidth: 860 }}>
        <Stack component="form" spacing={2.25} onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
          {mutation.isError ? <Alert severity="error">{mutation.error instanceof Error ? mutation.error.message : 'Implementation request failed.'}</Alert> : null}
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
            <TextField label="Client name" {...form.register('clientName')} error={Boolean(form.formState.errors.clientName)} helperText={form.formState.errors.clientName?.message} />
            <TextField label="Contact email" type="email" {...form.register('contactEmail')} error={Boolean(form.formState.errors.contactEmail)} helperText={form.formState.errors.contactEmail?.message} />
            <TextField label="Shop domain" placeholder="client-store.myshopify.com" {...form.register('shopDomain')} error={Boolean(form.formState.errors.shopDomain)} helperText={form.formState.errors.shopDomain?.message} />
            <TextField label="Vertical" {...form.register('vertical')} placeholder="Fashion, electronics, health/beauty" />
            <TextField label="Requested tier" select {...form.register('requestedTier')}>
              <MenuItem value="FREE">Free</MenuItem>
              <MenuItem value="STARTER">Starter</MenuItem>
              <MenuItem value="ELITE">Elite request</MenuItem>
            </TextField>
            <TextField label="Known integrations" {...form.register('knownIntegrationsText')} placeholder="Reviews app, page builder" />
          </Box>
          <Box>
            <Typography variant="h3">Requested surfaces</Typography>
            <Typography color="text.secondary" sx={{ mt: 0.5 }}>
              Free requests are limited to AI search. Starter requests stay read-only and exclude order lookup.
            </Typography>
            <Controller
              control={form.control}
              name="requestedSurfaces"
              render={({ field }) => (
                <FormGroup sx={{ mt: 1, display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
                  {starterSurfaces.map((surface) => (
                    <FormControlLabel
                      key={surface.id}
                      control={
                        <Checkbox
                          checked={tier === 'FREE' ? surface.id === 'ai-search' : field.value.includes(surface.id)}
                          disabled={tier === 'FREE'}
                          onChange={(event) => {
                            if (event.target.checked) {
                              field.onChange([...new Set([...field.value, surface.id])])
                            } else {
                              field.onChange(field.value.filter((value) => value !== surface.id))
                            }
                          }}
                        />
                      }
                      label={surface.label}
                    />
                  ))}
                </FormGroup>
              )}
            />
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
