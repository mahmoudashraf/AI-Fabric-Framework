import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

test('Open Console keeps branded styling and existing routes across public surfaces', { timeout: 120_000 }, async t => {
  const output = process.env.CONSOLE_ENTRY_OUTPUT || '/private/tmp/loomai-console-entry'
  await mkdir(output, { recursive: true })
  const entry = '\0console-entry-fixture'
  const server = await createServer({
    root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, envFile: false, logLevel: 'error',
    esbuild: { jsx: 'automatic' }, cacheDir: `${output}/vite-cache`,
    resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 0 },
    plugins: [{ name: 'console-entry-fixture', resolveId(id) { if (id === '/console-entry-fixture.jsx') return entry },
      load(id) {
        if (id !== entry) return
        return `import React from 'react';import{createRoot}from'react-dom/client';import{BrowserRouter}from'react-router-dom';
          import{CustomerConsoleTheme}from'/src/theme/CustomerConsoleTheme.tsx';import{useTheme}from'@mui/material';
          import{KitPublicHeader}from'/src/components/kits/KitIdentity.tsx';
          import{PublicProductsPage}from'/src/pages/PublicProductsPage.tsx';
          import'/src/pages/ExperienceKits.css';import'/src/components/kits/KitCatalogueCards.css';
          const q=new URLSearchParams(location.search),authenticated=q.get('authenticated')!=='false',surface=q.get('surface');
          function Kits(){const theme=useTheme();return React.createElement('div',{className:'ek-page ek-public-page','data-kit-theme':theme.palette.mode},
            React.createElement('div',{className:'ek-public-container'},React.createElement(KitPublicHeader,
              {authenticated,selectedCode:'ai-customer-experience',consolePath:'/kits',catalogue:true})))}
          const view=surface==='kits'?React.createElement(CustomerConsoleTheme,null,React.createElement(Kits)):
            React.createElement(PublicProductsPage,{authenticated,consolePath:'/home',product:surface==='detail'?'platform':undefined});
          createRoot(document.getElementById('root')).render(React.createElement(BrowserRouter,null,view));`
      }, configureServer(vite) { vite.middlewares.use((req, res, next) => {
        if (!new URL(req.url, 'http://fixture').pathname.startsWith('/entry-test')) return next()
        res.setHeader('Content-Type', 'text/html')
        res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><div id="root"></div><script type="module" src="/console-entry-fixture.jsx"></script></body></html>')
      }) },
    }],
  })
  t.after(() => server.close())
  await server.listen()
  const browser = await chromium.launch({ headless: true })
  t.after(() => browser.close())
  const page = await browser.newPage()
  const errors = [], observations = []
  page.on('pageerror', error => errors.push(error.message))
  const base = server.resolvedUrls.local[0]
  for (const mode of ['dark', 'light']) {
    await page.addInitScript(mode => localStorage.setItem('loomai-customer-console-color-scheme', mode), mode)
    for (const surface of ['kits', 'detail', 'products']) {
      for (const width of [1440, 390, 320]) {
        await page.setViewportSize({ width, height: 1000 })
        await page.goto(`${base}entry-test?surface=${surface}`)
        const button = page.getByRole('link', { name: 'Open Console', exact: true })
        await button.waitFor()
        assert.equal(await button.getAttribute('href'), surface === 'kits' ? '/kits' : '/home')
        assert.equal(await button.locator('[data-testid="NorthEastRoundedIcon"]').count(), 1)
        const state = await button.evaluate(element => {
          const style = getComputedStyle(element), bounds = element.getBoundingClientRect()
          return { background: style.backgroundImage, color: style.color, radius: style.borderRadius, fontSize: style.fontSize,
            height: bounds.height, left: bounds.left, right: bounds.right, viewport: innerWidth,
            scrollWidth: document.documentElement.scrollWidth, textWidth: element.scrollWidth, width: element.clientWidth }
        })
        assert.match(state.background, /linear-gradient/)
        assert.match(state.background, mode === 'dark' ? /8, 125, 186/ : /4, 199, 238/)
        assert.equal(state.color, 'rgb(255, 255, 255)')
        assert.equal(state.radius, '8px')
        assert.equal(state.fontSize, '16px')
        assert.ok(state.height >= 44 && state.left >= 0 && state.right <= width, JSON.stringify({ surface, width, state }))
        assert.ok(state.scrollWidth <= width && state.textWidth <= state.width + 1, JSON.stringify({ surface, width, state }))
        await button.hover()
        assert.match(await button.evaluate(element => getComputedStyle(element).backgroundImage), /linear-gradient/)
        assert.equal(await button.evaluate(element => getComputedStyle(element).textDecorationLine), 'none')
        observations.push({ mode, surface, width, ...state })
        if (surface === 'kits') await page.screenshot({ path: `${output}/${surface}-${mode}-${width}.png` })
      }
      await page.goto(`${base}entry-test?surface=${surface}&authenticated=false`)
      assert.equal(await page.getByRole('link', { name: 'Open Console', exact: true }).count(), 0)
      assert.equal(await page.getByRole('link', { name: 'Sign in', exact: true }).getAttribute('href'),
        surface === 'kits' ? '/sign-in?kit=ai-customer-experience' : '/sign-in?product=platform')
    }
  }
  await page.goto(`${base}entry-test?surface=kits`)
  await page.getByRole('link', { name: 'Open Console', exact: true }).click()
  await page.waitForURL('**/kits')
  assert.deepEqual(errors, [])
  await writeFile(`${output}/report.json`, JSON.stringify({ observations, errors }, null, 2))
})
