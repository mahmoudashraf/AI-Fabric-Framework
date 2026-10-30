import { createRequire } from 'node:module'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const require = createRequire(path.join(root, 'Platfrom/ui/package.json'))
const { build } = require('esbuild')
const targets = ['Platfrom/ui/public', 'Platfrom/loomai-site/public', 'Platfrom/partner-ui/public',
  'workspace-apps/standalone-ai-workspace/public', 'product-services/loomai-desk-service/ui/public',
  'product-services/shopify-bridge-service/ui/public', ...process.argv.slice(2)]
const result = await build({ entryPoints: [path.join(root, 'web-analytics/client/entry.mjs')], bundle: true,
  format: 'iife', target: ['es2020'], minify: true, write: false })
const content = result.outputFiles[0].text
for (const target of targets) {
  const file = path.resolve(root, target, 'assets/loomai-ui-visits.js')
  if (process.env.CHECK_UI_VISITS_CLIENT === 'true') {
    if (await readFile(file, 'utf8') !== content) throw new Error('Outdated analytics client: ' + file)
  } else {
    await mkdir(path.dirname(file), { recursive: true })
    await writeFile(file, content)
  }
}
console.log(`${targets.length} identical first-party analytics clients ${process.env.CHECK_UI_VISITS_CLIENT === 'true' ? 'verified' : 'generated'}`)
