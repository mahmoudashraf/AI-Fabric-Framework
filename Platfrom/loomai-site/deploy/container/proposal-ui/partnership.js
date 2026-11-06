import { node, icon, photo, button, publicLink, sectionHeading, createWorkspace } from './presentation.js';

export function videoEmbedUrl(id) {
  return typeof id === 'string' && /^[a-zA-Z0-9_-]{11}$/.test(id)
    ? `https://www.youtube-nocookie.com/embed/${id}?autoplay=1&rel=0` : null;
}

function introductionVideo(data, presenterImage, presenter) {
  if (!data) return null;
  const figure = node('figure', 'pp-video');
  const copy = node('div', 'pp-video-copy', node('span', 'pp-eyebrow', data.eyebrow), node('h2', '', data.title), node('p', '', data.description));
  const embed = videoEmbedUrl(data.youtubeId);
  if (embed) {
    figure.classList.add('pp-video--playable');
    copy.append(button(data.playLabel, 'play', () => {
      const frame = node('iframe', 'pp-video-frame');
      frame.title = data.title;
      frame.src = embed;
      frame.referrerPolicy = 'no-referrer';
      frame.allow = 'autoplay; encrypted-media; picture-in-picture; fullscreen';
      frame.allowFullscreen = true;
      figure.replaceChildren(frame);
    }, 'pv-button--blue'));
  } else copy.append(node('span', 'pp-video-pending', icon('play'), data.pendingLabel));
  figure.append(copy, node('div', 'pp-video-presenter', photo(presenterImage, presenter), node('strong', '', presenter)), node('figcaption', '', data.caption));
  return figure;
}

export function renderPartnership(root, data, { openContact, appendDocument }) {
  const section = (id, eyebrow, title, subtitle) => {
    const result = node('section', 'pp-section');
    result.id = id;
    result.append(node('span', 'pp-eyebrow', eyebrow), sectionHeading(title, subtitle));
    return result;
  };
  const list = (items, className = '') => node('ul', className, items.map(text => node('li', '', icon('circle-check'), node('span', '', text))));
  const intro = node('header', 'pp-intro');
  const heading = node('h1', '', data.title);
  heading.tabIndex = -1;
  intro.append(node('span', 'pp-eyebrow', data.eyebrow), heading, node('p', 'pp-lead', data.subtitle),
    node('div', 'pv-byline', photo(data.presenterImage, data.presenter), node('div', '', node('strong', '', data.preparedBy), node('span', '', data.presentedBy))),
    node('div', 'pp-request', icon('plug'), node('div', '', node('strong', '', data.request.title), node('p', '', data.request.summary))),
    node('p', 'pp-status', data.status));

  const value = section('partnership-value', data.value.eyebrow, data.value.title, data.value.subtitle);
  value.append(node('div', 'pp-values', data.value.items.map((item, index) => node('div', '', icon(['search', 'book-open', 'circle-check'][index]), node('h3', '', item.title), node('p', '', item.text)))));
  if (data.value.leadJourney) {
    const journey = data.value.leadJourney;
    value.append(node('div', 'pp-lead-journey', node('h3', '', journey.title), node('ol', '', journey.steps.map((step, index) => node('li', '', icon(['monitor', 'message-square', 'shield-check', 'file-check'][index]), node('strong', '', step.title), node('p', '', step.text)))), node('p', 'pp-lead-destination', journey.destination)), node('p', 'pp-caption', journey.measurement));
  }

  const experience = section('retailer-experience', data.experience.eyebrow, data.experience.title, data.experience.subtitle);
  const workspace = createWorkspace(data.workspace);
  const detail = node('div', 'pp-experience-copy', node('p', 'pp-caption', data.experience.notice),
    list(data.experience.channels, 'pp-checks'), node('p', 'pp-boundary', data.experience.installation),
    publicLink(data.experience.demoLabel, '/demos/dealership-ai/', 'arrow-up-right'));
  experience.append(node('div', 'pp-experience', workspace, detail));

  const flow = section('integration-flow', data.flow.eyebrow, data.flow.title, data.flow.subtitle);
  const provider = node('div', 'pp-flow-source', icon('car-front'), node('h3', '', data.flow.provider.title), node('p', '', data.flow.provider.text));
  const runtime = node('div', 'pp-flow-runtime', icon('brain-circuit'), node('span', 'pp-caption', data.flow.runtime.scope), node('h3', '', data.flow.runtime.title), list(data.flow.runtime.items, 'pp-checks'));
  const channels = node('div', 'pp-flow-channels', icon('message-square'), node('h3', '', data.flow.channels.title), list(data.flow.channels.items, 'pp-checks'));
  flow.append(node('div', 'pp-flow', provider, icon('arrow-right'), runtime, icon('arrow-right'), channels),
    node('div', 'pp-documents', icon('file-text'), node('strong', '', data.flow.documents.title), node('span', '', data.flow.documents.text)),
    node('div', 'pp-return', icon('arrow-right'), node('div', '', node('strong', '', data.flow.return.title), node('p', '', data.flow.return.text))),
    node('p', 'pp-caption', data.flow.boundary));

  const technical = section('sandbox-scope', data.scope.eyebrow, data.scope.title, data.scope.subtitle);
  const api = node('div', 'pp-api');
  for (const item of data.scope.calls) api.append(node('div', 'pp-api-row', node('code', '', item.endpoint), node('div', '', node('strong', '', item.title), node('p', '', item.text)), node('span', 'pp-api-status', item.status)));
  const facts = node('div', 'pp-data', node('div', '', node('h3', '', data.scope.dataTitle), list(data.scope.data, 'pp-checks')), node('div', '', node('h3', '', data.scope.exclusionsTitle), node('p', '', data.scope.exclusions)));
  technical.append(api, facts, node('p', 'pp-boundary', data.scope.auth), node('p', 'pp-caption', data.scope.contract));

  const pilot = section('proof-of-concept', data.pilot.eyebrow, data.pilot.title, data.pilot.subtitle);
  pilot.append(node('ol', 'pp-phases', data.pilot.phases.map((phase, index) => node('li', '', node('span', 'pi-number', String(index + 1)), node('div', '', node('h3', '', phase.title), node('p', '', phase.text))))),
    node('div', 'pp-success', node('h3', '', data.pilot.successTitle), list(data.pilot.success, 'pp-checks')),
    node('p', 'pp-caption', data.pilot.measurement));

  const commercial = section('partnership-model', data.commercial.eyebrow, data.commercial.title, data.commercial.subtitle);
  commercial.append(node('div', 'pp-models', data.commercial.options.map((option, index) => node('div', '', node('span', 'pp-eyebrow', option.label), icon(index === 0 ? 'plug' : 'monitor'), node('h3', '', option.title), node('p', '', option.text)))), node('p', 'pp-caption', data.commercial.note));

  const next = section('next-step', data.next.eyebrow, data.next.title, data.next.subtitle);
  next.classList.add('pp-next');
  next.append(list(data.next.items, 'pp-checks'), node('div', 'pp-next-actions', button(data.next.contactLabel, 'calendar-days', openContact, 'pv-button--blue'), publicLink(data.experience.demoLabel, '/demos/dealership-ai/')));

  const details = node('details', 'pv-full-details');
  const documentBody = node('div', 'pv-full-details-body');
  appendDocument(documentBody);
  details.append(node('summary', '', data.detailsLabel), documentBody);
  const video = introductionVideo(data.video, data.presenterImage, data.presenter);
  root.replaceChildren(intro, ...(video ? [video] : []), value, experience, flow, technical, pilot, commercial, next, details);
}
