#!/usr/bin/env python3
"""Offline policies and installer failure-path tests; never calls a cluster."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import yaml

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('targets', HERE / 'check-targets.py')
targets = importlib.util.module_from_spec(spec)
spec.loader.exec_module(targets)


def fixtures():
    pods = {'items': [{'metadata': {'name': f'api-{i}', 'uid': f'uid-{i}'}, 'status': {'phase': 'Running',
            'podIP': f'10.42.0.{i}', 'conditions': [{'type': 'Ready', 'status': 'True'}],
            'containerStatuses': [{'name': 'api', 'restartCount': 0}]}}
            for i in range(1, 4)]}
    response = {'status': 'success', 'data': {'activeTargets': [
        {'labels': {'job': 'oficina-api', 'namespace': 'oficina-staging', 'pod': f'api-{i}'},
         'health': 'up', 'lastError': '', 'scrapeInterval': '15s',
         'scrapeUrl': f'http://10.42.0.{i}:8080/actuator/prometheus'} for i in range(1, 4)]}}
    return pods, response


def deployment_fixture(replicas=3):
    return {'metadata': {'uid': 'deployment-uid', 'generation': 1}, 'spec': {'replicas': replicas},
            'status': {'observedGeneration': 1, 'replicas': replicas, 'updatedReplicas': replicas,
                       'readyReplicas': replicas, 'availableReplicas': replicas}}


class TargetTests(unittest.TestCase):
    def test_three_individual_pods(self):
        self.assertEqual(len(targets.validate(*fixtures())), 3)

    def test_invalid_targets(self):
        for field, value in [('health', 'down'), ('lastError', 'timeout'),
                             ('scrapeInterval', '1m'), ('scrapeUrl', 'http://oficina-api:8080/actuator/prometheus')]:
            with self.subTest(field=field):
                pods, response = fixtures()
                response['data']['activeTargets'][0][field] = value
                with self.assertRaises(ValueError):
                    targets.validate(pods, response)

    def test_missing_duplicate_foreign_and_unready(self):
        for mode in ['missing', 'duplicate', 'foreign', 'unready', 'terminating']:
            with self.subTest(mode=mode):
                pods, response = fixtures()
                active = response['data']['activeTargets']
                if mode == 'missing': active.pop()
                if mode == 'duplicate': active[0] = copy.deepcopy(active[1])
                if mode == 'foreign': active[0]['labels']['namespace'] = 'default'
                if mode == 'unready': pods['items'][0]['status']['conditions'][0]['status'] = 'False'
                if mode == 'terminating': pods['items'][0]['metadata']['deletionTimestamp'] = 'now'
                with self.assertRaises(ValueError): targets.validate(pods, response)

    def test_stable_hpa_samples(self):
        for count in (1, 2, 3):
            pods, response = fixtures()
            pods['items'] = pods['items'][:count]
            response['data']['activeTargets'] = response['data']['activeTargets'][:count]
            deployment = deployment_fixture(count)
            self.assertIn('amostra estável', targets.stable_sample(
                pods, response, 'auto', deployment, pods, deployment))

    def test_changing_or_incomplete_samples_fail(self):
        for mode in ('generation', 'replicas', 'uid', 'restart', 'unready', 'override', 'empty', 'missing-target'):
            with self.subTest(mode=mode):
                pods, response = fixtures()
                deployment = deployment_fixture()
                after, deployment_after = copy.deepcopy(pods), copy.deepcopy(deployment)
                expected = 'auto'
                if mode == 'generation': deployment_after['metadata']['generation'] += 1
                if mode == 'replicas': deployment_after = deployment_fixture(2)
                if mode == 'uid': after['items'][0]['metadata']['uid'] = 'replacement'
                if mode == 'restart': after['items'][0]['status']['containerStatuses'][0]['restartCount'] = 1
                if mode == 'unready': deployment['status']['readyReplicas'] = 2
                if mode == 'override': expected = '2'
                if mode == 'empty':
                    deployment = deployment_after = deployment_fixture(0)
                    pods['items'] = after['items'] = []
                    response['data']['activeTargets'] = []
                if mode == 'missing-target': response['data']['activeTargets'].pop()
                with self.assertRaises(ValueError):
                    targets.stable_sample(pods, response, expected, deployment, after, deployment_after)


class PolicyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rendered = subprocess.check_output([os.environ.get('KUBECTL', 'kubectl'), 'kustomize',
                        str(ROOT / 'k8s/staging/observability')], text=True)
        cls.objects = list(yaml.safe_load_all(cls.rendered))

    def test_resource_scope_and_rbac(self):
        allowed = {'Deployment', 'Service', 'ConfigMap', 'PersistentVolumeClaim', 'ServiceAccount', 'Role', 'RoleBinding', 'NetworkPolicy'}
        for obj in self.objects:
            self.assertIn(obj['kind'], allowed)
            self.assertEqual(obj['metadata']['namespace'], 'oficina-staging')
            self.assertTrue(obj['metadata']['name'].startswith('oficina-observability-')
                            or (obj['kind'] == 'Service' and obj['metadata']['name'] == 'oficina-tempo'))
            if obj['kind'] == 'Role':
                self.assertEqual(obj['rules'], [{'apiGroups': [''], 'resources': ['pods'], 'verbs': ['list', 'watch']}])
            if obj['kind'] == 'Service':
                self.assertEqual(obj['spec']['type'], 'ClusterIP')
                self.assertNotIn('externalIPs', obj['spec'])
        api = yaml.safe_load((ROOT / 'k8s/staging/kustomization.yaml').read_text())
        self.assertNotIn('observability', str(api))

    def test_hardening_resources_and_storage(self):
        for obj in self.objects:
            if obj['kind'] == 'Deployment':
                pod = obj['spec']['template']['spec']; c = pod['containers'][0]
                self.assertRegex(c['image'], r':v?\d+\.\d+\.\d+@sha256:[0-9a-f]{64}$')
                self.assertTrue(pod['securityContext']['runAsNonRoot'])
                self.assertEqual(pod['securityContext']['seccompProfile']['type'], 'RuntimeDefault')
                self.assertTrue(c['securityContext']['readOnlyRootFilesystem'])
                self.assertFalse(c['securityContext']['allowPrivilegeEscalation'])
                self.assertEqual(c['securityContext']['capabilities']['drop'], ['ALL'])
                self.assertEqual(pod['nodeSelector']['kubernetes.io/arch'], 'amd64')
                self.assertFalse(pod.get('hostNetwork', False))
                self.assertEqual(c['resources']['requests']['memory'], '128Mi')
                self.assertEqual(c['resources']['limits']['memory'], '768Mi' if c['name'] == 'prometheus' else '384Mi')
                self.assertEqual(pod['automountServiceAccountToken'], c['name'] == 'prometheus')
                if c['name'] == 'prometheus':
                    self.assertIn('--storage.tsdb.retention.time=24h', c['args'])
                    self.assertIn('--storage.tsdb.retention.size=1GB', c['args'])
                elif c['name'] == 'grafana':
                    env = {e['name']: e for e in c['env']}
                    self.assertIn('GF_SECURITY_ADMIN_PASSWORD__FILE', env)
                    self.assertIn('secretKeyRef', env['GF_SECURITY_ADMIN_USER']['valueFrom'])
            if obj['kind'] == 'PersistentVolumeClaim':
                self.assertEqual(obj['spec']['resources']['requests']['storage'], '2Gi')
                self.assertEqual(obj['spec']['storageClassName'], 'local-path')

    def test_discovery_and_dashboard(self):
        config = yaml.safe_load((ROOT / 'k8s/staging/observability/config/prometheus.yml').read_text())
        self.assertEqual(config['global']['scrape_interval'], '15s')
        job = config['scrape_configs'][0]
        self.assertEqual(job['kubernetes_sd_configs'][0]['role'], 'pod')
        self.assertEqual(job['kubernetes_sd_configs'][0]['namespaces']['names'], ['oficina-staging'])
        self.assertEqual(job['metrics_path'], '/actuator/prometheus')
        dashboard = json.loads((ROOT / 'k8s/staging/observability/config/dashboard.json').read_text())
        queries = [t['expr'] for p in dashboard['panels'] for t in p['targets']]
        for query in queries:
            self.assertIn('job="oficina-api",namespace="oficina-staging"', query)
            if 'oficina_notification' in query: self.assertTrue(query.startswith('max('))
        self.assertEqual(len([q for q in queries if 'oficina_notification' in q]), 4)


# A fake cluster persists inventory, simulates partial applies and rollout failure,
# and records every operation. It cannot reach Kubernetes or AWS.
MOCK = r'''#!/usr/bin/env python3
import json,os,pathlib,sys,time,yaml
p=pathlib.Path(os.environ['FAKE_DIR']); args=sys.argv[1:]
with (p/'calls').open('a') as f: f.write(json.dumps(args)+'\n')
if args[:1]==['-n']: args=args[2:]
state=json.loads((p/'cluster.json').read_text())
mode=os.environ.get('FAIL_MODE','')
if args[0]=='kustomize': print((p/'rendered.yaml').read_text()); sys.exit()
if args[0]=='get':
 if args[1]=='secret' and mode=='secret': sys.exit(1)
 if args[1]=='deployment': print((p/'deployment.json').read_text())
 elif args[1]=='pods':
  pods=json.loads((p/'pods.json').read_text())
  counter=p/'pod-reads'; reads=int(counter.read_text()) if counter.exists() else 0
  counter.write_text(str(reads+1))
  if mode=='preflight-churn' or (mode=='churn' and state):
   pods['items'][0]['metadata']['uid']=str(reads//2)
  print(json.dumps(pods))
 elif '-f' in args:
  if mode=='get-error': sys.exit(7)
  if mode=='empty-json' and not state: sys.exit(0)
  if mode=='invalid-json': print('{invalid'); sys.exit(0)
  if mode=='whitespace-json': print('  '); sys.exit(0)
  docs=list(yaml.safe_load_all(pathlib.Path(args[args.index('-f')+1]).read_text()))
  items=docs[0]['items'] if docs[0]['kind']=='List' else docs
  ids={(x['kind'],x['metadata']['name']) for x in items}
  print(json.dumps({'apiVersion':'v1','kind':'List','items':[x for x in state if (x['kind'],x['metadata']['name']) in ids]}))
 elif '-o' in args and args[args.index('-o')+1]=='json': print(json.dumps({'apiVersion':'v1','kind':'List','items':state}))
 else: print('exists')
elif args[0]=='apply':
 if '--dry-run=server' in args: sys.exit(1 if mode=='dry-run' else 0)
 path=pathlib.Path(args[args.index('-f')+1])
 docs=list(yaml.safe_load_all(path.read_text())); items=docs[0]['items'] if docs[0]['kind']=='List' else docs
 if path.name=='before.json':
  if mode=='rollback-fails': sys.exit(1)
  (p/'restored').touch()
 if mode=='partial' and path.name=='desired.yaml': items=items[:5]
 lookup={(x['kind'],x['metadata']['name']):x for x in state}
 lookup.update({(x['kind'],x['metadata']['name']):x for x in items})
 (p/'cluster.json').write_text(json.dumps(list(lookup.values())))
 if mode=='partial' and path.name=='desired.yaml': sys.exit(1)
elif args[0]=='delete':
 docs=json.loads(pathlib.Path(args[args.index('-f')+1]).read_text())
 keys={(x['kind'],x['metadata']['name']) for x in docs['items']}
 (p/'cluster.json').write_text(json.dumps([x for x in state if (x['kind'],x['metadata']['name']) not in keys]))
elif args[0]=='rollout':
 if mode in ['rollout','rollback-fails'] and not (p/'restored').exists(): sys.exit(1)
elif args[0]=='port-forward':
 print('Forwarding from 127.0.0.1:19090 -> 9090',flush=True)
 while True: time.sleep(1)
else: sys.exit('Unexpected fake command: '+str(args))
'''


class InstallerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name)
        self.env = dict(os.environ, FAKE_DIR=str(self.path), STATE_DIR=str(self.path/'state'),
                        KUBECTL=str(self.path/'kubectl'), PATH=str(self.path)+':'+os.environ['PATH'])
        self.env.pop('EXPECTED_API_PODS', None)
        (self.path/'kubectl').write_text(MOCK)
        (self.path/'curl').write_text('#!/bin/sh\ncat "$FAKE_DIR/targets.json"\n')
        (self.path/'sleep').write_text('#!/bin/sh\n/usr/bin/sleep 0.02\n')
        for name in ['kubectl', 'curl', 'sleep']: (self.path/name).chmod(0o755)
        (self.path/'rendered.yaml').write_text(PolicyTests.rendered)
        (self.path/'cluster.json').write_text('[]')
        pods, response = fixtures()
        (self.path/'pods.json').write_text(json.dumps(pods))
        (self.path/'targets.json').write_text(json.dumps(response))
        (self.path/'deployment.json').write_text(json.dumps(deployment_fixture()))

    def run_install(self, mode='', args=None):
        return subprocess.run(['bash', str(HERE/'install.sh'), *(args or ['install'])],
                              env=dict(self.env, FAIL_MODE=mode), capture_output=True, text=True, timeout=30)

    def inventory(self): return json.loads((self.path/'cluster.json').read_text())

    def test_empty_successful_kubectl_output_is_no_resources(self):
        result = self.run_install('empty-json')
        self.assertEqual(result.returncode, 0, result.stderr)
        attempt = next((self.path/'state').glob('attempt.*'))
        self.assertEqual(json.loads((attempt/'before.json').read_text())['items'], [])
        self.assertTrue(self.inventory())

    def test_bad_kubectl_output_never_reaches_mutation(self):
        for mode in ['get-error', 'invalid-json', 'whitespace-json']:
            with self.subTest(mode=mode):
                (self.path/'calls').write_text('')
                result = self.run_install(mode)
                self.assertNotEqual(result.returncode, 0)
                if mode == 'get-error': self.assertEqual(result.returncode, 7)
                self.assertEqual(self.inventory(), [])
                calls = [json.loads(line) for line in (self.path/'calls').read_text().splitlines()]
                self.assertFalse(any('apply' in call or 'delete' in call for call in calls))

    def test_upgrade_and_rollback_only_touch_selected_resources(self):
        self.assertEqual(self.run_install().returncode, 0)
        # An additional owned object is outside this change, as are the old Services/RBAC/PVC.
        unrelated = {'apiVersion': 'v1', 'kind': 'ConfigMap', 'metadata': {
            'name': 'oficina-observability-unrelated', 'namespace': 'oficina-staging',
            'labels': {'app.kubernetes.io/part-of': 'oficina-observability'}}, 'data': {'keep': 'yes'}}
        old = self.inventory() + [unrelated]
        (self.path/'cluster.json').write_text(json.dumps(old))
        attempts = set((self.path/'state').glob('attempt.*'))
        result = self.run_install(args=['upgrade'])
        self.assertEqual(result.returncode, 0, result.stderr)
        attempt, = set((self.path/'state').glob('attempt.*')) - attempts
        desired = json.loads((attempt/'desired.yaml').read_text())['items']
        ids = {(x['kind'], x['metadata']['name']) for x in desired}
        self.assertNotIn(('PersistentVolumeClaim', 'oficina-observability-prometheus'), ids)
        self.assertNotIn(('Service', 'oficina-observability-grafana'), ids)
        self.assertFalse(any(x[0] in {'Secret', 'Role', 'RoleBinding', 'ServiceAccount'} for x in ids))
        # A new unrelated object created after the snapshot must also survive rollback.
        later = copy.deepcopy(unrelated); later['metadata']['name'] += '-later'
        (self.path/'cluster.json').write_text(json.dumps(self.inventory() + [later]))
        result = self.run_install(args=['rollback', str(attempt)])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.inventory(), old + [later])

    def test_success_and_idempotence(self):
        first = self.run_install(); self.assertEqual(first.returncode, 0, first.stderr)
        calls = [json.loads(line) for line in (self.path/'calls').read_text().splitlines()]
        first_apply = next(i for i, call in enumerate(calls) if 'apply' in call)
        self.assertGreaterEqual(sum('pods' in c for c in calls[:first_apply]), 4)
        self.assertIn('Preflight API estável', first.stdout)
        before = self.inventory()
        second = self.run_install(); self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(self.inventory(), before)

    def test_k3s_wrapper_and_hpa_counts(self):
        (self.path/'k3s').write_text('#!/bin/bash\n[[ "$1" == kubectl ]] || exit 1\nshift\nexec "$FAKE_DIR/kubectl" "$@"\n')
        (self.path/'k3s').chmod(0o755)
        self.env['KUBECTL'] = str(HERE/'k3s-kubectl.sh')
        self.env['K3S'] = str(self.path/'k3s')
        self.env['EXPECTED_API_PODS'] = 'auto'
        for count in (1, 2, 3):
            with self.subTest(count=count):
                pods, response = fixtures()
                pods['items'] = pods['items'][:count]
                response['data']['activeTargets'] = response['data']['activeTargets'][:count]
                (self.path/'pods.json').write_text(json.dumps(pods))
                (self.path/'targets.json').write_text(json.dumps(response))
                (self.path/'deployment.json').write_text(json.dumps(deployment_fixture(count)))
                result = self.run_install()
                self.assertEqual(result.returncode, 0, result.stderr)

    def test_churn_never_accepts_single_healthy_sample(self):
        result = self.run_install('churn')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual([x['kind'] for x in self.inventory()], ['PersistentVolumeClaim', 'PersistentVolumeClaim'])

    def test_override_cannot_hide_missing_pod(self):
        self.env['EXPECTED_API_PODS'] = '2'
        pods, response = fixtures()
        pods['items'].pop(); response['data']['activeTargets'].pop()
        (self.path/'pods.json').write_text(json.dumps(pods))
        (self.path/'targets.json').write_text(json.dumps(response))
        result = self.run_install()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.inventory(), [])

    def test_unstable_api_never_reaches_apply(self):
        result = self.run_install('preflight-churn')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.inventory(), [])
        calls = [json.loads(line) for line in (self.path/'calls').read_text().splitlines()]
        self.assertFalse(any('apply' in call or 'delete' in call for call in calls))
        self.assertIn('nenhum recurso da stack foi aplicado', result.stderr)

    def test_current_sample_keeps_three_expected(self):
        pods, response = fixtures()
        pods['items'] = pods['items'][:2]
        response['data']['activeTargets'] = response['data']['activeTargets'][:2]
        (self.path/'pods.json').write_text(json.dumps(pods))
        (self.path/'targets.json').write_text(json.dumps(response))
        (self.path/'deployment.json').write_text(json.dumps(deployment_fixture(2)))
        result = self.run_install()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.inventory(), [])
        self.assertIn('EXPECTED_API_PODS difere', result.stderr)

    def test_preflight_failures_do_not_mutate(self):
        for mode in ['secret', 'dry-run']:
            result = self.run_install(mode)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(self.inventory(), [])

    def test_partial_first_install_is_removed(self):
        result = self.run_install('partial')
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(all(x['kind']=='PersistentVolumeClaim' for x in self.inventory()))
        self.assertIn('Rollback concluído', result.stdout)

    def test_rollout_failure_preserves_pvc(self):
        result = self.run_install('rollout')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual([x['kind'] for x in self.inventory()], ['PersistentVolumeClaim', 'PersistentVolumeClaim'])

    def test_target_failure_rolls_back(self):
        pods, response = fixtures(); response['data']['activeTargets'].pop()
        (self.path/'targets.json').write_text(json.dumps(response))
        result = self.run_install()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual([x['kind'] for x in self.inventory()], ['PersistentVolumeClaim', 'PersistentVolumeClaim'])

    def test_update_failure_restores_snapshot(self):
        self.assertEqual(self.run_install().returncode, 0)
        old = self.inventory()
        for x in old:
            if x['kind']=='Deployment': x['spec']['template']['metadata']['annotations']={'previous':'yes'}
        (self.path/'cluster.json').write_text(json.dumps(old))
        result = self.run_install('rollout')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.inventory(), old)
        self.assertIn('Rollback concluído', result.stdout)

    def test_collision_does_not_modify_existing_resource(self):
        existing = copy.deepcopy(PolicyTests.objects[0])
        existing['metadata'].pop('labels', None)
        (self.path/'cluster.json').write_text(json.dumps([existing]))
        result = self.run_install()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.inventory(), [existing])
        self.assertIn('não gerenciado', result.stderr)

    def test_failed_rollback_reports_snapshot_and_preserves_state(self):
        self.assertEqual(self.run_install().returncode, 0)
        result = self.run_install('rollback-fails')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('ROLLBACK FALHOU', result.stderr)
        self.assertTrue(any(x['kind']=='PersistentVolumeClaim' for x in self.inventory()))

    def test_manual_rollback_first_install(self):
        self.assertEqual(self.run_install().returncode, 0)
        attempt = next((self.path/'state').glob('attempt.*'))
        result = self.run_install(args=['rollback', str(attempt)])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual([x['kind'] for x in self.inventory()], ['PersistentVolumeClaim', 'PersistentVolumeClaim'])


class SecretTests(unittest.TestCase):
    def test_k3s_wrapper_passes_stdin_and_arguments(self):
        with tempfile.TemporaryDirectory() as directory:
            k3s = Path(directory)/'k3s executable'
            # Check internally; never echo the Secret, even with synthetic credentials.
            k3s.write_text('#!/usr/bin/env python3\nimport json,sys\n'
                            'assert sys.argv[1:] == ["kubectl", "create", "-f", "-"]\n'
                            'assert json.load(sys.stdin)["kind"] == "Secret"\n')
            k3s.chmod(0o755)
            with patch.dict(os.environ, KUBECTL=str(HERE/'k3s-kubectl.sh'),
                            K3S=str(k3s)), \
                    patch('getpass.getpass', return_value='synthetic-Only-Test-123456!'):
                runpy.run_path(str(HERE/'create-admin-secret.py'), run_name='__main__')

    def test_password_only_in_stdin(self):
        password = 'synthetic-Only-Test-123456!'
        with patch('getpass.getpass', return_value=password), patch('subprocess.run') as run:
            run.return_value.returncode = 0
            runpy.run_path(str(HERE/'create-admin-secret.py'), run_name='__main__')
            args, kwargs = run.call_args
            self.assertNotIn(password, str(args))
            self.assertEqual(args[0][1:], ['create', '-f', '-'])
            self.assertEqual(kwargs['stdout'], subprocess.DEVNULL)
            self.assertEqual(kwargs['stderr'], subprocess.DEVNULL)
            self.assertEqual(json.loads(kwargs['input'])['metadata']['name'], 'oficina-observability-grafana-admin')

    def test_weak_password_never_calls_kubectl(self):
        with patch('getpass.getpass', return_value='admin'), patch('subprocess.run') as run:
            with self.assertRaises(SystemExit):
                runpy.run_path(str(HERE/'create-admin-secret.py'), run_name='__main__')
            run.assert_not_called()


if __name__ == '__main__':
    # Policy rendering must precede the fake-cluster suite.
    suite = unittest.TestSuite(unittest.defaultTestLoader.loadTestsFromTestCase(c)
                              for c in [PolicyTests, TargetTests, InstallerTests, SecretTests])
    raise SystemExit(not unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful())
