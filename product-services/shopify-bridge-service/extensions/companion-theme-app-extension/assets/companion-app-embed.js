(function () {
  function bootstrap() {
    var root = document.getElementById('loom-companion-embed-root')
    if (!root) {
      return
    }

    var bridgeBaseUrl = (root.dataset.bridgeBaseUrl || '').trim()
    var shopDomain = (root.dataset.shopDomain || '').trim()
    var launcherLabel = (root.dataset.launcherLabel || 'Ask the store assistant').trim()

    if (!bridgeBaseUrl || !shopDomain) {
      root.dataset.status = 'configuration-required'
      return
    }

    resolveBootstrap(root, bridgeBaseUrl, shopDomain, launcherLabel)
  }

  function resolveBootstrap(root, bridgeBaseUrl, shopDomain, launcherLabel) {
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
        renderWidget(root, bridgeBaseUrl, launcherLabel, payload)
      })
      .catch(function (error) {
        root.dataset.status = 'failed'
        root.textContent = error && error.message ? error.message : 'Store assistant bootstrap failed.'
      })
  }

  function renderWidget(root, bridgeBaseUrl, launcherLabel, payload) {
    root.dataset.status = 'ready'
    root.textContent = ''

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
          content: payload.message || 'Store assistant is ready. Ask about products, policies, or collections.',
        },
      ],
    }

    var button = document.createElement('button')
    button.type = 'button'
    button.className = 'loom-companion-launcher'
    button.setAttribute('aria-haspopup', 'dialog')
    button.textContent = launcherLabel

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
        row.textContent = message.content
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
          content: state.messages.length > 1 ? state.messages[state.messages.length - 1].content : '',
          maxSuggestions: 4,
        }),
      })
        .then(function (response) {
          state.suggestions = extractSuggestions(response)
          render()
        })
        .catch(function () {
          state.suggestions = [
            'Show me your best sellers',
            'What is your shipping policy?',
            'Compare your top product categories',
            'What should I buy for travel?',
          ]
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
        }),
      })
        .then(function (response) {
          state.conversationId = response.conversationId || state.conversationId
          state.messages.push({
            role: 'assistant',
            content: extractAssistantMessage(response),
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

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bootstrap, { once: true })
  } else {
    bootstrap()
  }
})()
