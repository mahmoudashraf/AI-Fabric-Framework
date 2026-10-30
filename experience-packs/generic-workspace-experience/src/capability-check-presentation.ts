import type {
  GenericCapabilityCheckMapping, GenericCapabilityCheckPresentationConfig, GenericCapabilityCheckPresentationInput,
} from './types'

const ELEMENT = 'loomai-capability-check'
const RENDERER = 'loomai.capability-check.v1'
const SCHEMA = 'loomai.capability-check.v1'
const NATIVE_ACTION = 'consult_specialist'

/** Exact structural presentation mapping, never a semantic action/source router. */
export function capabilityCheckMapping(actionName = NATIVE_ACTION): GenericCapabilityCheckMapping {
  return { actionName, rendererId: RENDERER, schemaVersion: SCHEMA, projection: {
    fields: ['name', 'status', 'executionRef', 'observedAt'].map(sourcePath => ({ sourcePath })),
  } }
}

export function capabilityCheckActionPresentationConfig(): GenericCapabilityCheckPresentationConfig {
  return {
    renderers: [{ id: RENDERER, kind: 'custom-element', elementName: ELEMENT, schemaVersions: [SCHEMA] }],
    mappings: [capabilityCheckMapping()],
  }
}

/** Retain host renderers; the reserved action always receives the reviewed four-field projection. */
export function withCapabilityCheckPresentation(existing: unknown): Record<string, unknown> {
  const config = record(existing) ? existing : {}
  const own = capabilityCheckActionPresentationConfig()
  return { ...config,
    renderers: [...(Array.isArray(config.renderers) ? config.renderers.filter(item => !record(item) || item.id !== RENDERER) : []), ...own.renderers],
    mappings: [...(Array.isArray(config.mappings) ? config.mappings.filter(item => !record(item) || item.actionName !== NATIVE_ACTION) : []), ...own.mappings],
  }
}

export function registerCapabilityCheckPresentationElement(): void {
  if (typeof customElements === 'undefined' || typeof HTMLElement === 'undefined' || customElements.get(ELEMENT)) return
  customElements.define(ELEMENT, class extends HTMLElement {
    private data: Record<string, unknown> = {}
    constructor() { super(); this.attachShadow({ mode: 'open' }) }
    set presentation(input: GenericCapabilityCheckPresentationInput) {
      const value = input?.presentationData
      // Copy only public scalar fields even if a host assigns the property without widget projection.
      this.data = record(value) ? Object.fromEntries(['name', 'status', 'executionRef', 'observedAt']
        .map(key => [key, typeof value[key] === 'string' ? value[key] : undefined])) : {}
      this.render()
    }
    connectedCallback() { this.render() }
    private render() {
      const root = this.shadowRoot!
      const wasOpen = root.querySelector('details')?.open ?? false
      const style = document.createElement('style')
      style.textContent = `
        :host { display:block; min-width:0; color:inherit; font:inherit; }
        * { box-sizing:border-box; }
        details { border:1px solid #bac4ca; border-color:color-mix(in srgb,currentColor 22%,transparent); border-radius:8px; }
        summary { cursor:pointer; padding:12px 14px; }
        summary:focus-visible { outline:2px solid currentColor; outline-offset:3px; border-radius:6px; }
        .line { display:inline-flex; width:calc(100% - 20px); align-items:center; justify-content:space-between; gap:12px; vertical-align:middle; }
        strong { font-size:13px; font-weight:600; }
        .outcome { font-size:11px; border:1px solid #bac4ca; border-color:color-mix(in srgb,currentColor 22%,transparent); border-radius:4px; padding:3px 7px; overflow-wrap:anywhere; }
        .name { display:block; margin:6px 0 0 18px; font-size:12px; opacity:.8; overflow-wrap:anywhere; }
        dl { margin:0; padding:0 14px 14px; display:grid; grid-template-columns:auto minmax(0,1fr); gap:8px 16px; font-size:12px; }
        dt { opacity:.72; } dd { margin:0; min-width:0; overflow-wrap:anywhere; } code { font-size:11px; }
      `
      const details = document.createElement('details')
      details.open = wasOpen
      const summary = document.createElement('summary')
      const line = document.createElement('span'); line.className = 'line'
      const title = document.createElement('strong'); title.textContent = 'Resolution checks'
      const status = scalar(this.data.status, 100) || 'Not reported'
      const outcome = document.createElement('span'); outcome.className = 'outcome'; outcome.textContent = status
      line.append(title, outcome); summary.append(line)
      const name = scalar(this.data.name, 240)
      if (name) { const label = document.createElement('span'); label.className = 'name'; label.textContent = name; summary.append(label) }
      const facts = document.createElement('dl')
      const add = (label: string, value: string, tag: 'span' | 'code' | 'time' = 'span') => {
        const term = document.createElement('dt'); term.textContent = label
        const description = document.createElement('dd'), text = document.createElement(tag); text.textContent = value
        if (tag === 'time' && Number.isFinite(Date.parse(value))) (text as HTMLTimeElement).dateTime = value
        description.append(text); facts.append(term, description)
      }
      add('Overall outcome', status)
      const observedAt = scalar(this.data.observedAt, 100), ref = scalar(this.data.executionRef, 500)
      if (observedAt) add('Observed at', observedAt, 'time')
      if (ref) add('Execution reference', ref, 'code')
      details.append(summary, facts)
      root.replaceChildren(style, details)
    }
  })
}

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
function scalar(value: unknown, max: number): string {
  return typeof value === 'string' ? value.trim().slice(0, max) : ''
}
