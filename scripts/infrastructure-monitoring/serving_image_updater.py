#!/usr/bin/env python3
"""Opt-in, host-local adoption of catalogued serving images. No build or SSH port."""

import argparse
from contextlib import contextmanager
import fcntl
import http.client
import json
import os
from pathlib import Path
import re
import signal
import stat
import subprocess
import tempfile
import time


CONFIG = Path('/etc/loomai/serving-images.json')
STATE_DIR = Path('/var/lib/loomai-serving-images')
COOLIFY_URL = 'http://127.0.0.1:8000'
UUID = r'[A-Za-z0-9][A-Za-z0-9_-]{0,63}'
SHA = r'[0-9a-f]{40}'
DIGEST = r'sha256:[0-9a-f]{64}'
MAX_APPS = 25
API_TIMEOUT = 2
REGISTRY_TIMEOUT = 30
TICK_SECONDS = 600
MAX_BODY = 1024 * 1024
TERMINAL = {'finished', 'failed', 'cancelled'}
BUSY = {'queued', 'in_progress'}


class UpdateError(Exception):
    """Only fixed, credential-free error codes are logged."""


class Budget:
    def __init__(self, clock=time.monotonic):
        self.clock = clock
        self.deadline = clock() + TICK_SECONDS

    def timeout(self, maximum):
        remaining = self.deadline - self.clock()
        if remaining <= 0:
            raise UpdateError('tick-budget-exhausted')
        return min(maximum, remaining)


@contextmanager
def api_deadline(seconds):
    """Single-threaded worker: cap the whole HTTP exchange, including slow bodies."""
    previous = signal.getsignal(signal.SIGALRM)
    remaining, interval = signal.getitimer(signal.ITIMER_REAL)
    started = time.monotonic()
    def expired(_signum, _frame):
        raise UpdateError('coolify-request-timeout')
    signal.signal(signal.SIGALRM, expired)
    signal.setitimer(signal.ITIMER_REAL, seconds)
    try:
        yield
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, previous)
        if remaining:
            signal.setitimer(signal.ITIMER_REAL, max(0.001, remaining - (time.monotonic() - started)), interval)


def read_private_json(path, modes=(0o600,)):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
    with os.fdopen(fd, 'rb') as handle:
        info = os.fstat(handle.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_uid != 0 or stat.S_IMODE(info.st_mode) not in modes:
            raise UpdateError('root-owned-file-mode-required')
        raw = handle.read(MAX_BODY + 1)
        if len(raw) > MAX_BODY:
            raise UpdateError('local-file-too-large')
        return json.loads(raw)


def validate_config(config, catalogue):
    # The root-owned binding plus this catalogue is the resource/permission boundary.
    if not isinstance(config, dict) or set(config) != {'coolifyUrl', 'apiKey', 'apps'}:
        raise UpdateError('invalid-config-fields')
    if config['coolifyUrl'] != COOLIFY_URL:
        raise UpdateError('loopback-coolify-url-required')
    key = config['apiKey']
    if not isinstance(key, str) or not key or len(key) > 4096 or any(ord(c) < 33 or ord(c) > 126 for c in key):
        raise UpdateError('invalid-api-key')
    apps = config['apps']
    if not isinstance(apps, list) or not 1 <= len(apps) <= MAX_APPS:
        raise UpdateError('invalid-app-count')
    allowed = {app['id']: app for app in catalogue['apps']}
    seen = set()
    for app in apps:
        if not isinstance(app, dict) or set(app) != {'appUuid', 'image', 'channel', 'sourceRepository', 'branch', 'appId'}:
            raise UpdateError('invalid-binding-fields')
        if not all(isinstance(value, str) for value in app.values()):
            raise UpdateError('invalid-binding-value')
        if not re.fullmatch(UUID, app['appUuid']) or app['appUuid'] in seen:
            raise UpdateError('invalid-or-duplicate-app-uuid')
        seen.add(app['appUuid'])
        entry = allowed.get(app['appId'])
        if not entry:
            raise UpdateError('non-serving-app-denied')
        source = catalogue['sources'][entry['source']]
        if app['sourceRepository'] != 'https://github.com/' + source['repository'] or app['branch'] != source['branch']:
            raise UpdateError('source-binding-mismatch')
        if not re.fullmatch(r'ghcr\.io/[a-z0-9]+(?:[._-][a-z0-9]+)*/' + re.escape(entry['image']), app['image']):
            raise UpdateError('image-repository-mismatch')
        if app['channel'] != app['appId'] + '-current':
            raise UpdateError('channel-binding-mismatch')
    return config


class Coolify:
    def __init__(self, api_key, budget, connection=http.client.HTTPConnection):
        self.api_key, self.budget, self.connection = api_key, budget, connection

    def _request(self, method, path, body=None):
        read = method == 'GET' and (
            re.fullmatch('/applications/' + UUID, path)
            or re.fullmatch('/deployments/' + UUID, path)
            or re.fullmatch('/deployments/applications/' + UUID + r'\?skip=0&take=(1|20)', path)
            or path == '/deployments'
            or re.fullmatch('/deploy' + r'\?uuid=' + UUID + '&force=false', path)
        )
        patch = method == 'PATCH' and re.fullmatch('/applications/' + UUID, path)
        if not (read or patch) or (patch and (not isinstance(body, dict) or set(body) != {
            'docker_registry_image_name', 'docker_registry_image_tag'
        })) or (read and body is not None):
            raise UpdateError('api-operation-denied')
        connection = self.connection('127.0.0.1', 8000, timeout=self.budget.timeout(API_TIMEOUT))
        try:
            with api_deadline(self.budget.timeout(API_TIMEOUT)):
                connection.request(method, '/api/v1' + path,
                                   body=json.dumps(body) if body is not None else None,
                                   headers={'Authorization': 'Bearer ' + self.api_key,
                                            'Accept': 'application/json', 'Content-Type': 'application/json'})
                response = connection.getresponse()
                # http.client does not follow redirects or use proxy environment variables.
                if not 200 <= response.status < 300:
                    raise UpdateError('coolify-http-failed')
                raw = response.read(MAX_BODY + 1)
                if len(raw) > MAX_BODY:
                    raise UpdateError('coolify-response-too-large')
                return json.loads(raw)
        except UpdateError:
            raise
        except Exception:
            raise UpdateError('coolify-response-unavailable') from None
        finally:
            connection.close()

    def app(self, uuid):
        return self._request('GET', '/applications/' + uuid)

    def queue(self):
        return self._request('GET', '/deployments')

    def history(self, uuid, take=20):
        if type(take) is not int or take not in (1, 20):
            raise UpdateError('api-operation-denied')
        result = self._request('GET', '/deployments/applications/' + uuid + '?skip=0&take=' + str(take))
        if (not isinstance(result, dict) or not isinstance(result.get('count'), int)
                or not isinstance(result.get('deployments'), list) or len(result['deployments']) > take
                or result['count'] < len(result['deployments'])):
            raise UpdateError('deployment-history-unavailable')
        return result['deployments']

    def patch_image(self, uuid, image, tag):
        return self._request('PATCH', '/applications/' + uuid,
                             {'docker_registry_image_name': image, 'docker_registry_image_tag': tag})

    def deploy(self, uuid):
        return self._request('GET', '/deploy?uuid=' + uuid + '&force=false')

    def deployment(self, uuid):
        return self._request('GET', '/deployments/' + uuid)


class Docker:
    def __init__(self, budget, run=subprocess.run):
        self.budget, self.run = budget, run

    def _command(self, args):
        try:
            result = self.run(['/usr/bin/docker'] + args, check=True, capture_output=True, text=True,
                              timeout=self.budget.timeout(REGISTRY_TIMEOUT))
            if len(result.stdout) > MAX_BODY:
                raise UpdateError('docker-response-too-large')
            return result.stdout
        except UpdateError:
            raise
        except Exception:
            raise UpdateError('docker-pull-or-inspect-failed') from None

    def pull(self, reference):
        self._command(['image', 'pull', '--quiet', reference])
        template = '{"labels":{{json .Config.Labels}},"digests":{{json .RepoDigests}},"id":{{json .Id}}}'
        try:
            return json.loads(self._command(['image', 'inspect', '--format', template, reference]))
        except (ValueError, TypeError):
            raise UpdateError('invalid-docker-image-metadata') from None

    def candidate(self, app):
        channel = self.pull(app['image'] + ':' + app['channel'])
        sha, digest = validate_image(channel, app)
        tag = sha + '-' + app['appId']
        immutable = self.pull(app['image'] + ':' + tag)
        other_sha, other_digest = validate_image(immutable, app)
        if (sha, digest, channel['id']) != (other_sha, other_digest, immutable['id']):
            raise UpdateError('channel-immutable-digest-mismatch')
        return {'image': app['image'], 'tag': tag, 'digest': digest}


def validate_image(metadata, app):
    if not isinstance(metadata, dict) or not isinstance(metadata.get('labels'), dict):
        raise UpdateError('missing-image-labels')
    labels = metadata['labels']
    expected = {'org.opencontainers.image.source': app['sourceRepository'],
                'org.opencontainers.image.ref.name': app['branch'],
                'org.opencontainers.image.title': app['appId']}
    sha = labels.get('org.opencontainers.image.revision')
    if any(labels.get(key) != value for key, value in expected.items()) or not isinstance(sha, str) or not re.fullmatch(SHA, sha):
        raise UpdateError('image-provenance-mismatch')
    prefix = app['image'] + '@'
    digests = metadata.get('digests')
    matching = [value[len(prefix):] for value in digests if isinstance(value, str) and value.startswith(prefix)] if isinstance(digests, list) else []
    if (len(matching) != 1 or not re.fullmatch(DIGEST, matching[0])
            or not isinstance(metadata.get('id'), str) or not re.fullmatch(DIGEST, metadata['id'])):
        raise UpdateError('exact-repository-digest-required')
    return sha, matching[0]


class State:
    def __init__(self, directory):
        self.directory, self.path = directory, directory / 'state.json'
        try:
            self.data = read_private_json(self.path)
        except FileNotFoundError:
            self.data = {'version': 1, 'apps': {}}
        if (not isinstance(self.data, dict) or self.data.get('version') != 1
                or not isinstance(self.data.get('apps'), dict) or len(self.data['apps']) > MAX_APPS):
            raise UpdateError('invalid-mutation-state')

    def get(self, uuid):
        return self.data['apps'].get(uuid)

    def put(self, uuid, receipt):
        next_data = {'version': 1, 'apps': dict(self.data['apps'], **{uuid: receipt})}
        if len(next_data['apps']) > MAX_APPS:
            raise UpdateError('mutation-state-full')
        # Durable intent precedes each mutation, including a crash or lost response.
        name = None
        try:
            with tempfile.NamedTemporaryFile(mode='w', dir=self.directory, delete=False) as handle:
                name = handle.name
                os.chmod(name, 0o600)
                json.dump(next_data, handle, separators=(',', ':'))
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(name, self.path)
            parent = os.open(self.directory, os.O_RDONLY | os.O_DIRECTORY)
            try:
                os.fsync(parent)
            finally:
                os.close(parent)
            self.data = next_data
        finally:
            if name and os.path.exists(name):
                os.unlink(name)


def check_app(value, app):
    if (not isinstance(value, dict) or value.get('uuid') != app['appUuid']
            or value.get('build_pack') != 'dockerimage' or value.get('docker_registry_image_name') != app['image']
            or not isinstance(value.get('docker_registry_image_tag'), str)
            or value.get('status') not in {'running', 'running:healthy', 'running:unhealthy', 'running:unknown'}):
        raise UpdateError('existing-running-image-app-required')
    return value['docker_registry_image_tag']


def read_app(app, api, expected_id=None):
    value = api.app(app['appUuid'])
    check_app(value, app)
    identity = value.get('id')
    if identity is None:
        # The authenticated own-app history binds the redacted numeric queue identity.
        jobs = api.history(app['appUuid'], take=1)
        if (not isinstance(jobs, list) or len(jobs) != 1 or not isinstance(jobs[0], dict)
                or jobs[0].get('application_uuid', app['appUuid']) != app['appUuid']):
            raise UpdateError('application-identity-unavailable')
        identity = jobs[0].get('application_id')
    if (isinstance(identity, bool) or not isinstance(identity, (int, str))
            or not re.fullmatch(r'[1-9][0-9]*', str(identity))):
        raise UpdateError('application-identity-unavailable')
    identity = int(identity)
    if expected_id is not None and identity != expected_id:
        raise UpdateError('application-identity-mismatch')
    return dict(value, id=identity)


def is_busy(queue, value):
    if isinstance(queue, dict) and all(str(key).isdigit() for key in queue):
        queue = list(queue.values())
    if not isinstance(queue, list) or len(queue) > 1000:
        raise UpdateError('deployment-queue-unavailable')
    for job in queue:
        if not isinstance(job, dict) or 'application_id' not in job or job.get('status') not in BUSY | TERMINAL:
            raise UpdateError('deployment-queue-unavailable')
        if str(job['application_id']) == str(value['id']) and job['status'] in BUSY:
            return True
    return False


def trigger(app, receipt, api, state, apply, application_id):
    if not apply:
        return 'pending-deploy-check-only'
    fresh = read_app(app, api, application_id)
    if check_app(fresh, app) != receipt['tag']:
        raise UpdateError('pending-image-readback-mismatch')
    if is_busy(api.queue(), fresh):
        return 'busy'
    receipt = dict(receipt, phase='deploy')
    state.put(app['appUuid'], receipt)
    try:
        result = api.deploy(app['appUuid'])
        jobs = result.get('deployments') if isinstance(result, dict) else None
        if (not isinstance(jobs, list) or len(jobs) != 1 or not isinstance(jobs[0], dict)
                or jobs[0].get('resource_uuid') != app['appUuid']
                or not isinstance(jobs[0].get('deployment_uuid'), str)
                or not re.fullmatch(UUID, jobs[0]['deployment_uuid'])):
            raise UpdateError('deploy-response-unavailable')
        state.put(app['appUuid'], dict(receipt, deployment=jobs[0]['deployment_uuid']))
    except Exception:
        raise UpdateError('uncertain-deploy-readback-next-tick') from None
    return 'deployment-requested'


def reconcile(app, value, receipt, api, docker, state, apply):
    if (not isinstance(receipt, dict) or receipt.get('image') != app['image']
            or not re.fullmatch(SHA + '-' + re.escape(app['appId']), str(receipt.get('tag', '')))
            or not re.fullmatch(DIGEST, str(receipt.get('digest', '')))):
        raise UpdateError('mutation-receipt-binding-mismatch')
    phase = receipt.get('phase')
    if phase == 'failed':
        return 'failed-deployment-needs-review'
    if check_app(value, app) != receipt['tag']:
        return 'uncertain-patch-or-config-drift-needs-review'
    if is_busy(api.queue(), value):
        return 'busy'
    if phase == 'patch':
        if apply:
            state.put(app['appUuid'], dict(receipt, phase='ready'))
        return 'patch-confirmed-deploy-next-tick'
    if phase == 'ready':
        candidate = docker.candidate(app)
        if any(candidate[key] != receipt[key] for key in ('image', 'tag', 'digest')):
            return 'pending-channel-changed-needs-review'
        return trigger(app, receipt, api, state, apply, value['id'])
    if phase != 'deploy':
        raise UpdateError('invalid-mutation-phase')
    deployment = receipt.get('deployment')
    if not isinstance(deployment, str) or not re.fullmatch(UUID, deployment):
        api.history(app['appUuid'])
        return 'uncertain-deploy-history-read-needs-review'
    job = api.deployment(deployment)
    if (not isinstance(job, dict) or job.get('deployment_uuid') != deployment
            or str(job.get('application_id')) != str(value['id']) or job.get('status') not in BUSY | TERMINAL):
        raise UpdateError('deployment-readback-unavailable')
    status = job['status']
    if status in BUSY:
        return 'busy'
    if apply:
        state.put(app['appUuid'], dict(receipt, phase='done' if status == 'finished' else 'failed'))
    return 'deployment-finished' if status == 'finished' else 'failed-deployment-needs-review'


def update_one(app, api, docker, state, apply):
    value = read_app(app, api)
    tag = check_app(value, app)
    receipt = state.get(app['appUuid'])
    if receipt and receipt.get('phase') != 'done':
        return reconcile(app, value, receipt, api, docker, state, apply)
    if is_busy(api.queue(), value):
        return 'busy'
    candidate = docker.candidate(app)
    if tag == candidate['tag']:
        return 'already-current'
    if not apply:
        return 'update-available-check-only'
    fresh = read_app(app, api, value['id'])
    if check_app(fresh, app) != tag:
        return 'app-changed-during-pull'
    if is_busy(api.queue(), fresh):
        return 'busy'
    receipt = dict(candidate, phase='patch')
    state.put(app['appUuid'], receipt)
    try:
        api.patch_image(app['appUuid'], candidate['image'], candidate['tag'])
        if check_app(read_app(app, api, value['id']), app) != candidate['tag']:
            raise UpdateError('patch-readback-mismatch')
        receipt = dict(receipt, phase='ready')
        state.put(app['appUuid'], receipt)
    except Exception:
        raise UpdateError('uncertain-patch-readback-next-tick') from None
    return trigger(app, receipt, api, state, apply, value['id'])


def tick(config, api, docker, state, budget, apply=False, log=print):
    failed = False
    for app in config['apps']:
        try:
            budget.timeout(API_TIMEOUT)
            result = update_one(app, api, docker, state, apply)
        except Exception as error:
            result = str(error) if isinstance(error, UpdateError) else 'updater-operation-failed'
            failed = True
        log(json.dumps({'appId': app['appId'], 'result': result}, separators=(',', ':')))
        failed = failed or result.endswith('needs-review')
        if result == 'tick-budget-exhausted':
            break
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='explicitly permit image-only PATCH and pull-only deployment')
    args = parser.parse_args()
    try:
        if os.geteuid() != 0:
            raise UpdateError('root-required')
        catalogue = read_private_json(Path(__file__).with_name('build-catalogue.json'), (0o600, 0o644))
        config = validate_config(read_private_json(CONFIG), catalogue)
        info = STATE_DIR.lstat()
        if not stat.S_ISDIR(info.st_mode) or info.st_uid != 0 or stat.S_IMODE(info.st_mode) != 0o700:
            raise UpdateError('root-private-state-directory-required')
        lock = os.open(STATE_DIR / 'lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        with os.fdopen(lock, 'w') as handle:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
            budget = Budget()
            def expired(_signum, _frame):
                raise UpdateError('tick-budget-exhausted')
            signal.signal(signal.SIGALRM, expired)
            signal.setitimer(signal.ITIMER_REAL, TICK_SECONDS)
            try:
                return tick(config, Coolify(config['apiKey'], budget), Docker(budget), State(STATE_DIR), budget, args.apply)
            finally:
                signal.setitimer(signal.ITIMER_REAL, 0)
    except Exception:
        print('{"result":"configuration-state-or-lock-unavailable"}')
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
