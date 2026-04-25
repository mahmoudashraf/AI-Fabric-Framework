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

console.log('Partner UI smoke passed.')
