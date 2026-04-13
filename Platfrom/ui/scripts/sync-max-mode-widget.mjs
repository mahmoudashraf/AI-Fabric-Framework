import { execFileSync } from 'node:child_process'
import { copyFileSync, existsSync, mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const uiDir = path.resolve(__dirname, '..')
const repoRoot = path.resolve(uiDir, '..', '..')
const widgetDir = path.join(repoRoot, 'max-mode-widget')
const widgetBundlePath = path.join(widgetDir, 'dist', 'max-mode-widget.iife.js')
const publicDir = path.join(uiDir, 'public')
const targetPath = path.join(publicDir, 'max-mode-widget.iife.js')

function runWidgetBuild() {
  execFileSync('npm', ['run', 'build:iife'], {
    cwd: widgetDir,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  })
}

mkdirSync(publicDir, { recursive: true })
runWidgetBuild()

if (!existsSync(widgetBundlePath)) {
  throw new Error(`Expected widget bundle not found: ${widgetBundlePath}`)
}

copyFileSync(widgetBundlePath, targetPath)
console.log(`Synced Max Mode widget bundle to ${targetPath}`)
