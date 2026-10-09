import { createHash } from 'node:crypto'
import { cp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const scriptRoot = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(scriptRoot, '../../..')
const outputArg = process.argv.indexOf('--output')
const outputRoot = path.resolve(
  outputArg >= 0 && process.argv[outputArg + 1]
    ? process.argv[outputArg + 1]
    : path.join(repoRoot, 'Platfrom/backend/target/ai-workspace-assets'),
)

const widgetPackage = await json(path.join(repoRoot, 'max-mode-widget/package.json'))
const packPackage = await json(path.join(repoRoot, 'experience-packs/dealership-experience/package.json'))
const widgetVersion = exactVersion(widgetPackage.version, 'Max Mode widget')
const packVersion = exactVersion(packPackage.version, 'dealership experience pack')

await rm(outputRoot, { recursive: true, force: true })
await mkdir(outputRoot, { recursive: true })

const installer = await publish({
  role: 'installer',
  code: 'workspace-installer',
  version: widgetVersion,
  source: path.join(repoRoot, 'max-mode-widget/dist/ai-workspace-installer.iife.js'),
  relativeDirectory: '',
  outputName: 'install.js',
  requestPath: '/api/public/ai-workspace/install.js',
})
const workspace = await publishHashed({
  role: 'workspace',
  code: 'workspace',
  version: widgetVersion,
  source: path.join(repoRoot, 'max-mode-widget/dist/max-mode-widget.iife.js'),
  requestDirectory: `/api/public/ai-workspace/assets/workspace/${widgetVersion}`,
  relativeDirectory: `assets/workspace/${widgetVersion}`,
  baseName: 'max-mode-widget',
})
const dealership = await publishHashed({
  role: 'experience-pack',
  code: 'dealership',
  version: packVersion,
  source: path.join(repoRoot, 'experience-packs/dealership-experience/dist/dealership-experience.iife.js'),
  requestDirectory: `/api/public/ai-workspace/assets/dealership/${packVersion}`,
  relativeDirectory: `assets/dealership/${packVersion}`,
  baseName: 'dealership-experience',
})

const catalog = {
  schemaVersion: 'loomai-ai-workspace-assets-v1',
  installer,
  workspace,
  experiencePacks: [dealership],
}
await writeFile(path.join(outputRoot, 'catalog.json'), `${JSON.stringify(catalog, null, 2)}\n`, 'utf8')

async function publishHashed(input) {
  const bytes = await requiredBytes(input.source)
  const sha256 = digest('sha256', bytes, 'hex')
  const outputName = `${input.baseName}.${sha256.slice(0, 16)}.iife.js`
  return publish({ ...input, bytes, outputName, requestPath: `${input.requestDirectory}/${outputName}` })
}

async function publish(input) {
  const bytes = input.bytes || await requiredBytes(input.source)
  const targetDirectory = path.join(outputRoot, input.relativeDirectory)
  await mkdir(targetDirectory, { recursive: true })
  const target = path.join(targetDirectory, input.outputName)
  await cp(input.source, target)
  const relativeFile = path.relative(outputRoot, target).split(path.sep).join('/')
  return {
    role: input.role,
    code: input.code,
    version: input.version,
    requestPath: input.requestPath,
    file: relativeFile,
    sha256: digest('sha256', bytes, 'hex'),
    integrity: `sha384-${digest('sha384', bytes, 'base64')}`,
    size: bytes.length,
  }
}

async function requiredBytes(file) {
  const bytes = await readFile(file).catch(() => null)
  if (!bytes || bytes.length < 100) throw new Error(`Required AI Workspace build output is missing or empty: ${file}`)
  return bytes
}

async function json(file) {
  return JSON.parse(await readFile(file, 'utf8'))
}

function exactVersion(value, label) {
  if (!/^\d+\.\d+\.\d+$/.test(value || '')) throw new Error(`${label} requires an exact semver version.`)
  return value
}

function digest(algorithm, bytes, encoding) {
  return createHash(algorithm).update(bytes).digest(encoding)
}
