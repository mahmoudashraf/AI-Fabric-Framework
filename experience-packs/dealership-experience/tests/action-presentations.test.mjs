import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile, mkdir } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { join } from 'node:path'
import ts from 'typescript'
import { dealershipActionPresentationConfig } from '../dist/dealership-experience.esm.js'

const widgetSource = await readFile(new URL('../../../max-mode-widget/src/actionPresentation.ts', import.meta.url), 'utf8')
const widgetCode = ts.transpileModule(widgetSource, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const { resolveActionPresentation } = await import(`data:text/javascript;base64,${Buffer.from(widgetCode).toString('base64')}`)
const vehicle = {
  stockId: 'STOCK-42', make: 'Example', model: 'City EV', year: 2025,
  priceGbp: 24500, mileage: 4200, fuelType: 'Electric', transmission: 'Automatic',
  bodyType: 'Hatchback', availability: 'Available', location: 'Example Riverside',
  summary: 'A compact electric car with current dealership facts.',
  exteriorColour: 'Blue', seats: 5, electricRangeMiles: 210,
  features: [{ name: 'Parking sensors' }, { name: 'Heated seats' }],
  imageUrl: 'https://images.dealer.test/car.png',
  internalId: 'not-a-public-field', credentials: 'never-render-this',
}
const stock = {
  total: 1, results: [vehicle],
  source: { sourceId: 'Example stock', synchronizedAt: '2026-11-01T14:00:00Z' },
}

function resolve(actionName, actionData, config = {}) {
  return resolveActionPresentation({
    config: dealershipActionPresentationConfig(config), actionName, actionData, messageId: 'message-42',
  })
}

test('real widget projections retain declared public detail fields and exclude private extras', () => {
  const inventory = resolve('dealership_search_inventory', stock)
  const detail = resolve('dealership_get_vehicle', { vehicleRecord: vehicle })
  const comparison = resolve('dealership_compare_vehicles', { _items: [vehicle] })
  for (const record of [inventory.presentationData.items[0], detail.presentationData.vehicle, comparison.presentationData.vehicles[0]]) {
    assert.equal(record.location, 'Example Riverside')
    assert.equal(record.summary, vehicle.summary)
    assert.equal(record.seats, 5)
    assert.equal(record.availability, 'Available')
    assert.equal(record.internalId, undefined)
    assert.equal(record.credentials, undefined)
  }
  assert.equal(inventory.resultReferences[0].lookupValue, vehicle.stockId)
  assert.equal(inventory.resultReferences[0].sourceMessageId, 'message-42')
  assert.equal(inventory.context.detailNavigation, 'query')
  assert.equal(inventory.context.detailBasePath, '')
  assert.equal(resolve('unregistered_action', stock), null)
})

test('website navigation is explicit and does not inject runtime routing directives', () => {
  const result = resolve('dealership_search_inventory', stock, {
    detailNavigation: 'website', detailBasePath: '/vehicles/', detailSlugs: { 'STOCK-42': 'city-ev' },
  })
  assert.equal(result.context.detailNavigation, 'website')
  assert.equal(result.context.detailBasePath, '/vehicles/')
  assert.equal(result.context.preferredVectorSpaces, undefined)
  assert.equal(result.context.actionName, undefined)
})

test('real browser detail commands, public rendering and responsive layouts', async t => {
  const { chromium } = createRequire(new URL('../../../Platfrom/ui/package.json', import.meta.url))('playwright')
  const browser = await chromium.launch({ headless: true })
  t.after(() => browser.close())
  const page = await browser.newPage({ viewport: { width: 1100, height: 900 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const pack = await readFile(new URL('../dist/dealership-experience.esm.js', import.meta.url), 'utf8')
  await page.route('https://dealership.test/**', async route => {
    if (route.request().url().endsWith('/pack.js')) {
      await route.fulfill({ contentType: 'text/javascript', body: pack })
    } else if (route.request().url().endsWith('/contract.js')) {
      await route.fulfill({ contentType: 'text/javascript', body: widgetCode })
    } else {
      await route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body style="margin:0;font:14px system-ui;background:#f2f5f4"><main style="max-width:1000px;margin:auto;padding:16px"></main><script type="module">
        import { registerDealershipActionPresentationElements, dealershipActionPresentationConfig } from '/pack.js';
        import { resolveActionPresentation } from '/contract.js';
        registerDealershipActionPresentationElements();
        window.renderResult = ({ actionName = 'dealership_search_inventory', data, config = {}, referencePatch, rejectAsk = false }) => {
          const result = resolveActionPresentation({ config: dealershipActionPresentationConfig(config), actionName, actionData: data, messageId: 'message-42' });
          if (referencePatch) result.resultReferences = result.resultReferences.map(reference => ({ ...reference, ...referencePatch }));
          const element = document.createElement(result.renderer.elementName);
          window.commandLog = [];
          element.commands = {
            async ask(input) { window.commandLog.push({ command: 'ask', ...input }); if (rejectAsk) throw new Error('Request rejected by runtime'); },
            navigate(input) { window.commandLog.push({ command: 'navigate', ...input }); },
            attachResult(key) { window.commandLog.push({ command: 'attach', key }); },
            detachResult(key) { window.commandLog.push({ command: 'detach', key }); },
          };
          element.presentation = { ...result, selectedResultKeys: [] };
          document.querySelector('main').replaceChildren(element);
        };
      </script></body></html>` })
    }
  })
  await page.route('https://images.dealer.test/car.png', route => route.fulfill({
    contentType: 'image/png',
    body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9WQAAAAASUVORK5CYII=', 'base64'),
  }))
  await page.goto('https://dealership.test/')
  await page.waitForFunction(() => typeof window.renderResult === 'function')
  const render = fixture => page.evaluate(value => window.renderResult(value), fixture)
  const commands = () => page.evaluate(() => window.commandLog)

  await t.test('an inventory detail click asks with exactly its result reference and never invents a URL', async () => {
    await render({ data: stock })
    const element = page.locator('loomai-dealership-inventory')
    const button = element.getByRole('button', { name: 'View details', exact: true })
    assert.equal(await button.isEnabled(), true)
    assert.deepEqual(await commands(), [])
    assert.equal(await element.getByRole('button', { name: 'Ask about this' }).count(), 0)
    await button.click()
    assert.deepEqual(await commands(), [{
      command: 'ask', resultReferenceKeys: ['items-0'],
      query: 'Load the current live stock record for 2025 Example City EV, then summarize its dealership facts.',
    }])
  })

  await t.test('comparison details use the selected comparison reference instead of a no-op View control', async () => {
    await render({ actionName: 'dealership_compare_vehicles', data: {
      _items: [{ ...vehicle, registrationYear: vehicle.year }, {
        ...vehicle, stockId: 'STOCK-43', model: 'Family EV', registrationYear: 2024,
      }],
    } })
    const buttons = page.locator('loomai-dealership-vehicle-comparison').getByRole('button', { name: 'View details', exact: true })
    assert.equal(await buttons.count(), 2)
    await buttons.nth(1).click()
    assert.deepEqual((await commands())[0].resultReferenceKeys, ['vehicles-1'])
    assert.match((await commands())[0].query, /2024 Example Family EV/)
    assert.equal((await commands()).length, 1)
  })

  await t.test('missing, foreign and incomplete result identities cannot issue an unbound detail request', async () => {
    for (const referencePatch of [
      { key: 'foreign-key' }, { lookupValue: '' }, { lookupValue: 'OTHER-STOCK' },
      { sourceMessageId: '' }, { sourceActionName: '' },
    ]) {
      await render({ data: stock, referencePatch })
      const button = page.locator('loomai-dealership-inventory').getByRole('button', { name: 'View details', exact: true })
      assert.equal(await button.isDisabled(), true)
      assert.match(await button.getAttribute('title'), /no vehicle reference/)
      assert.deepEqual(await commands(), [])
    }
    await render({ data: { results: [{ ...vehicle, stockId: undefined }] } })
    assert.equal(await page.getByRole('button', { name: 'View details', exact: true }).isDisabled(), true)
  })

  await t.test('runtime rejection is visible and does not fall back to another query or navigation', async () => {
    await render({ data: stock, rejectAsk: true })
    await page.getByRole('button', { name: 'View details', exact: true }).click()
    const status = page.locator('loomai-dealership-inventory').getByRole('status')
    await status.filter({ hasText: 'could not start that request' }).waitFor({ state: 'visible' })
    assert.equal(await status.evaluate(node => node.className), 'presentation-status')
    assert.equal((await commands()).length, 1)
    assert.equal((await commands())[0].command, 'ask')
  })

  await t.test('a reviewed website mapping navigates on click and wins over a provider slug', async () => {
    await render({ data: { results: [{ ...vehicle, slug: 'provider-route' }] }, config: {
      detailNavigation: 'website', detailBasePath: '/approved/vehicles', detailSlugs: { 'STOCK-42': 'city-ev' },
    } })
    assert.deepEqual(await commands(), [])
    await page.getByRole('button', { name: 'View details', exact: true }).click()
    assert.deepEqual(await commands(), [{ command: 'navigate', url: 'https://dealership.test/approved/vehicles/city-ev' }])
  })

  await t.test('invalid or missing website routes stay unavailable without silently querying', async () => {
    for (const config of [
      { detailBasePath: '/vehicles/' },
      { detailSlugs: { 'STOCK-42': 'city-ev' } },
      ...['https://unapproved.test/vehicles/', '//unapproved.test/vehicles/', 'javascript:alert(1)',
        'https://user:password@dealership.test/vehicles/', '/vehicles/?next=', '/vehicles/#section']
        .map(detailBasePath => ({ detailBasePath, detailSlugs: { 'STOCK-42': 'city-ev' } })),
      { detailBasePath: '/vehicles/', detailSlugs: { 'STOCK-42': '..' } },
    ]) {
      await render({ data: stock, config: { ...config, detailNavigation: 'website' } })
      assert.equal(await page.getByRole('button', { name: 'View details', exact: true }).isDisabled(), true)
      assert.deepEqual(await commands(), [])
    }
  })

  await t.test('current detail results render optional facts as buyer content without raw private payloads', async () => {
    await render({ actionName: 'dealership_get_vehicle', data: { vehicleRecord: vehicle }, config: {
      imageHostAllowlist: ['images.dealer.test'],
    } })
    const element = page.locator('loomai-dealership-vehicle-detail')
    const content = await element.locator('.workspace').innerText()
    for (const text of ['Example Riverside', '210 miles', 'Blue', 'Heated seats', vehicle.summary]) {
      assert.ok(content.includes(text), `Expected detail content: ${text}`)
    }
    for (const text of ['not-a-public-field', 'never-render-this', '"stockId"', '[object Object]']) {
      assert.equal(content.includes(text), false)
    }
    await element.locator('img').evaluate(image => image.decode())
    assert.equal(await element.locator('img').evaluate(image => image.complete && image.naturalWidth > 0), true)
  })

  await t.test('narrow and desktop inventory/detail/comparison presentations fit and render without requests', async () => {
    const fixtures = [
      ['inventory', { data: stock }],
      ['detail', { actionName: 'dealership_get_vehicle', data: { vehicleRecord: vehicle } }],
      ['comparison', { actionName: 'dealership_compare_vehicles', data: {
        _items: [{ ...vehicle, registrationYear: 2025 }, { ...vehicle, stockId: 'STOCK-43', model: 'Family EV', registrationYear: 2024 }],
      } }],
    ]
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: 960 })
      for (const [kind, fixture] of fixtures) {
        await render(fixture)
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `${kind} at ${width}px`)
        assert.deepEqual(await commands(), [])
        if (kind === 'comparison') {
          assert.equal(await page.getByText('Available', { exact: true }).count(), 4)
        }
        if (process.env.DEALERSHIP_PRESENTATION_EVIDENCE_DIR) {
          await mkdir(process.env.DEALERSHIP_PRESENTATION_EVIDENCE_DIR, { recursive: true })
          await page.screenshot({ path: join(process.env.DEALERSHIP_PRESENTATION_EVIDENCE_DIR, `${kind}-${width}.png`), fullPage: true })
        }
      }
    }
  })
  assert.deepEqual(errors, [])
})
