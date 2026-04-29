import fs from 'node:fs'
import path from 'node:path'

const distIndex = path.resolve('dist/index.html')

if (!fs.existsSync(distIndex)) {
  console.error('Partner UI smoke failed: dist/index.html does not exist.')
  process.exit(1)
}

const html = fs.readFileSync(distIndex, 'utf8')
if (!html.includes('type="module"')) {
  console.error('Partner UI smoke failed: no module script found in dist/index.html.')
  process.exit(1)
}

const widgetBundle = path.resolve('dist/max-mode-widget.iife.js')
if (!fs.existsSync(widgetBundle)) {
  console.error('Partner UI smoke failed: Max widget bundle was not copied into dist.')
  process.exit(1)
}

const moduleMatch = html.match(/<script[^>]+type="module"[^>]+src="([^"]+)"/)
if (!moduleMatch) {
  console.error('Partner UI smoke failed: module script path could not be detected.')
  process.exit(1)
}

const modulePath = path.resolve('dist', moduleMatch[1].replace(/^\/+/, ''))
const moduleSource = fs.readFileSync(modulePath, 'utf8')
for (const expectedText of ['Live Max widget test', 'Run smoke query', 'Partner-secured routes']) {
  if (!moduleSource.includes(expectedText)) {
    console.error(`Partner UI smoke failed: missing Partner Max widget text: ${expectedText}`)
    process.exit(1)
  }
}

console.log('Partner UI smoke passed.')
