import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const helper = await readFile(new URL('../src/auth/customerSession.ts', import.meta.url), 'utf8')
const helperAst = ts.createSourceFile('customerSession.ts', helper, ts.ScriptTarget.Latest, true)
const declarations = helperAst.statements.filter(ts.isFunctionDeclaration).map(node => node.getText(helperAst).replace(/^export /, '')).join('\n')

// Pure-function tests load data URLs or CommonJS fragments without a bundler.
// Include the real shared auth predicate rather than duplicating it in fixtures.
export function withCustomerSessionSupport(source) {
  const ast = ts.createSourceFile('fixture.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  const imports = ast.statements.filter(node => ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier)
    && node.moduleSpecifier.text.endsWith('/auth/customerSession'))
  let body = source
  for (const node of imports.reverse()) body = body.slice(0, node.getFullStart()) + body.slice(node.end)
  return `${declarations}\n${body}`
}
