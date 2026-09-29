import { copyFile, mkdir, stat } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const siteRoot = path.resolve(__dirname, '..')
const source = path.resolve(siteRoot, '../../max-mode-widget/dist/max-mode-widget.iife.js')
const destinationDirectory = path.join(siteRoot, 'public/vendor')
const destination = path.join(destinationDirectory, 'max-mode-widget.iife.js')

const sourceStat = await stat(source).catch(() => null)
if (!sourceStat?.isFile() || sourceStat.size < 1000) {
  throw new Error(`Max Mode IIFE bundle is missing or invalid: ${source}`)
}

await mkdir(destinationDirectory, { recursive: true })
await copyFile(source, destination)
console.log(`Copied Max Mode browser bundle (${sourceStat.size} bytes)`)
