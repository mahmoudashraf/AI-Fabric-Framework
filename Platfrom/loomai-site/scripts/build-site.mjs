import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { mkdirSync, renameSync } from 'node:fs'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const root = path.resolve(__dirname, '..')
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

await run('npm', ['exec', '--', 'astro', 'build'])

// Authenticated pages must not exist in the public static resolver's directory.
const protectedRoot = path.join(root, 'protected-dist', 'founder')
mkdirSync(path.join(protectedRoot, 'assets'), { recursive: true })
renameSync(path.join(root, 'dist', 'founder', 'index.html'), path.join(protectedRoot, 'index.html'))
for (const name of ['mahmoud-studio.jpg', 'assistant-openapi-ready.png', 'assistant-saved-actions.png', 'assistant-review-draft.png']) {
  renameSync(path.join(root, 'dist', 'assets', 'profile', name), path.join(protectedRoot, 'assets', name))
}
