export interface IsolatedFrameEngine {
  init(config: any): void;
  openConversation(id: string): Promise<unknown>;
  destroy(): void;
}
export interface IsolatedFrameHost<T extends IsolatedFrameEngine> {
  maxMode: T;
  setTarget(element: HTMLElement | null): void;
  setLayer(layer: number): void;
  destroy(): void;
}

/** Style isolation for trusted, integrity-checked code, not a sandbox for untrusted plugins. */
export async function createIsolatedWorkspaceHost<T extends IsolatedFrameEngine = IsolatedFrameEngine>(asset: { url: string; integrity: string }, options: { layer?: number } = {}): Promise<IsolatedFrameHost<T>> {
  const url = new URL(asset.url, document.baseURI);
  const local = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if ((url.protocol !== 'https:' && !(local && url.protocol === 'http:')) || url.username || url.password
    || !/^sha384-[A-Za-z0-9+/=]+$/.test(asset.integrity)) throw new Error('Invalid isolated workspace asset.');
  const frame = document.createElement('iframe');
  const layer = options.layer ?? 2147483001;
  if (!Number.isInteger(layer) || layer < 0 || layer > 2147483647) throw new Error('The workspace frame layer is invalid.');
  frame.title = 'Support conversation';
  frame.dataset.loomaiWorkspaceFrame = 'true';
  frame.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-forms allow-popups');
  frame.style.cssText = `position:fixed;display:none;border:0;margin:0;z-index:${layer};background:transparent;`;
  // Static document only. Identity, intake and callbacks remain in memory, never executable HTML.
  frame.srcdoc = '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body{height:100%;margin:0;overflow:hidden;background:transparent}</style></head><body></body></html>';
  const loaded = new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Workspace frame timed out.')), 15000);
    frame.addEventListener('load', () => { clearTimeout(timer); resolve(); }, { once: true });
  });
  document.body.appendChild(frame);
  let maxMode: T;
  try {
    await loaded;
    const script = frame.contentDocument!.createElement('script');
    script.src = url.href; script.integrity = asset.integrity; script.crossOrigin = 'anonymous';
    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Workspace engine timed out.')), 15000);
      script.onload = () => { clearTimeout(timer); resolve(); };
      script.onerror = () => { clearTimeout(timer); reject(new Error('Workspace engine failed integrity or network validation.')); };
      frame.contentDocument!.head.appendChild(script);
    });
    maxMode = (frame.contentWindow as unknown as { MaxMode?: T }).MaxMode!;
    if (!maxMode?.init || !maxMode.openConversation) throw new Error('Workspace host API unavailable.');
  } catch (error) { frame.remove(); throw error; }
  let target: HTMLElement | null = null;
  let animation = 0;
  let destroyed = false;
  const update = () => {
    if (destroyed) return;
    if (!target?.isConnected) frame.style.display = 'none';
    else {
      const rect = target.getBoundingClientRect();
      frame.style.display = rect.width > 0 && rect.height > 0 ? 'block' : 'none';
      frame.style.left = `${rect.left}px`; frame.style.top = `${rect.top}px`;
      frame.style.width = `${rect.width}px`; frame.style.height = `${rect.height}px`;
      frame.style.clipPath = `inset(${Math.max(0, -rect.top)}px ${Math.max(0, rect.right - innerWidth)}px ${Math.max(0, rect.bottom - innerHeight)}px ${Math.max(0, -rect.left)}px)`;
    }
    // The frame never moves in the DOM. Scroll, resize and SPA layout changes only update its viewport.
    animation = target ? requestAnimationFrame(update) : 0;
  };
  return {
    maxMode,
    setLayer(value) {
      if (destroyed) throw new Error('This workspace frame has been destroyed.');
      if (!Number.isInteger(value) || value < 0 || value > 2147483647) throw new Error('The workspace frame layer is invalid.');
      frame.style.zIndex = String(value);
    },
    setTarget(element) {
      if (destroyed) throw new Error('This workspace frame has been destroyed.');
      target = element;
      cancelAnimationFrame(animation);
      update();
    },
    destroy() {
      if (destroyed) return;
      destroyed = true; cancelAnimationFrame(animation);
      try { maxMode.destroy(); } finally { frame.remove(); }
    },
  };
}
