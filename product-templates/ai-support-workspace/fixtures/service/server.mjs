import { createServer } from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const observations = JSON.parse(readFileSync(new URL('../acme-read-observations.json', import.meta.url), 'utf8'));
const documents = new Map([
  '01-project-and-connector-setup.txt',
  '02-service-status-and-maintenance.txt',
  '03-support-and-escalation.txt',
].map(name => [name, readFileSync(new URL(`../../documents/acme-cloud/${name}`, import.meta.url), 'utf8')]));
const routes = new Map([
  ['/api/support/workspace-status', 'acme_get_workspace_status'],
  ['/api/support/service-status', 'acme_get_service_status'],
]);

export function createAcmeFixtureServer(apiKey) {
  if (typeof apiKey !== 'string' || apiKey.trim().length < 16) {
    throw new Error('Set a dedicated ACME_SUPPORT_API_KEY of at least 16 characters. No default key is provided.');
  }
  const expected = Buffer.from(apiKey, 'utf8');
  return createServer((request, response) => {
    response.setHeader('Cache-Control', 'no-store');
    response.setHeader('X-Content-Type-Options', 'nosniff');
    response.setHeader('X-Acme-Simulation', 'true');
    const send = (status, body) => {
      response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
      response.end(JSON.stringify(body));
    };
    const url = new URL(request.url, 'http://fixture.invalid');
    if (request.method === 'GET' && url.pathname === '/healthz') {
      send(200, { status: 'UP', simulation: true });
      return;
    }
    const supplied = Buffer.from(request.headers['x-acme-support-service-key'] ?? '', 'utf8');
    if (supplied.length !== expected.length || !timingSafeEqual(supplied, expected)) {
      send(401, { error: 'UNAUTHORIZED', simulation: true });
      return;
    }
    if (request.method !== 'GET') {
      response.setHeader('Allow', 'GET');
      send(405, { error: 'READ_ONLY_FIXTURE', simulation: true });
      return;
    }
    if (url.search) {
      send(400, { error: 'NO_RESOURCE_SELECTOR_SUPPORTED', simulation: true });
      return;
    }
    const observation = observations[routes.get(url.pathname)];
    if (observation) {
      send(200, { simulation: true, ...observation });
      return;
    }
    const document = documents.get(url.pathname.startsWith('/documents/') ? url.pathname.slice('/documents/'.length) : '');
    if (document) {
      response.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
      response.end(document);
      return;
    }
    send(404, { error: 'NOT_FOUND', simulation: true });
  });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const port = Number(process.env.PORT ?? '8118');
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT must be a valid TCP port.');
  const server = createAcmeFixtureServer(process.env.ACME_SUPPORT_API_KEY);
  server.listen(port, '0.0.0.0', () => process.stdout.write(`Acme simulated support fixture listening on ${port}\n`));
  for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => server.close(() => process.exit(0)));
}
