import { Button } from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import AssignmentOutlined from '@mui/icons-material/AssignmentOutlined'
import CableRounded from '@mui/icons-material/CableRounded'
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined'
import GroupsOutlined from '@mui/icons-material/GroupsOutlined'
import MenuBookOutlined from '@mui/icons-material/MenuBookOutlined'
import RocketLaunchOutlined from '@mui/icons-material/RocketLaunchOutlined'
import StorageOutlined from '@mui/icons-material/StorageOutlined'
import SupportAgentRounded from '@mui/icons-material/SupportAgentRounded'
import { Link } from 'react-router-dom'
import { IntegrationCallButton } from '../IntegrationCallButton'
import type { KitPreviewKind } from './kitPreviewContent'
import { CommercialContent, PersonalContactContent } from '../../uiProfile'
import './KitIntegrationJourney.css'

const integrationContent = {
  dealership: {
    introduction: 'Your stock, your dealership knowledge, your customer journey. LoomAI handles the technical integration with the systems you approve.',
    systemsTitle: 'Your stock and customer systems',
    systems: 'Agree which stock feeds and customer systems to connect. They remain external integrations, with access and scope approved by you.',
    knowledge: 'Bring your vehicle information, dealership policies and buying guidance. Your team decides what the AI should know.',
    team: 'Test vehicle discovery, enquiries and the moments that need a person. Add your own support agents with LoomAI Desk when needed.',
    test: 'Try vehicle questions, comparisons and enquiry journeys with your own team before customers use the experience.',
    costs: 'Discuss the dealership offer, integration scope and any external provider costs with LoomAI.',
  },
  support: {
    introduction: 'Your support knowledge, your processes, your team. LoomAI handles the technical integration so you can focus on helping customers.',
    systemsTitle: 'Your support systems',
    systems: 'Agree which support systems and request workflows to connect. External systems stay separate, with access and scope approved by you.',
    knowledge: 'Bring approved help articles, policies and troubleshooting guidance. Your team decides what the AI should know.',
    team: 'Choose request tracking and human support separately. Your own agents can join through LoomAI Desk when human support is enabled.',
    test: 'Try support questions and any enabled request tracking or human handover with your own team before customers use the experience.',
    costs: 'Discuss your support requirements, integration scope and ongoing costs with LoomAI.',
  },
  adaptive: {
    introduction: 'Your products, your services, your business knowledge. LoomAI handles the technical integration with the systems you approve.',
    systemsTitle: 'Your business systems',
    systems: 'Agree which product, service and customer systems to connect. They remain external integrations, with access and scope approved by you.',
    knowledge: 'Bring your product information, service details and policies. Your team decides what the AI should know.',
    team: 'Test the customer journey and choose when to bring in a person. Add your own support agents with LoomAI Desk when needed.',
    test: 'Try customer questions and next steps with your own team before customers use the experience.',
    costs: 'Discuss your business requirements, integration scope and ongoing costs with LoomAI.',
  },
} satisfies Record<KitPreviewKind, {
  introduction: string; systemsTitle: string; systems: string; knowledge: string; team: string; test: string; costs: string
}>

export function KitIntegrationJourney({ kind, getStartedPath, compact = false }: { kind: KitPreviewKind; getStartedPath: string; compact?: boolean }) {
  const content = integrationContent[kind]
  const steps = [
    { title: 'Share requirements and access', Icon: AssignmentOutlined,
      description: 'Tell LoomAI what your customers need, which systems you use and what access you can approve.' },
    { title: 'LoomAI connects your systems', Icon: CableRounded,
      description: 'The LoomAI technical team connects your approved systems and knowledge within the agreed integration scope.' },
    { title: 'Test with your own team', Icon: FactCheckOutlined, description: content.test },
    { title: 'Go live', Icon: RocketLaunchOutlined,
      description: 'Review the experience together, then bring it to your customers through your chosen channels.' },
  ]

  const scope = <section className="ek-integration-band" aria-labelledby="ek-integration-title">
      <div className="ek-integration-heading"><span className="ek-integration-label"><CableRounded aria-hidden="true" />Integration by LoomAI</span>
        <h2 id="ek-integration-title">Your business. Connected by our team.</h2><p>{content.introduction}</p>
      </div>
      <div className="ek-integration-scope">
        <div><StorageOutlined aria-hidden="true" /><h3>{content.systemsTitle}</h3><p>{content.systems}</p></div>
        <div><MenuBookOutlined aria-hidden="true" /><h3>Your approved knowledge</h3><p>{content.knowledge}</p></div>
        <div><SupportAgentRounded aria-hidden="true" /><h3>Your people, in the loop</h3><p>{content.team}</p></div>
      </div>
      <p className="ek-integration-disclosure">Connections depend on approved access and the agreed scope. References to external systems do not imply an official partnership or endorsement.</p>
    </section>

  return <div className={`ek-integration-journey${compact ? ' ek-integration-journey-compact' : ''}`} data-kind={kind}>
    {!compact && scope}

    <section className="ek-integration-band" aria-labelledby="ek-integration-steps-title">
      <div className="ek-integration-heading"><span className="ek-integration-label"><GroupsOutlined aria-hidden="true" />From requirements to launch</span>
        <h2 id="ek-integration-steps-title">You know your business. We handle the integration.</h2>
        <p>No need to manage the technical setup yourself. Work with LoomAI from the first requirements conversation through testing and go-live.</p>
      </div>
      <ol className="ek-integration-steps">{steps.map(({ title, Icon, description }, index) => <li key={title}>
        <div className="ek-integration-step-marker"><span>{String(index + 1).padStart(2, '0')}</span><Icon aria-hidden="true" /></div>
        <h3>{title}</h3><p>{description}</p>
      </li>)}</ol>
      {!compact && <PersonalContactContent><div className="ek-integration-next"><div><h3>Talk through your integration</h3><CommercialContent><p>{content.costs}</p></CommercialContent></div>
        <div className="ek-integration-actions"><IntegrationCallButton variant="outlined">Start a project</IntegrationCallButton>
          <Button component={Link} to={getStartedPath} endIcon={<ArrowForwardRounded />}>Install kit</Button></div>
      </div></PersonalContactContent>}
    </section>
    {compact && <details className="ek-detail-integration-scope"><summary>What we connect, and what you control</summary>{scope}<CommercialContent><p>{content.costs}</p></CommercialContent></details>}
  </div>
}
