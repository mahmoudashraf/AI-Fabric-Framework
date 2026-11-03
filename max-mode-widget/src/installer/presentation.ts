import type { WorkspacePresentation } from './types';

export function resolveInstallationPresentation(
  script: Pick<HTMLScriptElement, 'dataset'>,
  deliveryMode: 'embedded' | 'standalone',
  document: Document,
): WorkspacePresentation {
  const mode = script.dataset.presentation ?? (deliveryMode === 'standalone' ? 'standalone' : 'widget');
  if (!['widget', 'page', 'standalone'].includes(mode)) throw new Error('Unsupported data-presentation.');
  const selector = script.dataset.container;
  // Local layout only: a bounded element ID cannot select arbitrary markup or remote destinations.
  if (selector !== undefined && !/^#[A-Za-z][A-Za-z0-9_-]{0,127}$/.test(selector)) {
    throw new Error('data-container must be a single element ID selector.');
  }
  if (mode === 'page') {
    if (!selector) throw new Error('Page presentation requires data-container.');
    const container = document.getElementById(selector.slice(1));
    if (!container || container === document.body || container === document.documentElement) {
      throw new Error('The support page container is missing or invalid.');
    }
    return { mode: 'page', container, containerSelector: selector };
  }
  if (selector) throw new Error('data-container is only supported with page presentation.');
  return { mode: mode as 'widget' | 'standalone' };
}
