import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

async function loadConfig(relative) {
  const source = await readFile(new URL(relative, import.meta.url), 'utf8')
  const exports = {}
  new Function('exports', ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText)(exports)
  return exports.integrationTeam
}

test('Console and website use the same approved public engineer and contact destinations', async () => {
  const ui = await loadConfig('../src/integrationTeam.ts')
  const site = await loadConfig('../../loomai-site/src/data/integrationTeam.ts')
  for (const key of ['name', 'role', 'location', 'bookingUrl', 'phone', 'phoneUrl', 'whatsappUrl']) assert.equal(ui[key], site[key], key)
  assert.equal(ui.name, 'Mo.Elgammal')
  assert.equal(ui.role, 'Founder / AI Enablement Engineer')
  assert.equal(ui.location, 'United Kingdom')
  assert.equal(ui.whatsappUrl, 'https://wa.me/447729946319')
  assert.equal(ui.phoneUrl, 'tel:+447729946319')
  assert.equal(new URL(ui.requestUrl, 'https://console.loomai.pro').href, site.requestUrl)
  const portrait = await readFile(new URL(`../public${ui.portraitUrl}`, import.meta.url))
  assert.ok(portrait.length > 0)
  assert.deepEqual(portrait, await readFile(new URL(`../../loomai-site/public${site.portraitUrl}`, import.meta.url)))
})
