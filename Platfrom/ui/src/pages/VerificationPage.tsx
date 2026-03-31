import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
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
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  fetchDeploymentDraft,
  fetchDeployments,
  validateDeploymentDraft,
} from '../api/platformApi'

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString()
}

export function VerificationPage() {
  const queryClient = useQueryClient()
  const [selectedDeploymentId, setSelectedDeploymentId] = useState('')

  const deploymentsQuery = useQuery({
    queryKey: ['deployments'],
    queryFn: fetchDeployments,
  })

  const deployments = deploymentsQuery.data ?? []

  useEffect(() => {
    if (deployments.length === 0) {
      if (selectedDeploymentId !== '') {
        setSelectedDeploymentId('')
      }
      return
    }

    if (!deployments.some((deployment) => deployment.id === selectedDeploymentId)) {
      setSelectedDeploymentId(deployments[0].id)
    }
  }, [deployments, selectedDeploymentId])

  const selectedDeployment = useMemo(
    () => deployments.find((deployment) => deployment.id === selectedDeploymentId) ?? null,
    [deployments, selectedDeploymentId],
  )

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const validationQuery = useQuery({
    queryKey: ['deployment-validation', draftQuery.data?.id],
    queryFn: () => validateDeploymentDraft(draftQuery.data!.id),
    enabled: !!draftQuery.data?.id,
  })

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Verification" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Draft validation
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 960 }}>
          The platform now validates draft configuration before publish. This screen shows whether the
          current draft is releasable and why not when validation fails.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment selection</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Validation runs against the active draft for the selected deployment.
              </Typography>
            </Box>

            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField
                select
                fullWidth
                label="Deployment"
                value={selectedDeploymentId}
                onChange={(event) => setSelectedDeploymentId(event.target.value)}
                disabled={deployments.length === 0}
              >
                {deployments.map((deployment) => (
                  <MenuItem key={deployment.id} value={deployment.id}>
                    {deployment.name} ({deployment.environment})
                  </MenuItem>
                ))}
              </TextField>

              <Button
                variant="outlined"
                startIcon={<RefreshRoundedIcon />}
                disabled={!draftQuery.data?.id}
                onClick={() => {
                  void queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] })
                  void queryClient.invalidateQueries({ queryKey: ['deployment-validation', draftQuery.data?.id] })
                }}
              >
                Refresh validation
              </Button>
            </Stack>

            {selectedDeployment ? (
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip label={selectedDeployment.status} color="primary" />
                <Chip label={`Active: ${selectedDeployment.activeVersion}`} variant="outlined" />
                {draftQuery.data ? <Chip label={`Draft ${draftQuery.data.revisionNumber}`} variant="outlined" /> : null}
              </Stack>
            ) : (
              <Alert severity="info">Create a deployment first to run draft validation.</Alert>
            )}
          </Stack>
        </CardContent>
      </Card>

      {selectedDeployment ? (
        <>
          <Grid container spacing={2.5}>
            <Grid item xs={12} md={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Validation status</Typography>
                    {draftQuery.isLoading || validationQuery.isLoading ? (
                      <Typography color="text.secondary">Running validation...</Typography>
                    ) : validationQuery.isError ? (
                      <Alert severity="error">
                        {validationQuery.error instanceof Error
                          ? validationQuery.error.message
                          : 'Validation failed'}
                      </Alert>
                    ) : validationQuery.data ? (
                      <>
                        <Chip
                          color={validationQuery.data.publishReady ? 'success' : 'warning'}
                          icon={
                            validationQuery.data.publishReady ? (
                              <CheckCircleRoundedIcon />
                            ) : (
                              <WarningAmberRoundedIcon />
                            )
                          }
                          label={validationQuery.data.publishReady ? 'Ready to publish' : 'Blocked'}
                          sx={{ alignSelf: 'flex-start' }}
                        />
                        <Typography variant="body2" color="text.secondary">
                          Validated {formatTimestamp(validationQuery.data.validatedAt)}
                        </Typography>
                        <Typography variant="body2">
                          Errors: <strong>{validationQuery.data.errorCount}</strong>
                        </Typography>
                        <Typography variant="body2">
                          Warnings: <strong>{validationQuery.data.warningCount}</strong>
                        </Typography>
                      </>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} md={8}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Typography variant="h6">What the validator checks</Typography>
                    <Grid container spacing={2}>
                      <Grid item xs={12} sm={6}>
                        <Alert severity="info">Action names, duplicates, descriptions, and route coverage.</Alert>
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <Alert severity="info">Entity config shape and vector dimensions.</Alert>
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <Alert severity="info">Provider profile completeness and built-in template alignment.</Alert>
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <Alert severity="info">Security booleans and remote authz readiness.</Alert>
                      </Grid>
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
                  <Typography variant="h6">Validation findings</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Errors block publish. Warnings are advisory and help clean up drafts before release.
                  </Typography>
                </Box>

                {validationQuery.isLoading ? (
                  <Typography color="text.secondary">Loading findings...</Typography>
                ) : validationQuery.data && validationQuery.data.issues.length === 0 ? (
                  <Alert severity="success">No validation findings. This draft is ready for publish.</Alert>
                ) : validationQuery.data ? (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Severity</TableCell>
                        <TableCell>Section</TableCell>
                        <TableCell>Path</TableCell>
                        <TableCell>Message</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {validationQuery.data.issues.map((issue) => (
                        <TableRow key={`${issue.code}:${issue.path}`} hover>
                          <TableCell>
                            <Chip
                              size="small"
                              color={issue.severity === 'ERROR' ? 'error' : 'warning'}
                              label={issue.severity}
                            />
                          </TableCell>
                          <TableCell>{issue.section}</TableCell>
                          <TableCell sx={{ fontFamily: 'monospace' }}>{issue.path}</TableCell>
                          <TableCell>{issue.message}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </>
      ) : null}
    </Stack>
  )
}
