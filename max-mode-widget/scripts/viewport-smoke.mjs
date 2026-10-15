import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

const source = readFileSync(new URL('../src/viewport.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const { bindWidgetViewport } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
const view = new EventTarget()
Object.assign(view, { innerWidth: 390, innerHeight: 844 })
const viewport = new EventTarget()
Object.assign(viewport, { width: 390, height: 844, offsetTop: 0, offsetLeft: 0 })
view.visualViewport = viewport
const values = new Map([['--max-mode-viewport-top', '3px']])
const element = { ownerDocument: { defaultView: view }, style: {
  getPropertyValue: (key) => values.get(key) || '',
  setProperty: (key, value) => values.set(key, value),
  removeProperty: (key) => values.delete(key),
} }
const release = bindWidgetViewport(element)
assert.equal(values.get('--max-mode-viewport-height'), '844px')
Object.assign(viewport, { height: 360, offsetTop: 180 })
viewport.dispatchEvent(new Event('resize'))
assert.equal(values.get('--max-mode-viewport-height'), '360px')
assert.equal(values.get('--max-mode-viewport-top'), '180px')
assert.equal(values.get('--max-mode-viewport-bottom'), '304px')
Object.assign(viewport, { offsetTop: 120, offsetLeft: 5, width: 380 })
viewport.dispatchEvent(new Event('scroll'))
assert.equal(values.get('--max-mode-viewport-left'), '5px')
assert.equal(values.get('--max-mode-viewport-right'), '5px')
release()
viewport.dispatchEvent(new Event('resize'))
viewport.dispatchEvent(new Event('scroll'))
view.dispatchEvent(new Event('resize'))
assert.deepEqual([...values], [['--max-mode-viewport-top', '3px']])
view.visualViewport = null
const releaseFallback = bindWidgetViewport(element)
view.innerHeight = 500
view.dispatchEvent(new Event('resize'))
assert.equal(values.get('--max-mode-viewport-height'), '500px')
releaseFallback()
assert.deepEqual([...values], [['--max-mode-viewport-top', '3px']])
console.log('Widget visual-viewport contract smoke passed.')
