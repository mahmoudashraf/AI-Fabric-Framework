import { renderPresentation } from './presentation.js';
import { renderPartnership } from './partnership.js';

(() => {
  'use strict';

  const app = document.querySelector('[data-proposal-app]');
  if (!app) return;
  const find = (name) => app.querySelector(`[data-proposal-${name}]`);
  const loading = find('loading');
  const access = find('access');
  const unavailable = find('unavailable');
  const viewer = find('viewer');
  const form = find('form');
  const codeInput = find('code');
  const submit = find('submit');
  const navigation = find('navigation');
  const sections = find('sections');
  const presentation = find('presentation');
  const documentView = find('document-view');
  const downloadButton = find('download');
  const downloadFeedback = find('download-feedback');
  const panel = find('contact-panel');
  const bookingUrl = 'https://calendly.com/mahmoud-elgammal-loomai/loomai-integration-customer-success';
  let endpoint = null;
  let requestController = null;
  let downloadController = null;
  let requestVersion = 0;
  let sectionObserver = null;
  let retryAction = 'content';
  let contactOpener = null;
  let calendarTimer = null;
  const downloadUrls = new Set();

  // The pathname is the only routing input; codes and cookies are never read from URLs or storage.
  try {
    const match = window.location.pathname.match(/^\/proposals\/([^/]+)\/?$/);
    const slug = match ? decodeURIComponent(match[1]) : '';
    if (slug && !/[/\\\u0000-\u001f\u007f]/.test(slug) && slug !== '.' && slug !== '..') {
      endpoint = `/proposals/${encodeURIComponent(slug)}`;
    }
  } catch {
    endpoint = null;
  }

  function element(tag, text, className) {
    const node = document.createElement(tag);
    if (typeof text === 'string') node.textContent = text;
    if (className) node.className = className;
    return node;
  }

  // Stored content may supply text and links, never HTML or executable URL schemes.
  function safeHref(value) {
    if (typeof value !== 'string' || /[\u0000-\u0020\u007f]/.test(value)) return null;
    try {
      const url = new URL(value);
      if (url.protocol === 'https:' && url.hostname && !url.username && !url.password) return url.href;
      if (url.protocol === 'tel:' && /^tel:\+?[0-9().-]+$/i.test(value)) return url.href;
    } catch {
      return null;
    }
    return null;
  }

  function appendRuns(parent, runs) {
    if (!Array.isArray(runs)) return;
    for (const run of runs) {
      if (!run || typeof run.text !== 'string') continue;
      const text = element(run.bold === true ? 'strong' : 'span', run.text);
      const href = safeHref(run.href);
      if (!href) {
        parent.append(text);
        continue;
      }
      const link = element('a');
      link.href = href;
      link.referrerPolicy = 'no-referrer';
      if (href.startsWith('https:')) {
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
      }
      if (href === bookingUrl) {
        link.addEventListener('click', (event) => {
          if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
          event.preventDefault();
          openContact({ currentTarget: link });
          openCalendar();
        });
      }
      link.append(text);
      parent.append(link);
    }
  }

  function resetCalendar() {
    window.clearTimeout(calendarTimer);
    find('calendar-frame').replaceChildren();
    find('calendar-status').textContent = '';
    find('calendar').hidden = true;
    find('calendar-back').hidden = true;
    find('contact-profile').hidden = false;
    panel.classList.remove('proposal-contact-panel--calendar');
    panel.setAttribute('aria-labelledby', 'proposal-contact-title');
  }

  function closeContact(restoreFocus = true) {
    if (panel.open) panel.close();
    resetCalendar();
    if (restoreFocus && contactOpener && !viewer.hidden) contactOpener.focus({ preventScroll: true });
    contactOpener = null;
  }

  function clearProtectedContent() {
    closeContact(false);
    downloadController?.abort();
    downloadController = null;
    sectionObserver?.disconnect();
    sectionObserver = null;
    sections.replaceChildren();
    if (!documentView.contains(sections)) documentView.querySelector('article').prepend(sections);
    if (presentation.contains(downloadButton)) {
      documentView.querySelector('.proposal-document-header__bottom').append(downloadButton);
      documentView.querySelector('.proposal-document-header').append(downloadFeedback);
    }
    presentation.replaceChildren();
    presentation.hidden = true;
    documentView.hidden = false;
    find('prepared-for').textContent = '';
    find('prepared-for').hidden = true;
    app.classList.remove('proposal-app--visual');
    navigation.replaceChildren();
    find('title').textContent = '';
    find('subtitle').textContent = '';
    find('metadata').replaceChildren();
    find('session-actions').hidden = true;
    find('contact-trigger').hidden = true;
    downloadButton.hidden = true;
    downloadButton.disabled = false;
    find('download-label').textContent = 'Download DOCX';
    find('download-message').textContent = '';
    find('download-retry').hidden = true;
    for (const url of downloadUrls) URL.revokeObjectURL(url);
    downloadUrls.clear();
    viewer.hidden = true;
  }

  function setView(state) {
    app.dataset.state = state;
    loading.hidden = state !== 'loading';
    access.hidden = state !== 'access';
    unavailable.hidden = state !== 'unavailable';
    viewer.hidden = state !== 'viewer';
  }

  function showAccess(message = '', invalid = false, focus = false) {
    clearProtectedContent();
    setView('access');
    codeInput.value = '';
    codeInput.disabled = false;
    codeInput.setAttribute('aria-invalid', String(invalid));
    submit.disabled = false;
    form.removeAttribute('aria-busy');
    find('submit-label').textContent = 'View proposal';
    find('access-message').textContent = message;
    find('access-message').dataset.tone = invalid ? 'error' : 'neutral';
    if (focus) codeInput.focus({ preventScroll: true });
  }

  function showUnavailable(action = 'content', focus = true) {
    clearProtectedContent();
    retryAction = action;
    setView('unavailable');
    const logoutFailed = action === 'logout';
    find('unavailable-title').textContent = logoutFailed ? 'Your proposal is hidden' : 'Temporarily unavailable';
    find('unavailable-message').textContent = logoutFailed
      ? "We couldn't confirm that your session was locked. Please try locking it again before leaving a shared device."
      : "We couldn't open your proposal just now. Please try again, or contact Mo if this continues.";
    find('retry').hidden = !endpoint;
    find('retry').setAttribute('aria-label', logoutFailed ? 'Retry locking proposal' : 'Retry opening proposal');
    find('retry-label').textContent = logoutFailed ? 'Try locking again' : 'Try again';
    if (focus) find('unavailable-title').focus({ preventScroll: true });
  }

  function beginRequest() {
    requestController?.abort();
    requestController = new AbortController();
    requestVersion += 1;
    return { controller: requestController, version: requestVersion };
  }

  async function request(path, controller, options = {}) {
    const timer = window.setTimeout(() => controller.abort(), 20000);
    try {
      return await fetch(`${endpoint}/${path}`, {
        ...options,
        credentials: 'same-origin',
        cache: 'no-store',
        redirect: 'error',
        referrerPolicy: 'no-referrer',
        signal: controller.signal,
      });
    } finally {
      window.clearTimeout(timer);
    }
  }

  function addNavigation(id, heading, index) {
    const item = element('li');
    const link = element('a');
    link.href = `#${id}`;
    const number = element('span', String(index + 1).padStart(2, '0'), 'proposal-navigation__number');
    number.setAttribute('aria-hidden', 'true');
    link.append(number, element('span', heading));
    item.append(link);
    navigation.append(item);
  }

  function renderProposal(content, focus) {
    // Only title and the section collection are structural requirements for a readable document.
    if (!content || typeof content.title !== 'string' || !Array.isArray(content.sections)) {
      throw new TypeError('Unreadable proposal structure');
    }
    clearProtectedContent();
    find('title').textContent = content.title;
    find('subtitle').textContent = typeof content.subtitle === 'string' ? content.subtitle : '';
    find('subtitle').hidden = !find('subtitle').textContent;
    const metadata = find('metadata');
    for (const [key, label] of [['recipient', 'Prepared for'], ['date', 'Date'], ['version', 'Version']]) {
      if (!['string', 'number'].includes(typeof content[key]) || content[key] === '') continue;
      const group = element('div');
      group.append(element('dt', label), element('dd', String(content[key])));
      metadata.append(group);
    }
    let index = 0;
    for (const section of content.sections) {
      if (!section || !Array.isArray(section.blocks)) continue;
      const heading = typeof section.heading === 'string' && section.heading ? section.heading : `Section ${index + 1}`;
      const node = element('section', undefined, 'proposal-section');
      node.id = `proposal-section-${index + 1}`;
      const title = element('h2', heading);
      title.id = `${node.id}-title`;
      node.setAttribute('aria-labelledby', title.id);
      node.append(title);
      for (const block of section.blocks) {
        if (!block) continue;
        if (block.type === 'paragraph') {
          const paragraph = element('p');
          appendRuns(paragraph, block.runs);
          node.append(paragraph);
        } else if (block.type === 'list' && Array.isArray(block.items)) {
          const list = element(block.ordered === true ? 'ol' : 'ul');
          for (const runs of block.items) {
            const item = element('li');
            appendRuns(item, runs);
            list.append(item);
          }
          node.append(list);
        }
      }
      sections.append(node);
      addNavigation(node.id, heading, index++);
    }
    addNavigation('proposal-public-demo', 'LoomAI in action', index++);
    addNavigation('proposal-next-conversation', 'Talk with Mo', index);
    downloadButton.hidden = content.downloadAvailable !== true;
    const presentationRenderer = new Map([['buying-workspace-v1', renderPresentation], ['partnership-v1', renderPartnership]]).get(content.presentation?.layout);
    if (presentationRenderer) {
      presentationRenderer(presentation, content.presentation, {
        openContact,
        appendDocument: (target) => {
          target.append(sections);
          if (content.downloadAvailable === true) {
            target.append(downloadButton, downloadFeedback);
          }
        },
      });
      presentation.hidden = false;
      documentView.hidden = true;
      app.classList.add('proposal-app--visual');
      find('prepared-for').textContent = `Prepared for ${content.recipient}`;
      find('prepared-for').hidden = false;
    }
    find('session-actions').hidden = false;
    find('contact-trigger').hidden = false;
    setView('viewer');
    const links = Array.from(navigation.querySelectorAll('a'));
    const markCurrent = (id) => {
      for (const link of links) {
        if (link.getAttribute('href') === `#${id}`) link.setAttribute('aria-current', 'location');
        else link.removeAttribute('aria-current');
      }
    };
    if (links[0]) markCurrent(links[0].getAttribute('href').slice(1));
    if ('IntersectionObserver' in window) {
      sectionObserver = new IntersectionObserver((entries) => {
        const visible = entries.filter((entry) => entry.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) markCurrent(visible[0].target.id);
      }, { rootMargin: '-20% 0px -60% 0px', threshold: 0 });
      for (const node of viewer.querySelectorAll('article section')) sectionObserver.observe(node);
    }
    for (const link of links) link.addEventListener('click', () => markCurrent(link.getAttribute('href').slice(1)));
    if (focus) {
      window.scrollTo({ top: 0, behavior: 'instant' });
      (presentation.hidden ? find('title') : presentation.querySelector('h1')).focus({ preventScroll: true });
    }
  }

  async function loadContent({ focus = false, quiet = false } = {}) {
    if (!endpoint) return showUnavailable('content', false);
    const { controller, version } = beginRequest();
    if (!quiet) {
      clearProtectedContent();
      setView('loading');
    }
    try {
      const response = await request('content', controller, { headers: { Accept: 'application/json' } });
      if (version !== requestVersion) return;
      if (response.status === 401) return showAccess(quiet ? 'Your access has ended. Enter your code to reopen the proposal.' : '', false, focus || quiet);
      if (response.status !== 200) return showUnavailable();
      const content = await response.json();
      if (version !== requestVersion) return;
      if (quiet && !viewer.hidden) return;
      renderProposal(content, focus);
    } catch {
      if (version === requestVersion) showUnavailable();
    }
  }

  async function submitAccess(event) {
    event.preventDefault();
    if (!endpoint || submit.disabled) return;
    const code = codeInput.value.trim();
    if (!code) {
      codeInput.setAttribute('aria-invalid', 'true');
      find('access-message').textContent = 'Enter the access code from your invitation.';
      codeInput.focus();
      return;
    }
    const { controller, version } = beginRequest();
    submit.disabled = true;
    codeInput.disabled = true;
    codeInput.value = '';
    form.setAttribute('aria-busy', 'true');
    find('submit-label').textContent = 'Checking access...';
    find('access-message').textContent = '';
    try {
      const response = await request('access', controller, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ code }),
      });
      if (version !== requestVersion) return;
      if (response.status === 204) return loadContent({ focus: true });
      if ([400, 401, 403, 404, 410, 422].includes(response.status)) {
        showAccess("That code couldn't open this proposal. Check it or ask Mo for a new code.", true, true);
      } else if (response.status === 429) {
        showAccess('Too many attempts. Please wait before trying again, or contact Mo.', true, true);
      } else {
        showUnavailable();
      }
    } catch {
      if (version === requestVersion) showUnavailable();
    }
  }

  async function lockProposal() {
    if (!endpoint) return;
    const { controller, version } = beginRequest();
    clearProtectedContent();
    setView('loading');
    try {
      const response = await request('logout', controller, { method: 'POST' });
      if (version !== requestVersion) return;
      if (response.status === 204) showAccess('Proposal locked. Enter your code to open it again.', false, true);
      else showUnavailable('logout');
    } catch {
      if (version === requestVersion) showUnavailable('logout');
    }
  }

  async function downloadProposal() {
    if (viewer.hidden || downloadButton.disabled || downloadButton.hidden) return;
    const controller = new AbortController();
    downloadController = controller;
    downloadButton.disabled = true;
    find('download-label').textContent = 'Preparing DOCX...';
    find('download-message').textContent = '';
    find('download-retry').hidden = true;
    try {
      const response = await request('download', controller);
      if (controller.signal.aborted || viewer.hidden) return;
      if (response.status === 401) {
        requestController?.abort();
        requestVersion += 1;
        showAccess('Your access has ended. Enter your code to download the proposal.', false, true);
        return;
      }
      // Do not turn a gateway error page into a misleading .docx download.
      const type = response.headers.get('content-type')?.split(';')[0].trim().toLowerCase();
      if (response.status !== 200 || !['application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/octet-stream'].includes(type)) {
        throw new Error('Download unavailable');
      }
      const blob = await response.blob();
      if (controller.signal.aborted || viewer.hidden) return;
      const url = URL.createObjectURL(blob);
      downloadUrls.add(url);
      const link = element('a');
      link.href = url;
      link.download = 'LoomAI-proposal.docx';
      link.hidden = true;
      app.append(link);
      link.click();
      link.remove();
      find('download-message').textContent = 'Your DOCX download is ready.';
      window.setTimeout(() => { URL.revokeObjectURL(url); downloadUrls.delete(url); }, 60000);
    } catch {
      if (!viewer.hidden && downloadController === controller) {
        find('download-message').textContent = "The download isn't available just now. Please try again.";
        find('download-retry').hidden = false;
      }
    } finally {
      if (downloadController === controller) {
        downloadController = null;
        downloadButton.disabled = false;
        find('download-label').textContent = 'Download DOCX';
      }
    }
  }

  function openContact(event) {
    if (viewer.hidden || panel.open) return;
    contactOpener = event.currentTarget;
    resetCalendar();
    panel.showModal();
  }

  function openCalendar() {
    if (viewer.hidden || !panel.open || !find('calendar').hidden) return;
    const frame = element('iframe');
    frame.title = 'Schedule a LoomAI integration call with Mo';
    frame.referrerPolicy = 'no-referrer';
    frame.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox');
    // The fixed public booking URL receives no proposal slug, content, code, or session data.
    const calendarUrl = new URL(bookingUrl);
    calendarUrl.searchParams.set('embed_type', 'Inline');
    calendarUrl.searchParams.set('embed_domain', location.host);
    frame.src = calendarUrl.toString();
    find('contact-profile').hidden = true;
    find('calendar').hidden = false;
    find('calendar-back').hidden = false;
    panel.classList.add('proposal-contact-panel--calendar');
    panel.setAttribute('aria-labelledby', 'proposal-calendar-title');
    find('calendar-status').textContent = 'Loading available times...';
    frame.addEventListener('load', () => {
      window.clearTimeout(calendarTimer);
      find('calendar-status').textContent = 'If booking does not appear, open Calendly in a new tab.';
    }, { once: true });
    calendarTimer = window.setTimeout(() => {
      find('calendar-status').textContent = 'Taking a little longer? You can open Calendly in a new tab.';
    }, 12000);
    find('calendar-frame').replaceChildren(frame);
    find('calendar-back').focus();
  }

  form.addEventListener('submit', submitAccess);
  codeInput.addEventListener('input', () => {
    codeInput.setAttribute('aria-invalid', 'false');
    find('access-message').textContent = '';
  });
  find('logout').addEventListener('click', lockProposal);
  find('retry').addEventListener('click', () => retryAction === 'logout' ? lockProposal() : loadContent({ focus: true }));
  downloadButton.addEventListener('click', downloadProposal);
  find('download-retry').addEventListener('click', downloadProposal);
  for (const trigger of app.querySelectorAll('[data-proposal-contact-open]')) trigger.addEventListener('click', openContact);
  find('contact-close').addEventListener('click', () => closeContact());
  panel.addEventListener('cancel', (event) => { event.preventDefault(); closeContact(); });
  panel.addEventListener('close', resetCalendar);
  panel.addEventListener('click', (event) => {
    if (event.target !== panel) return;
    const rect = panel.getBoundingClientRect();
    if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) closeContact();
  });
  find('calendar-start').addEventListener('click', openCalendar);
  find('calendar-back').addEventListener('click', () => { resetCalendar(); find('calendar-start').focus(); });

  // Clear private DOM before history caching, and revalidate a restored or revisited tab.
  window.addEventListener('pagehide', () => {
    requestVersion += 1;
    requestController?.abort();
    codeInput.value = '';
    clearProtectedContent();
    setView('loading');
  });
  window.addEventListener('pageshow', (event) => { if (event.persisted) loadContent(); });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && !viewer.hidden && !panel.open) loadContent({ quiet: true });
  });

  loadContent();
})();
