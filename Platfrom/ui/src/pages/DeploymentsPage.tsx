import { zodResolver } from '@hookform/resolvers/zod'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded'
import PendingRoundedIcon from '@mui/icons-material/PendingRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'
import {
  createDeployment,
  fetchDeployments,
  fetchDeploymentTemplates,
  type CreateDeploymentRequest,
} from '../api/platformApi'

const schema = z.object({
  name: z.string().min(3, 'Name must be at least 3 characters'),
  environment: z.string().min(2, 'Environment is required'),
  templateId: z.string().min(1, 'Template is required'),
})

type FormValues = z.infer<typeof schema>

export function DeploymentsPage() {
  const queryClient = useQueryClient()
  const templatesQuery = useQuery({
    queryKey: ['deployment-templates'],
    queryFn: fetchDeploymentTemplates,
  })
  const deploymentsQuery = useQuery({
    queryKey: ['deployments'],
    queryFn: fetchDeployments,
  })

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      environment: 'dev',
      templateId: '',
    },
  })

  const createMutation = useMutation({
    mutationFn: (payload: CreateDeploymentRequest) => createDeployment(payload),
    onSuccess: async () => {
      form.reset({
        name: '',
        environment: 'dev',
        templateId: '',
      })
      await queryClient.invalidateQueries({ queryKey: ['deployments'] })
    },
  })

  const templates = templatesQuery.data ?? []
  const deployments = deploymentsQuery.data ?? []

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Deployments" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Deployment control plane
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 920 }}>
          Phase 2 now includes persisted deployment state in the backend. This screen creates
          environment records and the revisions view exposes the publish and apply lifecycle on top
          of them.
        </Typography>
      </Box>

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={5}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2.5}>
                <Box>
                  <Typography variant="h6">Create deployment</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    This creates the control-plane deployment record using a selected template. Draft,
                    version, and release state are created in the backend and can be managed from the
                    revisions screen.
                  </Typography>
                </Box>

                <form
                  onSubmit={form.handleSubmit((values) => createMutation.mutate(values))}
                  noValidate
                >
                  <Stack spacing={2}>
                    <Controller
                      name="name"
                      control={form.control}
                      render={({ field, fieldState }) => (
                        <TextField
                          {...field}
                          label="Deployment name"
                          error={!!fieldState.error}
                          helperText={fieldState.error?.message ?? 'Visible in the operator console'}
                        />
                      )}
                    />

                    <Controller
                      name="environment"
                      control={form.control}
                      render={({ field, fieldState }) => (
                        <TextField
                          {...field}
                          label="Environment"
                          error={!!fieldState.error}
                          helperText={fieldState.error?.message ?? 'For example: dev, stage, prod'}
                        />
                      )}
                    />

                    <Controller
                      name="templateId"
                      control={form.control}
                      render={({ field, fieldState }) => (
                        <TextField
                          {...field}
                          select
                          label="Template"
                          error={!!fieldState.error}
                          helperText={fieldState.error?.message ?? 'Choose the base deployment template'}
                        >
                          {templates.map((template) => (
                            <MenuItem key={template.id} value={template.id}>
                              {template.name}
                            </MenuItem>
                          ))}
                        </TextField>
                      )}
                    />

                    {createMutation.isError ? (
                      <Alert severity="error">
                        {createMutation.error instanceof Error
                          ? createMutation.error.message
                          : 'Failed to create deployment'}
                      </Alert>
                    ) : null}

                    <Button
                      type="submit"
                      variant="contained"
                      startIcon={<AddRoundedIcon />}
                      disabled={createMutation.isPending || templatesQuery.isLoading}
                    >
                      {createMutation.isPending ? 'Creating...' : 'Create deployment'}
                    </Button>
                  </Stack>
                </form>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={7}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Templates</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Templates define the initial provider and vector strategy choices for new
                    environments.
                  </Typography>
                </Box>
                <Grid container spacing={2}>
                  {templates.map((template) => (
                    <Grid item xs={12} md={6} key={template.id}>
                      <Card
                        sx={{
                          height: '100%',
                          borderRadius: 3,
                          border: '1px solid',
                          borderColor: 'divider',
                          boxShadow: 'none',
                        }}
                      >
                        <CardContent>
                          <Stack spacing={1.25}>
                            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                              {template.name}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {template.description}
                            </Typography>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              <Chip size="small" label={template.llmProvider} />
                              <Chip size="small" label={template.vectorStrategy} />
                            </Stack>
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>
                  ))}
                </Grid>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Current deployments</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                These are the persisted deployment records. Railway provisioning and verification jobs
                are the next implementation phase.
              </Typography>
            </Box>
            <Divider />
            {deploymentsQuery.isLoading ? (
              <Typography color="text.secondary">Loading deployments...</Typography>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Environment</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Active Version</TableCell>
                    <TableCell>Runtime</TableCell>
                    <TableCell>Connector</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {deployments.map((deployment) => {
                    const isActive = deployment.status === 'ACTIVE'
                    return (
                      <TableRow key={deployment.id} hover>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {deployment.name}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {deployment.id}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>{deployment.environment}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            color={isActive ? 'success' : 'warning'}
                            icon={isActive ? <CheckCircleRoundedIcon /> : <PendingRoundedIcon />}
                            label={deployment.status}
                          />
                        </TableCell>
                        <TableCell>{deployment.activeVersion}</TableCell>
                        <TableCell>
                          {deployment.runtimeBaseUrl ? (
                            <Stack direction="row" spacing={0.75} alignItems="center">
                              <LaunchRoundedIcon fontSize="inherit" />
                              <Typography variant="body2">{deployment.runtimeBaseUrl}</Typography>
                            </Stack>
                          ) : (
                            <Typography color="text.secondary">Pending</Typography>
                          )}
                        </TableCell>
                        <TableCell>
                          {deployment.connectorBaseUrl ? (
                            <Stack direction="row" spacing={0.75} alignItems="center">
                              <LaunchRoundedIcon fontSize="inherit" />
                              <Typography variant="body2">{deployment.connectorBaseUrl}</Typography>
                            </Stack>
                          ) : (
                            <Typography color="text.secondary">Pending</Typography>
                          )}
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}
