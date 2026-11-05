import configparser
import copy
import io
import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import serving_image_updater as updater


HERE = Path(__file__).parent
CATALOGUE = json.loads((HERE / 'build-catalogue.json').read_text())
REVISION = 'a' * 40
DIGEST = 'sha256:' + 'b' * 64
IMAGE_ID = 'sha256:' + 'c' * 64
BINDING = {
    'appUuid': 'existing-demo-123', 'image': 'ghcr.io/mahmoudashraf/loomai-partner-ui',
    'channel': 'partner-ui-current', 'sourceRepository': 'https://github.com/mahmoudashraf/AI-Fabric-Framework',
    'branch': 'Platform-V11', 'appId': 'partner-ui',
}
TAG = REVISION + '-partner-ui'
CONFIG = {'coolifyUrl': updater.COOLIFY_URL, 'apiKey': 'private-test-token', 'apps': [BINDING]}


def metadata(binding=BINDING):
    return {'labels': {
        'org.opencontainers.image.source': binding['sourceRepository'],
        'org.opencontainers.image.revision': REVISION,
        'org.opencontainers.image.ref.name': binding['branch'],
        'org.opencontainers.image.title': binding['appId'],
    }, 'digests': [binding['image'] + '@' + DIGEST], 'id': IMAGE_ID}


class MemoryState:
    def __init__(self):
        self.data = {}
        self.writes = []

    def get(self, uuid):
        return copy.deepcopy(self.data.get(uuid))

    def put(self, uuid, value):
        self.data[uuid] = copy.deepcopy(value)
        self.writes.append(copy.deepcopy(value))


class FakeAPI:
    def __init__(self, state):
        self.value = {'uuid': BINDING['appUuid'], 'id': 123, 'status': 'running:healthy',
                      'build_pack': 'dockerimage', 'docker_registry_image_name': BINDING['image'],
                      'docker_registry_image_tag': 'old-qualified-tag'}
        self.state = state
        self.calls = []
        self.jobs = []
        self.status = 'finished'
        self.patch_failure = False
        self.deploy_failure = False
        self.fail_readback = False

    def app(self, uuid):
        self.calls.append(('app', uuid))
        if self.fail_readback and any(call[0] == 'patch' for call in self.calls):
            raise updater.UpdateError('coolify-response-unavailable')
        return copy.deepcopy(self.value)

    def queue(self):
        self.calls.append(('queue',))
        return copy.deepcopy(self.jobs)

    def history(self, uuid):
        self.calls.append(('history', uuid))
        return []

    def patch_image(self, uuid, image, tag):
        assert self.state.get(uuid)['phase'] == 'patch', 'mutation before durable patch intent'
        self.calls.append(('patch', uuid, image, tag))
        self.value['docker_registry_image_name'] = image
        self.value['docker_registry_image_tag'] = tag
        if self.patch_failure:
            raise OSError('private-test-token lost PATCH response')
        return {'uuid': uuid}

    def deploy(self, uuid):
        assert self.state.get(uuid)['phase'] == 'deploy', 'mutation before durable deploy intent'
        self.calls.append(('deploy', uuid))
        if self.deploy_failure:
            raise OSError('private-test-token lost deploy response')
        return {'deployments': [{'resource_uuid': uuid, 'deployment_uuid': 'deployment-1'}]}

    def deployment(self, uuid):
        self.calls.append(('deployment', uuid))
        return {'deployment_uuid': uuid, 'application_id': 123, 'status': self.status}


class FakeDocker:
    def __init__(self, budget):
        self.calls = []
        self.channel = metadata()
        self.immutable = metadata()
        self.missing = False
        self.after_pull = None
        self.port = updater.Docker(budget, self.run)

    def run(self, argv, **options):
        self.calls.append((argv, options))
        assert argv[:3] in (['/usr/bin/docker', 'image', 'pull'], ['/usr/bin/docker', 'image', 'inspect'])
        assert 'shell' not in options
        assert options['timeout'] <= 30
        if argv[2] == 'pull':
            if self.after_pull:
                self.after_pull()
            if self.missing and not argv[-1].endswith('-current'):
                raise subprocess.CalledProcessError(1, argv, stderr='private-test-token manifest absent')
            return SimpleNamespace(stdout='')
        value = self.channel if argv[-1].endswith('-current') else self.immutable
        return SimpleNamespace(stdout=json.dumps(value))


class ConfigTests(unittest.TestCase):
    def test_catalogue_bindings_are_the_only_authorized_resources(self):
        for entry in CATALOGUE['apps']:
            binding = dict(BINDING, appId=entry['id'], channel=entry['id'] + '-current',
                           image='ghcr.io/mahmoudashraf/' + entry['image'],
                           sourceRepository='https://github.com/' + CATALOGUE['sources'][entry['source']]['repository'],
                           branch=CATALOGUE['sources'][entry['source']]['branch'])
            updater.validate_config(dict(CONFIG, apps=[binding]), CATALOGUE)
        for app_id in ['customer-runtime', 'loomai-desk', 'loomai-engage', 'platform-backend', '$(id)']:
            with self.subTest(app_id=app_id), self.assertRaises(updater.UpdateError):
                updater.validate_config(dict(CONFIG, apps=[dict(BINDING, appId=app_id)]), CATALOGUE)

    def test_only_exact_loopback_url_is_allowed(self):
        for url in ['http://localhost:8000', 'https://127.0.0.1:8000', 'http://127.0.0.1:8000/',
                    'http://127.0.0.1:8000/api/v1', 'http://127.0.0.1:9000',
                    'http://127.0.0.1:8000@evil.test', 'http://46.225.162.106:8000']:
            with self.subTest(url=url), self.assertRaises(updater.UpdateError):
                updater.validate_config(dict(CONFIG, coolifyUrl=url), CATALOGUE)

    def test_exact_image_source_branch_channel_and_uuid_are_required(self):
        invalid = {'appUuid': '../envs', 'image': 'ghcr.io/mahmoudashraf/loomai-platform-backend',
                   'channel': 'latest', 'sourceRepository': 'https://github.com/attacker/repo', 'branch': 'other'}
        for key, value in invalid.items():
            with self.subTest(key=key), self.assertRaises(updater.UpdateError):
                updater.validate_config(dict(CONFIG, apps=[dict(BINDING, **{key: value})]), CATALOGUE)
        for key in ['env', 'domain', 'settings']:
            with self.assertRaises(updater.UpdateError):
                updater.validate_config(dict(CONFIG, apps=[dict(BINDING, **{key: 'denied'})]), CATALOGUE)

    def test_app_count_duplicates_and_header_injection_are_bounded(self):
        for apps in [[], [BINDING, BINDING], [dict(BINDING, appUuid='a' + str(n)) for n in range(26)]]:
            with self.assertRaises(updater.UpdateError):
                updater.validate_config(dict(CONFIG, apps=apps), CATALOGUE)
        updater.validate_config(dict(CONFIG, apps=[dict(BINDING, appUuid='a' + str(n)) for n in range(25)]), CATALOGUE)
        for token in ['', 'key\nInjected: header', 'key value', 'a' * 4097]:
            with self.assertRaises(updater.UpdateError):
                updater.validate_config(dict(CONFIG, apiKey=token), CATALOGUE)

    def test_secure_file_reader_requires_root_regular_0600_without_symlinks(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'config'
            target.write_text(json.dumps(CONFIG))
            for mode, uid in [(0o644, 0), (0o600, 501)]:
                with patch.object(updater.os, 'fstat', return_value=SimpleNamespace(st_mode=stat.S_IFREG | mode, st_uid=uid)):
                    with self.assertRaises(updater.UpdateError):
                        updater.read_private_json(target)
            with patch.object(updater.os, 'fstat', return_value=SimpleNamespace(st_mode=stat.S_IFREG | 0o600, st_uid=0)):
                self.assertEqual(updater.read_private_json(target), CONFIG)
            link = Path(directory) / 'link'
            link.symlink_to(target)
            with self.assertRaises(OSError):
                updater.read_private_json(link)


class PortTests(unittest.TestCase):
    def connection(self, payload=None, status=200, raw=None):
        calls = []
        class Connection:
            def request(self, *args, **kwargs):
                calls.append(('request', args, kwargs))
            def getresponse(self):
                content = raw if raw is not None else json.dumps(payload).encode()
                return SimpleNamespace(status=status, read=io.BytesIO(content).read)
            def close(self):
                calls.append(('close',))
        def factory(*args, **kwargs):
            calls.append(('connect', args, kwargs))
            return Connection()
        return updater.Coolify(CONFIG['apiKey'], updater.Budget(), factory), calls

    def test_api_auth_is_loopback_only_and_all_paths_are_fixed(self):
        api, calls = self.connection({'ok': True})
        api.app('demo-1')
        api.queue()
        api.deployment('job-1')
        api.patch_image('demo-1', BINDING['image'], TAG)
        api.deploy('demo-1')
        requests = [call for call in calls if call[0] == 'request']
        self.assertEqual([call[1][:2] for call in requests], [
            ('GET', '/api/v1/applications/demo-1'), ('GET', '/api/v1/deployments'),
            ('GET', '/api/v1/deployments/job-1'), ('PATCH', '/api/v1/applications/demo-1'),
            ('GET', '/api/v1/deploy?uuid=demo-1&force=false')])
        for call in calls:
            if call[0] == 'connect':
                self.assertEqual(call[1], ('127.0.0.1', 8000))
                self.assertLessEqual(call[2]['timeout'], 2)
        for call in requests:
            self.assertEqual(call[2]['headers']['Authorization'], 'Bearer private-test-token')
        self.assertEqual(json.loads(requests[3][2]['body']), {
            'docker_registry_image_name': BINDING['image'], 'docker_registry_image_tag': TAG})

    def test_api_does_not_redirect_retry_or_allow_other_mutations(self):
        for method, path, body in [
            ('GET', 'https://evil.test/', None), ('GET', '/applications/../envs', None),
            ('GET', '/deploy?uuid=demo-1&force=true', None), ('GET', '/deploy?uuid=a,b&force=false', None),
            ('POST', '/deploy?uuid=demo-1&force=false', None), ('DELETE', '/applications/demo-1', None),
            ('PATCH', '/applications/demo-1/envs', {}), ('PATCH', '/applications/demo-1', {'fqdn': 'evil'}),
        ]:
            api, calls = self.connection()
            with self.subTest(path=path), self.assertRaises(updater.UpdateError):
                api._request(method, path, body)
            self.assertEqual(calls, [])
        api, calls = self.connection(status=302)
        with self.assertRaises(updater.UpdateError):
            api.app('demo-1')
        self.assertEqual(sum(call[0] == 'connect' for call in calls), 1)

    def test_api_rejects_oversized_malformed_responses_and_bounded_history(self):
        for raw in [b'not json private-test-token', b'x' * (updater.MAX_BODY + 1)]:
            api, _ = self.connection(raw=raw)
            with self.assertRaises(updater.UpdateError) as error:
                api.app('demo-1')
            self.assertNotIn('private-test-token', str(error.exception))
        api, calls = self.connection({'count': 500, 'deployments': []})
        self.assertEqual(api.history('demo-1'), [])
        self.assertEqual(calls[1][1][1], '/api/v1/deployments/applications/demo-1?skip=0&take=20')
        api, _ = self.connection({'count': 0, 'deployments': [{}]})
        with self.assertRaises(updater.UpdateError):
            api.history('demo-1')

    def test_docker_only_pulls_and_inspects_sequentially_and_uses_existing_login(self):
        fake = FakeDocker(updater.Budget())
        self.assertEqual(fake.port.candidate(BINDING), {'image': BINDING['image'], 'tag': TAG, 'digest': DIGEST})
        self.assertEqual([call[0][2] for call in fake.calls], ['pull', 'inspect', 'pull', 'inspect'])
        self.assertEqual(fake.calls[0][0][-1], BINDING['image'] + ':partner-ui-current')
        self.assertEqual(fake.calls[2][0][-1], BINDING['image'] + ':' + TAG)
        self.assertTrue(all('env' not in options and 'shell' not in options for _, options in fake.calls))

    def test_image_provenance_all_labels_and_exact_repository_are_required(self):
        for key in metadata()['labels']:
            fake = FakeDocker(updater.Budget())
            fake.channel['labels'][key] = 'wrong'
            with self.subTest(key=key), self.assertRaises(updater.UpdateError):
                fake.port.candidate(BINDING)
        for digests in [[], ['ghcr.io/evil/repo@' + DIGEST], [BINDING['image'] + '@' + DIGEST] * 2]:
            fake = FakeDocker(updater.Budget())
            fake.channel['digests'] = digests
            with self.assertRaises(updater.UpdateError):
                fake.port.candidate(BINDING)

    def test_missing_immutable_or_mismatched_digest_cannot_be_used(self):
        for mode in ['missing', 'digest', 'id', 'revision']:
            fake = FakeDocker(updater.Budget())
            if mode == 'missing':
                fake.missing = True
            elif mode == 'revision':
                fake.immutable['labels']['org.opencontainers.image.revision'] = 'f' * 40
            elif mode == 'digest':
                fake.immutable['digests'] = [BINDING['image'] + '@sha256:' + 'f' * 64]
            else:
                fake.immutable['id'] = 'sha256:' + 'f' * 64
            with self.subTest(mode=mode), self.assertRaises(updater.UpdateError):
                fake.port.candidate(BINDING)


class AdoptionTests(unittest.TestCase):
    def setUp(self):
        self.state = MemoryState()
        self.api = FakeAPI(self.state)
        self.budget = updater.Budget()
        self.docker = FakeDocker(self.budget)
        self.logs = []

    def tick(self, apply=True):
        return updater.tick(CONFIG, self.api, self.docker.port, self.state, self.budget, apply, self.logs.append)

    def mutations(self):
        return [call for call in self.api.calls if call[0] in {'patch', 'deploy'}]

    def test_adopts_once_and_only_patches_image_fields_with_durable_intents(self):
        self.assertEqual(self.tick(), 0)
        self.assertEqual(self.mutations(), [('patch', BINDING['appUuid'], BINDING['image'], TAG), ('deploy', BINDING['appUuid'])])
        self.assertEqual([receipt['phase'] for receipt in self.state.writes], ['patch', 'ready', 'deploy', 'deploy'])
        self.assertEqual(self.tick(), 0)
        self.assertEqual(self.state.get(BINDING['appUuid'])['phase'], 'done')
        self.assertEqual(self.tick(), 0)
        self.assertEqual(len(self.mutations()), 2)
        self.assertIn('already-current', self.logs[-1])

    def test_no_redeploy_if_source_already_current_and_check_mode_never_mutates(self):
        self.api.value['docker_registry_image_tag'] = TAG
        self.assertEqual(self.tick(), 0)
        self.assertEqual(self.mutations(), [])
        self.api.value['docker_registry_image_tag'] = 'old'
        self.assertEqual(self.tick(apply=False), 0)
        self.assertEqual(self.mutations(), [])
        self.assertEqual(self.state.writes, [])

    def test_busy_queue_skips_without_pulling_or_mutating(self):
        self.api.jobs = [{'application_id': 123, 'status': 'in_progress'}]
        self.assertEqual(self.tick(), 0)
        self.assertEqual(self.docker.calls, [])
        self.assertEqual(self.mutations(), [])
        self.assertIn('busy', self.logs[-1])

    def test_unmapped_or_malformed_queue_blocks_instead_of_claiming_idle(self):
        for jobs in [[{'status': 'queued'}], {'error': 'unavailable'}, [{'application_id': 123, 'status': 'unknown'}]]:
            self.api.jobs = jobs
            self.assertEqual(self.tick(), 1)
        self.assertEqual(self.mutations(), [])

    def test_other_app_queue_is_not_a_global_deployment_freeze(self):
        self.api.jobs = [{'application_id': 999, 'status': 'queued'}]
        self.assertEqual(self.tick(), 0)
        self.assertEqual(len(self.mutations()), 2)

    def test_second_queue_read_catches_a_deployment_started_during_pull(self):
        def busy_after_pull():
            self.api.jobs = [{'application_id': 123, 'status': 'queued'}]
        self.docker.after_pull = busy_after_pull
        self.assertEqual(self.tick(), 0)
        self.assertEqual(self.mutations(), [])

    def test_app_changed_during_pull_is_not_overwritten(self):
        self.docker.after_pull = lambda: self.api.value.update(docker_registry_image_tag='operator-changed')
        self.assertEqual(self.tick(), 0)
        self.assertEqual(self.mutations(), [])
        self.assertIn('app-changed-during-pull', self.logs[-1])

    def test_stopped_source_built_wrong_image_or_wrong_uuid_apps_are_never_changed(self):
        for field, value in [('status', 'exited'), ('build_pack', 'dockerfile'),
                             ('docker_registry_image_name', 'ghcr.io/customer/runtime'), ('uuid', 'different')]:
            self.setUp()
            self.api.value[field] = value
            self.assertEqual(self.tick(), 1)
            self.assertEqual(self.mutations(), [])
            self.assertEqual(self.docker.calls, [])

    def test_pull_failure_cannot_patch_deploy_or_leak_output(self):
        self.docker.missing = True
        self.assertEqual(self.tick(), 1)
        self.assertEqual(self.mutations(), [])
        self.assertNotIn('private-test-token', ''.join(self.logs))

    def test_lost_patch_response_uses_readback_then_a_later_tick_not_patch_replay(self):
        self.api.patch_failure = True
        self.assertEqual(self.tick(), 1)
        self.assertEqual(len(self.mutations()), 1)
        self.assertEqual(self.state.get(BINDING['appUuid'])['phase'], 'patch')
        self.assertEqual(self.tick(), 0)
        self.assertEqual(self.state.get(BINDING['appUuid'])['phase'], 'ready')
        self.assertEqual(len(self.mutations()), 1)
        self.assertEqual(self.tick(), 0)
        self.assertEqual(len(self.mutations()), 2)
        self.assertEqual(sum(call[0] == 'patch' for call in self.mutations()), 1)

    def test_unapplied_uncertain_patch_never_replays_blindly(self):
        self.api.patch_failure = True
        self.tick()
        self.api.value['docker_registry_image_tag'] = 'old'
        self.tick()
        self.tick()
        self.assertEqual(len(self.mutations()), 1)
        self.assertIn('needs-review', self.logs[-1])

    def test_failed_patch_readback_prevents_deploy_and_reconciles_on_next_tick(self):
        self.api.fail_readback = True
        self.assertEqual(self.tick(), 1)
        self.assertEqual(len(self.mutations()), 1)
        self.assertEqual(self.state.get(BINDING['appUuid'])['phase'], 'patch')
        self.api.fail_readback = False
        self.tick()
        self.assertEqual(len(self.mutations()), 1)
        self.assertEqual(self.state.get(BINDING['appUuid'])['phase'], 'ready')

    def test_lost_deploy_response_reads_history_but_never_retriggers(self):
        self.api.deploy_failure = True
        self.assertEqual(self.tick(), 1)
        self.assertEqual(self.state.get(BINDING['appUuid'])['phase'], 'deploy')
        self.tick()
        self.tick()
        self.assertEqual(len(self.mutations()), 2)
        self.assertEqual(sum(call[0] == 'history' for call in self.api.calls), 2)
        self.assertIn('needs-review', self.logs[-1])
        self.assertNotIn('private-test-token', ''.join(self.logs))

    def test_failed_deployment_is_not_automatically_retried(self):
        self.tick()
        self.api.status = 'failed'
        self.tick()
        self.tick()
        self.assertEqual(self.state.get(BINDING['appUuid'])['phase'], 'failed')
        self.assertEqual(len(self.mutations()), 2)

    def test_unverifiable_deployment_readback_cannot_close_receipt(self):
        self.tick()
        with patch.object(self.api, 'deployment', return_value={'deployment_uuid': 'deployment-1', 'application_id': 999, 'status': 'finished'}):
            self.assertEqual(self.tick(), 1)
        self.assertEqual(self.state.get(BINDING['appUuid'])['phase'], 'deploy')
        self.assertEqual(len(self.mutations()), 2)

    def test_durable_state_failure_prevents_first_mutation(self):
        with patch.object(self.state, 'put', side_effect=OSError('disk full private-test-token')):
            self.assertEqual(self.tick(), 1)
        self.assertEqual(self.mutations(), [])
        self.assertNotIn('private-test-token', ''.join(self.logs))

    def test_pending_target_does_not_deploy_if_channel_changes_during_reconciliation(self):
        self.api.patch_failure = True
        self.tick()
        self.tick()
        self.docker.channel['labels']['org.opencontainers.image.revision'] = 'f' * 40
        self.docker.immutable = copy.deepcopy(self.docker.channel)
        self.tick()
        self.assertEqual(len(self.mutations()), 1)
        self.assertIn('pending-channel-changed', self.logs[-1])


class RuntimeTests(unittest.TestCase):
    def test_deadlines_bound_api_registry_and_total_tick(self):
        now = [0]
        budget = updater.Budget(lambda: now[0])
        self.assertEqual(budget.timeout(2), 2)
        self.assertEqual(budget.timeout(30), 30)
        now[0] = 599
        self.assertEqual(budget.timeout(30), 1)
        now[0] = 600
        with self.assertRaises(updater.UpdateError):
            budget.timeout(2)
        api = FakeAPI(MemoryState())
        logs = []
        self.assertEqual(updater.tick(CONFIG, api, None, None, budget, True, logs.append), 1)
        self.assertEqual(api.calls, [])

    def test_state_write_is_atomic_private_and_survives_reopen(self):
        with tempfile.TemporaryDirectory() as directory:
            state = updater.State(Path(directory))
            state.put('app-1', {'phase': 'patch', 'image': BINDING['image'], 'tag': TAG, 'digest': DIGEST})
            self.assertEqual(stat.S_IMODE(state.path.stat().st_mode), 0o600)
            with patch.object(updater.os, 'fstat', return_value=SimpleNamespace(st_mode=stat.S_IFREG | 0o600, st_uid=0)):
                again = updater.State(Path(directory))
            self.assertEqual(again.get('app-1')['phase'], 'patch')
            self.assertEqual(len(list(Path(directory).iterdir())), 1)

    def test_service_is_explicit_low_priority_bounded_and_timer_is_five_minutes(self):
        service = configparser.ConfigParser(interpolation=None)
        service.read(HERE / 'loomai-serving-images.service')
        values = service['Service']
        self.assertEqual(values['User'], 'root')
        self.assertTrue(values['ExecStart'].endswith('serving_image_updater.py --apply'))
        self.assertEqual(values['TimeoutStartSec'], '10min')
        self.assertEqual(values['StateDirectoryMode'], '0700')
        self.assertEqual(values['ProtectSystem'], 'strict')
        self.assertEqual(values['Environment'], 'DOCKER_CONFIG=/root/.docker')
        self.assertEqual(values['IOSchedulingClass'], 'idle')
        timer = configparser.ConfigParser()
        timer.read(HERE / 'loomai-serving-images.timer')
        self.assertEqual(timer['Timer']['OnUnitInactiveSec'], '5min')
        self.assertEqual(timer['Timer']['Persistent'], 'false')


if __name__ == '__main__':
    unittest.main()
