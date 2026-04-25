import { Alert, LinearProgress } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { getEscalationThread } from '../api/escalations'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { EscalationThread } from '../components/EscalationThread'
import { PageHeader } from '../components/PageHeader'

export function EscalationDetailPage() {
  const { escalationId = '' } = useParams()
  const { api } = useSupabaseAuth()
  const threadQuery = useQuery({
    queryKey: ['escalation-thread', escalationId],
    queryFn: () => getEscalationThread(api, escalationId),
    enabled: Boolean(escalationId),
  })

  if (threadQuery.isLoading) {
    return <LinearProgress />
  }
  if (threadQuery.isError || !threadQuery.data) {
    return <Alert severity="error">Escalation is not available to this partner workspace.</Alert>
  }

  return (
    <>
      <PageHeader
        title={threadQuery.data.escalation.title}
        subtitle={threadQuery.data.escalation.shopDomain ?? 'Partner support case'}
        breadcrumbs={[{ label: 'Support', to: '/support' }, { label: threadQuery.data.escalation.title }]}
      />
      <EscalationThread thread={threadQuery.data} />
    </>
  )
}
