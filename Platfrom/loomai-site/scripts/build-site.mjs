import { spawn } from 'node:child_process'
import { readdir, rm } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const root = path.resolve(__dirname, '..')
const generatedBrowserAssetDirectory = path.join(root, 'public/vendor')

async function cleanGeneratedBrowserAssets() {
  const entries = await readdir(generatedBrowserAssetDirectory).catch(() => [])
  for (const entry of entries) {
    if (
      entry === 'max-mode-widget.iife.js'
      || entry === 'max-mode-widget-manifest.json'
      || /^max-mode-widget\.[a-f0-9]{16}\.iife\.js$/.test(entry)
      || entry === 'dealership-experience.iife.js'
      || entry === 'dealership-experience-manifest.json'
      || /^dealership-experience\.[a-f0-9]{16}\.iife\.js$/.test(entry)
    ) {
      await rm(path.join(generatedBrowserAssetDirectory, entry), { force: true })
    }
  }
}

function run(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: root,
      env: process.env,
      stdio: 'inherit',
    })
    child.once('error', reject)
    child.once('exit', (code, signal) => {
      if (code === 0) resolve()
      else reject(new Error(`${command} ${args.join(' ')} failed (${signal || code}).`))
    })
  })
}

try {
  await run('npm', ['run', 'prepare:browser-assets'])
  await run('npm', ['exec', '--', 'astro', 'build'])
} finally {
  await cleanGeneratedBrowserAssets()
}
