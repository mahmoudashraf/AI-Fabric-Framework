(function () {
  var LOOM_COMPANION_SHELLS_KEY = '__loomCompanionShells'
  var LOOM_COMPANION_SURFACES_KEY = '__loomCompanionEmbeddedSurfaces'
  var bootstrapPayloadPromises = {}
  var shellLoadPromises = {}
  var surfaceLoadPromises = {}
  var stylesheetLoadPromises = {}

  function bootstrap() {
    var roots = document.querySelectorAll('[data-loom-companion-bootstrap="true"]')
    if (!roots.length) {
      return
    }
    Array.prototype.forEach.call(roots, function (root) {
      var bridgeBaseUrl = trimValue(root.dataset.bridgeBaseUrl)
      var shopDomain = trimValue(root.dataset.shopDomain)
      var requestedWidgetShell = normalizeWidgetShell(root.dataset.widgetShell)
      var maxModeShellScriptUrl = trimValue(root.dataset.maxModeShellScriptUrl)
      var maxModeScriptUrl = trimValue(root.dataset.maxModeScriptUrl)
      var embeddedSurfacesScriptUrl = trimValue(root.dataset.embeddedSurfacesScriptUrl)
      var embeddedSurfacesStylesheetUrl = trimValue(root.dataset.embeddedSurfacesStylesheetUrl)
      var effectiveWidgetShell = resolveEffectiveWidgetShell(
        requestedWidgetShell,
        maxModeShellScriptUrl,
        maxModeScriptUrl
      )
      if (!bridgeBaseUrl || !shopDomain) {
        root.dataset.status = 'configuration-required'
        return
      }
      root.dataset.requestedWidgetShell = requestedWidgetShell
      root.dataset.widgetShell = effectiveWidgetShell

      resolveBootstrap(root, {
        bridgeBaseUrl: bridgeBaseUrl,
        shopDomain: shopDomain,
        launcherLabel: trimValue(root.dataset.launcherLabel) || 'Ask the store assistant',
        widgetShell: effectiveWidgetShell,
        requestedWidgetShell: requestedWidgetShell,
        legacyShellScriptUrl: trimValue(root.dataset.legacyShellScriptUrl),
        maxModeShellScriptUrl: maxModeShellScriptUrl,
        maxModeScriptUrl: maxModeScriptUrl,
        embeddedSurfacesScriptUrl: embeddedSurfacesScriptUrl,
        embeddedSurfacesStylesheetUrl: embeddedSurfacesStylesheetUrl,
        storefrontContext: extractStorefrontContext(root),
        shellEnabled: root.dataset.shellEnabled !== 'false',
        surfaceMount: trimValue(root.dataset.surfaceMount) || 'floating',
        surfaceScope: trimValue(root.dataset.surfaceScope) || 'all',
      })
    })
  }

  function resolveBootstrap(root, config) {
    root.dataset.status = 'loading'
    root.textContent = ''
    loadBootstrapPayload(config)
      .then(function (payload) {
        if (!payload || !payload.available) {
          root.dataset.status = 'unavailable'
          root.textContent = payload && payload.message ? payload.message : 'Store assistant is not ready yet.'
          return
        }
        if (payload.chatFallbackEnabled === false || !config.shellEnabled) {
          teardownActiveShell(root)
          teardownEmbeddedSurfaces(root)
          root.dataset.status = 'ready'
          return renderEmbeddedSurfaces(root, config, payload)
        }
        return loadShellRenderer(config.widgetShell, config).then(function (renderer) {
          teardownActiveShell(root)
          teardownEmbeddedSurfaces(root)
          return Promise.resolve(
            renderer.render({
              root: root,
              bridgeBaseUrl: config.bridgeBaseUrl,
              launcherLabel: config.launcherLabel,
              maxModeScriptUrl: config.maxModeScriptUrl,
              payload: payload,
              storefrontContext: config.storefrontContext,
            })
          )
            .then(function () {
              root.dataset.activeShell = config.widgetShell
              return renderEmbeddedSurfaces(root, config, payload)
            })
            .catch(function (error) {
              if (config.widgetShell !== 'max-mode') {
                throw error
              }
              return loadShellRenderer('legacy', config).then(function (legacyRenderer) {
                teardownActiveShell(root)
                teardownEmbeddedSurfaces(root)
                return Promise.resolve(
                  legacyRenderer.render({
                    root: root,
                    bridgeBaseUrl: config.bridgeBaseUrl,
                    launcherLabel: config.launcherLabel,
                    maxModeScriptUrl: config.maxModeScriptUrl,
                    payload: payload,
                    storefrontContext: config.storefrontContext,
                  })
                ).then(function () {
                  root.dataset.activeShell = 'legacy'
                  root.dataset.widgetShell = 'max-mode'
                  root.dataset.fallbackShell = 'legacy'
                  return renderEmbeddedSurfaces(root, config, payload)
                })
              })
            })
        })
      })
      .catch(function (error) {
        root.dataset.status = 'failed'
        root.textContent = error && error.message ? error.message : 'Store assistant bootstrap failed.'
      })
  }

  function loadBootstrapPayload(config) {
    var key = [config.bridgeBaseUrl, config.shopDomain].join('|')
    if (!bootstrapPayloadPromises[key]) {
      bootstrapPayloadPromises[key] = fetch(
        joinUrl(config.bridgeBaseUrl, '/api/storefront/shops/' + encodeURIComponent(config.shopDomain) + '/bootstrap'),
        {
          headers: {
            Accept: 'application/json',
          },
        }
      ).then(function (response) {
        if (!response.ok) {
          return response.text().then(function (message) {
            throw new Error(message || 'Widget bootstrap failed with HTTP ' + response.status)
          })
        }
        return response.json()
      })
    }
    return bootstrapPayloadPromises[key]
  }

  function loadShellRenderer(widgetShell, config) {
    var registry = getShellRegistry()
    if (registry[widgetShell] && typeof registry[widgetShell].render === 'function') {
      return Promise.resolve(registry[widgetShell])
    }

    var shellUrl = widgetShell === 'max-mode' ? config.maxModeShellScriptUrl : config.legacyShellScriptUrl
    if (!shellUrl) {
      return Promise.reject(new Error('Theme app embed is missing the ' + widgetShell + ' shell asset URL.'))
    }

    if (!shellLoadPromises[shellUrl]) {
      shellLoadPromises[shellUrl] = loadScript(shellUrl).then(function () {
        var loadedRenderer = getShellRegistry()[widgetShell]
        if (!loadedRenderer || typeof loadedRenderer.render !== 'function') {
          throw new Error('Theme app embed did not register the ' + widgetShell + ' shell renderer.')
        }
        return loadedRenderer
      })
    }

    return shellLoadPromises[shellUrl]
  }

  function teardownActiveShell(root) {
    var activeShell = trimValue(root.dataset.activeShell)
    if (!activeShell) {
      return
    }
    var renderer = getShellRegistry()[activeShell]
    if (renderer && typeof renderer.teardown === 'function') {
      renderer.teardown(root)
    }
    delete root.dataset.activeShell
  }

  function loadScript(url) {
    return new Promise(function (resolve, reject) {
      var script = document.createElement('script')
      script.src = url
      script.async = true
      script.onload = function () {
        resolve()
      }
      script.onerror = function () {
        reject(new Error('Failed to load theme app embed shell asset.'))
      }
      document.head.appendChild(script)
    })
  }

  function renderEmbeddedSurfaces(root, config, payload) {
    if (!config.embeddedSurfacesScriptUrl) {
      return Promise.resolve()
    }
    return ensureStylesheet(config.embeddedSurfacesStylesheetUrl)
      .then(function () {
        return renderEmbeddedSurfacesLoaded(root, config, payload)
      })
  }

  function renderEmbeddedSurfacesLoaded(root, config, payload) {
    var registry = getSurfaceRegistry()
    if (registry.render && typeof registry.render === 'function') {
      return Promise.resolve(
        registry.render({
          root: root,
          bridgeBaseUrl: config.bridgeBaseUrl,
          payload: payload,
          storefrontContext: config.storefrontContext,
          mountMode: config.surfaceMount,
          surfaceScope: config.surfaceScope,
        })
      )
    }
    if (!surfaceLoadPromises[config.embeddedSurfacesScriptUrl]) {
      surfaceLoadPromises[config.embeddedSurfacesScriptUrl] = loadScript(config.embeddedSurfacesScriptUrl).then(function () {
        var loadedRegistry = getSurfaceRegistry()
        if (!loadedRegistry.render || typeof loadedRegistry.render !== 'function') {
          throw new Error('Theme app embed did not register embedded companion surfaces.')
        }
        return loadedRegistry
      })
    }
    return surfaceLoadPromises[config.embeddedSurfacesScriptUrl].then(function (loadedRegistry) {
      return loadedRegistry.render({
        root: root,
        bridgeBaseUrl: config.bridgeBaseUrl,
        payload: payload,
        storefrontContext: config.storefrontContext,
        mountMode: config.surfaceMount,
        surfaceScope: config.surfaceScope,
      })
    })
  }

  function ensureStylesheet(url) {
    if (!url) {
      return Promise.resolve()
    }
    if (document.querySelector('link[data-loom-companion-surfaces="' + url + '"]')) {
      return Promise.resolve()
    }
    if (!stylesheetLoadPromises[url]) {
      stylesheetLoadPromises[url] = new Promise(function (resolve, reject) {
        var link = document.createElement('link')
        link.rel = 'stylesheet'
        link.href = url
        link.dataset.loomCompanionSurfaces = url
        link.onload = function () {
          resolve()
        }
        link.onerror = function () {
          reject(new Error('Failed to load companion surfaces stylesheet.'))
        }
        document.head.appendChild(link)
      })
    }
    return stylesheetLoadPromises[url]
  }

  function teardownEmbeddedSurfaces(root) {
    var registry = getSurfaceRegistry()
    if (registry && typeof registry.teardown === 'function') {
      registry.teardown(root)
    }
  }

  function getSurfaceRegistry() {
    if (!window[LOOM_COMPANION_SURFACES_KEY]) {
      window[LOOM_COMPANION_SURFACES_KEY] = {}
    }
    return window[LOOM_COMPANION_SURFACES_KEY]
  }

  function getShellRegistry() {
    if (!window[LOOM_COMPANION_SHELLS_KEY]) {
      window[LOOM_COMPANION_SHELLS_KEY] = {}
    }
    return window[LOOM_COMPANION_SHELLS_KEY]
  }

  function extractStorefrontContext(root) {
    var pageType = trimValue(root.dataset.pageType) || 'unknown'
    var pageTitle = trimValue(root.dataset.pageTitle)
    var context = {
      pageType: pageType,
      pageTitle: pageTitle,
      product: null,
      collection: null,
    }

    var productTitle = trimValue(root.dataset.productTitle)
    var productHandle = trimValue(root.dataset.productHandle)
    var productId = trimValue(root.dataset.productId)
    if (productTitle || productHandle || productId) {
      context.product = {
        id: productId,
        handle: productHandle,
        title: productTitle || pageTitle || 'this product',
        vendor: trimValue(root.dataset.productVendor),
        type: trimValue(root.dataset.productType),
        variantId: trimValue(root.dataset.productVariantId),
        priceCents: trimValue(root.dataset.productPriceCents),
      }
    }

    var collectionTitle = trimValue(root.dataset.collectionTitle)
    var collectionHandle = trimValue(root.dataset.collectionHandle)
    var collectionId = trimValue(root.dataset.collectionId)
    if (collectionTitle || collectionHandle || collectionId) {
      context.collection = {
        id: collectionId,
        handle: collectionHandle,
        title: collectionTitle || pageTitle || 'this collection',
      }
    }

    return context
  }

  function normalizeWidgetShell(value) {
    return value === 'max-mode' ? 'max-mode' : 'legacy'
  }

  function resolveEffectiveWidgetShell(requestedWidgetShell, maxModeShellScriptUrl, maxModeScriptUrl) {
    if (maxModeShellScriptUrl && maxModeScriptUrl) {
      return 'max-mode'
    }
    return requestedWidgetShell
  }

  function trimValue(value) {
    return typeof value === 'string' && value.trim() ? value.trim() : ''
  }

  function joinUrl(baseUrl, suffix) {
    return baseUrl.replace(/\/+$/, '') + suffix
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bootstrap, { once: true })
  } else {
    bootstrap()
  }
})()
