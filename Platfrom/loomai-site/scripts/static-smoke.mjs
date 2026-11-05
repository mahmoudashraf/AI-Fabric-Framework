import { existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const root = path.resolve(__dirname, '..')
const dist = path.join(root, 'dist')

const indexedRoutes = [
  '',
  'products',
  'products/loomai-platform',
  'products/ai-fabric-framework',
  'products/ai-fabric-chat-ui',
  'experiments',
  'experiments/ai-shopping-experience',
  'experiments/account-resolver',
  'experiments/behavior-signals',
  'experiments/tenant-guard',
  'experiments/privacy-shield',
  'experiments/live-data-sync',
  'research',
  'research/application-data-ai-evidence-alignment',
  'research/explicit-application-context',
  'research/governed-ai-proposed-actions',
  'research/tenant-identity-orchestration-context',
  'research/privacy-aware-rag-context',
  'about',
  'connect',
  'demos/dealership-ai',
]
const noIndexRoutes = [
  'demos/dealership-ai/staff',
  'demos/dealership-ai/vehicles/aster-e1-motion',
  'demos/dealership-ai/vehicles/northstar-s4-touring',
  'demos/dealership-ai/vehicles/morrow-c2-city',
  'demos/dealership-ai/vehicles/caldera-x6-adventure',
  'demos/dealership-ai/vehicles/arden-v3-executive',
  'demos/dealership-ai/vehicles/aster-e2-sport',
]
const routes = [...indexedRoutes, ...noIndexRoutes]

const errors = []
const htmlFiles = []

for (const route of routes) {
  const target = path.join(dist, route, 'index.html')
  if (!existsSync(target)) {
    errors.push(`Missing prerendered route: /${route}`)
    continue
  }
  htmlFiles.push(target)
  const html = readFileSync(target, 'utf8')
  if (!route.startsWith('demos/') && !html.includes('href="https://calendly.com/mahmoud-elgammal-loomai/loomai-integration-customer-success"')) {
    errors.push(`Missing integration booking action at /${route}`)
  }
  const h1Count = (html.match(/<h1(?:\s|>)/g) || []).length
  if (h1Count !== 1) {
    errors.push(`Expected one h1 at /${route || ''}, found ${h1Count}`)
  }
  if (!html.includes('<main id="main-content">')) {
    errors.push(`Missing main landmark at /${route || ''}`)
  }
  if (html.includes('href="#"') || html.includes('Lorem ipsum')) {
    errors.push(`Placeholder content found at /${route || ''}`)
  }
}

for (const required of [
  '404.html',
  'robots.txt',
  'sitemap.xml',
  'research/feed.xml',
  'assets/loom-woven-hero.png',
  'assets/platform/dealership-ai-workspace-video-poster.jpg',
  'assets/demos/dealership/vehicle-01.webp',
  'assets/demos/dealership/vehicle-05.webp',
  'assets/demos/dealership/vehicle-02.webp',
  'assets/experiences/example-forma-chair.webp',
  'assets/experiences/example-studio-chair.webp',
  'assets/experiences/demo-support-agent.webp',
  'assets/experiences/google-drive.png',
  'assets/experiences/slack.svg',
  'assets/experiences/demo-support-agent-headset.webp',
  'assets/experiences/example-electric-suv.webp',
  'assets/experiences/kit-support-team.webp',
  'assets/experiences/whatsapp.svg',
  'assets/experiences/manrope-OFL.txt',
  'assets/experiences/mui-icons-LICENSE.txt',
]) {
  if (!existsSync(path.join(dist, required))) {
    errors.push(`Missing required static output: ${required}`)
  }
}

for (const route of ['', 'experiences']) {
  const html = readFileSync(path.join(dist, route, 'index.html'), 'utf8')
  if (route === '' && html.includes('class="experience-montage"')) {
    errors.push('Homepage still contains the removed document/consultation montage')
  }
  for (const expected of [
    'Adapts to your business',
    'Built for dealerships',
    'Website AI chat',
    'Max AI workspace',
    'Standalone AI space',
    'Optional with LoomAI Desk',
    'Fictional simulator stock, not live availability.',
    'Example content only. No messages are sent, enquiries submitted or bookings made.',
    'href="https://console.loomai.pro/experiences/ai-customer-experience"',
    'href="https://console.loomai.pro/experiences/dealership-ai-experience"',
    'href="https://console.loomai.pro/experiences/ai-support-experience"',
    'Only pay when value starts',
    'First 3 months free',
    'Free integration',
    'href="/demos/dealership-ai"',
  ]) {
    if (!html.includes(expected)) errors.push(`Experience catalogue at /${route} is missing ${expected}`)
  }
  for (const variant of ['generic', 'dealership', 'support']) {
    if (!html.includes(`id="catalogue-preview-${variant}"`)) errors.push(`Missing ${variant} native preview dialog at /${route}`)
    const entry = new RegExp(`data-catalogue-preview="${variant}"[^>]+data-catalogue-initial-format="workspace"`)
    if (!entry.test(html)) errors.push(`Missing ${variant} preview entry at /${route}`)
    const dialog = html.match(new RegExp(`<dialog id="catalogue-preview-${variant}"[\\s\\S]*?</dialog>`))?.[0] ?? ''
    for (const format of ['website', 'workspace', 'standalone']) {
      if (!dialog.includes(`data-catalogue-format="${format}"`)) errors.push(`Missing ${variant} ${format} preview control at /${route}`)
    }
  }
}

for (const route of ['demos/dealership-ai', 'demos/dealership-ai/vehicles/aster-e1-motion']) {
  const html = readFileSync(path.join(dist, route, 'index.html'), 'utf8')
  if (!html.includes('__LOOMAI_AI_WORKSPACE_INSTALL_URL__')
      || !html.includes('__LOOMAI_AI_WORKSPACE_INSTALLATION_ID__')
      || !html.includes('data-loomai-ai-workspace="true"')) {
    errors.push(`Dealership route /${route} does not use the Platform-hosted AI Workspace installer`)
  }
  if (html.includes('/vendor/max-mode-widget') || html.includes('/vendor/dealership-experience')) {
    errors.push(`Dealership route /${route} still references a site-hosted workspace bundle`)
  }
}

const platformProductHtml = readFileSync(
  path.join(dist, 'products', 'loomai-platform', 'index.html'),
  'utf8',
)
const portfolioHtml = readFileSync(path.join(dist, 'products', 'index.html'), 'utf8')
let previousLayer = -1
for (const layer of ['kits', 'products', 'platform', 'foundations']) {
  const position = portfolioHtml.indexOf(`data-portfolio-layer="${layer}"`)
  if (position <= previousLayer) errors.push(`Products portfolio layer is missing or out of order: ${layer}`)
  previousLayer = position
}
for (const expected of [
  'General AI experience kit', 'Industry kits', 'LoomAI Engage', 'LoomAI Desk',
  'Your settings remain editable.', 'Max workspace', 'Standalone app',
  'With optional LoomAI Desk', 'Policies.pdf', 'Product guide',
  '/assets/experiences/google-drive.png', '/assets/experiences/slack.svg',
  '/assets/experiences/demo-support-agent.webp',
  'href="/experiences/ai-customer-experience"',
  'href="/experiences/dealership-ai-experience"',
  'href="https://console.loomai.pro/products/engage"',
  'href="https://console.loomai.pro/products/desk"',
  'href="/products/loomai-platform"',
  'href="/products/ai-fabric-framework"',
  'href="/products/ai-fabric-chat-ui"',
]) {
  if (!portfolioHtml.includes(expected)) errors.push(`Products portfolio is missing ${expected}`)
}
if (portfolioHtml.includes('catalogue-dialog') || portfolioHtml.includes('product-comparison__heading')) {
  errors.push('Products overview must remain compact, without the full experience catalogue or responsibility matrix')
}
for (const route of ['', 'products']) {
  const html = readFileSync(path.join(dist, route, 'index.html'), 'utf8')
  const actionLabel = route === '' ? 'Get started' : 'Open console'
  if (!html.includes('href="https://console.loomai.pro/"') || !html.includes(actionLabel)) {
    errors.push(`Platform card is missing its Console action at /${route}`)
  }
}
for (const expected of [
  'Platform products',
  'Dealership AI Workspace',
  'data-platform-product-video="rNl_bcMiS4Y"',
  '/assets/platform/dealership-ai-workspace-video-poster.jpg',
]) {
  if (!platformProductHtml.includes(expected)) {
    errors.push(`LoomAI Platform product page is missing ${expected}`)
  }
}

const sitemap = readFileSync(path.join(dist, 'sitemap.xml'), 'utf8')
for (const route of indexedRoutes) {
  const expected = `https://loomai.pro/${route}`
  if (!sitemap.includes(expected)) {
    errors.push(`Sitemap missing ${expected}`)
  }
}

for (const route of noIndexRoutes) {
  const html = readFileSync(path.join(dist, route, 'index.html'), 'utf8')
  if (!html.includes('name="robots" content="noindex, nofollow"')) {
    errors.push(`Expected noindex metadata at /${route}`)
  }
  const unexpected = `https://loomai.pro/${route}`
  if (sitemap.includes(unexpected)) {
    errors.push(`Noindex route unexpectedly present in sitemap: ${unexpected}`)
  }
}

const forbiddenClaims = [
  'production-certified',
  'formally compliant',
  'trusted by thousands',
  'industry-leading accuracy',
]
const combinedHtml = htmlFiles.map((file) => readFileSync(file, 'utf8').toLowerCase()).join('\n')
for (const claim of forbiddenClaims) {
  if (combinedHtml.includes(claim)) {
    errors.push(`Forbidden unsupported claim found: ${claim}`)
  }
}

const assetDir = path.join(dist, '_astro')
const initialJavascriptGzipBytes = existsSync(assetDir)
  ? readdirSync(assetDir)
      .filter((file) => file.endsWith('.js'))
      .map((file) => gzipSync(readFileSync(path.join(assetDir, file))).byteLength)
      .reduce((total, size) => total + size, 0)
  : 0

if (initialJavascriptGzipBytes > 170 * 1024) {
  errors.push(`JavaScript gzip budget exceeded: ${initialJavascriptGzipBytes} bytes`)
}

if (errors.length > 0) {
  console.error(errors.join('\n'))
  process.exit(1)
}

console.log(`Static smoke passed for ${routes.length} routes`)
console.log(`Total emitted JavaScript: ${initialJavascriptGzipBytes} gzip bytes`)
