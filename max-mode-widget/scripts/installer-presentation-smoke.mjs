import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'
const source = readFileSync(new URL('../src/installer/presentation.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const { resolveInstallationPresentation: resolve } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
const container = {}, body = {}, html = {}
const document = { body, documentElement: html, getElementById: id => id === 'support' ? container : null }
assert.deepEqual(resolve({ dataset: {} }, 'embedded', document), { mode: 'widget' })
assert.deepEqual(resolve({ dataset: {} }, 'standalone', document), { mode: 'standalone' })
assert.deepEqual(resolve({ dataset: { presentation: 'page', container: '#support' } }, 'embedded', document), { mode: 'page', container, containerSelector: '#support' })
for (const dataset of [{ presentation: 'fullscreen' }, { presentation: 'page' }, { presentation: 'page', container: '#missing' },
  { presentation: 'page', container: 'body' }, { presentation: 'page', container: '#support,body' }, { container: '#support' }]) {
  assert.throws(() => resolve({ dataset }, 'embedded', document))
}
console.log('Installer presentation smoke passed: bounded mode/selector, local target, missing-target failure without overlay.')
