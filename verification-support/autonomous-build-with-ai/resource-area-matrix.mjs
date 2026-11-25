import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
import { configs } from './area-matrix.mjs'

export const resourceCases = [
  {
    id: 'human-support', task: 'HUMAN_SUPPORT', profile: 'workspace', auth: 'STAFF',
    auto: 'Set only the existing handover queue timeout to 180 seconds. Preserve its installed Desk binding, confirmation, recovery, enabled state and every other setting. Save the draft without publishing or assigning agents.',
    quick: 'Set only the existing handover queue timeout to 240 seconds. Preserve the installed binding and every other setting. Save without publication.',
    async prepare({ api, privateRoot, save, environment }) {
      const inventory = JSON.parse(fs.readFileSync(path.join(privateRoot, 'loomai-hybrid-retrieval-01073', 'authoring-01075-' + environment + '-document-inventory.private.json')))
      const id = inventory.brain
      const before = await api('/api/deployments/' + id + '/draft')
      const handover = before.behaviorConfig?.conversationHandover
      assert.equal(handover?.enabled, true, 'Use an actually installed handover; do not fabricate a channel binding.')
      assert.ok(handover.actionBinding?.marketplaceInstallId)
      const versions = await api('/api/deployments/' + id + '/versions'), releases = await api('/api/deployments/' + id + '/releases')
      save('human-support-original-draft', before)
      return { scope: { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: id, task: this.task },
        read: () => api('/api/deployments/' + id + '/draft'),
        async assertIsolated() { assert.deepEqual(await api('/api/deployments/' + id + '/versions'), versions); assert.deepEqual(await api('/api/deployments/' + id + '/releases'), releases) },
        async cleanup() {
          const current = await this.read(), behaviorConfig = structuredClone(current.behaviorConfig)
          behaviorConfig.conversationHandover.queueTimeoutSeconds = handover.queueTimeoutSeconds
          const restored = await api('/api/deployment-drafts/' + current.id, 'PUT', { behaviorConfig })
          assert.deepEqual(configs(restored), configs(before))
          return [{ brain: id, draftRestored: true, cleaned: true, noPublication: true }]
        },
      }
    },
    verify(before, after, mode) {
      const expected = structuredClone(configs(before)); expected.behaviorConfig.conversationHandover.queueTimeoutSeconds = mode === 'AUTONOMOUS' ? 180 : 240
      assert.deepEqual(configs(after), expected)
    },
  },
  {
    id: 'data-plugin', task: 'DATA_PLUGIN', profile: 'data',
    auto: 'Include priceGbp in the record content mapping alongside name. Preserve stable IDs, owning account, authentication, pagination and every other setting. Save this private draft only.',
    quick: 'Also include stock in the existing content mapping. Keep name and priceGbp and every other setting unchanged. Save the private draft.',
    async prepare({ api, privateRoot, save, uid }) {
      const { newDefinition } = await import('/private/tmp/loomai-01075-plugin-fixture.mjs')
      const manifest = JSON.parse(newDefinition('area-qualification-' + uid, 'HTTP_JSON'))
      manifest.displayName = 'Area qualification catalogue'
      const source = manifest.contributions.datasets[0].syncConnector.httpSource
      source.path = '/api/items'
      Object.assign(source.mapping, { recordsJsonPointer: '/items', idJsonPointer: '/id', resourceJsonPointer: '/accountId', contentFields: { name: '/name' } })
      const plugin = await api('/api/customer/plugin-library', 'POST', { manifest }, [201])
      save('data-plugin-fixture', plugin)
      return { scope: { resourceKind: 'PRIVATE_DATA_PLUGIN', resourceId: plugin.id, task: this.task },
        source: 'The existing source returns {"items":[{"id":"p1","accountId":"a1","name":"Desk","priceGbp":250,"stock":5}]}. Each record retains the stable id and accountId. Price and stock are public product fields. No connection or permissions change is requested.',
        read: () => api('/api/customer/plugin-library/' + plugin.id),
        async assertIsolated() { const p = await this.read(); assert.equal(p.versions.length, 0); assert.equal(p.installs.savedCount, 0) },
        async cleanup() { const p = await this.read(); const a = await api('/api/customer/plugin-library/' + plugin.id + '/archive', 'POST', { expectedRevision: p.revision }); assert.equal(a.status, 'ARCHIVED'); return [{ plugin: plugin.id, archived: true, cleaned: true, noInstallations: true }] },
      }
    },
    verify(before, after, mode) {
      const expected = structuredClone(before.manifest)
      expected.contributions.datasets[0].syncConnector.httpSource.mapping.contentFields[mode === 'AUTONOMOUS' ? 'priceGbp' : 'stock'] = mode === 'AUTONOMOUS' ? '/priceGbp' : '/stock'
      assert.deepEqual(after.manifest, expected)
    },
  },
  {
    id: 'engage', task: 'ENGAGE', profile: 'workspace',
    auto: 'Rename this draft workspace to Office helper and set its primary theme colour to #087F8C. Preserve experience, connection, authentication, domains, allowed websites and every other setting. Save the draft without activating it.',
    quick: 'Change only the theme primary colour to #164E63 and the workspace name to Office guide. Preserve all other settings. Save without activation.',
    async prepare({ api, save, uid, ui, createBrain, removeBrain }) {
      const customer = (await api('/api/platform/auth/session')).customerId
      const catalogue = await api('/api/platform/ai-workspaces/catalog')
      const pack = catalogue.experiencePacks.find(p => p.enabled && p.code === 'generic-workspace')
      const profile = catalogue.connectionProfiles.find(p => p.enabled && p.code === 'runtime-anonymous-direct')
      assert.ok(pack && profile)
      const brain = await createBrain('engage')
      let consumer, app
      const consumers = '/api/platform/customers/' + customer + '/consumers'
      const apps = '/api/platform/customers/' + customer + '/ai-workspace-installations'
      try {
        consumer = await api(consumers, 'POST', { consumerId: 'area-' + uid, displayName: 'Area qualification', deploymentId: brain.id, bindingReason: 'Resource-free inactive app qualification' }, [201])
        app = await api(apps, 'POST', { consumerId: consumer.consumerId, displayName: 'Area qualification workspace', experiencePackCode: pack.code, experiencePackVersion: pack.version,
          connectionMode: profile.mode, connectionProfileCode: profile.code, connectionProfileVersion: profile.version, connectionConfiguration: {}, allowedOrigins: [ui],
          configuration: { app: { assistantLabel: 'Office assistant' }, page: { rootSelector: 'body', contextLabel: 'Current page', maxChars: 1800, maxPages: 3, maxTotalChars: 10000 }, capabilities: { currentPage: true, handover: false }, theme: { primaryColor: '#0055AA', borderRadius: '0.5rem', fontFamily: 'Inter, system-ui, sans-serif', darkMode: false } },
          deliveryMode: 'embedded', customDomains: [], interactionPolicy: 'public', authConfiguration: {} }, [201])
        save('engage-fixture', { brain: brain.id, consumer: consumer.consumerId, app })
      } catch (error) {
        save('engage-partial-fixture', { brain: brain.id, consumer: consumer?.consumerId, app: app?.installationId })
        throw error
      }
      return { scope: { resourceKind: 'ENGAGE_APP', resourceId: app.installationId, task: this.task },
        read: () => api(apps + '/' + app.installationId),
        async assertIsolated() { const current = await this.read(); assert.notEqual(current.status, 'ACTIVE'); assert.equal((await api('/api/deployments/' + brain.id + '/releases')).length, 0) },
        async cleanup() {
          await api(apps + '/' + app.installationId, 'DELETE', undefined, [204])
          await api(apps + '/' + app.installationId, 'GET', undefined, [404])
          const current = (await api(consumers)).find(c => c.consumerId === consumer.consumerId)
          if (current?.boundDeploymentId) await api(consumers + '/' + consumer.consumerId + '/binding', 'PUT', { deploymentId: null, releaseId: null, targetProfileId: null, expectedBindingRevision: current.bindingRevision, reason: 'Remove exact resource-free qualification', operationKey: 'area-cleanup-' + uid })
          await api(consumers + '/' + consumer.consumerId, 'DELETE', undefined, [204])
          await removeBrain(brain.id)
          return [{ app: app.installationId, deleted: true }, { consumer: consumer.consumerId, deleted: true }, { brain: brain.id, deleted: true }]
        },
      }
    },
    verify(before, after, mode) {
      const expected = structuredClone(before.configuration); expected.theme.primaryColor = mode === 'AUTONOMOUS' ? '#087F8C' : '#164E63'
      assert.deepEqual(after.configuration, expected)
      assert.equal(after.displayName, mode === 'AUTONOMOUS' ? 'Office helper' : 'Office guide')
      for (const field of ['consumerId', 'connectionMode', 'connectionProfileCode', 'connectionProfileVersion', 'connectionConfiguration', 'allowedOrigins', 'authConfiguration', 'deliveryMode', 'status', 'domains', 'releaseId', 'deploymentId']) assert.deepEqual(after[field], before[field], field)
    },
  },
  {
    id: 'document-preparation', task: 'DOCUMENT_PREPARATION', profile: 'documents', auth: 'STAFF',
    auto: 'Use chunks of 320 tokens with 40 token overlap for this document and prepare five distinct unreviewed test questions from the selected sample. Keep OCR, evaluation options and every other setting unchanged. Save preparation settings only. Do not index, approve or run evaluation.',
    quick: 'Change only the processing chunk size to 360 tokens. Preserve overlap, all five questions, OCR, evaluation options and every other setting. Save settings only; do not index or evaluate.',
    async prepare({ api, privateRoot, save, environment }) {
      const inventory = JSON.parse(fs.readFileSync(path.join(privateRoot, 'loomai-hybrid-retrieval-01073', 'authoring-01075-' + environment + '-document-inventory.private.json')))
      const source = inventory.sources.value[0]
      assert.ok(source?.sourceId)
      const base = inventory.path + '/' + source.sourceId
      const before = await api(base + '/preparation'), sample = await api(base + '/preview')
      assert.ok(sample.chunks?.length)
      save('document-original-preparation', before)
      const data = { sourceId: source.sourceId, sourceVersion: sample.source.candidateVersion, sampleIsPartial: sample.chunksTruncated || sample.chunks.some(c => c.truncated), extraction: sample.extraction, documentCount: sample.documentCount, chunkCount: sample.chunkCount,
        excerpts: sample.chunks.slice(0, 6).map(c => ({ chunkId: c.chunkId, text: c.contentPreview, truncated: c.truncated })), evaluation: null }
      const content = JSON.stringify(data); assert.ok(content.length <= 32000)
      const reference = [inventory.brain, inventory.release.targetProfileId, inventory.release.id, source.sourceId].join('|')
      return { scope: { resourceKind: 'DOCUMENT_SOURCE', resourceId: reference, task: this.task }, source: content,
        read: () => api(base + '/preparation'),
        async assertIsolated() { const current = await this.read(); assert.equal(current.sourceVersion, before.sourceVersion) },
        async cleanup() {
          const current = await this.read()
          const restored = await api(base + '/preparation', 'POST', { expectedRevision: current.revision, configuration: before.configuration })
          assert.deepEqual(restored.configuration, before.configuration)
          return [{ source: source.sourceId, preparationRestored: true, cleaned: true, noIndexing: true }]
        },
      }
    },
    verify(before, after, mode) {
      const b = before.configuration, a = after.configuration
      const expected = structuredClone(b.processing)
      expected.chunkTokens = mode === 'AUTONOMOUS' ? 320 : 360
      if (mode === 'AUTONOMOUS') expected.overlapTokens = 40
      assert.deepEqual(a.processing, expected)
      assert.deepEqual(a.evaluationOptions, b.evaluationOptions)
      if (mode === 'AUTONOMOUS') { assert.equal(a.questions.length, 5); assert.ok(a.questions.every(q => q.reviewed === false && q.question && q.referenceAnswer)) }
      else assert.deepEqual(a.questions, b.questions)
    },
  },
]
