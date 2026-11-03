"""Exercise the documented payload offline; never call AWS or a real cluster."""
import copy
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import textwrap
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
DOC = (ROOT / 'docs/staging/promotion-manual.md').read_text()
BLOCKS = [textwrap.dedent(b) for b in re.findall(r'```bash\n(.*?)\n[ ]*```', DOC, re.S)]
NAMES = ['MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED',
         'MANAGEMENT_OTLP_TRACING_ENDPOINT', 'MANAGEMENT_TRACING_SAMPLING_PROBABILITY']
REPO = '797781632267.dkr.ecr.us-east-2.amazonaws.com/oficina-staging-api@sha256:'


class PromotionRunbookTests(unittest.TestCase):
    def generate(self, directory, old_env):
        before = {'uid': 'deployment-uid', 'image': REPO + 'a' * 64, 'env': old_env}
        desired = {'uid': before['uid'], 'image': REPO + 'b' * 64,
                   'env': [{'name': n, 'value': v} for n, v in zip(NAMES,
                           ['true', 'http://oficina-tempo:4318/v1/traces', '1.0'])]}
        for name, data in [('before', before), ('desired', desired)]:
            (directory / (name + '.json')).write_text(json.dumps(data))
        generator = next(b for b in BLOCKS if "code=r'''" in b)
        subprocess.run(['bash', '-se'], input=generator, text=True, check=True,
                       env={**os.environ, 'EVIDENCE_DIR': str(directory)})
        return before, desired

    def execute_payload(self, path, deployment):
        code = path.read_text().split("<<'PY_REMOTE'\n", 1)[1].split('\nPY_REMOTE', 1)[0]
        with patch('subprocess.check_output', return_value=json.dumps(deployment).encode()), \
                patch('subprocess.run') as run:
            exec(compile(code, str(path), 'exec'), {})
        return run.call_args_list

    def deployment(self, snapshot):
        return {'metadata': {'uid': snapshot['uid'], 'resourceVersion': '123'},
                'spec': {'template': {'spec': {'containers': [
                    {'name': 'sidecar', 'image': 'untouched'},
                    {'name': 'api', 'image': snapshot['image'],
                     'env': [{'name': 'DO_NOT_EXPORT', 'value': 'sensitive-fixture'}] +
                            copy.deepcopy(snapshot['env'])}]}}}}

    def test_all_documented_shell_blocks_parse(self):
        for block in BLOCKS:
            subprocess.run(['bash', '-n'], input=block, text=True, check=True)

    def test_promote_and_restore_absent_or_referenced_variables(self):
        for old_env in [[], [{'name': NAMES[0], 'value': 'false'},
                            {'name': NAMES[1], 'valueFrom': {
                                'configMapKeyRef': {'name': 'previous', 'key': 'endpoint'}}}]]:
            with self.subTest(old_env=old_env), tempfile.TemporaryDirectory() as tmp:
                directory = Path(tmp)
                before, desired = self.generate(directory, old_env)
                for label, source, target in [('promote', before, desired),
                                              ('rollback', desired, before),
                                              ('end-demo', desired, {**desired, 'env': old_env})]:
                    payload = directory / (label + '.sh')
                    subprocess.run(['bash', '-n', str(payload)], check=True)
                    calls = self.execute_payload(payload, self.deployment(source))
                    self.assertEqual(len(calls), 2)  # One patch, then rollout wait.
                    args, kwargs = calls[0]
                    self.assertNotIn('sensitive-fixture', ' '.join(args[0]))
                    self.assertIn('--patch-file=/dev/stdin', args[0])
                    operations = json.loads(kwargs['input'])
                    self.assertEqual(operations[0], {'op': 'test',
                                     'path': '/metadata/resourceVersion', 'value': '123'})
                    self.assertEqual(operations[1]['path'], '/spec/template/spec/containers/1/image')
                    self.assertEqual(operations[1]['value'], target['image'])
                    self.assertEqual(operations[2]['value'],
                                     [{'name': 'DO_NOT_EXPORT', 'value': 'sensitive-fixture'}] + target['env'])
                    self.assertIn('--timeout=300s', calls[1].args[0])

    def test_concurrent_change_blocks_any_mutation(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            before, _ = self.generate(directory, [])
            for field in ['uid', 'image', 'env']:
                d = self.deployment(before)
                if field == 'uid':
                    d['metadata']['uid'] = 'recreated'
                elif field == 'image':
                    d['spec']['template']['spec']['containers'][1]['image'] = REPO + 'c' * 64
                else:
                    d['spec']['template']['spec']['containers'][1]['env'].append(
                        {'name': NAMES[0], 'value': 'false'})
                with self.subTest(field=field), patch('subprocess.run') as run:
                    # execute_payload's inner run mock must never be called either.
                    code = (directory / 'promote.sh').read_text().split("<<'PY_REMOTE'\n", 1)[1].split('\nPY_REMOTE', 1)[0]
                    with patch('subprocess.check_output', return_value=json.dumps(d).encode()):
                        with self.assertRaises(AssertionError):
                            exec(compile(code, 'promote.sh', 'exec'), {})
                    run.assert_not_called()

    def test_verify_rejects_wrong_effective_digest(self):
        code = DOC.split("<<'PY'\nimport json, subprocess, urllib.request\n", 1)[1].split('\nPY\n', 1)[0]
        code = 'import json, subprocess, urllib.request\n' + code
        before = {'uid': 'deployment-uid', 'image': REPO + 'a' * 64, 'env': []}
        d = self.deployment(before)
        d['metadata']['generation'] = 1
        d['spec']['replicas'] = 1
        d['status'] = dict(observedGeneration=1, replicas=1, updatedReplicas=1,
                           readyReplicas=1, availableReplicas=1)
        pods = {'items': [{'metadata': {'name': 'api-1'}, 'status': {'containerStatuses': [
            {'name': 'api', 'ready': True, 'state': {'running': {}},
             'imageID': 'containerd://sha256:' + 'b' * 64}]}}]}
        with patch('subprocess.check_output', side_effect=[json.dumps(d).encode(), json.dumps(pods).encode()]), \
                patch('urllib.request.urlopen') as http:
            with self.assertRaises(AssertionError):
                exec(compile(code, 'verify.py', 'exec'), {'expected': before})
            http.assert_not_called()


if __name__ == '__main__':
    unittest.main()
