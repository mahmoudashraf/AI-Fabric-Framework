import { readdir, rm } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const directory = path.resolve(__dirname, '../public/vendor')

for (const entry of await readdir(directory).catch(() => [])) {
  if (
    entry === 'max-mode-widget.iife.js'
    || entry === 'max-mode-widget-manifest.json'
    || /^max-mode-widget\.[a-f0-9]{16}\.iife\.js$/.test(entry)
  ) {
    await rm(path.join(directory, entry), { force: true })
  }
}
