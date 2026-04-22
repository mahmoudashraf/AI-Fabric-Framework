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

function npmCommand() {
  const npmExecPath = process.env.npm_execpath?.trim()
  if (npmExecPath) {
    return {
      file: process.execPath,
      args: [npmExecPath, 'run', 'build:iife'],
    }
  }
  return {
    file: process.platform === 'win32' ? 'npm.cmd' : 'npm',
    args: ['run', 'build:iife'],
  }
}

function runWidgetBuild() {
  const command = npmCommand()
  execFileSync(command.file, command.args, {
    cwd: widgetDir,
    stdio: 'inherit',
  })
}

mkdirSync(publicDir, { recursive: true })

if (existsSync(widgetDir)) {
  runWidgetBuild()
} else if (existsSync(targetPath)) {
  console.log(`Widget source directory not present. Keeping existing ${targetPath}`)
  process.exit(0)
} else {
  throw new Error(`Widget source directory not found: ${widgetDir}`)
}

if (!existsSync(widgetBundlePath)) {
  throw new Error(`Expected widget bundle not found: ${widgetBundlePath}`)
}

copyFileSync(widgetBundlePath, targetPath)
console.log(`Synced Max Mode widget bundle to ${targetPath}`)
