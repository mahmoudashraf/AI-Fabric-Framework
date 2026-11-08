import { useQuery } from '@tanstack/react-query'
import { Button } from '@mui/material'
import ForumOutlined from '@mui/icons-material/ForumOutlined'
import { Link as RouterLink } from 'react-router-dom'
import { fetchDeploymentDesk, type DeploymentTargetReference } from '../../api/platformApi'
import { safeCustomerAppUrl } from './customerResourceModels'
import { scopedBrainTaskPath } from '../../brain/brainNavigation'

export function CustomerDeskLink({ target }: { target: DeploymentTargetReference }) {
  const desk = useQuery({
    queryKey: ['deployment-desk', target.deploymentId, target.targetProfileId, target.releaseId],
    queryFn: () => fetchDeploymentDesk(target), retry: false,
  })
  const portal = desk.data?.status === 'ENABLED' ? safeCustomerAppUrl(desk.data.portalUrl) : null
  return portal ? <Button component="a" href={portal} target="_blank" rel="noopener noreferrer"
    startIcon={<ForumOutlined />}>Open LoomAI Desk</Button>
    : <Button component={RouterLink} to={scopedBrainTaskPath(target.deploymentId, '/human-support', new URLSearchParams(target).toString())}
      startIcon={<ForumOutlined />}>Human support</Button>
}
