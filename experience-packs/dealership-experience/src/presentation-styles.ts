/** Opt-in tokens cross the custom element's Shadow DOM; legacy styles stay untouched. */
export const enhancedDealershipStyles = `
:host([data-mxw-style]:not([data-mxw-style=current])) {
  color: var(--mxw-style-text);
  font-family: var(--mxw-style-font);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.workspace, .vehicle-card, .comparison-mobile__card, .comparison-selection > div, .empty-state) {
  border-color: var(--mxw-style-border);
  border-radius: var(--mxw-style-radius);
  background: var(--mxw-style-surface);
  box-shadow: var(--mxw-style-elevation);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.workspace-header, .compare-toolbar, .comparison-table, .facts-grid div, .request-facts div) {
  background: var(--mxw-style-surface);
  border-color: var(--mxw-style-border);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.vehicle-grid, .detail-layout, .comparison-scroll, .comparison-mobile, .request-receipt, .comparison-table tbody th) {
  background: var(--mxw-style-canvas);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.workspace-header h2, .vehicle-card h3, .comparison-mobile__card h3, .comparison-selection strong) {
  color: var(--mxw-style-text);
  font-family: var(--mxw-style-display-font);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.summary, .request-message, .facts dd, .facts-grid dd, .request-facts dd, .feature-list li, .selection-control, .compare-toolbar p, .comparison-table td) {
  color: var(--mxw-style-text);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.source, .muted, .facts dt, .facts-grid dt, .request-facts dt, .empty-state p, .comparison-table tbody th) {
  color: var(--mxw-style-muted);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.eyebrow, .price, .detail-price, .comparison-table thead span),
:host([data-mxw-style]:not([data-mxw-style=current])) .feature-list li::before {
  color: var(--mxw-style-accent);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.filter-pills span, .request-state) {
  border-color: var(--mxw-style-border);
  border-radius: var(--mxw-style-radius);
  background: var(--mxw-style-tint);
  color: var(--mxw-style-text);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .vehicle-image {
  background: var(--mxw-style-canvas);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .detail-media .vehicle-image {
  border-radius: var(--mxw-style-radius);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.facts-grid div, .request-facts div) {
  border-left-color: var(--mxw-style-accent);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.comparison-table th, .comparison-table td) {
  border-color: var(--mxw-style-border);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .comparison-table thead th {
  background: var(--mxw-style-tint);
  color: var(--mxw-style-text);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .selection-control input {
  accent-color: var(--mxw-style-accent);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .button {
  border-radius: var(--mxw-style-radius);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .button:focus-visible {
  outline-color: var(--mxw-style-accent);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .button--primary {
  background: var(--mxw-style-accent);
  color: var(--mxw-style-on-accent);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .button--primary:hover:not(:disabled) {
  background: var(--mxw-style-accent);
  filter: brightness(.94);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .button--secondary {
  border-color: var(--mxw-style-border);
  background: var(--mxw-style-surface);
  color: var(--mxw-style-text);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .button--quiet {
  color: var(--mxw-style-muted);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.button--secondary, .button--quiet):hover:not(:disabled) {
  border-color: var(--mxw-style-accent);
  background: var(--mxw-style-tint);
  color: var(--mxw-style-text);
}
`
