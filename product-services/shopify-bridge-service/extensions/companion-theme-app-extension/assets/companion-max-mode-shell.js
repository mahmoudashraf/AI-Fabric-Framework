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
    return ensureMaxModeReady(options.maxModeScriptUrl)
      .then(function (maxModeApi) {
        if (!maxModeApi || typeof maxModeApi.init !== 'function') {
          throw new Error('Max Mode widget API is unavailable.')
        }
        var resolvedLauncherLabel = (options.payload.launcherLabel || options.launcherLabel || 'Ask the store assistant').trim()
        var shellModeProfile = normalizeShellModeProfile(options.payload.shellModeProfile)
        var defaultConversationMode = normalizeConversationMode(
          options.payload.defaultConversationMode || defaultLauncherMode(shellModeProfile)
        )
        var allowedConversationModes = normalizeAllowedConversationModes(
          options.payload.allowedConversationModes,
          defaultConversationMode
        )
        var pageModeMappings = normalizePageModeMappings(options.payload.pageModeMappings, allowedConversationModes)
        var effectiveConversationMode = resolveEffectiveConversationMode(
          options.storefrontContext,
          options.payload.effectiveConversationMode,
          defaultConversationMode,
          allowedConversationModes,
          pageModeMappings
        )
        var resolvedWelcomeMessage = deriveWelcomeMessage(options.payload, options.storefrontContext, shellModeProfile)
        var shopperSessionId = getOrCreateShopperSessionId(options.payload.shopDomain || options.root.dataset.shopDomain || 'storefront')
        var starterSuggestions = defaultSuggestionsForContext(options.storefrontContext, shellModeProfile)
        var requestContext = buildRequestContext(
          options.storefrontContext,
          shellModeProfile,
          effectiveConversationMode,
          allowedConversationModes,
          pageModeMappings
        )

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
            assistantLabel: assistantLabelForShellModeProfile(shellModeProfile),
            welcomeMessage: resolvedWelcomeMessage,
            starterPrompts: starterPromptsForContext(starterSuggestions, effectiveConversationMode, options.storefrontContext),
            starterSuggestions: starterSuggestions,
            requestContext: requestContext,
            defaultConversationMode: defaultConversationMode,
            effectiveConversationMode: effectiveConversationMode,
            allowedConversationModes: allowedConversationModes,
            pageModeMappings: pageModeMappings,
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

  function starterPromptsForContext(suggestions, conversationMode, storefrontContext) {
    return (suggestions || []).slice(0, 4).map(function (value) {
      return {
        label: truncateText(value, 48) || value,
        query: value,
        position: conversationPositionForContext(storefrontContext),
        mode: normalizeConversationMode(conversationMode),
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

  function deriveWelcomeMessage(payload, storefrontContext, shellModeProfile) {
    var fallback = (payload.welcomeMessage || payload.message || '').trim()
    if (fallback && !isGenericWelcomeMessage(fallback)) {
      return fallback
    }
    if (storefrontContext.product && storefrontContext.product.title) {
      if (shellModeProfile === 'GUIDED_SUPPORT') {
        return 'Ask for shopper support on ' + storefrontContext.product.title + ', compare options, or check shipping and return guidance before you buy.'
      }
      if (shellModeProfile === 'GUIDED_COMMERCE') {
        return 'Ask for guided buying help on ' + storefrontContext.product.title + ', compare options, or check store policies before you buy.'
      }
      return 'Ask about ' + storefrontContext.product.title + ', compare it with similar products, or check shipping and return policies before you buy.'
    }
    if (storefrontContext.collection && storefrontContext.collection.title) {
      if (shellModeProfile === 'GUIDED_SUPPORT') {
        return 'Ask for shopper support on products in ' + storefrontContext.collection.title + ', compare options, or check return guidance before you buy.'
      }
      if (shellModeProfile === 'GUIDED_COMMERCE') {
        return 'Ask for guided buying help in ' + storefrontContext.collection.title + ', compare products, or check store policies before you buy.'
      }
      return 'Ask for the best options in ' + storefrontContext.collection.title + ', compare products, or check store policies before you buy.'
    }
    if (fallback) {
      return fallback
    }
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return 'Store support guide is ready. Ask about products, shipping, returns, or what to verify before you buy.'
    }
    if (shellModeProfile === 'GUIDED_COMMERCE') {
      return 'Store buying guide is ready. Ask about products, compare options, or narrow the catalog faster.'
    }
    return 'Store assistant is ready. Ask about products, policies, or collections.'
  }

  function isGenericWelcomeMessage(value) {
    return value === 'Store assistant is ready. Ask about products, policies, or collections.'
  }

  function defaultSuggestionsForContext(storefrontContext, shellModeProfile) {
    if (storefrontContext.product && storefrontContext.product.title) {
      if (shellModeProfile === 'GUIDED_SUPPORT') {
        return [
          'What should I verify before buying ' + storefrontContext.product.title + '?',
          'What is your return policy for ' + storefrontContext.product.title + '?',
          'Compare ' + storefrontContext.product.title + ' with similar products',
          'What shipping details matter for this product?',
        ]
      }
      return [
        'Tell me about ' + storefrontContext.product.title,
        'How does ' + storefrontContext.product.title + ' compare to similar products?',
        'What should I know before buying ' + storefrontContext.product.title + '?',
        'What is your return policy for this product?',
      ]
    }
    if (storefrontContext.collection && storefrontContext.collection.title) {
      if (shellModeProfile === 'GUIDED_SUPPORT') {
        return [
          'Which products in ' + storefrontContext.collection.title + ' are easiest to buy with confidence?',
          'Compare the top picks in ' + storefrontContext.collection.title,
          'What store policies matter before buying from ' + storefrontContext.collection.title + '?',
          'What is your return policy?',
        ]
      }
      return [
        'Show me the highlights from ' + storefrontContext.collection.title,
        'Which products in ' + storefrontContext.collection.title + ' are best for everyday use?',
        'Compare the top picks in ' + storefrontContext.collection.title,
        'What is your shipping policy?',
      ]
    }
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return [
        'What should I know before buying from this store?',
        'What is your return policy?',
        'Compare your best options for a first purchase',
        'Show me products with the clearest fit and policy guidance',
      ]
    }
    if (shellModeProfile === 'GUIDED_COMMERCE') {
      return [
        'Show me your best sellers',
        'Help me find the right product quickly',
        'Compare your top product categories',
        'What should I buy for travel?',
      ]
    }
    return [
      'Show me your best sellers',
      'What is your shipping policy?',
      'Compare your top product categories',
      'What should I buy for travel?',
    ]
  }

  function buildRequestContext(storefrontContext, shellModeProfile, effectiveConversationMode, allowedConversationModes, pageModeMappings) {
    var context = {}
    if (storefrontContext && typeof storefrontContext === 'object') {
      Object.keys(storefrontContext).forEach(function (key) {
        context[key] = storefrontContext[key]
      })
    }
    context.shopifyShellModeProfile = shellModeProfile
    context.shopifySurfaceEntry = 'launcher'
    context.shopifyPageModeGroup = pageModeGroup(storefrontContext)
    context.shopifyEffectiveConversationMode = normalizeConversationMode(effectiveConversationMode)
    context.shopifyAllowedConversationModes = allowedConversationModes || []
    context.shopifyPageModeMappings = pageModeMappings || {}
    return context
  }

  function normalizeShellModeProfile(value) {
    var normalized = typeof value === 'string' ? value.trim().toUpperCase() : ''
    if (!normalized) {
      return 'SHOPIFY_COMPANION'
    }
    return normalized
  }

  function assistantLabelForShellModeProfile(shellModeProfile) {
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return 'Store support guide'
    }
    if (shellModeProfile === 'GUIDED_COMMERCE') {
      return 'Store buying guide'
    }
    return 'Store assistant'
  }

  function defaultLauncherMode(shellModeProfile) {
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return 'navigator_deep'
    }
    return 'navigator'
  }

  function normalizeConversationMode(value) {
    if (value === 'navigator_deep' || value === 'cart_assistant' || value === 'executor') {
      return value
    }
    return 'navigator'
  }

  function normalizeAllowedConversationModes(values, defaultConversationMode) {
    var normalized = []
    if (Array.isArray(values)) {
      values.forEach(function (value) {
        var candidate = normalizeConversationMode(value)
        if (normalized.indexOf(candidate) < 0) {
          normalized.push(candidate)
        }
      })
    }
    var fallback = normalizeConversationMode(defaultConversationMode)
    if (normalized.indexOf(fallback) < 0) {
      normalized.push(fallback)
    }
    return normalized.length ? normalized : [fallback]
  }

  function normalizePageModeMappings(values, allowedConversationModes) {
    var normalized = {}
    if (!values || typeof values !== 'object') {
      return normalized
    }
    Object.keys(values).forEach(function (key) {
      var normalizedKey = trimValue(key).toLowerCase()
      var normalizedMode = normalizeConversationMode(values[key])
      if (!normalizedKey || allowedConversationModes.indexOf(normalizedMode) < 0) {
        return
      }
      normalized[normalizedKey] = normalizedMode
    })
    return normalized
  }

  function resolveEffectiveConversationMode(storefrontContext, payloadEffectiveMode, defaultConversationMode, allowedConversationModes, pageModeMappings) {
    var payloadMode = normalizeConversationMode(payloadEffectiveMode)
    if (allowedConversationModes.indexOf(payloadMode) >= 0) {
      return payloadMode
    }
    var mappedMode = pageModeMappings[pageModeGroup(storefrontContext)]
    if (mappedMode && allowedConversationModes.indexOf(mappedMode) >= 0) {
      return mappedMode
    }
    return normalizeConversationMode(defaultConversationMode)
  }

  function pageModeGroup(storefrontContext) {
    var pageType = storefrontContext && storefrontContext.pageType ? String(storefrontContext.pageType).toLowerCase() : ''
    if (pageType === 'product') {
      return 'product'
    }
    if (pageType === 'collection' || pageType === 'list-collections') {
      return 'collection'
    }
    if (pageType === 'search') {
      return 'search'
    }
    if (pageType === 'cart') {
      return 'cart'
    }
    if (pageType === 'article' || pageType === 'blog' || pageType === 'page') {
      return 'content'
    }
    if (
      pageType === 'customers/account' ||
      pageType === 'customers/login' ||
      pageType === 'customers/register' ||
      pageType === 'customers/order' ||
      pageType === 'account' ||
      pageType === 'orders'
    ) {
      return 'account'
    }
    return 'landing'
  }

  function conversationPositionForContext(storefrontContext) {
    var pageGroup = pageModeGroup(storefrontContext)
    if (pageGroup === 'product' || pageGroup === 'collection' || pageGroup === 'content') {
      return 'catalog'
    }
    if (pageGroup === 'search') {
      return 'search'
    }
    if (pageGroup === 'cart' || pageGroup === 'account') {
      return 'cart'
    }
    return 'landing'
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
