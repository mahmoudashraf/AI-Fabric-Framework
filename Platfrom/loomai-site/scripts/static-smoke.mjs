import { createHash } from 'node:crypto'
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
  'assets/demos/dealership/vehicle-01.webp',
  'assets/demos/dealership/vehicle-05.webp',
  'vendor/max-mode-widget-manifest.json',
  'vendor/dealership-experience-manifest.json',
]) {
  if (!existsSync(path.join(dist, required))) {
    errors.push(`Missing required static output: ${required}`)
  }
}

verifyBrowserBundleManifest({
  manifestName: 'max-mode-widget-manifest.json',
  schemaVersion: 'loomai-widget-bundle-v1',
  filePattern: /^max-mode-widget\.[a-f0-9]{16}\.iife\.js$/,
  label: 'Widget',
})
verifyBrowserBundleManifest({
  manifestName: 'dealership-experience-manifest.json',
  schemaVersion: 'loomai-dealership-experience-bundle-v1',
  filePattern: /^dealership-experience\.[a-f0-9]{16}\.iife\.js$/,
  label: 'Dealership experience',
})

function verifyBrowserBundleManifest({ manifestName, schemaVersion, filePattern, label }) {
  const manifestPath = path.join(dist, 'vendor', manifestName)
  if (!existsSync(manifestPath)) return
  try {
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
    if (manifest.schemaVersion !== schemaVersion) {
      errors.push(`${label} bundle manifest has an unsupported schema version`)
    }
    if (!filePattern.test(manifest.file || '')) {
      errors.push(`${label} bundle manifest has an invalid file name`)
    } else {
      const bundlePath = path.join(dist, 'vendor', manifest.file)
      if (!existsSync(bundlePath)) {
        errors.push(`Missing content-hashed ${label.toLowerCase()} bundle: vendor/${manifest.file}`)
      } else {
        const actualSha256 = createHash('sha256').update(readFileSync(bundlePath)).digest('hex')
        if (manifest.sha256 !== actualSha256) {
          errors.push(`${label} bundle manifest SHA-256 does not match the emitted bundle`)
        }
      }
    }
  } catch (error) {
    errors.push(`${label} bundle manifest is not valid JSON: ${error instanceof Error ? error.message : error}`)
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
