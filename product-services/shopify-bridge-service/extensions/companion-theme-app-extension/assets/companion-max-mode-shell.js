(function () {
  var LOOM_COMPANION_SHELLS_KEY = '__loomCompanionShells'
  var MAX_MODE_SHADOW_HOST_ID = 'max-mode-widget-shadow-host'
  var maxModeLoadPromise = null

  function getShellRegistry() {
    if (!window[LOOM_COMPANION_SHELLS_KEY]) {
      window[LOOM_COMPANION_SHELLS_KEY] = {}
    }
    return window[LOOM_COMPANION_SHELLS_KEY]
  }

  function render(options) {
    teardown()
    options.root.dataset.status = 'loading'
    options.root.textContent = ''
    ensureMaxModeReady(options.maxModeScriptUrl)
      .then(function (maxModeApi) {
        if (!maxModeApi || typeof maxModeApi.init !== 'function') {
          throw new Error('Max Mode widget API is unavailable.')
        }
        var resolvedLauncherLabel = (options.payload.launcherLabel || options.launcherLabel || 'Ask the store assistant').trim()
        var resolvedWelcomeMessage = deriveWelcomeMessage(options.payload, options.storefrontContext)
        var shopperSessionId = getOrCreateShopperSessionId(options.payload.shopDomain || options.root.dataset.shopDomain || 'storefront')
        var starterSuggestions = defaultSuggestionsForContext(options.storefrontContext)

        maxModeApi.init({
          apiConfig: {
            chatBaseUrl: options.bridgeBaseUrl,
            defaultHeaders: {
              'X-AI-FABRIC-SHOPPER-SESSION-ID': shopperSessionId,
            },
            probeShellConfigOnOpen: false,
            runtimeRoutes: {
              chatQueryUrl: options.payload.bridgeQueryUrl,
              suggestionsUrl: options.payload.bridgeSuggestionsUrl,
            },
            runtimeAuth: {
              probeAuthContextOnOpen: false,
            },
          },
          integrationMode: 'backend-mediated-private-runtime',
          features: {
            cart: false,
            debug: false,
            conversations: false,
            quickActions: true,
          },
          theme: {
            primaryColor: '#111827',
            borderRadius: '1rem',
            fontFamily: '"Helvetica Neue", Arial, sans-serif',
            darkMode: false,
          },
          position: 'bottom-right',
          launcher: true,
          host: {
            launcherLabel: resolvedLauncherLabel,
            launcherAriaLabel: resolvedLauncherLabel,
            launcherVariant: 'pill',
            assistantLabel: 'Store assistant',
            welcomeMessage: resolvedWelcomeMessage,
            starterPrompts: starterPromptsForContext(starterSuggestions),
            starterSuggestions: starterSuggestions,
            requestContext: options.storefrontContext,
            showUtilityPanel: false,
          },
          onEvent: function (event) {
            if (event && event.type === 'widget:opened') {
              recordStorefrontEvent(options.payload, shopperSessionId, options.storefrontContext, 'WIDGET_OPENED')
            }
          },
        })

        options.root.dataset.status = 'ready'
      })
      .catch(function (error) {
        options.root.dataset.status = 'failed'
        options.root.textContent = error && error.message ? error.message : 'Max Mode widget failed to initialize.'
      })
  }

  function teardown() {
    if (!document.getElementById(MAX_MODE_SHADOW_HOST_ID)) {
      return
    }
    var maxModeApi = resolveMaxModeGlobal()
    if (maxModeApi && typeof maxModeApi.destroy === 'function') {
      maxModeApi.destroy()
    }
  }

  function starterPromptsForContext(suggestions) {
    return (suggestions || []).slice(0, 4).map(function (value) {
      return {
        label: truncateText(value, 48) || value,
        query: value,
        position: 'search',
        mode: 'navigator',
      }
    })
  }

  function recordStorefrontEvent(payload, shopperSessionId, storefrontContext, eventType) {
    if (!payload || !payload.bridgeEventUrl) {
      return
    }
    fetch(payload.bridgeEventUrl, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-AI-FABRIC-SHOPPER-SESSION-ID': shopperSessionId,
      },
      body: JSON.stringify({
        eventType: eventType,
        pageType: storefrontContext.pageType || 'unknown',
        pageTitle: storefrontContext.pageTitle || null,
        productHandle: storefrontContext.product ? storefrontContext.product.handle : null,
        collectionHandle: storefrontContext.collection ? storefrontContext.collection.handle : null,
      }),
    }).catch(function () {
      return null
    })
  }

  function ensureMaxModeReady(scriptUrl) {
    var maxModeApi = resolveMaxModeGlobal()
    if (maxModeApi) {
      return Promise.resolve(maxModeApi)
    }
    if (!scriptUrl) {
      return Promise.reject(new Error('Max Mode script URL is not configured on the theme app embed.'))
    }
    if (!maxModeLoadPromise) {
      maxModeLoadPromise = new Promise(function (resolve, reject) {
        var script = document.createElement('script')
        script.src = scriptUrl
        script.async = true
        script.onload = function () {
          var loadedApi = resolveMaxModeGlobal()
          if (loadedApi) {
            resolve(loadedApi)
            return
          }
          reject(new Error('Max Mode loaded but did not expose window.MaxMode.'))
        }
        script.onerror = function () {
          reject(new Error('Failed to load Max Mode storefront bundle.'))
        }
        document.head.appendChild(script)
      })
    }
    return maxModeLoadPromise
  }

  function resolveMaxModeGlobal() {
    if (window.MaxMode && typeof window.MaxMode.init === 'function') {
      return window.MaxMode
    }
    return null
  }

  function deriveWelcomeMessage(payload, storefrontContext) {
    var fallback = (payload.welcomeMessage || payload.message || '').trim()
    if (fallback && !isGenericWelcomeMessage(fallback)) {
      return fallback
    }
    if (storefrontContext.product && storefrontContext.product.title) {
      return 'Ask about ' + storefrontContext.product.title + ', compare it with similar products, or check shipping and return policies before you buy.'
    }
    if (storefrontContext.collection && storefrontContext.collection.title) {
      return 'Ask for the best options in ' + storefrontContext.collection.title + ', compare products, or check store policies before you buy.'
    }
    if (fallback) {
      return fallback
    }
    return 'Store assistant is ready. Ask about products, policies, or collections.'
  }

  function isGenericWelcomeMessage(value) {
    return value === 'Store assistant is ready. Ask about products, policies, or collections.'
  }

  function defaultSuggestionsForContext(storefrontContext) {
    if (storefrontContext.product && storefrontContext.product.title) {
      return [
        'Tell me about ' + storefrontContext.product.title,
        'How does ' + storefrontContext.product.title + ' compare to similar products?',
        'What should I know before buying ' + storefrontContext.product.title + '?',
        'What is your return policy for this product?',
      ]
    }
    if (storefrontContext.collection && storefrontContext.collection.title) {
      return [
        'Show me the highlights from ' + storefrontContext.collection.title,
        'Which products in ' + storefrontContext.collection.title + ' are best for everyday use?',
        'Compare the top picks in ' + storefrontContext.collection.title,
        'What is your shipping policy?',
      ]
    }
    return [
      'Show me your best sellers',
      'What is your shipping policy?',
      'Compare your top product categories',
      'What should I buy for travel?',
    ]
  }

  function getOrCreateShopperSessionId(shopDomain) {
    var storageKey = 'loom-companion-shopper-session:' + shopDomain
    try {
      var existing = window.localStorage.getItem(storageKey)
      if (existing && /^[A-Za-z0-9._:-]{8,120}$/.test(existing)) {
        return existing
      }
      var created = createSessionId()
      window.localStorage.setItem(storageKey, created)
      return created
    } catch (_error) {
      return createSessionId()
    }
  }

  function createSessionId() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
      return 'shopper-' + window.crypto.randomUUID().replace(/[^A-Za-z0-9._:-]/g, '')
    }
    return 'shopper-' + Math.random().toString(36).slice(2) + Date.now().toString(36)
  }

  function truncateText(value, maxLength) {
    if (!value) {
      return null
    }
    if (value.length <= maxLength) {
      return value
    }
    return value.slice(0, maxLength - 1).trimEnd() + '…'
  }

  getShellRegistry()['max-mode'] = {
    render: render,
    teardown: teardown,
  }
})()
