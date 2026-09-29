import { rm } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const target = path.resolve(__dirname, '../public/vendor/max-mode-widget.iife.js')

await rm(target, { force: true })
