(function () {
  var REGISTRY_KEY = '__loomCompanionEmbeddedSurfaces'
  var ROOT_ID_PREFIX = 'loom-companion-embedded-surfaces'
  var CACHE_PREFIX = 'loom-companion-surface-cache:'
  var CACHE_TTL_MS = 5 * 60 * 1000

  function render(options) {
    if (!options || !options.payload || !options.root) {
      return
    }
    teardown(options.root)
    var enabledSurfaces = normalizeEnabledSurfaces(options.payload.enabledSurfaces)
    if (enabledSurfaces.length === 0) {
      return
    }
    var surfaceScope = normalizeSurfaceScope(options.surfaceScope)
    if (surfaceScope !== 'all' && enabledSurfaces.indexOf(surfaceScope) < 0) {
      return
    }

    var pageType = normalizePageType(options.storefrontContext)
    var shellModeProfile = normalizeShellModeProfile(options.payload.shellModeProfile)
    var queryCoordinator = createSurfaceQueryCoordinator(options, shellModeProfile)
    var mountMode = normalizeMountMode(options.mountMode)
    var mountTarget = resolveMountTarget(options.root, mountMode)
    if (!mountTarget) {
      return
    }
    var host = document.createElement('div')
    host.id = ROOT_ID_PREFIX + '-' + ensureRootKey(options.root)
    host.dataset.loomCompanionSurfaceHost = ensureRootKey(options.root)
    host.className = mountMode === 'inline'
      ? 'loom-companion-surfaces loom-companion-surfaces--inline'
      : 'loom-companion-surfaces'

    if ((surfaceScope === 'all' || surfaceScope === 'ai-search') && enabledSurfaces.indexOf('ai-search') >= 0) {
      host.appendChild(renderSearchDock(options, shellModeProfile, queryCoordinator))
    }

    if (surfaceScope === 'product-insight' && pageType === 'product') {
      host.appendChild(renderStandaloneInsightCard(options, shellModeProfile, queryCoordinator))
    } else if (surfaceScope === 'policy-strip' && pageType === 'product') {
      host.appendChild(renderStandalonePolicyCard(options, shellModeProfile, queryCoordinator))
    } else if (surfaceScope === 'product-faq' && pageType === 'product') {
      host.appendChild(renderStandaloneFaqCard(options, shellModeProfile))
    } else if (surfaceScope === 'comparison' && pageType === 'product') {
      host.appendChild(renderStandaloneComparisonCard(options, shellModeProfile))
    } else if (
      surfaceScope === 'contextual-pill' &&
      (pageType === 'product' || pageType === 'collection')
    ) {
      host.appendChild(renderStandaloneContextualCard(options, shellModeProfile))
    } else if (pageType === 'product' && surfaceScope === 'all') {
      host.appendChild(renderProductInsightCluster(options, enabledSurfaces, shellModeProfile, queryCoordinator))
      if (enabledSurfaces.indexOf('contextual-pill') >= 0) {
        host.appendChild(renderContextualPillBar(options, shellModeProfile, queryCoordinator))
      }
    } else if (pageType === 'collection' && surfaceScope === 'all' && enabledSurfaces.indexOf('contextual-pill') >= 0) {
      host.appendChild(renderCollectionPillBar(options, shellModeProfile, queryCoordinator))
    }

    if (host.childNodes.length > 0) {
      if (mountMode === 'inline') {
        options.root.textContent = ''
      }
      mountTarget.appendChild(host)
    }
  }

  function teardown(root) {
    if (!root) {
      return
    }
    var existing = document.getElementById(ROOT_ID_PREFIX + '-' + ensureRootKey(root))
    if (existing) {
      existing.remove()
    }
  }

  function createSurfaceQueryCoordinator(options, shellModeProfile) {
    var inlineHandler = null
    return {
      bindInlineHandler: function (handler) {
        inlineHandler = typeof handler === 'function' ? handler : null
      },
      dispatch: function (query, position, mode, requestContext) {
        if (sendPrompt(query, position, mode, requestContext)) {
          return true
        }
        if (!inlineHandler) {
          return false
        }
        inlineHandler({
          query: query,
          position: position,
          mode: mode,
          requestContext: requestContext || createRequestContext(options.storefrontContext, shellModeProfile),
        })
        return true
      },
    }
  }

  function renderSearchDock(options, shellModeProfile, queryCoordinator) {
    var dock = document.createElement('section')
    dock.className = 'loom-companion-surface-card loom-companion-surface-card--search'
    var state = {
      conversationId: null,
      isLoading: false,
      suggestionsLoaded: false,
      suggestions: searchQuickActions(options.storefrontContext, shellModeProfile),
      lastQuery: '',
      resultMessage: '',
      resultProducts: [],
      resultSources: [],
      errorMessage: '',
    }

    var eyebrow = document.createElement('div')
    eyebrow.className = 'loom-companion-surface-eyebrow'
    eyebrow.textContent = 'AI search'
    dock.appendChild(eyebrow)

    var title = document.createElement('h3')
    title.className = 'loom-companion-surface-title'
    title.textContent = searchDockTitle(shellModeProfile)
    dock.appendChild(title)

    var description = document.createElement('p')
    description.className = 'loom-companion-surface-copy'
    description.textContent = searchDockDescription(options.storefrontContext)
    dock.appendChild(description)

    if (options.payload.poweredByBadgeRequired) {
      dock.appendChild(renderPoweredByBadge())
    }

    var status = document.createElement('div')
    status.className = 'loom-companion-surface-status'
    status.hidden = true
    dock.appendChild(status)

    var form = document.createElement('form')
    form.className = 'loom-companion-search-form'
    var input = document.createElement('input')
    input.type = 'search'
    input.className = 'loom-companion-search-input'
    input.placeholder = searchPlaceholder(shellModeProfile)
    input.maxLength = 180
    var submit = document.createElement('button')
    submit.type = 'submit'
    submit.className = 'loom-companion-primary-button'
    submit.textContent = 'Search'
    form.appendChild(input)
    form.appendChild(submit)
    form.addEventListener('submit', function (event) {
      event.preventDefault()
      var query = trimValue(input.value)
      if (!query) {
        return
      }
      submitInlineQuery(query)
      input.value = ''
    })
    dock.appendChild(form)

    var quickRow = document.createElement('div')
    quickRow.className = 'loom-companion-chip-row'
    dock.appendChild(quickRow)

    var results = document.createElement('div')
    results.className = 'loom-companion-surface-results'
    results.hidden = true

    var resultsLabel = document.createElement('div')
    resultsLabel.className = 'loom-companion-surface-results__label'
    results.appendChild(resultsLabel)

    var resultsSummary = document.createElement('div')
    resultsSummary.className = 'loom-companion-surface-copy loom-companion-surface-copy--results'
    results.appendChild(resultsSummary)

    var cardsHost = document.createElement('div')
    cardsHost.className = 'loom-companion-surface-results__cards'
    results.appendChild(cardsHost)

    var actions = document.createElement('div')
    actions.className = 'loom-companion-chip-row loom-companion-chip-row--results'
    results.appendChild(actions)

    var continueButton = document.createElement('button')
    continueButton.type = 'button'
    continueButton.className = 'loom-companion-chip'
    continueButton.textContent = 'Continue in assistant'
    continueButton.addEventListener('click', function () {
      if (!state.lastQuery) {
        return
      }
      sendPrompt(
        state.lastQuery,
        'search',
        defaultSearchMode(shellModeProfile),
        createRequestContext(options.storefrontContext, shellModeProfile, 'ai-search')
      )
    })
    actions.appendChild(continueButton)

    dock.appendChild(results)

    queryCoordinator.bindInlineHandler(function (request) {
      if (!request || !request.query) {
        return
      }
      submitInlineQuery(request.query, request.mode, request.requestContext)
    })

    refreshSuggestions()
    renderQuickActions()
    renderResults()

    return dock

    function submitInlineQuery(query, mode, requestContext) {
      if (state.isLoading) {
        return
      }
      state.isLoading = true
      state.errorMessage = ''
      state.lastQuery = query
      renderResults()
      fetchJson(options.payload.bridgeQueryUrl, {
        method: 'POST',
        headers: shopperHeaders(options),
        body: JSON.stringify({
          query: query,
          mode: mode || defaultSearchMode(shellModeProfile),
          conversationId: state.conversationId || undefined,
          storefrontContext: requestContext || createRequestContext(options.storefrontContext, shellModeProfile, 'ai-search'),
        }),
      })
        .then(function (response) {
          state.conversationId = response.conversationId || state.conversationId
          state.resultMessage = extractAssistantMessage(response) || 'Companion found a result but did not return a summary.'
          state.resultProducts = extractProductCards(response)
          state.resultSources = extractSourceCards(response)
          var nextSuggestions = extractSuggestions(response)
          if (nextSuggestions.length > 0) {
            state.suggestions = nextSuggestions
            renderQuickActions()
          }
          recordSurfaceEvent(options, 'SEARCH_SUBMITTED')
        })
        .catch(function (error) {
          state.resultMessage = ''
          state.resultProducts = []
          state.resultSources = []
          state.errorMessage = error && error.message ? error.message : 'Companion search is not available right now.'
        })
        .finally(function () {
          state.isLoading = false
          renderResults()
        })
    }

    function refreshSuggestions() {
      if (state.suggestionsLoaded || !options.payload.bridgeSuggestionsUrl) {
        return
      }
      state.suggestionsLoaded = true
      fetchJson(options.payload.bridgeSuggestionsUrl, {
        method: 'POST',
        headers: shopperHeaders(options),
        body: JSON.stringify({
          content: buildContextPrompt(options.storefrontContext),
          maxSuggestions: 4,
          storefrontContext: createRequestContext(options.storefrontContext, shellModeProfile, 'ai-search'),
        }),
      })
        .then(function (response) {
          var nextSuggestions = extractSuggestions(response)
          if (nextSuggestions.length > 0) {
            state.suggestions = nextSuggestions
            renderQuickActions()
          }
        })
        .catch(function () {
          return null
        })
    }

    function renderQuickActions() {
      quickRow.innerHTML = ''
      state.suggestions.forEach(function (item) {
        var label = typeof item === 'string' ? item : item.label
        var query = typeof item === 'string' ? item : item.query
        quickRow.appendChild(createChipButton(label, function () {
          submitInlineQuery(
            query,
            defaultSearchMode(shellModeProfile),
            createRequestContext(options.storefrontContext, shellModeProfile, 'ai-search')
          )
        }))
      })
    }

    function renderResults() {
      status.hidden = !(state.isLoading || state.errorMessage)
      if (state.isLoading) {
        status.textContent = 'Searching the catalog…'
      } else if (state.errorMessage) {
        status.textContent = state.errorMessage
      } else {
        status.textContent = ''
      }

      var hasResults = !!(state.resultMessage || state.resultProducts.length > 0 || state.resultSources.length > 0)
      results.hidden = !hasResults
      if (!hasResults) {
        return
      }

      resultsLabel.textContent = state.lastQuery
        ? 'Result for "' + truncateText(state.lastQuery, 48) + '"'
        : 'Companion result'
      resultsSummary.textContent = truncateText(state.resultMessage || 'Companion returned a result.', 320)
      cardsHost.innerHTML = ''

      if (state.resultProducts.length > 0) {
        cardsHost.appendChild(renderCardGroup('Matched products', state.resultProducts, 'Open product'))
      }
      if (state.resultSources.length > 0) {
        cardsHost.appendChild(renderCardGroup('Grounding sources', state.resultSources, 'Open source'))
      }

      continueButton.hidden = !window.MaxMode || typeof window.MaxMode.sendMessage !== 'function'
    }
  }

  function renderProductInsightCluster(options, enabledSurfaces, shellModeProfile, queryCoordinator) {
    var cluster = document.createElement('section')
    cluster.className = 'loom-companion-surface-stack'

    var summaryCard = document.createElement('article')
    summaryCard.className = 'loom-companion-surface-card loom-companion-surface-card--insight'

    var eyebrow = document.createElement('div')
    eyebrow.className = 'loom-companion-surface-eyebrow'
    eyebrow.textContent = shellModeProfile === 'GUIDED_SUPPORT' ? 'Shopping help' : 'Product insight'
    summaryCard.appendChild(eyebrow)

    var title = document.createElement('h3')
    title.className = 'loom-companion-surface-title'
    title.textContent = insightTitle(options.storefrontContext)
    summaryCard.appendChild(title)

    var body = document.createElement('div')
    body.className = 'loom-companion-surface-copy'
    body.textContent = 'Loading companion insight…'
    summaryCard.appendChild(body)

    if (enabledSurfaces.indexOf('policy-strip') >= 0) {
      var policyStrip = document.createElement('div')
      policyStrip.className = 'loom-companion-policy-strip'
      policyStrip.textContent = 'Loading shipping and return guidance…'
      summaryCard.appendChild(policyStrip)
      void resolveSurfaceSummary(
        options,
        'policy-strip',
        policyPrompt(options.storefrontContext, shellModeProfile),
        defaultInsightMode(shellModeProfile),
        shellModeProfile
      ).then(function (message) {
        policyStrip.textContent = truncateText(message || 'Policy details are not available yet.', 180)
      })
    }

    var footer = document.createElement('div')
    footer.className = 'loom-companion-chip-row'
    if (enabledSurfaces.indexOf('product-faq') >= 0) {
      faqPrompts(options.storefrontContext).forEach(function (item) {
        footer.appendChild(createChipButton(item.label, function () {
          queryCoordinator.dispatch(
            item.query,
            'catalog',
            defaultInsightMode(shellModeProfile),
            createRequestContext(options.storefrontContext, shellModeProfile, 'product-faq')
          )
        }))
      })
    }
    if (enabledSurfaces.indexOf('comparison') >= 0) {
      footer.appendChild(createChipButton('Compare options', function () {
        queryCoordinator.dispatch(
          comparePrompt(options.storefrontContext, shellModeProfile),
          'catalog',
          defaultInsightMode(shellModeProfile),
          createRequestContext(options.storefrontContext, shellModeProfile, 'comparison')
        )
      }))
    }
    footer.appendChild(createChipButton('Ask more', function () {
      queryCoordinator.dispatch(
        insightPrompt(options.storefrontContext, shellModeProfile),
        'catalog',
        defaultInsightMode(shellModeProfile),
        createRequestContext(options.storefrontContext, shellModeProfile, 'product-insight')
      )
    }))
    summaryCard.appendChild(footer)
    appendGovernedCommercePanel(summaryCard, options, 'product-insight', shellModeProfile)

    cluster.appendChild(summaryCard)

    if (enabledSurfaces.indexOf('product-insight') >= 0) {
      void resolveSurfaceSummary(
        options,
        'product-insight',
        insightPrompt(options.storefrontContext, shellModeProfile),
        defaultInsightMode(shellModeProfile),
        shellModeProfile
      ).then(function (message) {
        body.textContent = truncateText(message || 'Companion insight is not available yet.', 260)
      })
    } else {
      body.textContent = 'Ask the companion to compare this product, explain it, or answer policy questions.'
    }

    return cluster
  }

  function renderStandaloneInsightCard(options, shellModeProfile, _queryCoordinator) {
    var card = document.createElement('article')
    card.className = 'loom-companion-surface-card loom-companion-surface-card--insight'

    var eyebrow = document.createElement('div')
    eyebrow.className = 'loom-companion-surface-eyebrow'
    eyebrow.textContent = shellModeProfile === 'GUIDED_SUPPORT' ? 'Shopping help' : 'Product insight'
    card.appendChild(eyebrow)

    var title = document.createElement('h3')
    title.className = 'loom-companion-surface-title'
    title.textContent = insightTitle(options.storefrontContext)
    card.appendChild(title)

    var body = document.createElement('div')
    body.className = 'loom-companion-surface-copy'
    body.textContent = 'Loading companion insight…'
    card.appendChild(body)

    void resolveSurfaceSummary(
      options,
      'product-insight',
      insightPrompt(options.storefrontContext, shellModeProfile),
      defaultInsightMode(shellModeProfile),
      shellModeProfile
    ).then(function (message) {
      body.textContent = truncateText(message || 'Companion insight is not available yet.', 260)
    })

    appendGovernedCommercePanel(card, options, 'product-insight', shellModeProfile)

    return card
  }

  function renderStandalonePolicyCard(options, shellModeProfile, _queryCoordinator) {
    var card = document.createElement('article')
    card.className = 'loom-companion-surface-card loom-companion-surface-card--policy'

    var eyebrow = document.createElement('div')
    eyebrow.className = 'loom-companion-surface-eyebrow'
    eyebrow.textContent = shellModeProfile === 'GUIDED_SUPPORT' ? 'Policy help' : 'Policy strip'
    card.appendChild(eyebrow)

    var title = document.createElement('h3')
    title.className = 'loom-companion-surface-title'
    title.textContent = policyTitle(options.storefrontContext)
    card.appendChild(title)

    var body = document.createElement('div')
    body.className = 'loom-companion-surface-copy'
    body.textContent = 'Loading shipping and return guidance…'
    card.appendChild(body)

    void resolveSurfaceSummary(
      options,
      'policy-strip',
      policyPrompt(options.storefrontContext, shellModeProfile),
      defaultInsightMode(shellModeProfile),
      shellModeProfile
    ).then(function (message) {
      body.textContent = truncateText(message || 'Policy details are not available yet.', 220)
    })

    return card
  }

  function renderStandaloneFaqCard(options, shellModeProfile) {
    return renderStandalonePromptCard(options, {
      surfaceId: 'product-faq',
      surfaceClassName: 'loom-companion-surface-card--faq',
      eyebrow: 'Product FAQ',
      title: faqTitle(options.storefrontContext),
      description: faqDescription(options.storefrontContext),
      prompts: faqPrompts(options.storefrontContext),
      promptVariant: 'chip',
      loadingLabel: 'Loading companion FAQ…',
      emptyLabel: 'Choose a shopper question to see a grounded answer.',
      analyticsEventType: 'PRODUCT_FAQ_CLICKED',
      shellModeProfile: shellModeProfile,
    })
  }

  function renderStandaloneComparisonCard(options, shellModeProfile) {
    return renderStandalonePromptCard(options, {
      surfaceId: 'comparison',
      surfaceClassName: 'loom-companion-surface-card--comparison',
      eyebrow: 'Comparison',
      title: compareTitle(options.storefrontContext),
      description: compareDescription(options.storefrontContext, shellModeProfile),
      prompts: compareSurfacePrompts(options.storefrontContext, shellModeProfile),
      promptVariant: 'chip',
      loadingLabel: 'Loading companion comparison…',
      emptyLabel: 'Choose a comparison prompt to surface grounded tradeoffs before opening chat.',
      analyticsEventType: 'COMPARISON_CLICKED',
      shellModeProfile: shellModeProfile,
    })
  }

  function renderStandaloneContextualCard(options, shellModeProfile) {
    var pageType = normalizePageType(options.storefrontContext)
    var prompts = pageType === 'collection'
      ? collectionPrompts(options.storefrontContext, shellModeProfile)
      : contextualPillPrompts(options.storefrontContext, shellModeProfile)

    return renderStandalonePromptCard(options, {
      surfaceId: 'contextual-pill',
      surfaceClassName: 'loom-companion-surface-card--contextual',
      eyebrow: pageType === 'collection' ? 'Collection guidance' : 'Contextual pill',
      title: contextualCardTitle(options.storefrontContext),
      description: contextualCardDescription(options.storefrontContext, shellModeProfile),
      prompts: prompts,
      promptVariant: 'pill',
      loadingLabel: 'Loading companion guidance…',
      emptyLabel: 'Choose a shortcut to see grounded guidance for this page.',
      analyticsEventType: 'CONTEXTUAL_PROMPT_CLICKED',
      shellModeProfile: shellModeProfile,
    })
  }

  function renderStandalonePromptCard(options, config) {
    var card = document.createElement('article')
    card.className = 'loom-companion-surface-card ' + config.surfaceClassName

    var eyebrow = document.createElement('div')
    eyebrow.className = 'loom-companion-surface-eyebrow'
    eyebrow.textContent = config.eyebrow
    card.appendChild(eyebrow)

    var title = document.createElement('h3')
    title.className = 'loom-companion-surface-title'
    title.textContent = config.title
    card.appendChild(title)

    var description = document.createElement('p')
    description.className = 'loom-companion-surface-copy'
    description.textContent = config.description
    card.appendChild(description)

    if (options.payload.poweredByBadgeRequired) {
      card.appendChild(renderPoweredByBadge())
    }

    var status = document.createElement('div')
    status.className = 'loom-companion-surface-status'
    status.hidden = true
    card.appendChild(status)

    var promptRow = document.createElement('div')
    promptRow.className = 'loom-companion-chip-row loom-companion-chip-row--surface-prompts'
    card.appendChild(promptRow)

    var results = document.createElement('div')
    results.className = 'loom-companion-surface-results'
    results.hidden = true

    var resultsLabel = document.createElement('div')
    resultsLabel.className = 'loom-companion-surface-results__label'
    results.appendChild(resultsLabel)

    var resultsSummary = document.createElement('div')
    resultsSummary.className = 'loom-companion-surface-copy loom-companion-surface-copy--results'
    results.appendChild(resultsSummary)

    var cardsHost = document.createElement('div')
    cardsHost.className = 'loom-companion-surface-results__cards'
    results.appendChild(cardsHost)

    var actions = document.createElement('div')
    actions.className = 'loom-companion-chip-row loom-companion-chip-row--results'
    results.appendChild(actions)

    var continueButton = document.createElement('button')
    continueButton.type = 'button'
    continueButton.className = 'loom-companion-chip'
    continueButton.textContent = 'Continue in assistant'
    continueButton.addEventListener('click', function () {
      if (!state.lastQuery) {
        return
      }
      sendPrompt(
        state.lastQuery,
        state.lastPosition || 'catalog',
        state.lastMode || defaultInsightMode(config.shellModeProfile),
        state.lastRequestContext ||
          createRequestContext(options.storefrontContext, config.shellModeProfile, config.surfaceId)
      )
    })
    actions.appendChild(continueButton)

    card.appendChild(results)
    appendGovernedCommercePanel(card, options, config.surfaceId, config.shellModeProfile)

    var state = {
      conversationId: null,
      isLoading: false,
      errorMessage: '',
      lastLabel: '',
      lastQuery: '',
      lastPosition: null,
      lastMode: null,
      lastRequestContext: null,
      resultMessage: '',
      resultProducts: [],
      resultSources: [],
    }

    renderPromptButtons()
    renderResults()

    return card

    function renderPromptButtons() {
      promptRow.innerHTML = ''
      if (!config.prompts || config.prompts.length === 0) {
        var empty = document.createElement('div')
        empty.className = 'loom-companion-surface-copy loom-companion-surface-copy--muted'
        empty.textContent = config.emptyLabel
        promptRow.appendChild(empty)
        return
      }
      config.prompts.forEach(function (item) {
        var buttonFactory = config.promptVariant === 'pill' ? createPillButton : createChipButton
        promptRow.appendChild(
          buttonFactory(item.label, function () {
            submitPrompt(item)
          })
        )
      })
    }

    function submitPrompt(item) {
      if (!item || !item.query || state.isLoading) {
        return
      }
      state.isLoading = true
      state.errorMessage = ''
      state.lastLabel = item.label || config.title
      state.lastQuery = item.query
      state.lastPosition = item.position || 'catalog'
      state.lastMode = item.mode || defaultInsightMode(config.shellModeProfile)
      state.lastRequestContext =
        item.requestContext ||
        createRequestContext(options.storefrontContext, config.shellModeProfile, item.surfaceId || config.surfaceId)
      renderResults()

      fetchJson(options.payload.bridgeQueryUrl, {
        method: 'POST',
        headers: shopperHeaders(options),
        body: JSON.stringify({
          query: state.lastQuery,
          mode: state.lastMode,
          conversationId: state.conversationId || undefined,
          storefrontContext: state.lastRequestContext,
        }),
      })
        .then(function (response) {
          state.conversationId = response.conversationId || state.conversationId
          state.resultMessage = extractAssistantMessage(response) || 'Companion returned a result without a summary.'
          state.resultProducts = extractProductCards(response)
          state.resultSources = extractSourceCards(response)
          recordSurfaceEvent(options, config.analyticsEventType)
        })
        .catch(function (error) {
          state.resultMessage = ''
          state.resultProducts = []
          state.resultSources = []
          state.errorMessage = error && error.message ? error.message : 'Companion is not available right now.'
        })
        .finally(function () {
          state.isLoading = false
          renderResults()
        })
    }

    function renderResults() {
      status.hidden = !(state.isLoading || state.errorMessage)
      if (state.isLoading) {
        status.textContent = config.loadingLabel
      } else if (state.errorMessage) {
        status.textContent = state.errorMessage
      } else {
        status.textContent = ''
      }

      var hasResults = !!(state.resultMessage || state.resultProducts.length > 0 || state.resultSources.length > 0)
      results.hidden = !hasResults
      if (!hasResults) {
        return
      }

      resultsLabel.textContent = state.lastLabel ? state.lastLabel : config.title
      resultsSummary.textContent = truncateText(state.resultMessage || 'Companion returned a result.', 320)
      cardsHost.innerHTML = ''

      if (state.resultProducts.length > 0) {
        cardsHost.appendChild(renderCardGroup('Matched products', state.resultProducts, 'Open product'))
      }
      if (state.resultSources.length > 0) {
        cardsHost.appendChild(renderCardGroup('Grounding sources', state.resultSources, 'Open source'))
      }

      continueButton.hidden = !window.MaxMode || typeof window.MaxMode.sendMessage !== 'function'
    }
  }

  function appendGovernedCommercePanel(card, options, surfaceId, shellModeProfile) {
    if (!card || !isGovernedCommerceAvailable(options, surfaceId)) {
      return
    }
    card.appendChild(renderGovernedCommercePanel(options, surfaceId, shellModeProfile))
  }

  function renderGovernedCommercePanel(options, surfaceId, shellModeProfile) {
    var panel = document.createElement('section')
    panel.className = 'loom-companion-governed-actions'

    var title = document.createElement('div')
    title.className = 'loom-companion-governed-actions__title'
    title.textContent = 'Elite guided commerce'
    panel.appendChild(title)

    var copy = document.createElement('p')
    copy.className = 'loom-companion-surface-copy loom-companion-surface-copy--muted'
    copy.textContent = 'Companion can add the current variant to cart and update quantity with explicit confirmation and bridge audit.'
    panel.appendChild(copy)

    var status = document.createElement('div')
    status.className = 'loom-companion-surface-status'
    status.hidden = true
    panel.appendChild(status)

    var buttons = document.createElement('div')
    buttons.className = 'loom-companion-chip-row loom-companion-chip-row--actions'
    panel.appendChild(buttons)

    var cartSnapshot = document.createElement('div')
    cartSnapshot.className = 'loom-companion-governed-actions__meta'
    panel.appendChild(cartSnapshot)

    var state = {
      busy: false,
      message: '',
      currentVariantId: resolveSelectedVariantId(options),
      cartLineKey: null,
      cartQuantity: 0,
    }

    var addButton = createPrimaryActionButton('Add to cart', function () {
      submitGovernedAction('ADD_TO_CART', {
        requestedQuantity: resolveRequestedQuantity(),
      })
    })
    buttons.appendChild(addButton)

    var addOneButton = createChipButton('Add one more', function () {
      submitGovernedAction('ADD_TO_CART', {
        requestedQuantity: 1,
      })
    })
    buttons.appendChild(addOneButton)

    var removeOneButton = createChipButton('Remove one', function () {
      submitGovernedAction('UPDATE_CART_QUANTITY', {
        targetQuantity: Math.max(0, state.cartQuantity - 1),
        cartLineKey: state.cartLineKey,
      })
    })
    buttons.appendChild(removeOneButton)

    var guidanceButton = createChipButton('Variant guidance', function () {
      var guidanceQuery = variantGuidancePrompt(options.storefrontContext, shellModeProfile)
      if (
        !sendPrompt(
          guidanceQuery,
          'catalog',
          defaultInsightMode(shellModeProfile),
          createRequestContext(options.storefrontContext, shellModeProfile, 'guided-commerce')
        )
      ) {
        status.hidden = false
        status.textContent = 'Open the assistant shell to continue with variant guidance.'
      }
    })
    buttons.appendChild(guidanceButton)

    refreshCartState()
    render()

    return panel

    function resolveRequestedQuantity() {
      var quantityInput = document.querySelector('form[action*="/cart/add"] [name="quantity"]')
      var parsed = quantityInput && quantityInput.value ? parseInt(String(quantityInput.value), 10) : 1
      if (!parsed || parsed < 1) {
        return 1
      }
      if (parsed > 10) {
        return 10
      }
      return parsed
    }

    function refreshCartState() {
      state.currentVariantId = resolveSelectedVariantId(options)
      if (!state.currentVariantId) {
        state.cartLineKey = null
        state.cartQuantity = 0
        render()
        return
      }
      fetchJson(window.location.origin + '/cart.js', {
        method: 'GET',
        headers: {
          Accept: 'application/json',
        },
      })
        .then(function (cart) {
          var item = findCartItemForVariant(cart, state.currentVariantId)
          state.cartLineKey = item ? trimValue(item.key) : null
          state.cartQuantity = item && item.quantity ? Number(item.quantity) : 0
          render()
        })
        .catch(function () {
          state.cartLineKey = null
          state.cartQuantity = 0
          render()
        })
    }

    function submitGovernedAction(actionType, request) {
      if (state.busy) {
        return
      }
      state.currentVariantId = resolveSelectedVariantId(options)
      if (!state.currentVariantId) {
        state.message = 'Select a Shopify variant before using guided commerce.'
        render()
        return
      }
      if ((options.payload.actionCapability || {}).requiresExplicitConfirmation) {
        var confirmed = window.confirm(actionConfirmationMessage(actionType, request))
        if (!confirmed) {
          return
        }
      }
      state.busy = true
      state.message = ''
      render()

      fetchJson(options.payload.actionCapability.grantUrl, {
        method: 'POST',
        headers: shopperHeaders(options),
        body: JSON.stringify({
          actionType: actionType,
          surfaceId: surfaceId,
          pageType: normalizePageType(options.storefrontContext),
          productId: options.storefrontContext && options.storefrontContext.product ? options.storefrontContext.product.id : null,
          productHandle: options.storefrontContext && options.storefrontContext.product ? options.storefrontContext.product.handle : null,
          productTitle: options.storefrontContext && options.storefrontContext.product ? options.storefrontContext.product.title : null,
          variantId: state.currentVariantId,
          requestedQuantity: request.requestedQuantity,
          targetQuantity: request.targetQuantity,
          cartLineKey: request.cartLineKey,
          confirmationAccepted: true,
        }),
      })
        .then(function (grant) {
          return executeGovernedCommerceOperation(grant)
            .then(function (nativeResult) {
              return fetchJson(options.payload.actionCapability.completeUrl, {
                method: 'POST',
                headers: shopperHeaders(options),
                body: JSON.stringify({
                  auditId: grant.auditId,
                  token: grant.token,
                  status: 'COMPLETED',
                  message: nativeResult.message,
                  cartLineKey: nativeResult.cartLineKey,
                  resultingQuantity: nativeResult.resultingQuantity,
                }),
              })
            })
            .catch(function (error) {
              return fetchJson(options.payload.actionCapability.completeUrl, {
                method: 'POST',
                headers: shopperHeaders(options),
                body: JSON.stringify({
                  auditId: grant.auditId,
                  token: grant.token,
                  status: 'FAILED',
                  message: error && error.message ? error.message : 'Guided commerce action failed.',
                  cartLineKey: request.cartLineKey || null,
                  resultingQuantity: state.cartQuantity,
                }),
              }).then(function () {
                throw error
              })
            })
        })
        .then(function (audit) {
          state.message = audit && audit.message ? audit.message : 'Guided commerce action completed.'
          announceCartRefresh()
          refreshCartState()
        })
        .catch(function (error) {
          state.message = error && error.message ? error.message : 'Guided commerce action failed.'
        })
        .finally(function () {
          state.busy = false
          render()
        })
    }

    function render() {
      var hasVariant = !!state.currentVariantId
      addButton.disabled = state.busy || !hasVariant
      addOneButton.disabled = state.busy || !hasVariant
      removeOneButton.disabled = state.busy || !hasVariant || !state.cartLineKey || state.cartQuantity < 1
      guidanceButton.disabled = state.busy
      addButton.textContent = state.busy ? 'Working…' : 'Add to cart'
      cartSnapshot.textContent = hasVariant
        ? 'Current variant ' + truncateText(state.currentVariantId, 18) + ' · cart quantity ' + state.cartQuantity
        : 'Select a variant to unlock governed commerce actions.'
      status.hidden = !state.message
      status.textContent = state.message
    }
  }

  function renderContextualPillBar(options, shellModeProfile, queryCoordinator) {
    var pillBar = document.createElement('section')
    pillBar.className = 'loom-companion-contextual-pillbar'
    contextualPillPrompts(options.storefrontContext, shellModeProfile).forEach(function (item) {
      pillBar.appendChild(createPillButton(item.label, function () {
        queryCoordinator.dispatch(
          item.query,
          item.position,
          item.mode,
          createRequestContext(options.storefrontContext, shellModeProfile, item.surfaceId || 'contextual-pill')
        )
      }))
    })
    return pillBar
  }

  function renderCollectionPillBar(options, shellModeProfile, queryCoordinator) {
    var pillBar = document.createElement('section')
    pillBar.className = 'loom-companion-contextual-pillbar loom-companion-contextual-pillbar--collection'
    collectionPrompts(options.storefrontContext, shellModeProfile).forEach(function (item) {
      pillBar.appendChild(createPillButton(item.label, function () {
        queryCoordinator.dispatch(
          item.query,
          item.position,
          item.mode,
          createRequestContext(options.storefrontContext, shellModeProfile, item.surfaceId || 'contextual-pill')
        )
      }))
    })
    return pillBar
  }

  function createChipButton(label, onClick) {
    var button = document.createElement('button')
    button.type = 'button'
    button.className = 'loom-companion-chip'
    button.textContent = label
    button.addEventListener('click', onClick)
    return button
  }

  function createPrimaryActionButton(label, onClick) {
    var button = document.createElement('button')
    button.type = 'button'
    button.className = 'loom-companion-primary-button'
    button.textContent = label
    button.addEventListener('click', onClick)
    return button
  }

  function createPillButton(label, onClick) {
    var button = document.createElement('button')
    button.type = 'button'
    button.className = 'loom-companion-pill'
    button.textContent = label
    button.addEventListener('click', onClick)
    return button
  }

  function renderPoweredByBadge() {
    var badge = document.createElement('div')
    badge.className = 'loom-companion-powered-by'
    badge.textContent = 'Powered by Loom Companion'
    return badge
  }

  function resolveSurfaceSummary(options, surfaceId, query, mode, shellModeProfile) {
    var cacheKey = CACHE_PREFIX + [
      options.payload.shopDomain || 'storefront',
      normalizePageType(options.storefrontContext),
      surfaceId,
      contextCacheKey(options.storefrontContext),
      normalizeShellModeProfile(options.payload.shellModeProfile),
    ].join(':')

    var cached = readCachedValue(cacheKey)
    if (cached) {
      return Promise.resolve(cached)
    }

    return fetchJson(options.payload.bridgeQueryUrl, {
      method: 'POST',
      headers: shopperHeaders(options),
      body: JSON.stringify({
        query: query,
        mode: mode,
        storefrontContext: createRequestContext(options.storefrontContext, shellModeProfile, surfaceId),
      }),
    })
      .then(function (response) {
        var message = extractAssistantMessage(response)
        if (message) {
          writeCachedValue(cacheKey, message)
        }
        return message
      })
      .catch(function () {
        return null
      })
  }

  function recordSurfaceEvent(options, eventType) {
    if (!options || !options.payload || !options.payload.bridgeEventUrl) {
      return
    }
    fetch(options.payload.bridgeEventUrl, {
      method: 'POST',
      headers: shopperHeaders(options),
      body: JSON.stringify({
        eventType: eventType,
        pageType: normalizePageType(options.storefrontContext),
        pageTitle: options.storefrontContext && options.storefrontContext.pageTitle ? options.storefrontContext.pageTitle : null,
        productHandle: options.storefrontContext && options.storefrontContext.product ? options.storefrontContext.product.handle : null,
        collectionHandle: options.storefrontContext && options.storefrontContext.collection ? options.storefrontContext.collection.handle : null,
      }),
    }).catch(function () {
      return null
    })
  }

  function buildContextPrompt(storefrontContext) {
    if (storefrontContext && storefrontContext.product) {
      var productParts = ['Current product: ' + storefrontContext.product.title]
      if (storefrontContext.product.vendor) {
        productParts.push('vendor ' + storefrontContext.product.vendor)
      }
      if (storefrontContext.product.type) {
        productParts.push('type ' + storefrontContext.product.type)
      }
      return productParts.join(', ')
    }
    if (storefrontContext && storefrontContext.collection) {
      return 'Current collection: ' + storefrontContext.collection.title
    }
    if (storefrontContext && storefrontContext.pageTitle) {
      return 'Current page: ' + storefrontContext.pageTitle
    }
    return ''
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

  function searchQuickActions(storefrontContext, shellModeProfile) {
    if (storefrontContext && storefrontContext.collection && storefrontContext.collection.title) {
      return [
        {
          label: 'Best in this collection',
          query: 'Show me the best options in ' + storefrontContext.collection.title,
        },
        {
          label: 'Compare top picks',
          query: 'Compare the top products in ' + storefrontContext.collection.title,
        },
      ]
    }
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return [
        { label: 'Shipping help', query: 'What is your shipping policy?' },
        { label: 'Return help', query: 'What is your return policy?' },
      ]
    }
    return [
      { label: 'Best sellers', query: 'Show me your best sellers' },
      { label: 'Gift ideas', query: 'Show me good gift ideas from this store' },
    ]
  }

  function contextualPillPrompts(storefrontContext, shellModeProfile) {
    var productTitle = productTitleForContext(storefrontContext)
    return [
      {
        label: 'Why this product?',
        query: insightPrompt(storefrontContext, shellModeProfile),
        position: 'catalog',
        mode: defaultInsightMode(shellModeProfile),
        surfaceId: 'contextual-pill',
      },
      {
        label: 'Compare options',
        query: comparePrompt(storefrontContext, shellModeProfile),
        position: 'catalog',
        mode: defaultInsightMode(shellModeProfile),
        surfaceId: 'comparison',
      },
      {
        label: shellModeProfile === 'GUIDED_SUPPORT' ? 'Return policy' : 'Shipping & returns',
        query: policyPrompt(storefrontContext, shellModeProfile),
        position: 'catalog',
        mode: defaultInsightMode(shellModeProfile),
        surfaceId: 'policy-strip',
      },
      {
        label: productTitle ? 'Ask about ' + truncateText(productTitle, 22) : 'Ask more',
        query: 'Help me understand whether this product is the right fit.',
        position: 'catalog',
        mode: defaultSearchMode(shellModeProfile),
        surfaceId: 'contextual-pill',
      },
    ]
  }

  function collectionPrompts(storefrontContext, shellModeProfile) {
    var collectionTitle = storefrontContext && storefrontContext.collection ? storefrontContext.collection.title : 'this collection'
    return [
      {
        label: 'Best in ' + truncateText(collectionTitle || 'this collection', 18),
        query: 'Show me the best products in ' + collectionTitle,
        position: 'search',
        mode: defaultSearchMode(shellModeProfile),
        surfaceId: 'contextual-pill',
      },
      {
        label: 'Compare top picks',
        query: 'Compare the top picks in ' + collectionTitle,
        position: 'search',
        mode: defaultInsightMode(shellModeProfile),
        surfaceId: 'comparison',
      },
      {
        label: shellModeProfile === 'GUIDED_SUPPORT' ? 'Policy help' : 'Ask the store',
        query: 'What store policies should I know before buying from this collection?',
        position: 'search',
        mode: defaultInsightMode(shellModeProfile),
        surfaceId: 'policy-strip',
      },
    ]
  }

  function faqPrompts(storefrontContext) {
    var productTitle = productTitleForContext(storefrontContext)
    if (!productTitle) {
      return []
    }
    return [
      {
        label: 'Use case',
        query: 'What kinds of shoppers or use cases is ' + productTitle + ' best for?',
      },
      {
        label: 'What to know',
        query: 'What should I know before buying ' + productTitle + '?',
      },
      {
        label: 'Who is it for?',
        query: 'Who is ' + productTitle + ' best for, and who should look at alternatives?',
      },
    ]
  }

  function compareSurfacePrompts(storefrontContext, shellModeProfile) {
    var productTitle = productTitleForContext(storefrontContext)
    if (!productTitle) {
      return [
        {
          label: 'Compare best fits',
          query: 'Compare the strongest options on this page and explain who should choose each one.',
          position: 'catalog',
          mode: defaultInsightMode(shellModeProfile),
          surfaceId: 'comparison',
        },
      ]
    }
    return [
      {
        label: 'Best alternative',
        query: 'What is the best alternative to "' + productTitle + '" in this store, and why?',
        position: 'catalog',
        mode: defaultInsightMode(shellModeProfile),
        surfaceId: 'comparison',
      },
      {
        label: 'Cheaper option',
        query: 'Compare "' + productTitle + '" with a lower-priced alternative in this store and explain the tradeoffs.',
        position: 'catalog',
        mode: defaultInsightMode(shellModeProfile),
        surfaceId: 'comparison',
      },
      {
        label: 'Who should choose it?',
        query: comparePrompt(storefrontContext, shellModeProfile),
        position: 'catalog',
        mode: defaultInsightMode(shellModeProfile),
        surfaceId: 'comparison',
      },
    ]
  }

  function insightPrompt(storefrontContext, shellModeProfile) {
    var productTitle = productTitleForContext(storefrontContext)
    if (!productTitle) {
      return 'Give me a concise store shopping insight based on the current page.'
    }
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return 'Give me a concise shopper support-style overview for the product "' + productTitle + '". Focus on fit, expectations, and what to check before buying.'
    }
    return 'Give me a concise shopper insight for the product "' + productTitle + '". Focus on what stands out, who it is best for, and practical buying considerations.'
  }

  function policyPrompt(storefrontContext, shellModeProfile) {
    var productTitle = productTitleForContext(storefrontContext)
    var prefix = shellModeProfile === 'GUIDED_SUPPORT'
      ? 'Summarize the most relevant support guidance'
      : 'Summarize the most relevant shipping and return policy'
    if (!productTitle) {
      return prefix + ' for shoppers on this page.'
    }
    return prefix + ' for shoppers considering "' + productTitle + '". Keep it concise and specific.'
  }

  function comparePrompt(storefrontContext, shellModeProfile) {
    var productTitle = productTitleForContext(storefrontContext)
    if (!productTitle) {
      return 'Compare the strongest options on this page and explain who should choose each one.'
    }
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return 'Compare "' + productTitle + '" with similar options in this store and tell me what a shopper should verify before choosing.'
    }
    return 'Compare "' + productTitle + '" with similar options in this store and explain who should choose each one.'
  }

  function variantGuidancePrompt(storefrontContext, shellModeProfile) {
    var productTitle = productTitleForContext(storefrontContext)
    if (!productTitle) {
      return 'Help me choose the right variant for the current product.'
    }
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return 'Help me choose the right variant of "' + productTitle + '" and explain what a shopper should verify before buying.'
    }
    return 'Help me choose the right variant of "' + productTitle + '" and explain the practical tradeoffs between options.'
  }

  function shopperHeaders(options) {
    return {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-AI-FABRIC-SHOPPER-SESSION-ID': getOrCreateShopperSessionId(options.payload.shopDomain || 'storefront'),
    }
  }

  function isGovernedCommerceAvailable(options, surfaceId) {
    if (!options || !options.payload || !options.payload.actionCapability) {
      return false
    }
    if (normalizePageType(options.storefrontContext) !== 'product') {
      return false
    }
    if (['product-insight', 'comparison', 'contextual-pill'].indexOf(surfaceId) < 0) {
      return false
    }
    return !!options.payload.actionCapability.available
  }

  function resolveSelectedVariantId(options) {
    var fromContext =
      options &&
      options.storefrontContext &&
      options.storefrontContext.product &&
      trimValue(options.storefrontContext.product.variantId)
    var fromForm = readCurrentProductFormValue()
    return fromForm || fromContext
  }

  function readCurrentProductFormValue() {
    var checkedInput = document.querySelector('form[action*="/cart/add"] input[name="id"]:checked')
    if (checkedInput && trimValue(checkedInput.value)) {
      return trimValue(checkedInput.value)
    }
    var selectedInput = document.querySelector('form[action*="/cart/add"] [name="id"]')
    if (selectedInput && trimValue(selectedInput.value)) {
      return trimValue(selectedInput.value)
    }
    return null
  }

  function findCartItemForVariant(cart, variantId) {
    var normalizedVariantId = trimValue(variantId)
    if (!cart || !Array.isArray(cart.items) || !normalizedVariantId) {
      return null
    }
    for (var i = 0; i < cart.items.length; i += 1) {
      var item = cart.items[i]
      if (trimValue(item.variant_id || item.id) === normalizedVariantId) {
        return item
      }
    }
    return null
  }

  function executeGovernedCommerceOperation(grant) {
    if (!grant || !grant.operationKind) {
      return Promise.reject(new Error('Guided commerce approval is missing operation details.'))
    }
    if (grant.operationKind === 'CART_ADD') {
      return fetchJson(window.location.origin + '/cart/add.js', {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          id: grant.variantId,
          quantity: grant.requestedQuantity || 1,
        }),
      }).then(function (payload) {
        return {
          cartLineKey: trimValue(payload.key) || null,
          resultingQuantity: payload && payload.quantity ? Number(payload.quantity) : grant.requestedQuantity || 1,
          message: 'Guided add-to-cart completed.',
        }
      })
    }
    if (grant.operationKind === 'CART_CHANGE') {
      return fetchJson(window.location.origin + '/cart/change.js', {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          id: grant.cartLineKey,
          quantity: grant.targetQuantity,
        }),
      }).then(function (payload) {
        var item = payload && Array.isArray(payload.items)
          ? payload.items.find(function (candidate) {
              return trimValue(candidate.key) === trimValue(grant.cartLineKey)
            })
          : null
        return {
          cartLineKey: trimValue(grant.cartLineKey) || null,
          resultingQuantity: item && item.quantity != null ? Number(item.quantity) : grant.targetQuantity || 0,
          message: 'Guided cart update completed.',
        }
      })
    }
    return Promise.reject(new Error('Unsupported guided commerce operation.'))
  }

  function announceCartRefresh() {
    document.dispatchEvent(new CustomEvent('cart:refresh'))
    document.dispatchEvent(new CustomEvent('loom-companion:cart-updated'))
  }

  function actionConfirmationMessage(actionType, request) {
    if (actionType === 'UPDATE_CART_QUANTITY') {
      return 'Allow Loom Companion to update the quantity for the current product in your cart?'
    }
    var quantity = request && request.requestedQuantity ? request.requestedQuantity : 1
    return 'Allow Loom Companion to add ' + quantity + ' item' + (quantity === 1 ? '' : 's') + ' to your cart?'
  }

  function fetchJson(url, init) {
    return fetch(url, init).then(function (response) {
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
              'Storefront query failed with HTTP ' + response.status
            throw new Error(message)
          })
      }
      return response.json()
    })
  }

  function sendPrompt(query, position, mode, requestContext) {
    if (!window.MaxMode || typeof window.MaxMode.sendMessage !== 'function') {
      return false
    }
    window.MaxMode.sendMessage(query, {
      open: true,
      position: position,
      mode: mode,
      requestContext: requestContext,
    })
    return true
  }

  function createRequestContext(storefrontContext, shellModeProfile, surfaceId) {
    var context = {}
    if (storefrontContext && typeof storefrontContext === 'object') {
      Object.keys(storefrontContext).forEach(function (key) {
        context[key] = storefrontContext[key]
      })
    }
    context.shopifyShellModeProfile = normalizeShellModeProfile(shellModeProfile)
    if (surfaceId) {
      context.shopifySurfaceEntry = surfaceId
    }
    return context
  }

  function defaultSearchMode(shellModeProfile) {
    return shellModeProfile === 'GUIDED_SUPPORT' ? 'navigator_deep' : 'navigator'
  }

  function defaultInsightMode(_shellModeProfile) {
    return 'navigator_deep'
  }

  function extractAssistantMessage(payload) {
    if (!payload || typeof payload !== 'object') {
      return null
    }
    if (payload.result && payload.result.sanitizedPayload && payload.result.sanitizedPayload.safeSummary) {
      return String(payload.result.sanitizedPayload.safeSummary)
    }
    if (payload.result && payload.result.sanitizedPayload && payload.result.sanitizedPayload.message) {
      return String(payload.result.sanitizedPayload.message)
    }
    if (payload.result && payload.result.message) {
      return String(payload.result.message)
    }
    if (payload.message) {
      return String(payload.message)
    }
    return null
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

  function normalizeProductCard(item) {
    if (!item || typeof item !== 'object') {
      return null
    }
    var title = trimValue(item.title || item.name || item.productName || item.sku || item.id)
    if (!title) {
      return null
    }
    var subtitle = trimValue(item.subtitle || item.vendor || item.brand || item.category)
    var detail = trimValue(item.detail || item.price || item.priceText || item.stockStatus)
    var excerpt = trimValue(item.excerpt || item.description || item.summary)
    var url = trimValue(item.url || item.link || item.productUrl)
    return {
      title: title,
      subtitle: subtitle,
      detail: detail,
      excerpt: truncateText(excerpt, 140),
      url: url,
    }
  }

  function normalizeSourceCard(item) {
    if (!item || typeof item !== 'object') {
      return null
    }
    var title = trimValue(item.title || item.label || item.name || item.source)
    if (!title) {
      return null
    }
    var subtitle = trimValue(item.subtitle || item.type || item.kind)
    var detail = trimValue(item.detail || item.section || item.category)
    var excerpt = trimValue(item.excerpt || item.summary || item.snippet || item.text)
    var url = trimValue(item.url || item.link)
    return {
      title: title,
      subtitle: subtitle,
      detail: detail,
      excerpt: truncateText(excerpt, 140),
      url: url,
    }
  }

  function firstArray() {
    for (var i = 0; i < arguments.length; i += 1) {
      if (Array.isArray(arguments[i])) {
        return arguments[i]
      }
    }
    return null
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

  function readCachedValue(key) {
    try {
      var raw = window.sessionStorage.getItem(key)
      if (!raw) {
        return null
      }
      var parsed = JSON.parse(raw)
      if (!parsed || !parsed.value || !parsed.timestamp) {
        return null
      }
      if (Date.now() - Number(parsed.timestamp) > CACHE_TTL_MS) {
        window.sessionStorage.removeItem(key)
        return null
      }
      return String(parsed.value)
    } catch (_error) {
      return null
    }
  }

  function writeCachedValue(key, value) {
    try {
      window.sessionStorage.setItem(
        key,
        JSON.stringify({
          value: value,
          timestamp: Date.now(),
        })
      )
    } catch (_error) {
      return null
    }
  }

  function normalizeEnabledSurfaces(values) {
    if (!Array.isArray(values) || values.length === 0) {
      return ['ai-search', 'contextual-pill', 'product-insight', 'policy-strip', 'product-faq', 'comparison']
    }
    return values
      .map(function (value) {
        return trimValue(value).toLowerCase()
      })
      .filter(function (value, index, all) {
        return value && all.indexOf(value) === index
      })
  }

  function normalizeShellModeProfile(value) {
    var normalized = trimValue(value).toUpperCase()
    if (!normalized) {
      return 'SHOPIFY_COMPANION'
    }
    return normalized
  }

  function normalizePageType(storefrontContext) {
    return storefrontContext && storefrontContext.pageType ? String(storefrontContext.pageType).toLowerCase() : 'unknown'
  }

  function searchDockTitle(shellModeProfile) {
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return 'Find products and get quick store answers'
    }
    if (shellModeProfile === 'GUIDED_COMMERCE') {
      return 'Search the catalog with guided buying help'
    }
    return 'Search the store with companion guidance'
  }

  function searchPlaceholder(shellModeProfile) {
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return 'Search or ask a store question'
    }
    if (shellModeProfile === 'GUIDED_COMMERCE') {
      return 'Search for the right product'
    }
    return 'Search with Loom Companion'
  }

  function searchDockDescription(storefrontContext) {
    if (storefrontContext && storefrontContext.collection && storefrontContext.collection.title) {
      return 'Use companion search to narrow ' + storefrontContext.collection.title + ' faster.'
    }
    return 'Ask for best sellers, compare options, or find the right fit without starting from scratch.'
  }

  function insightTitle(storefrontContext) {
    var productTitle = productTitleForContext(storefrontContext)
    return productTitle ? productTitle + ' at a glance' : 'Companion product insight'
  }

  function policyTitle(storefrontContext) {
    var productTitle = productTitleForContext(storefrontContext)
    return productTitle ? productTitle + ' shipping and returns' : 'Companion policy guidance'
  }

  function faqTitle(storefrontContext) {
    var productTitle = productTitleForContext(storefrontContext)
    return productTitle ? productTitle + ' shopper FAQ' : 'Companion product FAQ'
  }

  function faqDescription(storefrontContext) {
    var productTitle = productTitleForContext(storefrontContext)
    if (!productTitle) {
      return 'Ask common shopper questions and get grounded answers before opening chat.'
    }
    return 'Use these common shopper questions to understand ' + truncateText(productTitle, 42) + ' faster.'
  }

  function compareTitle(storefrontContext) {
    var productTitle = productTitleForContext(storefrontContext)
    return productTitle ? truncateText(productTitle, 42) + ' comparisons' : 'Companion comparison'
  }

  function compareDescription(storefrontContext, shellModeProfile) {
    var productTitle = productTitleForContext(storefrontContext)
    if (!productTitle) {
      return 'Compare the strongest options on the current page and surface grounded tradeoffs without opening chat.'
    }
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return 'Use grounded comparison prompts to show where ' + truncateText(productTitle, 42) + ' fits, where alternatives win, and what shoppers should verify before buying.'
    }
    return 'Use grounded comparison prompts to show where ' + truncateText(productTitle, 42) + ' fits, which alternatives are stronger, and who should choose each option.'
  }

  function contextualCardTitle(storefrontContext) {
    if (storefrontContext && storefrontContext.collection && storefrontContext.collection.title) {
      return truncateText(storefrontContext.collection.title, 42) + ' shopping shortcuts'
    }
    var productTitle = productTitleForContext(storefrontContext)
    return productTitle ? truncateText(productTitle, 42) + ' shortcuts' : 'Companion shopping shortcuts'
  }

  function contextualCardDescription(storefrontContext, shellModeProfile) {
    if (storefrontContext && storefrontContext.collection && storefrontContext.collection.title) {
      return 'Guide shoppers through ' + truncateText(storefrontContext.collection.title, 42) + ' with quick prompts that stay on the page.'
    }
    if (shellModeProfile === 'GUIDED_SUPPORT') {
      return 'Use quick prompts to explain the current product, compare options, or surface policy help without opening chat.'
    }
    return 'Use quick prompts to explain the current product, compare options, or move deeper only when needed.'
  }

  function productTitleForContext(storefrontContext) {
    return storefrontContext && storefrontContext.product && storefrontContext.product.title
      ? String(storefrontContext.product.title)
      : null
  }

  function contextCacheKey(storefrontContext) {
    if (!storefrontContext || typeof storefrontContext !== 'object') {
      return 'storefront'
    }
    if (storefrontContext.product && storefrontContext.product.handle) {
      return 'product:' + storefrontContext.product.handle
    }
    if (storefrontContext.collection && storefrontContext.collection.handle) {
      return 'collection:' + storefrontContext.collection.handle
    }
    return normalizePageType(storefrontContext)
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
      return ''
    }
    var normalized = String(value).replace(/\s+/g, ' ').trim()
    if (normalized.length <= maxLength) {
      return normalized
    }
    return normalized.slice(0, maxLength - 1).trim() + '…'
  }

  function trimValue(value) {
    return typeof value === 'string' && value.trim() ? value.trim() : ''
  }

  function normalizeSurfaceScope(value) {
    var normalized = trimValue(value).toLowerCase()
    if (!normalized) {
      return 'all'
    }
    return normalized
  }

  function normalizeMountMode(value) {
    return trimValue(value).toLowerCase() === 'inline' ? 'inline' : 'floating'
  }

  function ensureRootKey(root) {
    if (!root.dataset.loomCompanionRootKey) {
      root.dataset.loomCompanionRootKey = createSessionId()
    }
    return root.dataset.loomCompanionRootKey
  }

  function resolveMountTarget(root, mountMode) {
    if (mountMode === 'inline') {
      return root
    }
    return document.body
  }

  window[REGISTRY_KEY] = {
    render: render,
    teardown: teardown,
  }
})()
