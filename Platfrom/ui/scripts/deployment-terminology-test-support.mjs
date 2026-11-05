import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/deploymentTerminology.ts', import.meta.url), 'utf8')

export function deploymentTerminologySource(mode) {
  return mode ? source.replace(": DeploymentTerminologyMode = 'smart-brain'", `: DeploymentTerminologyMode = '${mode}'`) : source
}

// Direct-source fixtures have no bundler. Inline the real UI-only module in both
// ESM/data-URL and CommonJS fixtures, retaining the same configuration switch.
export function withDeploymentTerminology(source, mode) {
  const ast = ts.createSourceFile('fixture.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  const imports = ast.statements.filter(node => ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier)
    && node.moduleSpecifier.text.endsWith('/deploymentTerminology'))
  if (!imports.length) return source
  let body = source
  for (const node of imports.reverse()) body = body.slice(0, node.getFullStart()) + body.slice(node.end)
  return `${deploymentTerminologySource(mode).replace(/^export /gm, '')}\n${body}`
}
