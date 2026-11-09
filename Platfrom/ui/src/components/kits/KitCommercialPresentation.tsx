import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded'
import DirectionsCarOutlined from '@mui/icons-material/DirectionsCarOutlined'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import SupportAgentOutlined from '@mui/icons-material/SupportAgentOutlined'
import type { KitPreviewKind } from './kitPreviewContent'

export const dealershipOffer = {
  price: 'Tailored pricing',
  value: 'Only pay when value starts',
  benefits: ['First 3 months free', 'Free integration'],
} as const

export const kitLaunchOffers = {
  adaptive: { label: 'Customer experience launch offer', price: 'Tailored pricing', value: 'Integration included', benefits: ['Built around your business'] },
  dealership: { label: 'Dealership offer', ...dealershipOffer },
  support: { label: 'Support launch offer', price: 'Tailored pricing', value: 'Setup included', benefits: ['Knowledge onboarding included'] },
} as const

export const kitOfferTerms = 'Your proposal confirms the agreed integration scope, included AI usage, storage and agent seats, plus any third-party charges. Work outside that scope is quoted separately.'
export const dealershipPilotTerms = 'Before launch, we agree in writing the pilot acceptance milestone, the start of the three-month free period and when billing begins.'

export function KitLaunchOffer({ kind }: { kind: KitPreviewKind }) {
  const offer = kitLaunchOffers[kind]
  const Icon = kind === 'dealership' ? DirectionsCarOutlined : kind === 'support' ? SupportAgentOutlined : AutoAwesomeOutlined
  return <aside className={`ek-launch-offer${kind === 'dealership' ? ' ek-dealership-offer' : ''}`} aria-label={offer.label}>
    <span className="ek-offer-title"><Icon />{kind === 'dealership' ? offer.label : 'Launch offer'}</span>
    <div className="ek-offer-value"><span>{offer.price}</span><strong>{offer.value}</strong></div>
    <ul>{offer.benefits.map(benefit => <li key={benefit}><CheckCircleRounded />{benefit}</li>)}</ul>
  </aside>
}
