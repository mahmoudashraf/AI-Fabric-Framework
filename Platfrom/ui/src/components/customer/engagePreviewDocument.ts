export function engagePreviewDocument(script: string) {
  return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src https:; connect-src 'none'; font-src 'none'; form-action 'none'; base-uri 'none'; object-src 'none'">
<style>html,body,#preview-root{height:100%;margin:0}body{overflow:hidden}</style></head><body><div id="preview-root"></div><script>${script.replace(/<\/script/gi, '<\\/script')}</script></body></html>`
}
