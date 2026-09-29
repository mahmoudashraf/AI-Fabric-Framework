import { spawn } from 'node:child_process'
import { rm } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const root = path.resolve(__dirname, '..')
const generatedWidget = path.join(root, 'public/vendor/max-mode-widget.iife.js')

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
  await run('npm', ['run', 'prepare:widget'])
  await run('npm', ['exec', '--', 'astro', 'build'])
} finally {
  await rm(generatedWidget, { force: true })
}
