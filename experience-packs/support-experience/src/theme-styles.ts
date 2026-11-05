/** Keep status/error colours semantic; only neutral chrome opts into the shared style. */
export const enhancedSupportStyles = `
:host([data-mxw-style]:not([data-mxw-style=current])) {
  --support-accent: var(--mxw-style-accent);
  --support-bg: var(--mxw-style-surface);
  --support-text: var(--mxw-style-text);
  --support-muted: var(--mxw-style-muted);
  --support-line: var(--mxw-style-border);
  --support-subtle: var(--mxw-style-canvas);
  --support-hover: var(--mxw-style-tint);
  font-family: var(--mxw-style-font);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.shell, .launcher) {
  border-radius: var(--mxw-style-radius);
  box-shadow: var(--mxw-style-elevation);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(.title, .form-heading, .panel-header h2, .detail h3) {
  font-family: var(--mxw-style-display-font);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(button, input, select, textarea) {
  border-color: var(--mxw-style-border);
  border-radius: var(--mxw-style-radius);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .request-row {
  border-radius: 0;
}
:host([data-mxw-style]:not([data-mxw-style=current])) .primary {
  background: var(--mxw-style-accent);
  border-color: var(--mxw-style-accent);
  color: var(--mxw-style-on-accent);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .primary:hover {
  background: var(--mxw-style-accent);
}
:host([data-mxw-style]:not([data-mxw-style=current])) .initials {
  background: var(--mxw-style-human);
  color: var(--mxw-style-text);
}
:host([data-mxw-style]:not([data-mxw-style=current])) :is(button, input, select, textarea):focus-visible {
  outline-color: var(--mxw-style-accent);
}
:host(.standalone[data-mxw-style]:not([data-mxw-style=current])) .shell {
  border-radius: 0;
  box-shadow: none;
}
`
