import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, mkdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const entry = '\0engage-style-presets-fixture.jsx'
const evidence = process.env.ENGAGE_STYLE_EVIDENCE_DIR || join(tmpdir(), 'loomai-engage-style-presets-ui')
let vite, browser, cache, base
before(async () => {
  cache = await mkdtemp(join(tmpdir(), 'engage-style-presets-'))
  await mkdir(evidence, { recursive: true })
  vite = await createServer({ root, configFile: false, envFile: false, cacheDir: cache, logLevel: 'error',
    esbuild: { jsx: 'automatic' }, resolve: { dedupe: ['react', 'react-dom'] },
    server: { host: '127.0.0.1', port: 0, hmr: false, fs: { allow: [fileURLToPath(new URL('../../../', import.meta.url))] } },
    plugins: [{ name: 'engage-style-presets-fixture',
      resolveId(id) { if (id === '/style-presets-fixture.jsx') return entry },
      load(id) {
        if (id !== entry) return
        return `import React,{useEffect,useState}from'react';import{createRoot}from'react-dom/client';
          import{ThemeProvider,createTheme,CssBaseline,Box}from'@mui/material';
          import{EngageAppearanceFields}from'/src/components/customer/EngageAppearanceFields.tsx';
          import{EngageAppearancePreview}from'/src/components/customer/EngageAppearancePreview.tsx';
          import{readWorkspaceAppearance,applyWorkspaceAppearance}from'/src/pages/aiWorkspaceAppearance.ts';
          import{buildWorkspacePreviewConfiguration}from'/src/components/customer/buildWorkspacePreviewConfiguration.ts';
          const config={app:{assistantLabel:'Northfield AI'},theme:{primaryColor:'#147d64',darkMode:'auto',fontFamily:'Custom font',borderRadius:'17px'},
            toolGroups:{default:{label:'Browse',tools:[{label:'Find',query:'Original query'}]}},
            apiConfig:{chatBaseUrl:'https://runtime.example.test'},auth:{policy:'protected'},capabilities:{handover:true},
            copy:{welcomeMessage:'Welcome',placeholder:'Ask us'}};
          if(new URLSearchParams(location.search).has('unbranded'))config.theme={darkMode:'auto'};
          const capabilities={history:true,attachments:true,currentPage:true,sources:true,authentication:{signInAvailable:true},
            handover:{enabled:true,runtimeConfigured:true,userCanCancelPending:true,userCanResumeAi:true}};
          function Fixture(){const[value,setValue]=useState(()=>readWorkspaceAppearance(config,'generic-workspace'));
            useEffect(()=>{window.styleFixture={value,output:applyWorkspaceAppearance(config,'generic-workspace',value),
              preview:buildWorkspacePreviewConfiguration({configuration:config,packCode:'generic-workspace',interactionPolicy:'public-with-protected-capabilities',capabilities,appearance:value})}},[value]);
            const editor=React.createElement(EngageAppearanceFields,{value,onChange:setValue,onReset:()=>setValue(readWorkspaceAppearance(config,'generic-workspace')),error:null});
            if(!new URLSearchParams(location.search).has('preview'))return editor;
            return React.createElement(Box,{sx:{display:'grid',gridTemplateColumns:'minmax(0,1fr) minmax(0,1fr)',gap:3}},editor,
              React.createElement(EngageAppearancePreview,{appearance:value,appName:'Example app',deliveryMode:'standalone',configuration:config,
                packCode:'generic-workspace',interactionPolicy:'public-with-protected-capabilities',capabilities,showBrandingError:false}))}
          const dark=new URLSearchParams(location.search).has('dark');const theme=createTheme({palette:{mode:dark?'dark':'light'},shape:{borderRadius:8}});
          createRoot(document.getElementById('root')).render(React.createElement(ThemeProvider,{theme},React.createElement(CssBaseline),
            React.createElement(Box,{sx:{p:2,maxWidth:new URLSearchParams(location.search).has('preview')?1280:560,mx:'auto'}},React.createElement(Fixture))));`
      },
      configureServer(server) { server.middlewares.use((req, res, next) => {
        if (new URL(req.url, 'http://fixture.local').pathname !== '/style-presets-fixture') return next()
        res.setHeader('Content-Type', 'text/html')
        res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script type="module" src="/style-presets-fixture.jsx"></script></body></html>')
      }) },
    }],
  })
  await vite.listen(); base = vite.resolvedUrls.local[0]
  browser = await chromium.launch({ headless: true })
})
after(async () => { await browser?.close(); await vite?.close(); if (cache) await rm(cache, { recursive: true, force: true }) })

async function fixture(t, options = {}) {
  const errors = [], remote = []
  const page = await browser.newPage({ viewport: options.viewport || { width: 1280, height: 1000 } })
  t.after(() => page.close()); page.setDefaultTimeout(15_000)
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  page.on('request', request => { if (!request.url().startsWith(base)) remote.push(request.url()) })
  const query = new URLSearchParams()
  if (options.dark) query.set('dark', '')
  if (options.preview) query.set('preview', '')
  if (options.unbranded) query.set('unbranded', '')
  await page.goto(`${base}style-presets-fixture?${query}`)
  try { await page.getByRole('button', { name: 'Choose workspace style', exact: true }).waitFor() }
  catch (error) { throw new Error(`${error.message}\n${errors.join('\n')}\n${(await page.locator('body').innerText()).slice(0, 2000)}`) }
  await page.waitForFunction(() => Boolean(window.styleFixture))
  return { page, errors, remote }
}

test('compact editor opens thirteen descriptive styles with Current first and native keyboard selection', async t => {
  const { page, errors, remote } = await fixture(t)
  assert.equal(await page.getByRole('radio').count(), 0, 'The closed editor stays compact')
  assert.equal(await page.locator('[data-selected-style]').getAttribute('data-selected-style'), 'current')
  await page.getByRole('button', { name: 'Choose workspace style', exact: true }).click()
  const gallery = page.getByRole('dialog', { name: 'Choose workspace style', exact: true })
  await gallery.waitFor()
  assert.equal(await gallery.getByRole('radio').count(), 13)
  assert.equal(await gallery.getByRole('radio').first().getAttribute('value'), 'current')
  for (const radio of await gallery.getByRole('radio').all()) {
    assert.ok(await radio.getAttribute('aria-labelledby'))
    const descriptionId = await radio.getAttribute('aria-describedby')
    assert.ok(await page.locator(`[id="${descriptionId}"]`).innerText())
  }
  await gallery.getByRole('radio', { name: 'Current', exact: true }).focus()
  await page.keyboard.press('ArrowRight')
  assert.equal(await gallery.getByRole('radio', { name: 'Clarity', exact: true }).isChecked(), true)
  await page.screenshot({ path: join(evidence, 'gallery-light-desktop.png'), animations: 'disabled' })
  await page.keyboard.press('Escape')
  await gallery.waitFor({ state: 'hidden' })
  assert.equal(await page.getByRole('button', { name: 'Choose workspace style', exact: true }).evaluate(element => element === document.activeElement), true)
  assert.equal(await page.evaluate(() => window.styleFixture.value.stylePreset), 'clarity')
  assert.deepEqual(errors, []); assert.deepEqual(remote, [])
})

test('all preset selections change only appearance, update the unsaved preview, and Current restores existing settings', async t => {
  const { page, errors, remote } = await fixture(t)
  const initial = await page.evaluate(() => window.styleFixture.output)
  await page.getByRole('textbox', { name: 'Assistant name', exact: true }).fill('My own assistant')
  await page.getByRole('button', { name: 'Choose workspace style', exact: true }).click()
  const gallery = page.getByRole('dialog')
  const values = await gallery.getByRole('radio').evaluateAll(radios => radios.map(radio => ({ id: radio.value, label: radio.labels[0].querySelector('[id$="-label"]').textContent })))
  for (const { id, label } of values) {
    await gallery.getByRole('radio', { name: label, exact: true }).check()
    await page.waitForFunction(id => window.styleFixture.value.stylePreset === id, id)
    const state = await page.evaluate(() => window.styleFixture)
    assert.equal(state.preview.theme.stylePreset, id)
    assert.equal(state.preview.theme.primaryColor, '#147d64')
    assert.equal(state.preview.theme.darkMode, 'auto')
    assert.deepEqual(state.output, { ...initial, app: { ...initial.app, assistantLabel: 'My own assistant' },
      theme: id === 'current' ? initial.theme : { ...initial.theme, stylePreset: id } })
  }
  await gallery.getByRole('radio', { name: 'Current', exact: true }).check()
  await gallery.getByRole('button', { name: 'Done', exact: true }).click()
  assert.equal(await page.getByRole('textbox', { name: 'Assistant name', exact: true }).inputValue(), 'My own assistant')
  await page.getByRole('button', { name: 'Reset changes', exact: true }).click()
  assert.deepEqual(await page.evaluate(() => window.styleFixture.output), initial)
  assert.equal(await page.locator('[data-selected-style]').getAttribute('data-selected-style'), 'current')
  assert.deepEqual(errors, []); assert.deepEqual(remote, [])
})

test('dark mobile gallery fits, scrolls to the final style, and leaves the editor usable', async t => {
  const { page, errors, remote } = await fixture(t, { dark: true, viewport: { width: 390, height: 844 } })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
  await page.getByRole('button', { name: 'Choose workspace style', exact: true }).click()
  const gallery = page.getByRole('dialog')
  await gallery.waitFor()
  assert.equal(await gallery.evaluate(element => element.scrollWidth <= element.clientWidth), true)
  await page.screenshot({ path: join(evidence, 'gallery-dark-mobile.png'), animations: 'disabled' })
  await gallery.getByRole('radio', { name: 'Human', exact: true }).check()
  await gallery.getByRole('button', { name: 'Done', exact: true }).click()
  await page.getByRole('textbox', { name: 'Welcome message', exact: true }).fill('A personal welcome')
  assert.equal(await page.evaluate(() => window.styleFixture.value.stylePreset), 'human')
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
  await page.screenshot({ path: join(evidence, 'editor-dark-mobile.png'), fullPage: true, animations: 'disabled' })
  assert.deepEqual(errors, []); assert.deepEqual(remote, [])
})

test('real isolated customer preview applies unsaved styles and bundled fonts without resetting the draft', async t => {
  const { page, errors, remote } = await fixture(t, { preview: true, viewport: { width: 1440, height: 1000 } })
  const frame = page.frameLocator('iframe[title="Engage customer appearance preview"]')
  const root = frame.locator('.max-mode-widget-root')
  await root.locator(':scope[data-mxw-style="current"]').waitFor()
  const input = frame.getByPlaceholder('Ask us', { exact: true })
  await input.fill('Keep my draft while trying styles')
  await page.getByRole('button', { name: 'Choose workspace style', exact: true }).click()
  await page.getByRole('radio', { name: 'Editorial', exact: true }).check()
  await page.getByRole('button', { name: 'Done', exact: true }).click()
  await root.locator(':scope[data-mxw-style="editorial"]').waitFor()
  assert.equal(await input.inputValue(), 'Keep my draft while trying styles')
  const fonts = await root.evaluate(async element => {
    await element.ownerDocument.fonts.ready
    return Array.from(element.ownerDocument.fonts).map(font => ({ family: font.family, status: font.status }))
  })
  assert(fonts.some(font => font.family.includes('LoomAI Editorial') && font.status === 'loaded'), JSON.stringify(fonts))
  await page.screenshot({ path: join(evidence, 'editor-and-real-preview.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: 'Choose workspace style', exact: true }).click()
  await page.getByRole('radio', { name: 'Current', exact: true }).check()
  await page.getByRole('button', { name: 'Done', exact: true }).click()
  await root.locator(':scope[data-mxw-style="current"]').waitFor()
  assert.equal(await input.inputValue(), 'Keep my draft while trying styles')
  assert.deepEqual(errors, []); assert.deepEqual(remote, [])
})

test('unbranded preset selection previews its own typography and colour, then Current restores original defaults', async t => {
  const { page, errors, remote } = await fixture(t, { preview: true, unbranded: true, viewport: { width: 1440, height: 1000 } })
  const initial = await page.evaluate(() => window.styleFixture.output)
  const frame = page.frameLocator('iframe[title="Engage customer appearance preview"]')
  await frame.locator('.max-mode-widget-root[data-mxw-style="current"]').waitFor()
  const color = page.getByLabel('Brand colour', { exact: true })
  assert.equal(await color.inputValue(), '#155eef')
  await page.getByRole('button', { name: 'Choose workspace style', exact: true }).click()
  await page.getByRole('radio', { name: 'Obsidian', exact: true }).check()
  await page.getByRole('button', { name: 'Done', exact: true }).click()
  const root = frame.locator('.max-mode-widget-root[data-mxw-style="obsidian"]')
  await root.waitFor()
  assert.equal(await color.inputValue(), '#78602d')
  assert.match(await root.evaluate(element => element.style.getPropertyValue('--mxw-style-display-font')), /LoomAI Editorial/)
  const selected = await page.evaluate(() => window.styleFixture)
  assert.deepEqual(selected.output.theme, { darkMode: 'auto', stylePreset: 'obsidian' })
  assert.equal(selected.preview.theme.fontFamily, '')
  assert.equal(selected.preview.theme.borderRadius, '3px')
  await page.getByRole('button', { name: 'Choose workspace style', exact: true }).click()
  await page.getByRole('radio', { name: 'Current', exact: true }).check()
  await page.getByRole('button', { name: 'Done', exact: true }).click()
  await frame.locator('.max-mode-widget-root[data-mxw-style="current"]').waitFor()
  assert.equal(await color.inputValue(), '#155eef')
  assert.deepEqual(await page.evaluate(() => window.styleFixture.output), initial)
  assert.deepEqual(errors, []); assert.deepEqual(remote, [])
})

test('Console Docker stages include the canonical style module and every preview pack', async () => {
  const { readFile } = await import('node:fs/promises')
  const dockerfile = await readFile(new URL('../Dockerfile', import.meta.url), 'utf8')
  assert.match(dockerfile, /COPY max-mode-widget\/src\/stylePresets\.ts \/workspace\/max-mode-widget\/src\/stylePresets\.ts/)
  assert.match(dockerfile, /COPY max-mode-widget\/src\/theme\.ts \/workspace\/max-mode-widget\/src\/theme\.ts/)
  assert.match(dockerfile, /COPY max-mode-widget\/src\/installer\/isolatedHost\.ts \/workspace\/max-mode-widget\/src\/installer\/isolatedHost\.ts/)
  assert.equal(dockerfile.match(/COPY experience-packs\/support-experience\/src /g)?.length, 2)
})
