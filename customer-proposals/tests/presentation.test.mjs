import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import {safeAsset} from '../../Platfrom/loomai-site/deploy/container/proposal-ui/presentation.js';

test('visual media stays on the existing same-origin asset boundary', () => {
  assert.equal(safeAsset('/assets/proposals/founder-video-preview.jpg'), '/assets/proposals/founder-video-preview.jpg');
  for (const input of ['https://evil.example/track.png', '//evil.example/x.jpg', '/assets//evil.jpg', '/assets/../secret.jpg', '/assets/%2e%2e/secret.jpg', '/assets/p.png?code=secret', 'data:image/svg+xml,<svg/>', '/assets/execute.svg', '/assets/track.jpg#private']) {
    assert.equal(safeAsset(input), null);
  }
});

test('public visual template contains no recipient or invitation content', () => {
  for (const name of ['index.html', 'client.js', 'presentation.js', 'presentation.css', 'integration.css', 'partnership.js', 'partnership.css']) {
    const source = readFileSync(new URL(`../../Platfrom/loomai-site/deploy/container/proposal-ui/${name}`, import.meta.url), 'utf8');
    assert.doesNotMatch(source, /Paul Rigby|paulrigbygroup|AutoTrader|autotrader\.co\.uk|EakBVN|gatewayKey|sbp_|github_pat_/);
  }
});

test('partnership presentation uses the existing safe renderer and fixed public demo only', () => {
  const source = readFileSync(new URL('../../Platfrom/loomai-site/deploy/container/proposal-ui/partnership.js', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /innerHTML|outerHTML|insertAdjacentHTML|document\.write|eval\(|fetch\(|window\.open/);
  assert.match(source, /from '\.\/presentation\.js'/);
  assert.match(source, /createWorkspace\(data\.workspace\)/);
  assert.match(source, /appendDocument\(documentBody\)/);
  assert.match(source, /publicLink\(data\.experience\.demoLabel, '\/demos\/dealership-ai\//);
});

test('renderer uses safe DOM construction and no arbitrary external app URL', () => {
  const source = readFileSync(new URL('../../Platfrom/loomai-site/deploy/container/proposal-ui/presentation.js', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /innerHTML|outerHTML|insertAdjacentHTML|document\.write|eval\(/);
  assert.doesNotMatch(source, /\.href\s*=\s*d\.|window\.open/);
  assert.match(source, /appDialog\.showModal\(\)/);
  assert.match(source, /youtube-nocookie\.com\/embed\//);
});

test('new integration symbols use a versioned sprite instead of an already-cached asset', () => {
  const source = readFileSync(new URL('../../Platfrom/loomai-site/deploy/container/proposal-ui/presentation.js', import.meta.url), 'utf8');
  assert.match(source, /icons-v2\.svg#/);
  const sprite = readFileSync(new URL('../../Platfrom/loomai-site/public/assets/proposals/icons-v2.svg', import.meta.url), 'utf8');
  for (const name of ['file-text', 'cloud-upload', 'database', 'plug', 'refresh-cw', 'rocket', 'lock']) assert.ok(sprite.includes(`id="${name}"`));
});
