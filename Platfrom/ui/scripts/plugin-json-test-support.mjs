import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const helper = await readFile(new URL('../src/lib/losslessJson.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(helper.replace("'lossless-json'", JSON.stringify(import.meta.resolve('lossless-json'))), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const moduleUrl = `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`

// Data-URL unit harnesses do not run Vite's dependency resolver.
export function withPluginJsonSupport(source) {
  return source.replace("'../lib/losslessJson'", JSON.stringify(moduleUrl))
}
