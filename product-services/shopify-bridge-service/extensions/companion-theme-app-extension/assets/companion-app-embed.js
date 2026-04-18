(function () {
  function bootstrap() {
    var root = document.getElementById('loom-companion-embed-root')
    if (!root) {
      return
    }

    var bridgeBaseUrl = (root.dataset.bridgeBaseUrl || '').trim()
    var shopDomain = (root.dataset.shopDomain || '').trim()
    var launcherLabel = (root.dataset.launcherLabel || 'Ask the store assistant').trim()
    var storefrontContext = extractStorefrontContext(root)

    if (!bridgeBaseUrl || !shopDomain) {
      root.dataset.status = 'configuration-required'
      return
    }

    resolveBootstrap(root, bridgeBaseUrl, shopDomain, launcherLabel, storefrontContext)
  }

  function resolveBootstrap(root, bridgeBaseUrl, shopDomain, launcherLabel, storefrontContext) {
    root.dataset.status = 'loading'
    fetch(joinUrl(bridgeBaseUrl, '/api/storefront/shops/' + encodeURIComponent(shopDomain) + '/bootstrap'), {
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
        renderWidget(root, bridgeBaseUrl, launcherLabel, payload, storefrontContext)
      })
      .catch(function (error) {
        root.dataset.status = 'failed'
        root.textContent = error && error.message ? error.message : 'Store assistant bootstrap failed.'
      })
  }

  function renderWidget(root, bridgeBaseUrl, launcherLabel, payload, storefrontContext) {
    root.dataset.status = 'ready'
    root.textContent = ''
    var resolvedLauncherLabel = (payload.launcherLabel || launcherLabel || 'Ask the store assistant').trim()
    var resolvedWelcomeMessage = deriveWelcomeMessage(payload, storefrontContext)

    var state = {
      conversationId: null,
      shopperSessionId: getOrCreateShopperSessionId(payload.shopDomain || root.dataset.shopDomain || 'storefront'),
      isOpen: false,
      isLoading: false,
      suggestionsLoaded: false,
      suggestions: [],
      messages: [
        {
          role: 'assistant',
          content: resolvedWelcomeMessage,
        },
      ],
    }

    var button = document.createElement('button')
    button.type = 'button'
    button.className = 'loom-companion-launcher'
    button.setAttribute('aria-haspopup', 'dialog')
    button.textContent = resolvedLauncherLabel

    var panel = document.createElement('aside')
    panel.className = 'loom-companion-panel'
    panel.hidden = true

    var header = document.createElement('header')
    header.className = 'loom-companion-panel__header'
    header.innerHTML =
      '<div><strong>Store assistant</strong><div class="loom-companion-panel__subhead">' +
      escapeHtml(payload.shopDomain || '') +
      '</div></div>' +
      '<button type="button" class="loom-companion-close" aria-label="Close assistant">Close</button>'

    var contextBar = document.createElement('div')
    contextBar.className = 'loom-companion-panel__context'
    contextBar.hidden = !hasContextTarget(storefrontContext)
    if (!contextBar.hidden) {
      contextBar.textContent = contextBadgeLabel(storefrontContext)
    }

    var messages = document.createElement('div')
    messages.className = 'loom-companion-messages'

    var suggestions = document.createElement('div')
    suggestions.className = 'loom-companion-suggestions'

    var status = document.createElement('div')
    status.className = 'loom-companion-status'
    status.hidden = true

    var composer = document.createElement('form')
    composer.className = 'loom-companion-composer'
    composer.innerHTML =
      '<textarea class="loom-companion-input" rows="3" maxlength="500" placeholder="Ask about products, collections, shipping, or policies"></textarea>' +
      '<div class="loom-companion-composer__actions">' +
      '<button type="button" class="loom-companion-reset">New chat</button>' +
      '<button type="submit" class="loom-companion-send">Send</button>' +
      '</div>'

    var input = composer.querySelector('.loom-companion-input')
    var sendButton = composer.querySelector('.loom-companion-send')
    var resetButton = composer.querySelector('.loom-companion-reset')
    var closeButton = header.querySelector('.loom-companion-close')

    panel.appendChild(header)
    panel.appendChild(contextBar)
    panel.appendChild(messages)
    panel.appendChild(suggestions)
    panel.appendChild(status)
    panel.appendChild(composer)

    button.addEventListener('click', function () {
      state.isOpen = !state.isOpen
      panel.hidden = !state.isOpen
      root.dataset.open = state.isOpen ? 'true' : 'false'
      if (state.isOpen && !state.suggestionsLoaded) {
        loadSuggestions()
      }
      if (state.isOpen) {
        recordEvent('WIDGET_OPENED')
      }
    })

    closeButton.addEventListener('click', function () {
      state.isOpen = false
      panel.hidden = true
      root.dataset.open = 'false'
    })

    resetButton.addEventListener('click', function () {
      state.conversationId = null
      state.messages = [
        {
          role: 'assistant',
          content: 'Started a new conversation. Ask another shopping question.',
        },
      ]
      state.suggestionsLoaded = false
      state.suggestions = []
      render()
      loadSuggestions()
      recordEvent('CHAT_RESET')
    })

    composer.addEventListener('submit', function (event) {
      event.preventDefault()
      var value = (input.value || '').trim()
      if (!value || state.isLoading) {
        return
      }
      input.value = ''
      state.messages.push({ role: 'user', content: value })
      render()
      sendQuery(value)
    })

    root.appendChild(button)
    root.appendChild(panel)
    render()

    function render() {
      messages.innerHTML = ''
      state.messages.forEach(function (message) {
        var row = document.createElement('div')
        row.className = 'loom-companion-message loom-companion-message--' + message.role
        var content = document.createElement('div')
        content.textContent = message.content
        row.appendChild(content)
        if (message.role === 'assistant' && Array.isArray(message.products) && message.products.length > 0) {
          row.appendChild(renderCardGroup('Matched products', message.products, 'Open product'))
        }
        if (message.role === 'assistant' && Array.isArray(message.sources) && message.sources.length > 0) {
          row.appendChild(renderCardGroup('Grounding sources', message.sources, 'Open source'))
        }
        messages.appendChild(row)
      })
      messages.scrollTop = messages.scrollHeight

      suggestions.innerHTML = ''
      if (state.suggestions.length > 0) {
        state.suggestions.forEach(function (suggestionText) {
          var suggestion = document.createElement('button')
          suggestion.type = 'button'
          suggestion.className = 'loom-companion-suggestion'
          suggestion.textContent = suggestionText
          suggestion.addEventListener('click', function () {
            if (state.isLoading) {
              return
            }
            recordEvent('SUGGESTION_CLICKED')
            state.messages.push({ role: 'user', content: suggestionText })
            render()
            sendQuery(suggestionText)
          })
          suggestions.appendChild(suggestion)
        })
      }

      if (state.isLoading) {
        showStatus('Thinking...')
      } else {
        hideStatus()
      }
      sendButton.disabled = state.isLoading
      input.disabled = state.isLoading
    }

    function showStatus(message) {
      status.hidden = false
      status.textContent = message
    }

    function hideStatus() {
      status.hidden = true
      status.textContent = ''
    }

    function loadSuggestions() {
      state.suggestionsLoaded = true
      fetchJson(payload.bridgeSuggestionsUrl, {
        method: 'POST',
        headers: shopperHeaders(),
        body: JSON.stringify({
          content: state.messages.length > 1 ? state.messages[state.messages.length - 1].content : buildContextPrompt(storefrontContext),
          maxSuggestions: 4,
          storefrontContext: storefrontContext,
        }),
      })
        .then(function (response) {
          state.suggestions = extractSuggestions(response)
          if (state.suggestions.length === 0) {
            state.suggestions = defaultSuggestionsForContext(storefrontContext)
          }
          render()
        })
        .catch(function () {
          state.suggestions = defaultSuggestionsForContext(storefrontContext)
          render()
        })
    }

    function sendQuery(userText) {
      state.isLoading = true
      render()
      fetchJson(payload.bridgeQueryUrl, {
        method: 'POST',
        headers: shopperHeaders(),
        body: JSON.stringify({
          query: userText,
          conversationId: state.conversationId || undefined,
          storefrontContext: storefrontContext,
        }),
      })
        .then(function (response) {
          state.conversationId = response.conversationId || state.conversationId
          state.messages.push({
            role: 'assistant',
            content: extractAssistantMessage(response),
            products: extractProductCards(response),
            sources: extractSourceCards(response),
          })
          var nextSuggestions = extractSuggestions(response)
          if (nextSuggestions.length > 0) {
            state.suggestions = nextSuggestions
          }
        })
        .catch(function (error) {
          state.messages.push({
            role: 'assistant',
            content: error && error.message ? error.message : 'Store assistant could not answer right now.',
          })
        })
        .finally(function () {
          state.isLoading = false
          render()
        })
    }

    function shopperHeaders() {
      return {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-AI-FABRIC-SHOPPER-SESSION-ID': state.shopperSessionId,
      }
    }

    function recordEvent(eventType) {
      if (!payload.bridgeEventUrl) {
        return
      }
      fetch(payload.bridgeEventUrl, {
        method: 'POST',
        headers: shopperHeaders(),
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

  function hasContextTarget(storefrontContext) {
    return !!((storefrontContext.product && storefrontContext.product.title) || (storefrontContext.collection && storefrontContext.collection.title))
  }

  function contextBadgeLabel(storefrontContext) {
    if (storefrontContext.product && storefrontContext.product.title) {
      return 'Product context: ' + storefrontContext.product.title
    }
    if (storefrontContext.collection && storefrontContext.collection.title) {
      return 'Collection context: ' + storefrontContext.collection.title
    }
    return ''
  }

  function buildContextPrompt(storefrontContext) {
    if (storefrontContext.product) {
      var parts = ['Current product: ' + storefrontContext.product.title]
      if (storefrontContext.product.vendor) {
        parts.push('vendor ' + storefrontContext.product.vendor)
      }
      if (storefrontContext.product.type) {
        parts.push('type ' + storefrontContext.product.type)
      }
      return parts.join(', ')
    }
    if (storefrontContext.collection) {
      return 'Current collection: ' + storefrontContext.collection.title
    }
    if (storefrontContext.pageTitle) {
      return 'Current page: ' + storefrontContext.pageTitle
    }
    return ''
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

  function trimValue(value) {
    return typeof value === 'string' && value.trim() ? value.trim() : ''
  }

  function fetchJson(path, init) {
    return fetch(path, init).then(function (response) {
      if (!response.ok) {
        return response
          .json()
          .catch(function () {
            return null
          })
          .then(function (payload) {
            var message =
              (payload && (payload.message || payload.error)) ||
              response.statusText ||
              'Request failed with HTTP ' + response.status
            throw new Error(message)
          })
      }
      return response.json()
    })
  }

  function renderCardGroup(title, items, linkLabel) {
    var group = document.createElement('div')
    group.className = 'loom-companion-card-group'

    var heading = document.createElement('div')
    heading.className = 'loom-companion-card-group__title'
    heading.textContent = title
    group.appendChild(heading)

    items.forEach(function (item) {
      var card = document.createElement('div')
      card.className = 'loom-companion-card'

      var cardTitle = document.createElement('div')
      cardTitle.className = 'loom-companion-card__title'
      cardTitle.textContent = item.title || item.label || 'Item'
      card.appendChild(cardTitle)

      if (item.subtitle) {
        var subtitle = document.createElement('div')
        subtitle.className = 'loom-companion-card__meta'
        subtitle.textContent = item.subtitle
        card.appendChild(subtitle)
      }

      if (item.detail) {
        var detail = document.createElement('div')
        detail.className = 'loom-companion-card__meta'
        detail.textContent = item.detail
        card.appendChild(detail)
      }

      if (item.excerpt) {
        var excerpt = document.createElement('div')
        excerpt.className = 'loom-companion-card__meta'
        excerpt.textContent = item.excerpt
        card.appendChild(excerpt)
      }

      if (item.url) {
        var link = document.createElement('a')
        link.className = 'loom-companion-card__link'
        link.href = item.url
        link.target = '_blank'
        link.rel = 'noreferrer'
        link.textContent = linkLabel
        card.appendChild(link)
      }

      group.appendChild(card)
    })

    return group
  }

  function extractAssistantMessage(payload) {
    if (!payload || typeof payload !== 'object') {
      return 'I could not process that request.'
    }
    if (payload.result && payload.result.sanitizedPayload && payload.result.sanitizedPayload.message) {
      return String(payload.result.sanitizedPayload.message)
    }
    if (payload.result && payload.result.message) {
      return String(payload.result.message)
    }
    if (payload.response) {
      return String(payload.response)
    }
    if (payload.message) {
      return String(payload.message)
    }
    return 'I processed your request.'
  }

  function extractSuggestions(payload) {
    var candidates = []
    if (payload && Array.isArray(payload.suggestions)) {
      candidates = payload.suggestions
    } else if (
      payload &&
      payload.result &&
      payload.result.sanitizedPayload &&
      Array.isArray(payload.result.sanitizedPayload.suggestions)
    ) {
      candidates = payload.result.sanitizedPayload.suggestions
    }
    return candidates
      .map(function (value) {
        if (typeof value === 'string') {
          return value.trim()
        }
        if (value && typeof value === 'object' && typeof value.text === 'string') {
          return value.text.trim()
        }
        if (value && typeof value === 'object' && typeof value.label === 'string') {
          return value.label.trim()
        }
        return ''
      })
      .filter(function (value, index, all) {
        return value && all.indexOf(value) === index
      })
      .slice(0, 4)
  }

  function extractProductCards(payload) {
    var candidates =
      firstArray(
        readPath(payload, 'result', 'sanitizedPayload', 'products'),
        readPath(payload, 'result', 'products'),
        readPath(payload, 'products'),
        readPath(payload, 'result', 'sanitizedPayload', 'items')
      ) || []
    return candidates.map(normalizeProductCard).filter(Boolean).slice(0, 4)
  }

  function extractSourceCards(payload) {
    var candidates =
      firstArray(
        readPath(payload, 'result', 'sanitizedPayload', 'sources'),
        readPath(payload, 'result', 'sources'),
        readPath(payload, 'sources')
      ) || []
    return candidates.map(normalizeSourceCard).filter(Boolean).slice(0, 4)
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

  function joinUrl(baseUrl, suffix) {
    return baseUrl.replace(/\/+$/, '') + suffix
  }

  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;')
  }

  function readPath(root) {
    var current = root
    for (var i = 1; i < arguments.length; i += 1) {
      var segment = arguments[i]
      if (!current || typeof current !== 'object' || !(segment in current)) {
        return undefined
      }
      current = current[segment]
    }
    return current
  }

  function firstArray() {
    for (var i = 0; i < arguments.length; i += 1) {
      if (Array.isArray(arguments[i])) {
        return arguments[i]
      }
    }
    return null
  }

  function normalizeProductCard(candidate) {
    if (!candidate || typeof candidate !== 'object') {
      return null
    }
    var title = firstText(candidate.title, candidate.name, candidate.label)
    if (!title) {
      return null
    }
    return {
      title: title,
      subtitle: joinText(firstText(candidate.vendor, candidate.brand), firstText(candidate.type, candidate.subtitle)),
      detail: joinText(
        firstText(candidate.formattedPrice, candidate.priceText, scalarText(candidate.price)),
        firstText(candidate.availability, candidate.status)
      ),
      url: firstText(candidate.url, candidate.href),
    }
  }

  function normalizeSourceCard(candidate) {
    if (typeof candidate === 'string' && candidate.trim()) {
      return {
        label: candidate.trim(),
        excerpt: null,
        url: null,
      }
    }
    if (!candidate || typeof candidate !== 'object') {
      return null
    }
    var label = firstText(candidate.title, candidate.label, candidate.name, candidate.id)
    if (!label) {
      return null
    }
    return {
      label: label,
      excerpt: truncateText(firstText(candidate.snippet, candidate.summary, candidate.excerpt, candidate.text, candidate.content), 220),
      url: firstText(candidate.url, candidate.href),
    }
  }

  function firstText() {
    for (var i = 0; i < arguments.length; i += 1) {
      var normalized = scalarText(arguments[i])
      if (normalized) {
        return normalized
      }
    }
    return null
  }

  function scalarText(value) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
      return String(value)
    }
    return null
  }

  function joinText() {
    var parts = []
    for (var i = 0; i < arguments.length; i += 1) {
      var value = arguments[i]
      if (typeof value === 'string' && value.trim()) {
        parts.push(value.trim())
      }
    }
    return parts.length ? parts.join(' · ') : null
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

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bootstrap, { once: true })
  } else {
    bootstrap()
  }
})()
