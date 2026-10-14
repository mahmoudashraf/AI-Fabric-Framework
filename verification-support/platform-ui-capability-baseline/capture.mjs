import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const directory = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(directory, '../..');
const require = createRequire(path.join(root, 'Platfrom/ui/package.json'));
const ts = require('typescript');
const args = process.argv.slice(2);
const compareAt = args.indexOf('--compare');
const outputAt = args.indexOf('--output');
const relative = (file) => path.relative(root, file).split(path.sep).join('/');
const compact = (value) => value.replace(/\s+/g, ' ').trim();
const git = (...parameters) => execFileSync('git', parameters, { cwd: root, encoding: 'utf8' }).trim();
const hash = (file) => createHash('sha256').update(fs.readFileSync(file)).digest('hex');

function parse(file) {
  const content = fs.readFileSync(file, 'utf8');
  const ast = ts.createSourceFile(file, content, ts.ScriptTarget.Latest, true,
    file.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
  assert.equal(ast.parseDiagnostics.length, 0, `Cannot parse ${file}`);
  return ast;
}

function walk(node, visit) {
  visit(node);
  ts.forEachChild(node, (child) => walk(child, visit));
}

function unwrap(node) {
  while (node && (ts.isAsExpression(node) || ts.isParenthesizedExpression(node))) node = node.expression;
  return node;
}

function attributes(node, ast) {
  return Object.fromEntries(node.attributes.properties.filter(ts.isJsxAttribute).map((attribute) => {
    const value = attribute.initializer;
    return [attribute.name.getText(ast), !value ? 'true'
      : ts.isStringLiteral(value) ? value.text
        : compact(value.getText(ast))];
  }));
}

function namedArray(ast, name) {
  let result;
  walk(ast, (node) => {
    if (ts.isVariableDeclaration(node) && node.name.getText(ast) === name) result = unwrap(node.initializer);
  });
  assert.ok(result && ts.isArrayLiteralExpression(result), `Missing ${name}`);
  return result.elements;
}

const app = parse(path.join(root, 'Platfrom/ui/src/App.tsx'));
const imports = new Map();
for (const node of app.statements.filter(ts.isImportDeclaration)) {
  const binding = node.importClause?.namedBindings;
  if (binding && ts.isNamedImports(binding) && node.moduleSpecifier.text.startsWith('./')) {
    for (const element of binding.elements) imports.set(element.name.text, node.moduleSpecifier.text);
  }
}
const routes = [];
walk(app, (node) => {
  if (!(ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) || node.tagName.getText(app) !== 'Route') return;
  const values = attributes(node, app);
  const element = node.attributes.properties.find((item) => ts.isJsxAttribute(item) && item.name.getText(app) === 'element');
  let component;
  if (element?.initializer) walk(element.initializer, (child) => {
    if (!component && (ts.isJsxOpeningElement(child) || ts.isJsxSelfClosingElement(child))) component = child.tagName.getText(app);
  });
  routes.push({ path: values.path, component, line: app.getLineAndCharacterOfPosition(node.getStart()).line + 1,
    source: imports.has(component) ? `Platfrom/ui/src/${imports.get(component).slice(2)}.tsx` : null,
    redirect: component === 'Navigate' ? values.element : null });
});

const shell = parse(path.join(root, 'Platfrom/ui/src/layout/AppShell.tsx'));
const navigation = namedArray(shell, 'navItems').map((node) => Object.fromEntries(
  node.properties.filter(ts.isPropertyAssignment).filter((item) => item.name.getText(shell) !== 'icon')
    .map((item) => [item.name.getText(shell), ts.isStringLiteral(item.initializer) ? item.initializer.text
      : item.initializer.kind === ts.SyntaxKind.TrueKeyword ? true : compact(item.initializer.getText(shell))]),
));
const context = parse(path.join(root, 'Platfrom/ui/src/workspace/DeploymentWorkspaceContext.tsx'));
const deploymentScopedPaths = namedArray(context, 'DEPLOYMENT_WORKSPACE_PATHS').map((node) => node.text);

const uiFiles = [...new Set(git('ls-files', '--cached', '--others', '--exclude-standard', '--', 'Platfrom/ui/src')
  .split('\n').filter((file) => /\.tsx?$/.test(file)))].sort();
const controlsOfInterest = new Set(['Button', 'IconButton', 'MenuItem', 'Tab', 'TextField', 'Select',
  'Checkbox', 'Switch', 'Slider', 'ToggleButton', 'FormControlLabel', 'Dialog', 'DialogTitle',
  'AccordionSummary', 'Tooltip', 'Link', 'ListItemButton']);
const relevantProperties = new Set(['label', 'title', 'aria-label', 'onClick', 'onChange', 'onSubmit',
  'disabled', 'readOnly', 'required', 'select', 'type', 'value', 'checked', 'href', 'to', 'id', 'name']);
const surfaces = uiFiles.filter((file) => !file.endsWith('/api/platformApi.ts')).map((file) => {
  const ast = parse(path.join(root, file));
  const apiImports = new Map();
  for (const node of ast.statements.filter(ts.isImportDeclaration)) {
    const binding = node.importClause?.namedBindings;
    if (node.moduleSpecifier.text.endsWith('/platformApi') && binding && ts.isNamedImports(binding)) {
      for (const element of binding.elements) {
        if (!element.isTypeOnly && !node.importClause.isTypeOnly) apiImports.set(element.name.text, element.propertyName?.text ?? element.name.text);
      }
    }
  }
  const references = new Map();
  const controls = [];
  walk(ast, (node) => {
    if (ts.isIdentifier(node) && apiImports.has(node.text) && !ts.isImportSpecifier(node.parent)) {
      const name = apiImports.get(node.text);
      const lines = references.get(name) ?? new Set();
      lines.add(ast.getLineAndCharacterOfPosition(node.getStart()).line + 1);
      references.set(name, lines);
    }
    if (!(ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node))) return;
    const kind = node.tagName.getText(ast);
    if (!controlsOfInterest.has(kind)) return;
    const props = attributes(node, ast);
    const text = ts.isJsxOpeningElement(node) && ts.isJsxElement(node.parent)
      ? compact(node.parent.children.filter((child) => ts.isJsxText(child) || ts.isJsxExpression(child))
        .map((child) => child.getText(ast)).join(' ')) : '';
    controls.push({ kind, line: ast.getLineAndCharacterOfPosition(node.getStart()).line + 1,
      text, props: Object.fromEntries(Object.entries(props).filter(([name]) => relevantProperties.has(name))) });
  });
  return { file, sha256: hash(path.join(root, file)), apiReferences: [...references].map(([name, lines]) => ({ name, lines: [...lines] })), controls };
});

const apiFile = 'Platfrom/ui/src/api/platformApi.ts';
const apiAst = parse(path.join(root, apiFile));
const exportedApis = apiAst.statements.filter((node) => ts.isFunctionDeclaration(node)
  && node.modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword)).map((node) => {
  const requests = [];
  walk(node, (child) => {
    if (!ts.isCallExpression(child) || !['request', 'fetch'].includes(child.expression.getText(apiAst))) return;
    const options = child.arguments.find(ts.isObjectLiteralExpression);
    const method = options?.properties.find((property) => ts.isPropertyAssignment(property) && property.name.getText(apiAst) === 'method');
    requests.push({ pathExpression: child.arguments[0] ? compact(child.arguments[0].getText(apiAst)) : null,
      method: method ? compact(method.initializer.getText(apiAst)).replace(/["']/g, '') : 'GET' });
  });
  return { name: node.name.text, line: apiAst.getLineAndCharacterOfPosition(node.getStart()).line + 1, requests };
});
assert.ok(exportedApis.some((item) => item.requests.length > 0), 'No HTTP API contracts captured');

const policySources = [
  'Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/PlatformRole.java',
  'Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/web/PlatformAuthController.java',
  'Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/service/PlatformCustomerAccessService.java',
  'Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentAccessService.java',
  'Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/DeploymentPracticalPromotionController.java',
];
const snapshot = {
  schemaVersion: 1, capturedAt: new Date().toISOString(), sourceCommit: git('rev-parse', 'HEAD'),
  branch: git('branch', '--show-current'), evidenceLevel: 'SOURCE_ONLY_NOT_LIVE_VERIFIED',
  dirtyTrackedFilesInCapturedScopes: git('diff', 'HEAD', '--name-only', '--', 'Platfrom/ui/src', ...policySources).split('\n').filter(Boolean),
  navigation, routes, deploymentScopedPaths, surfaces, exportedApis,
  files: [...uiFiles, ...policySources].map((file) => ({ file, sha256: hash(path.join(root, file)) })),
};

if (compareAt >= 0) {
  assert.ok(args[compareAt + 1], '--compare needs a baseline JSON path');
  const baseline = JSON.parse(fs.readFileSync(path.resolve(root, args[compareAt + 1]), 'utf8'));
  const currentFiles = new Map(snapshot.files.map((item) => [item.file, item.sha256]));
  const currentApiNames = new Set(snapshot.exportedApis.map((item) => item.name));
  const currentRoutePaths = new Set(snapshot.routes.map((item) => item.path));
  const currentReferences = new Set(snapshot.surfaces.flatMap((item) => item.apiReferences.map((reference) => reference.name)));
  const baselineReferences = new Set(baseline.surfaces.flatMap((item) => item.apiReferences.map((reference) => reference.name)));
  const baselineFiles = new Set(baseline.files.map((item) => item.file));
  const currentApis = new Map(snapshot.exportedApis.map((item) => [item.name, item.requests]));
  const changedFiles = baseline.files.filter((item) => currentFiles.get(item.file) !== item.sha256).map((item) => item.file);
  const addedFiles = snapshot.files.filter((item) => !baselineFiles.has(item.file)).map((item) => item.file);
  const removedRoutes = baseline.routes.filter((item) => !currentRoutePaths.has(item.path)).map((item) => item.path);
  const removedApiExports = baseline.exportedApis.filter((item) => !currentApiNames.has(item.name)).map((item) => item.name);
  const missingApiReferences = [...baselineReferences].filter((name) => !currentReferences.has(name));
  const changedApiContracts = baseline.exportedApis.filter((item) => currentApis.has(item.name)
    && JSON.stringify(item.requests) !== JSON.stringify(currentApis.get(item.name))).map((item) => item.name);
  const navigationChanged = JSON.stringify(baseline.navigation) !== JSON.stringify(snapshot.navigation);
  const deploymentScopeChanged = JSON.stringify(baseline.deploymentScopedPaths) !== JSON.stringify(snapshot.deploymentScopedPaths);
  console.log(JSON.stringify({ evidenceLevel: snapshot.evidenceLevel, baselineCommit: baseline.sourceCommit,
    currentCommit: snapshot.sourceCommit, removedRoutes, removedApiExports, missingApiReferences,
    changedApiContracts, navigationChanged, deploymentScopeChanged, changedFiles, addedFiles,
    reviewRequired: Boolean(removedRoutes.length || removedApiExports.length || missingApiReferences.length
      || changedApiContracts.length || navigationChanged || deploymentScopeChanged || changedFiles.length || addedFiles.length),
    limitation: 'Static change review only. Component moves, equivalent replacement APIs, authorization, controls, and live behavior require the human capability register and browser tests.' }, null, 2));
} else {
  assert.ok(outputAt >= 0 && args[outputAt + 1], 'Use --output <new-baseline.json>; existing baselines are never overwritten.');
  const output = path.resolve(root, args[outputAt + 1]);
  fs.writeFileSync(output, `${JSON.stringify(snapshot, null, 2)}\n`, { flag: 'wx' });
  console.log(JSON.stringify({ output: relative(output), sourceCommit: snapshot.sourceCommit,
    evidenceLevel: snapshot.evidenceLevel, routes: routes.length, navigationItems: navigation.length,
    sourceFiles: snapshot.files.length, controls: surfaces.reduce((count, surface) => count + surface.controls.length, 0),
    apiExports: exportedApis.length, apiExportsWithHttpContracts: exportedApis.filter((item) => item.requests.length > 0).length,
    dirtyCapturedFiles: snapshot.dirtyTrackedFilesInCapturedScopes }, null, 2));
}
