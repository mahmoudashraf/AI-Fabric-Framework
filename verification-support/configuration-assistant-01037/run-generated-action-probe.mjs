import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const args = process.argv.slice(2)
const option = name => { const index = args.indexOf(name); return index < 0 ? undefined : args[index + 1] }
const negative = option('--negative')
assert.ok(negative, 'Provide --negative with the untouched first hosted evidence JSON or action catalogue')
const synthetic = args.includes('--self-test')
assert.ok(!synthetic || !option('--candidate'), 'Self-test must not be represented as hosted candidate evidence')
const runtime = resolve(option('--runtime-jar') ?? resolve(here, '../../ai-infrastructure-module/ai-fabric-runtime/target/ai-fabric-runtime-0.1.0-preview.jar'))
const connector = resolve(option('--connector-jar') ?? resolve(here, '../../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/target/ai-infrastructure-generic-rest-connector-0.1.0-preview.jar'))
const temporary = mkdtempSync(resolve(tmpdir(), 'loomai-generated-action-'))

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', timeout: 60000, maxBuffer: 8 * 1024 * 1024, ...options })
  if (result.error) throw result.error
  if (result.status !== 0) throw new Error(`${command} failed (${result.status}):\n${result.stdout}\n${result.stderr}`)
  return result.stdout
}

async function checksum(path) {
  const hash = createHash('sha256')
  for await (const chunk of createReadStream(path)) hash.update(chunk)
  return hash.digest('hex')
}

try {
  const entries = run('unzip', ['-Z', '-1', runtime]).split('\n')
  const libraries = entries.filter(entry => /^BOOT-INF\/lib\/ai-fabric-.*-0\./.test(entry))
  assert.ok(libraries.length && libraries.every(entry => entry.endsWith('-0.8.46.jar')), 'Runtime must package only released AI Fabric 0.8.46 libraries')
  const runtimeLibs = resolve(temporary, 'runtime-libs')
  const connectorClasses = resolve(temporary, 'connector/BOOT-INF/classes')
  run('unzip', ['-q', '-j', runtime, 'BOOT-INF/lib/*', '-d', runtimeLibs])
  run('unzip', ['-q', connector, 'BOOT-INF/classes/*', '-d', resolve(temporary, 'connector')])
  const classpath = `${temporary}:${connectorClasses}:${runtimeLibs}/*`
  const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin/java') : 'java'
  const javac = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin/javac') : 'javac'
  run(javac, ['--release', '21', '-proc:none', '--class-path', classpath, '-d', temporary, resolve(here, 'GeneratedActionLoadProbe.java')])
  let candidate = option('--candidate')
  if (synthetic) {
    candidate = resolve(temporary, 'synthetic-harness-candidate.json')
    writeFileSync(candidate, JSON.stringify({ actionsConfig: { actions: [
      { name: 'existing_unrouted', accessMode: 'READ', params: [] },
      { name: 'search_records', accessMode: 'READ', resultPresentationHint: 'LIST', params: [
        { name: 'q', type: 'STRING', required: true, visibility: 'USER' },
        { name: 'limit', type: 'INTEGER', min: 1, max: 20, visibility: 'USER' },
      ] },
      { name: 'register_interest', accessMode: 'WRITE_ONLY', requiresConfirmation: true, resultPresentationHint: 'STATUS', params: [] },
    ] }, routingConfig: { actions: { search_records: { method: 'GET', path: '/v1/records',
      request: { query: { q: '{{params.q}}', limit: '{{params.limit}}' } },
      response: { 'success-http-status': [200], result: { records: '{{body.records}}' } },
    } } } }), { mode: 0o600 })
  }
  const proofArgs = [resolve(negative)]
  if (candidate) proofArgs.push(resolve(candidate), resolve(here, 'local-read-operator-routing.json'), option('--result-pointer') ?? '/records')
  const output = run(java, ['--class-path', classpath, 'GeneratedActionLoadProbe', ...proofArgs])
  const line = output.split('\n').find(line => line.startsWith('PROBE_RESULT '))
  assert.ok(line, 'The canonical probe did not return a result')
  const proof = JSON.parse(line.slice('PROBE_RESULT '.length))
  const report = {
    mode: synthetic ? 'SYNTHETIC_POSITIVE_HARNESS_ONLY' : candidate ? 'HOSTED_CANDIDATE_LOCAL_MOCK_ONLY' : 'ACTUAL_NEGATIVE_CONTROL_ONLY',
    runtimeJar: runtime, runtimeSha256: await checksum(runtime),
    connectorJar: connector, connectorSha256: await checksum(connector),
    frameworkLibraries: libraries, connectorFrameworkLibrariesLoaded: false,
    negativeInput: resolve(negative), negativeSha256: await checksum(resolve(negative)),
    ...(candidate ? { candidateInput: synthetic ? '(temporary synthetic fixture)' : resolve(candidate), candidateSha256: await checksum(resolve(candidate)) } : {}),
    ...proof,
  }
  if (option('--output')) writeFileSync(resolve(option('--output')), JSON.stringify(report, null, 2) + '\n', { mode: 0o600, flag: 'wx' })
  console.log(JSON.stringify(report, null, 2))
} finally {
  rmSync(temporary, { recursive: true, force: true })
}
