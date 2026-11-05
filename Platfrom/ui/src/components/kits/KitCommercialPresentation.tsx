import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded'
import DirectionsCarOutlined from '@mui/icons-material/DirectionsCarOutlined'

export const dealershipOffer = {
  price: '\u00a3500 / month',
  value: 'Only pay when value starts',
  benefits: ['First 3 months free', 'Free integration'],
} as const

export function KitDealershipOffer({ compact = false }: { compact?: boolean }) {
  return <aside className={`ek-dealership-offer${compact ? ' ek-dealership-offer-compact' : ''}`} aria-label="Dealership offer">
    <span className="ek-offer-title"><DirectionsCarOutlined />Dealership offer</span>
    <div className="ek-offer-value"><span>{dealershipOffer.price}</span><strong>{dealershipOffer.value}</strong></div>
    <ul>{dealershipOffer.benefits.map(benefit => <li key={benefit}><CheckCircleRounded />{benefit}</li>)}</ul>
  </aside>
}
