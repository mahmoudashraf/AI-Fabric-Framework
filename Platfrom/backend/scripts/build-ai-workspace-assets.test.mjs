import { createHash } from 'node:crypto'
import { cp, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import assert from 'node:assert/strict'

const script = fileURLToPath(new URL('./build-ai-workspace-assets.mjs', import.meta.url))

async function fixture(run) {
  const root = await mkdtemp(path.join(os.tmpdir(), 'support-assets-'))
  try {
    const location = path.join(root, 'Platfrom/backend/scripts/build-ai-workspace-assets.mjs')
    await mkdir(path.dirname(location), { recursive: true })
    await cp(script, location)
    const packages = [
      ['max-mode-widget', ['max-mode-widget.iife.js', 'ai-workspace-installer.iife.js']],
      ['experience-packs/dealership-experience', ['dealership-experience.iife.js']],
      ['experience-packs/generic-workspace-experience', ['generic-workspace-experience.iife.js']],
      ['experience-packs/support-experience', ['support-experience.iife.js']],
    ]
    for (const [directory, files] of packages) {
      await mkdir(path.join(root, directory, 'dist'), { recursive: true })
      await writeFile(path.join(root, directory, 'package.json'), JSON.stringify({ version: '1.0.0' }))
      for (const file of files) await writeFile(path.join(root, directory, 'dist', file), `/* ${file} */\n${'void 0;\n'.repeat(30)}`)
    }
    const output = path.join(root, 'out')
    await run({ root, output, build: () => spawnSync(process.execPath, [location, '--output', output], { encoding: 'utf8' }) })
  } finally {
    await rm(root, { recursive: true, force: true })
  }
}

test('publishes support alongside existing packs with immutable hash, size and SRI', () => fixture(async ({ output, build }) => {
  const result = build()
  assert.equal(result.status, 0, result.stderr)
  const catalog = JSON.parse(await readFile(path.join(output, 'catalog.json'), 'utf8'))
  assert.deepEqual(catalog.experiencePacks.map(pack => pack.code), ['generic-workspace', 'dealership', 'support'])
  const support = catalog.experiencePacks.find(pack => pack.code === 'support')
  const bytes = await readFile(path.join(output, support.file))
  assert.equal(support.sha256, createHash('sha256').update(bytes).digest('hex'))
  assert.equal(support.integrity, `sha384-${createHash('sha384').update(bytes).digest('base64')}`)
  assert.equal(support.size, bytes.length)
  assert.match(support.requestPath, /^\/api\/public\/ai-workspace\/assets\/support\/1\.0\.0\/support-experience\.[a-f0-9]{16}\.iife\.js$/)
}))

test('fails packaging rather than publishing a catalogue with missing support bytes', () => fixture(async ({ root, output, build }) => {
  await rm(path.join(root, 'experience-packs/support-experience/dist/support-experience.iife.js'))
  const result = build()
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /Required AI Workspace build output is missing or empty/)
  await assert.rejects(readFile(path.join(output, 'catalog.json')), { code: 'ENOENT' })
}))
