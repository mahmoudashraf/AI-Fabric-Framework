import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile, mkdir } from 'node:fs/promises'
import { createServer } from 'node:http'
import { createRequire } from 'node:module'
import { join } from 'node:path'
import ts from 'typescript'
import {
  capabilityCheckActionPresentationConfig, capabilityCheckMapping, withCapabilityCheckPresentation, mountInstallation,
} from '../dist/generic-workspace-experience.esm.js'

const widgetSource = await readFile(new URL('../../../max-mode-widget/src/actionPresentation.ts', import.meta.url), 'utf8')
const widgetCode = ts.transpileModule(widgetSource, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText
const { resolveActionPresentation } = await import(`data:text/javascript;base64,${Buffer.from(widgetCode).toString('base64')}`)
const data = { capabilityId: 'private-capability-id', name: 'Account investigation', status: 'PARTIAL',
  executionRef: 'assist-chain-account@1', observedAt: '2026-10-30T15:00:00Z',
  findings: { secretWorkerOutput: 'private findings' }, evidence: [{ prompt: 'private prompt' }] }

test('real widget projection maps only the four public fields for the exact action ID', () => {
  const config = capabilityCheckActionPresentationConfig()
  const result = resolveActionPresentation({ config, actionName: 'consult_specialist', actionData: data, messageId: 'message-1' })
  assert.deepEqual(result.presentationData, { name: data.name, status: data.status, executionRef: data.executionRef, observedAt: data.observedAt })
  assert.deepEqual(result.resultReferences, [])
  assert.equal(resolveActionPresentation({ config, actionName: 'consult_specialist_extra', actionData: data, messageId: 'message-1' }), null)
  const reused = { ...config, mappings: [capabilityCheckMapping('customer_account_check')] }
  assert.equal(resolveActionPresentation({ config: reused, actionName: 'customer_account_check', actionData: data, messageId: 'message-2' }).presentationData.status, 'PARTIAL')
})

test('mount preserves other injectable renderers and enforces the reserved safe projection', async () => {
  const other = { id: 'customer.other', kind: 'custom-element', elementName: 'customer-other' }
  const mapping = { actionName: 'other_action', rendererId: other.id, schemaVersion: 'v1', projection: {} }
  const previous = { renderers: [other], mappings: [mapping, { actionName: 'consult_specialist', projection: { fields: [{ sourcePath: 'findings' }] } }] }
  const merged = withCapabilityCheckPresentation(previous)
  assert.deepEqual(merged.renderers[0], other)
  assert.deepEqual(merged.mappings[0], mapping)
  assert.equal(merged.mappings.filter(item => item.actionName === 'consult_specialist').length, 1)
  assert.deepEqual(merged.mappings[1], capabilityCheckMapping())
  assert.equal(previous.mappings.length, 2)
  let mounted
  const controller = await mountInstallation({ configuration: { app: { assistantLabel: 'Customer help' } }, widgetConfig: { host: { actionPresentation: previous } },
    deliveryMode: 'embedded', maxMode: { init(value) { mounted = value }, destroy() {} }, refreshAssignment: async () => {} })
  assert.deepEqual(mounted.host.actionPresentation, merged)
  controller.destroy()
})

test('collapsed generic renderer shows actual outcomes, optional metadata, and no worker content', async t => {
  const pack = await readFile(new URL('../dist/generic-workspace-experience.esm.js', import.meta.url))
  const server = createServer((req, res) => {
    if (req.url === '/pack.js' || req.url === '/contract.js') {
      res.writeHead(200, { 'Content-Type': 'text/javascript' }); res.end(req.url === '/pack.js' ? pack : widgetCode); return
    }
    res.writeHead(200, { 'Content-Type': 'text/html' })
    res.end(`<!doctype html><html><body style="margin:0;font:14px system-ui"><main style="max-width:640px;margin:24px;padding:16px"></main><script type="module">
      import { registerCapabilityCheckPresentationElement, capabilityCheckActionPresentationConfig } from '/pack.js';
      import { resolveActionPresentation } from '/contract.js';
      registerCapabilityCheckPresentationElement(); registerCapabilityCheckPresentationElement();
      window.renderCheck = (data, direct = false) => {
        const result = resolveActionPresentation({ config: capabilityCheckActionPresentationConfig(), actionName:'consult_specialist', actionData:data, messageId:'fixture' });
        const element = document.createElement(result.renderer.elementName);
        element.presentation = direct ? { presentationData:data } : result;
        document.querySelector('main').replaceChildren(element);
      };
    </script></body></html>`)
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const { chromium } = createRequire(new URL('../../../Platfrom/ui/package.json', import.meta.url))('playwright')
  const browser = await chromium.launch({ headless: true })
  t.after(async () => { await browser.close(); await new Promise(resolve => server.close(resolve)) })
  const page = await browser.newPage({ viewport: { width: 390, height: 700 } })
  const errors = []; page.on('pageerror', error => errors.push(error.message))
  await page.goto(`http://127.0.0.1:${server.address().port}`)
  await page.waitForFunction(() => typeof window.renderCheck === 'function')
  for (const [status, background, color] of [['PARTIAL', '#ffffff', '#24272d'], ['FAILED', '#17191c', '#e3e5e9']]) {
    await page.evaluate(({ background, color, data }) => {
      document.body.style.background = background; document.body.style.color = color; window.renderCheck(data)
    }, { background, color, data: { ...data, status } })
    const element = page.locator('loomai-capability-check'), details = element.locator('details')
    assert.equal(await details.getAttribute('open'), null)
    assert.equal(await element.locator('.outcome').innerText(), status)
    assert.equal(await element.locator('dl').isVisible(), false)
    await element.locator('summary').click()
    assert.equal(await element.locator('dl').isVisible(), true)
    assert.equal(await element.locator('time').innerText(), data.observedAt)
    assert.equal(await element.locator('code').innerText(), data.executionRef)
    assert.equal(await element.locator('dt').allTextContents().then(labels => labels.join('|')), 'Overall outcome|Observed at|Execution reference')
    const content = await element.evaluate(node => node.shadowRoot.textContent)
    for (const forbidden of ['private-capability-id', 'private findings', 'private prompt', 'secretWorkerOutput']) assert.equal(content.includes(forbidden), false)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    if (process.env.CAPABILITY_CHECK_EVIDENCE_DIR) {
      await mkdir(process.env.CAPABILITY_CHECK_EVIDENCE_DIR, { recursive: true })
      await page.screenshot({ path: join(process.env.CAPABILITY_CHECK_EVIDENCE_DIR, `${status.toLowerCase()}.png`) })
    }
  }
  await page.evaluate(() => window.renderCheck({ name:'<img src=x onerror=alert(1)>', findings:'hidden direct value', status:'UNRECOGNIZED_OUTCOME' }, true))
  assert.equal(await page.locator('loomai-capability-check').locator('img').count(), 0)
  assert.equal(await page.locator('loomai-capability-check').locator('.outcome').innerText(), 'UNRECOGNIZED_OUTCOME')
  assert.equal(await page.locator('loomai-capability-check').evaluate(node => node.shadowRoot.textContent.includes('hidden direct value')), false)
  await page.evaluate(() => window.renderCheck({ name:'Only a capability name' }))
  assert.equal(await page.locator('loomai-capability-check').locator('.outcome').innerText(), 'Not reported')
  assert.equal(await page.locator('loomai-capability-check').locator('time,code').count(), 0)
  assert.deepEqual(errors, [])
})
