import { Button, IconButton, Tooltip, useTheme } from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined'
import LightModeOutlined from '@mui/icons-material/LightModeOutlined'
import ForumOutlined from '@mui/icons-material/ForumOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import ViewInArRounded from '@mui/icons-material/ViewInArRounded'
import DirectionsCarOutlined from '@mui/icons-material/DirectionsCarOutlined'
import { Link } from 'react-router-dom'
import { useCustomerColorPreference } from '../../theme/CustomerConsoleTheme'

export const kitAsset = (name: string) => `${import.meta.env.BASE_URL}products/${name}`

export function KitMark({ product, large = false }: { product: 'platform' | 'engage' | 'desk' | 'dealership'; large?: boolean }) {
  const Icon = product === 'platform' ? ViewInArRounded : product === 'engage' ? ForumOutlined
    : product === 'desk' ? HeadsetMicOutlined : DirectionsCarOutlined
  return <span className={`ek-mark ek-${product}${large ? ' ek-mark-large' : ''}`}><Icon /></span>
}

export function KitProductLine({ dealership = false, support = false }: { dealership?: boolean; support?: boolean }) {
  return <div className="ek-product-line"><span><KitMark product="platform" />Platform</span><i>+</i>
    <span><KitMark product="engage" />Engage</span>
    {dealership && <><i>+</i><span><KitMark product="dealership" />Dealership experience</span></>}
    {support && <><i>+</i><span className="ek-amber"><KitMark product="desk" />Human support setup requested</span></>}
  </div>
}

export function KitPublicHeader({ authenticated, selectedCode, consolePath = '/kits' }: { authenticated: boolean; selectedCode?: string; consolePath?: string }) {
  const theme = useTheme()
  const { setPreference } = useCustomerColorPreference()
  const label = theme.palette.mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
  const query = selectedCode ? `?kit=${encodeURIComponent(selectedCode)}` : ''
  return <header className="ek-public-header">
    <a href="https://loomai.pro/" className="ek-logo" aria-label="LoomAI home"><img src={kitAsset('box.svg')} width="32" height="32" alt="" />LoomAI</a>
    <div className="ek-header-actions"><Tooltip title={label}><IconButton aria-label={label}
      onClick={() => setPreference(theme.palette.mode === 'dark' ? 'light' : 'dark')}>
      {theme.palette.mode === 'dark' ? <LightModeOutlined /> : <DarkModeOutlined />}</IconButton></Tooltip>
      <Button component={Link} to={authenticated ? consolePath : `/sign-in${query}`}>{authenticated ? 'My kits' : 'Sign in'}</Button>
    </div>
  </header>
}

export function KitProductLinks() {
  return <section className="ek-own-products" aria-labelledby="ek-own-products-title"><h2 id="ek-own-products-title">The products behind your experience</h2>
    <div className="ek-own-products-grid">{([
      ['platform', 'Platform', 'Help your AI work with your business information.'],
      ['engage', 'Engage', 'Give customers a workspace with your brand.'],
      ['desk', 'Desk', 'Bring your support team into the conversation.'],
    ] as const).map(([product, name, summary]) => <Link key={product} to={`/products/${product}`}>
      <KitMark product={product} /><span><strong>{name}</strong><small>{summary}</small></span><ArrowForwardRounded />
    </Link>)}</div>
  </section>
}

export function KitSupportBand() {
  return <section className="ek-support-band" aria-labelledby="ek-support-band-title"><KitMark product="desk" />
    <img src={kitAsset('demo-support-agent.webp')} alt="Illustrative support agent" width="54" height="54" loading="lazy" />
    <div><h2 id="ek-support-band-title">A person when your customers need one</h2><p>Add your own support team with LoomAI Desk, now or later. Customers can continue without repeating their story.</p></div>
    <span className="ek-badge ek-badge-amber">Human support is optional</span>
  </section>
}
