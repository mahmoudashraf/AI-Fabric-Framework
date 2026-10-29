import { execFileSync } from 'node:child_process'
import { copyFileSync, existsSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const widget = fileURLToPath(new URL('../../../max-mode-widget/', import.meta.url))
const destination = fileURLToPath(new URL('../public/engage-preview.iife.js', import.meta.url))
if (existsSync(new URL('../../../max-mode-widget/vite.config.preview.ts', import.meta.url))) {
  const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm'
  execFileSync(npm, ['run', 'build:preview'], { cwd: widget, stdio: 'inherit' })
  mkdirSync(new URL('../public/', import.meta.url), { recursive: true })
  copyFileSync(new URL('../../../max-mode-widget/dist/engage-preview.iife.js', import.meta.url), destination)
} else if (!existsSync(destination)) {
  throw new Error('Build the shared Engage preview asset before building the Console.')
}
