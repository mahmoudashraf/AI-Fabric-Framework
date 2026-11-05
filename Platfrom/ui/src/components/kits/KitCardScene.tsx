import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined'
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded'
import CompareArrowsRounded from '@mui/icons-material/CompareArrowsRounded'
import ConfirmationNumberOutlined from '@mui/icons-material/ConfirmationNumberOutlined'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import ForumOutlined from '@mui/icons-material/ForumOutlined'
import MenuBookOutlined from '@mui/icons-material/MenuBookOutlined'
import PersonOutlineRounded from '@mui/icons-material/PersonOutlineRounded'
import SendOutlined from '@mui/icons-material/SendOutlined'
import { KitMark, kitAsset } from './KitIdentity'
import { kitPreviewContent, type KitPreviewKind } from './kitPreviewContent'

const sceneCopy = {
  adaptive: { name: 'Oak & Co AI', query: 'Which chair suits a small home office?', answer: 'Two compact options worth exploring.' },
  dealership: { name: 'Northfield AI', query: 'An electric SUV under \u00a335,000?', answer: 'Here is one from your current stock.' },
  support: { name: 'Your support AI', query: 'How do I connect my data source?', answer: 'Here are the steps from your setup guide.' },
} as const

const sceneHeaderImage = { adaptive: 'example-forma-chair.webp', dealership: 'example-electric-suv.webp', support: 'example-studio-chair.webp' } as const

/** Display-only examples. Real actions and messages belong to the dismissible preview or live demo. */
export function KitCardScene({ kind }: { kind: KitPreviewKind }) {
  const copy = sceneCopy[kind]
  const content = kind === 'support' ? null : kitPreviewContent(kind)
  return <figure className="ek-card-scene" role="img" aria-label={`Illustrative ${copy.name} workspace. Fictional example, not live AI or an assigned agent.`}>
    <div aria-hidden="true">
      <div className="ek-card-scene-header"><img src={kitAsset(sceneHeaderImage[kind])} alt="" width="320" height="120" loading="lazy" /><ForumOutlined /><strong>{copy.name}</strong></div>
      <div className="ek-card-scene-body">
        <div className="ek-card-question"><PersonOutlineRounded /><span>{copy.query}</span></div>
        <div className="ek-card-answer"><KitMark product="engage" /><span>{copy.answer}</span></div>
        {kind === 'adaptive' && content && <>
          <div className="ek-card-chair-pair">{content.products.map(product => <div key={product.name}>
            <img src={kitAsset(product.image)} alt="" width="480" height="360" loading="lazy" /><strong>{product.name.replace(' Chair', '')}</strong>
          </div>)}</div>
          <div className="ek-card-context-line"><span><CompareArrowsRounded />Compare</span><span><MenuBookOutlined />Your approved knowledge</span></div>
        </>}
        {kind === 'dealership' && content && <>
          <div className="ek-card-stock"><img src={kitAsset('example-electric-suv.webp')} alt="" width="720" height="480" loading="lazy" />
            <div><strong>{content.products[0].name}</strong><span>{content.products[0].price.replace('GBP ', '\u00a3')}</span><small>Electric &middot; 4,850 miles</small></div></div>
          <div className="ek-card-compare"><div>{content.products.map((product, index) => <img key={product.name} src={kitAsset(index === 0 ? 'example-electric-suv.webp' : product.image)} alt="" width="96" height="64" loading="lazy" />)}</div>
            <span><CompareArrowsRounded />Compare vehicles</span></div>
          <div className="ek-card-next-step"><CalendarMonthOutlined />Request a test drive</div>
          <div className="ek-card-live"><CheckCircleRounded />Live inventory connected</div>
        </>}
        {kind === 'support' && <>
          <div className="ek-card-guide"><strong><DescriptionOutlined />Connection guide</strong><ol>
            <li>Choose your data source.</li><li>Add your approved access details.</li><li>Test the connection with your team.</li>
          </ol></div>
          <div className="ek-card-request"><ConfirmationNumberOutlined /><strong>Request #1042</strong><span>Open</span></div>
          <div className="ek-card-human"><img src={kitAsset('demo-support-agent.webp')} alt="" width="40" height="40" loading="lazy" />
            <div><strong>Alex &middot; Support</strong><span>I can help with the next step.</span></div></div>
        </>}
        {kind !== 'dealership' && <div className="ek-card-input"><span>{kind === 'support' ? 'Reply to your support team...' : 'Ask a follow-up...'}</span><SendOutlined /></div>}
      </div>
    </div>
  </figure>
}
