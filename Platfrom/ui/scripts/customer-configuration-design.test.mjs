import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { StaticRouter } from 'react-router-dom'

const require = createRequire(import.meta.url)
async function load(path, dependencies = {}) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText
  const exports = {}
  new Function('require', 'exports', code)(name => name in dependencies ? dependencies[name] : require(name), exports)
  return exports
}
const { createConsoleTheme } = await load('../src/theme/consoleTheme.ts')
const terminology = await load('../src/deploymentTerminology.ts')
let selectedDeploymentId = 'dep/a & b'
const editor = await load('../src/components/customer/CustomerDeploymentAdvanced.tsx', {
  '../../deploymentTerminology': terminology,
  '../../workspace/DeploymentWorkspaceContext': { useDeploymentWorkspace: () => ({
    selectedDeploymentId,
    selectedDeploymentSummary: { name: 'Customer workspace', environment: 'staging', status: 'ACTIVE', activeVersion: 'v12' },
  }) },
})
const surface = await load('../src/components/ConfigurationSurface.tsx', { '../theme/consoleTheme': { createConsoleTheme } })
const render = children => renderToStaticMarkup(React.createElement(StaticRouter, {
  location: '/prompts?deploymentId=dep%2Fa+%26+b&targetProfileId=target%2Fstaging',
}, children))

test('light and dark customer themes match Kit surfaces and keep typography compact', async () => {
  const css = await readFile(new URL('../src/pages/ExperienceKits.css', import.meta.url), 'utf8')
  for (const mode of ['light', 'dark']) {
    const theme = createConsoleTheme(mode)
    for (const color of [theme.palette.background.default, theme.palette.background.paper,
      theme.palette.text.primary, theme.palette.text.secondary, theme.palette.divider,
      theme.palette.primary.main, theme.palette.success.main]) assert.ok(css.includes(color === '#ffffff' ? '#fff' : color), `${mode}: ${color} exists in the Kit palette`)
    assert.equal(theme.shape.borderRadius, 6)
    assert.equal(theme.typography.h4.letterSpacing, 0)
    assert.equal(theme.components.MuiPaper.styleOverrides.root.backgroundImage, 'none')
  }
})

test('all five existing configuration links retain the exact Brain and target scope', () => {
  const links = editor.deploymentEditorLinks('dep/a & b', 'target/staging')
  assert.deepEqual(links.map(item => item.path), ['/revisions', '/actions', '/prompts', '/providers', '/plugins'])
  for (const { href } of links) {
    const url = new URL(href, 'https://console.example.test')
    assert.equal(url.searchParams.get('deploymentId'), 'dep/a & b')
    assert.equal(url.searchParams.get('targetProfileId'), 'target/staging')
  }
})

test('configuration header keeps back navigation, active section and editor children', () => {
  const html = render(React.createElement(editor.CustomerDeploymentAdvanced, null,
    React.createElement('button', { disabled: true }, 'Save prompt draft')))
  assert.match(html, /<h1[^>]*>Customer workspace<\/h1>/)
  assert.equal((html.match(/aria-current="page"/g) ?? []).length, 1)
  assert.match(html, /href="\/prompts\?deploymentId=dep%2Fa\+%26\+b&amp;targetProfileId=target%2Fstaging"/)
  assert.match(html, /resourceKind=deployment&amp;resourceId=dep%2Fa\+%26\+b&amp;deploymentId=/)
  assert.match(html, /<button disabled="">Save prompt draft<\/button>/)
})

test('no-selection state does not render a misleading editor', () => {
  selectedDeploymentId = ''
  const html = render(React.createElement(editor.CustomerDeploymentAdvanced, null, 'Editor content'))
  assert.match(html, /Select a smart brain/)
  assert.ok(!html.includes('Editor content'))
  selectedDeploymentId = 'dep/a & b'
})

test('shared editor skin is opt-in and disclosures retain their controls', () => {
  const content = React.createElement('button', { disabled: true }, 'Apply')
  assert.equal(render(React.createElement(surface.ConfigurationSurface, { enabled: false }, content)), '<button disabled="">Apply</button>')
  const enabled = render(React.createElement(surface.ConfigurationSurface, null,
    React.createElement(surface.ConfigurationDetails, { label: 'Draft & access' }, content)))
  assert.match(enabled, /configuration-surface/)
  assert.match(enabled, /Draft &amp; access/)
  assert.match(enabled, /<button disabled="">Apply<\/button>/)
})
