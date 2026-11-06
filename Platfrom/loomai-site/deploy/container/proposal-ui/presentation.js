const assets = '/assets/proposals/';
const svgNamespace = 'http://www.w3.org/2000/svg';

export function safeAsset(value) {
  return typeof value === 'string' && /^\/assets\/[a-zA-Z0-9/_-]+\.(?:png|jpe?g|webp|avif)$/.test(value)
    && !value.includes('//') ? value : null;
}

function node(tag, className, ...children) {
  const result = document.createElement(tag);
  if (className) result.className = className;
  for (const child of children.flat()) if (child != null) result.append(typeof child === 'string' ? document.createTextNode(child) : child);
  return result;
}

function icon(name) {
  const svg = document.createElementNS(svgNamespace, 'svg');
  svg.classList.add('pv-icon');
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS(svgNamespace, 'use');
  use.setAttribute('href', `${assets}icons-v2.svg#${name}`);
  svg.append(use);
  return svg;
}

function photo(src, alt, className = '', eager = false) {
  const image = node('img', className);
  const path = safeAsset(src);
  if (path) image.src = path;
  image.alt = alt || '';
  image.loading = eager ? 'eager' : 'lazy';
  image.decoding = 'async';
  return image;
}

function button(label, iconName, click, className = '') {
  const result = node('button', `pv-button ${className}`, iconName ? icon(iconName) : null, label);
  result.type = 'button';
  result.addEventListener('click', click);
  return result;
}

function publicLink(label, href, iconName = 'arrow-up-right', className = '') {
  const result = node('a', `pv-button ${className}`, icon(iconName), label);
  // Preview actions use fixed site routes, not unverified proposal URLs.
  result.href = href;
  result.target = '_blank';
  result.rel = 'noopener noreferrer';
  result.referrerPolicy = 'no-referrer';
  return result;
}

function windowTop(label) {
  return node('div', 'pv-window-top', node('span', 'pv-traffic', node('i'), node('i'), node('i')), node('span', '', label));
}

function sectionHeading(title, subtitle) {
  return node('div', 'pv-section-heading', node('h2', '', title), subtitle ? node('p', '', subtitle) : null);
}

function chatLine(text, user = false) {
  return node('div', `pv-chat-line ${user ? 'pv-chat-line--user' : ''}`, icon(user ? 'user-round' : 'sparkles'), node('p', '', text));
}

function vehicleTiles(data, compact = false) {
  return node('div', `pv-vehicles ${compact ? 'pv-vehicles--compact' : ''}`, data.vehicles.map(vehicle =>
    node('div', 'pv-vehicle', photo(vehicle.image, vehicle.label), node('strong', '', vehicle.label))));
}

function createWorkspace(data, compact = false) {
  const preview = node('div', `pv-workspace ${compact ? 'pv-workspace--compact' : ''}`);
  const body = node('div', 'pv-workspace-body');
  const response = node('div', 'pv-response');
  response.setAttribute('aria-live', 'polite');
  const tabs = node('div', 'pv-preview-tabs');
  const items = [['scale', data.compareLabel], ['file-check', data.deliveryLabel], ['headset', data.humanLabel]];
  function select(index) {
    for (const [position, tab] of Array.from(tabs.children).entries()) tab.setAttribute('aria-pressed', String(position === index));
    if (index === 0) {
      const table = node('div', 'pv-comparison', data.comparison.map(row => node('div', '', node('strong', '', row[0]), node('span', '', row[1]), node('span', '', row[2]))));
      response.replaceChildren(chatLine(data.question, true), chatLine(data.answer), vehicleTiles(data), ...(!compact ? [table, node('div', 'pv-citation', icon('car-front'), data.compareSource)] : []));
    } else if (index === 1) {
      response.replaceChildren(chatLine(data.deliveryQuestion, true), chatLine(data.deliveryAnswer), node('div', 'pv-citation', icon('book-open'), data.source));
    } else {
      response.replaceChildren(chatLine(data.humanQuestion, true), node('div', 'pv-handover-preview', photo(data.agentImage, 'Illustrative support agent'), node('div', '', node('strong', '', data.humanTitle), node('p', '', data.humanAnswer))));
    }
  }
  items.forEach(([name, label], index) => tabs.append(button(label, name, () => select(index))));
  const top = node('div', 'pv-workspace-top', node('strong', '', icon('car-front'), data.brand), button(data.humanLabel, 'user-round', () => select(2)));
  body.append(tabs, response);
  const actions = node('div', 'pv-preview-actions', button(data.deliveryLabel, 'file-check', () => select(1)), button(data.enquiryLabel, 'calendar-days', () => {
    response.replaceChildren(chatLine(data.enquiryQuestion, true), chatLine(data.enquiryAnswer), node('div', 'pv-example-notice', icon('shield-check'), data.exampleNotice));
    for (const tab of tabs.children) tab.setAttribute('aria-pressed', 'false');
  }, 'pv-button--blue'));
  body.append(actions, node('div', 'pv-handover', photo(data.agentImage, ''), icon('circle-check'), data.handover));
  if (compact) body.append(node('div', 'pv-composer pv-composer--static', data.composer, icon('send')));
  preview.append(top, body);
  select(0);
  return preview;
}

function websiteScene(data, expand) {
  const site = node('div', 'pv-browser');
  site.append(windowTop(data.websiteLabel), node('div', 'pv-site-brand', data.brand));
  const hero = node('div', 'pv-site-hero', photo(data.vehicles[0].image, 'Illustrative electric SUV'), node('div', 'pv-site-hero-copy', node('h3', '', data.websiteHeadline), node('span', '', data.websiteAction, icon('arrow-right'))));
  site.append(hero, node('div', 'pv-site-stock', node('strong', '', data.stockLabel), vehicleTiles(data, true)));
  const dock = node('div', 'pv-dock', icon('message-square'), node('span', '', data.dockPrompt), button(data.expandLabel, 'expand', expand));
  site.append(dock);
  return site;
}

function integrationProcess(data) {
  const p = data.integrationProcess;
  const stepHeading = (number, title, owner) => node('div', 'pi-heading', node('span', 'pi-number', number), node('h3', '', title), owner ? node('span', 'pi-owner', icon('monitor'), owner) : null);
  const role = (text, ours = false) => node('span', `pi-role ${ours ? 'pi-role--loomai' : ''}`, icon(ours ? 'plug' : 'user-round'), text);
  const section = node('section', 'pv-integration-process');
  section.id = 'proposal-integration-journey';
  section.setAttribute('aria-labelledby', 'proposal-integration-journey-title');
  const heading = sectionHeading(p.title, p.subtitle);
  heading.querySelector('h2').id = 'proposal-integration-journey-title';
  section.append(node('span', 'pi-eyebrow', p.eyebrow), heading);

  const documents = node('div', 'pi-documents', p.documents.labels.map((label, index) => node('div', 'pi-document', node('div', 'pi-document-page', icon('file-text'), node('strong', index === 1 ? 'pi-file-type pi-file-type--word' : 'pi-file-type', index === 1 ? 'W' : 'PDF')), node('span', '', label))));
  const upload = node('div', 'pi-upload', windowTop(p.documents.consoleTitle), icon('cloud-upload'), node('span', 'pi-upload-label', p.documents.uploadLabel), node('div', 'pi-upload-file', icon('file-text'), node('i'), icon('circle-check')), node('div', 'pi-upload-file', icon('file-text'), node('i'), icon('circle-check')));
  const bucket = node('div', 'pi-storage', node('span', 'pi-aws', 'AWS S3'), icon('database'), node('strong', '', p.documents.storageTitle), node('span', '', p.documents.storageOwner));
  const docs = node('div', 'pi-source pi-source--documents', stepHeading('1', p.documents.title), node('div', 'pi-roles', role(p.documents.customerRole), role(p.documents.loomaiRole, true)), node('div', 'pi-document-flow', documents, icon('arrow-right'), upload, icon('arrow-right'), bucket), node('strong', 'pi-note', p.documents.reassurance), node('p', 'pi-note', p.documents.indexing), node('p', 'pi-optional', p.documents.optional));

  const alternatives = node('div', 'pi-alternatives', node('div', '', icon('plug'), p.inventory.sources[0]), node('span', 'pi-or', p.inventory.orLabel), node('div', '', icon('car-front'), p.inventory.sources[1]));
  const sync = node('div', 'pi-sync', icon('refresh-cw'), node('strong', '', p.inventory.syncLabel), node('p', '', p.inventory.syncNote));
  const stock = node('div', 'pi-stock', data.vehicles.map(vehicle => photo(vehicle.image, '')));
  const inventory = node('div', 'pi-source pi-source--inventory', stepHeading('2', p.inventory.title), node('div', 'pi-roles', role(p.inventory.customerRole), role(p.inventory.loomaiRole, true)), node('div', 'pi-inventory-flow', alternatives, icon('arrow-right'), sync, stock), node('p', 'pi-optional', p.inventory.qualification));

  const brain = node('div', 'pi-brain', stepHeading('3', p.brain.title, p.owner), node('div', 'pi-brain-mark', icon('brain-circuit'), node('strong', '', p.brain.label)), node('ul', 'pi-brain-capabilities', p.brain.capabilities.map((label, index) => node('li', '', icon(['book-open', 'search', 'shield-check'][index]), label))), node('p', 'pi-brain-note', p.brain.note));

  const channels = node('div', 'pi-channels', stepHeading('4', p.channels.title, p.owner));
  const webMiniature = node('div', 'pi-channel-screen pi-channel-screen--website', node('strong', '', data.brand), photo(data.vehicles[0].image, ''), node('div', 'pi-mini-composer', icon('message-square'), data.composer, icon('send')));
  const expandedMiniature = node('div', 'pi-channel-screen pi-channel-screen--expanded', node('strong', '', data.brand), vehicleTiles(data, true), node('div', 'pi-mini-options', data.compareLabel, node('i'), data.deliveryLabel));
  const appMiniature = node('div', 'pi-channel-screen pi-channel-screen--app', node('div', 'pi-mini-address', icon('lock'), data.standalone.address), node('strong', '', data.brand), photo(data.vehicles[0].image, ''), node('div', 'pi-mini-composer', data.composer, icon('send')));
  for (const [index, miniature] of [webMiniature, expandedMiniature, appMiniature].entries()) {
    const label = node('div', 'pi-channel-label', icon(['monitor', 'message-square', 'smartphone'][index]), node('strong', '', p.channels.labels[index]));
    if (index === 2) label.append(node('span', 'pi-channel-domain', data.standalone.address), node('small', '', data.standalone.status));
    channels.append(node('div', 'pi-channel', miniature, label));
  }
  channels.append(node('div', 'pi-desk', icon('headset'), photo(data.agentImage, ''), node('strong', '', p.channels.desk)));

  const review = node('div', 'pi-review', node('h3', '', icon('circle-check'), p.review.title), node('ul', '', p.review.checks.map((label, index) => node('li', '', icon(['search', 'message-square', 'rocket'][index]), label))));
  section.append(node('div', 'pi-grid', node('div', 'pi-inputs', docs, inventory), brain, channels), review, node('p', 'pi-reassurance', p.reassurance));
  return section;
}

export function renderPresentation(root, data, { openContact, appendDocument }) {
  const d = data;
  const intro = node('header', 'pv-intro');
  const title = node('h1', '', d.title);
  title.tabIndex = -1;
  intro.append(title, node('p', 'pv-subtitle', d.subtitle), node('div', 'pv-byline', photo(d.presenterImage, d.presenter), node('div', '', node('strong', '', d.preparedBy), node('span', '', d.presentedBy))));

  const video = node('figure', 'pv-video', photo(d.video.poster, 'Illustrative cover for the forthcoming LoomAI Labs video walkthrough', '', true));
  const videoCopy = node('div', 'pv-video-copy', node('h2', '', d.video.title), node('p', '', d.video.presenter));
  if (/^[a-zA-Z0-9_-]{11}$/.test(d.video.youtubeId || '')) {
    video.append(button('Play proposal walkthrough', 'play', () => {
      const frame = node('iframe', 'pv-video-frame');
      frame.title = d.video.title;
      frame.src = `https://www.youtube-nocookie.com/embed/${d.video.youtubeId}?autoplay=1&rel=0`;
      frame.referrerPolicy = 'no-referrer';
      frame.allow = 'autoplay; encrypted-media; picture-in-picture; fullscreen';
      frame.allowFullscreen = true;
      video.replaceChildren(frame);
    }, 'pv-video-play'));
  } else {
    videoCopy.append(node('span', 'pv-video-pending', icon('play'), d.video.placeholder));
  }
  video.append(videoCopy, node('figcaption', 'pv-video-caption', d.video.caption));

  const scenes = node('section', 'pv-experience');
  scenes.append(sectionHeading(d.experienceTitle, d.experienceSubtitle), node('p', 'pv-illustration-note', d.illustrationNote));
  const workspace = createWorkspace(d);
  const workspaceFrame = node('div', 'pv-scene', node('div', 'pv-step-label', '2. ', d.expandedTitle), workspace);
  workspaceFrame.id = 'proposal-expanded-workspace';
  const website = node('div', 'pv-scene', node('div', 'pv-step-label', '1. ', d.websiteTitle), websiteScene(d, () => {
    workspaceFrame.scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'center' });
    workspace.querySelector('button').focus({ preventScroll: true });
  }));
  scenes.append(node('div', 'pv-scenes', website, workspaceFrame));

  const standalone = node('section', 'pv-standalone');
  standalone.append(sectionHeading(`3. ${d.standalone.title}`, d.standalone.subtitle));
  const laptop = node('div', 'pv-laptop', windowTop(d.standalone.address), createWorkspace(d, true));
  const phone = node('div', 'pv-phone', node('div', 'pv-phone-camera'), node('span', 'pv-phone-address', icon('lock'), d.standalone.address), node('strong', '', d.brand), photo(d.vehicles[0].image, d.vehicles[0].label), node('strong', '', d.standalone.phoneQuestion), node('div', 'pv-phone-lines', node('i'), node('i'), node('i')), node('div', 'pv-phone-input', d.composer, icon('send')));
  const devices = node('div', 'pv-devices', laptop, phone);
  const appDialog = node('dialog', 'pv-app-dialog');
  appDialog.setAttribute('aria-label', d.standalone.title);
  appDialog.append(node('div', 'pv-app-dialog-top', node('span', '', d.standalone.address), button('Close preview', 'arrow-right', () => appDialog.close())), node('p', 'pv-example-notice', d.standalone.disclaimer), createWorkspace(d, true));
  appDialog.addEventListener('click', event => { if (event.target === appDialog) { const r = appDialog.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) appDialog.close(); } });
  const openPreview = () => appDialog.showModal();
  const address = button(d.standalone.url, 'link', openPreview, 'pv-app-address');
  address.setAttribute('aria-label', `${d.standalone.address}. ${d.standalone.status}. ${d.standalone.previewLabel}`);
  const standaloneCopy = node('div', 'pv-standalone-copy', address, node('span', 'pv-proposed', d.standalone.status), button(d.standalone.previewLabel, 'external-link', openPreview, 'pv-button--blue'), node('p', 'pv-small', d.standalone.disclaimer), node('ul', 'pv-app-uses', d.standalone.uses.map((text, index) => node('li', '', icon(['mail', 'message-square', 'shield-check'][index]), text))));
  standalone.append(node('div', 'pv-standalone-layout', devices, standaloneCopy), appDialog);

  const journey = node('ol', 'pv-journey', d.journey.map((step, index) => node('li', '', icon(['search', 'scale', 'book-open', 'message-square', 'users-round'][index]), node('span', '', step), index < d.journey.length - 1 ? icon('arrow-right') : null)));
  journey.setAttribute('aria-label', 'Customer buying journey');

  const integration = node('section', 'pv-integration', sectionHeading(d.integration.title));
  const source = node('div', 'pv-integration-source', node('div', 'pv-source-art', photo(d.vehicles[0].image, ''), photo(d.vehicles[1].image, ''), node('span', '', icon('file-check'))), node('p', '', d.integration.source));
  const brain = node('div', 'pv-brain', icon('brain-circuit'), node('strong', '', d.integration.brain));
  const outputs = node('ul', 'pv-integration-outputs', d.integration.outputs.map((text, index) => node('li', '', icon(['monitor', 'expand', 'link', 'headset'][index]), text)));
  integration.append(node('div', 'pv-integration-flow', source, icon('arrow-right'), brain, icon('arrow-right'), outputs), node('p', 'pv-reassurance', icon('circle-check'), d.integration.reassurance), node('p', 'pv-small', d.integration.qualification));

  const outcomes = node('section', 'pv-outcomes', sectionHeading(d.outcomesTitle), node('div', 'pv-outcome-grid', d.outcomes.map((outcome, index) => {
    const art = node('div', `pv-outcome-art pv-outcome-art--${index}`, icon(['sparkles', 'car-front', 'headset'][index]), node('div', 'pv-art-lines', node('i'), node('i')), icon('circle-check'));
    if (index === 2) art.replaceChild(photo(d.agentImage, ''), art.firstChild);
    return node('div', 'pv-outcome', art, node('h3', '', outcome.title), node('p', '', outcome.body));
  })));

  const offer = node('section', 'pv-offer');
  const benefits = node('ul', 'pv-offer-benefits', d.offer.checks.map(text => node('li', '', icon('circle-check'), text)));
  offer.append(node('div', 'pv-offer-top', node('div', '', node('h2', '', d.offer.title), node('p', 'pv-offer-value', d.offer.value)), node('div', '', benefits, node('p', 'pv-small', d.offer.price))), node('div', 'pv-offer-bottom', node('div', 'pv-byline', photo(d.presenterImage, d.presenter), node('div', '', node('strong', '', d.presenter, ' · LoomAI Labs'), node('span', '', d.presenterRole))), node('div', 'pv-offer-actions', button(d.offer.primaryLabel, 'calendar-days', openContact, 'pv-button--blue'), publicLink(d.offer.demoLabel, '/demos/dealership-ai/', 'play'))));

  const details = node('details', 'pv-full-details');
  details.append(node('summary', '', d.detailsLabel));
  const detailsBody = node('div', 'pv-full-details-body');
  appendDocument(detailsBody);
  details.append(detailsBody);
  root.replaceChildren(intro, video, scenes, standalone, journey, ...(!d.integrationProcess ? [integration] : []), outcomes, ...(d.integrationProcess ? [integrationProcess(d)] : []), offer, details);
}
