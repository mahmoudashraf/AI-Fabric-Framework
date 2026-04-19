(function () {
  var LOOM_COMPANION_SHELLS_KEY = '__loomCompanionShells'
  var shellLoadPromises = {}

  function bootstrap() {
    var root = document.getElementById('loom-companion-embed-root')
    if (!root) {
      return
    }

    var bridgeBaseUrl = trimValue(root.dataset.bridgeBaseUrl)
    var shopDomain = trimValue(root.dataset.shopDomain)
    if (!bridgeBaseUrl || !shopDomain) {
      root.dataset.status = 'configuration-required'
      return
    }

    resolveBootstrap(root, {
      bridgeBaseUrl: bridgeBaseUrl,
      shopDomain: shopDomain,
      launcherLabel: trimValue(root.dataset.launcherLabel) || 'Ask the store assistant',
      widgetShell: normalizeWidgetShell(root.dataset.widgetShell),
      legacyShellScriptUrl: trimValue(root.dataset.legacyShellScriptUrl),
      maxModeShellScriptUrl: trimValue(root.dataset.maxModeShellScriptUrl),
      maxModeScriptUrl: trimValue(root.dataset.maxModeScriptUrl),
      storefrontContext: extractStorefrontContext(root),
    })
  }

  function resolveBootstrap(root, config) {
    root.dataset.status = 'loading'
    root.textContent = ''
    fetch(joinUrl(config.bridgeBaseUrl, '/api/storefront/shops/' + encodeURIComponent(config.shopDomain) + '/bootstrap'), {
      headers: {
        Accept: 'application/json',
      },
    })
      .then(function (response) {
        if (!response.ok) {
          return response.text().then(function (message) {
            throw new Error(message || 'Widget bootstrap failed with HTTP ' + response.status)
          })
        }
        return response.json()
      })
      .then(function (payload) {
        if (!payload || !payload.available) {
          root.dataset.status = 'unavailable'
          root.textContent = payload && payload.message ? payload.message : 'Store assistant is not ready yet.'
          return
        }
        return loadShellRenderer(config.widgetShell, config).then(function (renderer) {
          teardownActiveShell(root)
          renderer.render({
            root: root,
            bridgeBaseUrl: config.bridgeBaseUrl,
            launcherLabel: config.launcherLabel,
            maxModeScriptUrl: config.maxModeScriptUrl,
            payload: payload,
            storefrontContext: config.storefrontContext,
          })
          root.dataset.activeShell = config.widgetShell
        })
      })
      .catch(function (error) {
        root.dataset.status = 'failed'
        root.textContent = error && error.message ? error.message : 'Store assistant bootstrap failed.'
      })
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
