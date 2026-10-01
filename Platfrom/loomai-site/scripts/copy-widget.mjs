import { createHash } from 'node:crypto'
import { copyFile, mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const siteRoot = path.resolve(__dirname, '..')
const source = path.resolve(siteRoot, '../../max-mode-widget/dist/max-mode-widget.iife.js')
const destinationDirectory = path.join(siteRoot, 'public/vendor')
const manifestPath = path.join(destinationDirectory, 'max-mode-widget-manifest.json')
const legacyDestination = path.join(destinationDirectory, 'max-mode-widget.iife.js')

const sourceStat = await stat(source).catch(() => null)
if (!sourceStat?.isFile() || sourceStat.size < 1000) {
  throw new Error(`Max Mode IIFE bundle is missing or invalid: ${source}`)
}

await mkdir(destinationDirectory, { recursive: true })
const sourceBytes = await readFile(source)
const sha256 = createHash('sha256').update(sourceBytes).digest('hex')
const file = `max-mode-widget.${sha256.slice(0, 16)}.iife.js`
const destination = path.join(destinationDirectory, file)

for (const existing of await readdir(destinationDirectory)) {
  if (
    existing === 'max-mode-widget.iife.js'
    || existing === 'max-mode-widget-manifest.json'
    || /^max-mode-widget\.[a-f0-9]{16}\.iife\.js$/.test(existing)
  ) {
    await rm(path.join(destinationDirectory, existing), { force: true })
  }
}

await copyFile(source, destination)
await copyFile(source, legacyDestination)
await writeFile(manifestPath, `${JSON.stringify({
  schemaVersion: 'loomai-widget-bundle-v1',
  file,
  sha256,
}, null, 2)}\n`)
console.log(`Copied Max Mode browser bundle ${file} (${sourceStat.size} bytes)`)
