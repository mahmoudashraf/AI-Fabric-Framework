import { createHash } from 'node:crypto'
import { copyFile, mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const siteRoot = path.resolve(__dirname, '..')
const destinationDirectory = path.join(siteRoot, 'public/vendor')
const bundles = [
  {
    source: path.resolve(siteRoot, '../../max-mode-widget/dist/max-mode-widget.iife.js'),
    baseName: 'max-mode-widget',
    schemaVersion: 'loomai-widget-bundle-v1',
  },
  {
    source: path.resolve(
      siteRoot,
      '../../experience-packs/dealership-experience/dist/dealership-experience.iife.js',
    ),
    baseName: 'dealership-experience',
    schemaVersion: 'loomai-dealership-experience-bundle-v1',
  },
]

await mkdir(destinationDirectory, { recursive: true })

for (const bundle of bundles) {
  const sourceStat = await stat(bundle.source).catch(() => null)
  if (!sourceStat?.isFile() || sourceStat.size < 1000) {
    throw new Error(`Browser bundle is missing or invalid: ${bundle.source}`)
  }
  const sourceBytes = await readFile(bundle.source)
  const sha256 = createHash('sha256').update(sourceBytes).digest('hex')
  const file = `${bundle.baseName}.${sha256.slice(0, 16)}.iife.js`
  const manifestName = `${bundle.baseName}-manifest.json`

  for (const existing of await readdir(destinationDirectory)) {
    if (
      existing === `${bundle.baseName}.iife.js`
      || existing === manifestName
      || new RegExp(`^${bundle.baseName}\\.[a-f0-9]{16}\\.iife\\.js$`).test(existing)
    ) {
      await rm(path.join(destinationDirectory, existing), { force: true })
    }
  }

  await copyFile(bundle.source, path.join(destinationDirectory, file))
  await copyFile(bundle.source, path.join(destinationDirectory, `${bundle.baseName}.iife.js`))
  await writeFile(path.join(destinationDirectory, manifestName), `${JSON.stringify({
    schemaVersion: bundle.schemaVersion,
    file,
    sha256,
  }, null, 2)}\n`)
  console.log(`Copied browser bundle ${file} (${sourceStat.size} bytes)`)
}
