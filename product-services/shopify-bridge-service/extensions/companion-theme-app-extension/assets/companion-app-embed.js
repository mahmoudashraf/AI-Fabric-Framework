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
        renderLauncher(root, bridgeBaseUrl, launcherLabel, payload)
      })
      .catch(function (error) {
        root.dataset.status = 'failed'
        root.textContent = error && error.message ? error.message : 'Store assistant bootstrap failed.'
      })
  }

  function renderLauncher(root, bridgeBaseUrl, launcherLabel, payload) {
    root.dataset.status = 'ready'
    root.textContent = ''

    var button = document.createElement('button')
    button.type = 'button'
    button.className = 'loom-companion-launcher'
    button.setAttribute('aria-haspopup', 'dialog')
    button.textContent = launcherLabel

    var panel = document.createElement('aside')
    panel.className = 'loom-companion-panel'
    panel.hidden = true
    panel.innerHTML =
      '<header class="loom-companion-panel__header"><strong>Store assistant</strong></header>' +
      '<div class="loom-companion-panel__body">' +
      '<p>Bridge: ' + escapeHtml(bridgeBaseUrl) + '</p>' +
      '<p>Consumer: ' + escapeHtml(payload.consumerId || '—') + '</p>' +
      '<p>Deployment: ' + escapeHtml(payload.deploymentId || '—') + '</p>' +
      '<p>Query path: ' + escapeHtml(payload.bridgeQueryUrl || '—') + '</p>' +
      '<p>' + escapeHtml(payload.message || 'Storefront bootstrap resolved.') + '</p>' +
      '</div>'

    button.addEventListener('click', function () {
      panel.hidden = !panel.hidden
      root.dataset.open = panel.hidden ? 'false' : 'true'
    })

    root.appendChild(button)
    root.appendChild(panel)
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
