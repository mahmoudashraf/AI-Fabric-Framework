import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined'
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded'
import CompareArrowsRounded from '@mui/icons-material/CompareArrowsRounded'
import ConfirmationNumberOutlined from '@mui/icons-material/ConfirmationNumberOutlined'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import ForumOutlined from '@mui/icons-material/ForumOutlined'
import PersonOutlineRounded from '@mui/icons-material/PersonOutlineRounded'
import SendOutlined from '@mui/icons-material/SendOutlined'
import { kitAsset } from './KitIdentity'
import { kitDetailContent } from './kitDetailContent'
import { kitPreviewContent, type KitPreviewFormat, type KitPreviewKind } from './kitPreviewContent'

export function KitDetailWorkspace({ kind, format = 'max' }: { kind: KitPreviewKind; format?: KitPreviewFormat }) {
  const content = kind === 'support' ? null : kitPreviewContent(kind)
  const dealer = kind === 'dealership'
  return <span className={`ek-detail-screen ek-detail-screen-${format}`} aria-hidden="true">
    <span className="ek-screen-browser"><i /><i /><i /><small>{format === 'standalone' ? 'ai.your-business.example' : 'Illustrative workspace'}</small></span>
    <span className="ek-screen-brand"><ForumOutlined /><strong>{content?.brand ?? 'Your support AI'}</strong><small>{kind === 'support' ? 'Help & requests' : 'Explore · Compare · Ask'}</small></span>
    <span className="ek-screen-body">
      <span className="ek-screen-question"><PersonOutlineRounded /><span>{kind === 'support' ? 'How do I connect my data source?' : dealer ? 'An electric SUV under £35,000?' : 'Which chair suits a small home office?'}</span></span>
      <span className="ek-screen-answer"><AutoAwesomeOutlined /><span>{kind === 'support' ? 'Let\'s work through your approved setup guide.' : dealer ? 'Here\'s an option from current demo stock.' : 'Two compact options worth exploring.'}</span></span>
      {content ? <span className="ek-screen-products">{content.products.map((product, index) => <span className="ek-screen-product" key={product.name}>
        <img src={kitAsset(dealer && index === 0 ? 'example-electric-suv.webp' : product.image)} alt="" width="480" height="320" loading="lazy" />
        <span><strong>{product.name}</strong><span>{product.price.replace('GBP ', '£')}</span><small>{product.comparison}</small></span>
      </span>)}</span> : <span className="ek-screen-support">
        <span><DescriptionOutlined /><strong>Connection guide</strong></span>
        <span><i>1</i>Choose your data source.</span><span><i>2</i>Add approved access details.</span><span><i>3</i>Test the connection with your team.</span>
        <span className="ek-screen-ticket"><ConfirmationNumberOutlined /><strong>Request #1042</strong><small>Open · Sample</small></span>
      </span>}
      <span className="ek-screen-commands">{kind === 'support' ? <><span><DescriptionOutlined />Knowledge</span><span><ConfirmationNumberOutlined />My requests</span></>
        : <><span><CompareArrowsRounded />Compare</span><span>{dealer ? <CalendarMonthOutlined /> : <DescriptionOutlined />}{dealer ? 'Request test drive' : 'Delivery guide'}</span></>}</span>
      <span className="ek-screen-composer"><span>{kind === 'support' ? 'Ask a question or follow up...' : dealer ? 'Ask about cars, compare or enquire...' : 'Ask about products, services or options...'}</span><SendOutlined /></span>
    </span>
  </span>
}

export function KitDeliveryScene({ kind, format }: { kind: KitPreviewKind; format: KitPreviewFormat }) {
  const copy = kitDetailContent[kind]
  return <span className={`ek-delivery-visual ek-delivery-${format}`} aria-hidden="true">
    {format === 'website' && <span className="ek-delivery-site"><img src={kitAsset(copy.image)} alt="" width="960" height="384" loading="lazy" />
      <span className="ek-delivery-site-nav">Your business<span>Explore &nbsp; About &nbsp; Contact</span></span>
      <strong>{kind === 'dealership' ? 'Your next car, made simple.' : kind === 'support' ? 'How can we help?' : 'Find something that fits.'}</strong>
      <span className="ek-delivery-site-link">{kind === 'support' ? 'Help centre' : 'Explore more'}</span>
    </span>}
    <KitDetailWorkspace kind={kind} format={format} />
  </span>
}

export function KitOutcomeScene({ kind, step }: { kind: KitPreviewKind; step: number }) {
  const content = kind === 'support' ? null : kitPreviewContent(kind)
  if (kind === 'support') return <div className="ek-outcome-visual" aria-hidden="true">
    {step === 0 ? <><DescriptionOutlined /><div><strong>Your approved guidance</strong><span>A useful answer, with its source.</span></div></>
      : step === 1 ? <><ConfirmationNumberOutlined /><div><strong>Request #1042</strong><span>Open · Linked conversation</span></div></>
        : <><img className="ek-outcome-agent" src={kitAsset('demo-support-agent-headset.webp')} alt="" width="56" height="56" loading="lazy" /><div><strong>Your support team</strong><span>Continue the conversation.</span></div></>}
  </div>
  return <div className={`ek-outcome-visual${step === 1 ? ' ek-outcome-compare' : ''}`} aria-hidden="true">
    {step === 2 ? <><CheckCircleOutlineRounded /><div><strong>{kind === 'dealership' ? 'Test-drive enquiry' : 'Your next step'}</strong><span>{kind === 'dealership' ? 'Ready for your team to review' : 'Guidance from your own business'}</span></div></>
      : content!.products.slice(0, step === 1 ? 2 : 1).map((product, index) => <span key={product.name}>
        <img src={kitAsset(kind === 'dealership' && index === 0 ? 'example-electric-suv.webp' : product.image)} alt="" width="180" height="120" loading="lazy" />
        <span><strong>{product.name}</strong><small>{product.price.replace('GBP ', '£')}</small></span></span>)}
  </div>
}
