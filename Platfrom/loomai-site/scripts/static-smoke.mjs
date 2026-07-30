import { existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const root = path.resolve(__dirname, '..')
const dist = path.join(root, 'dist')

const routes = [
  '',
  'products',
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
]

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
]) {
  if (!existsSync(path.join(dist, required))) {
    errors.push(`Missing required static output: ${required}`)
  }
}

const sitemap = readFileSync(path.join(dist, 'sitemap.xml'), 'utf8')
for (const route of routes) {
  const expected = `https://loomai.pro/${route}`
  if (!sitemap.includes(expected)) {
    errors.push(`Sitemap missing ${expected}`)
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
