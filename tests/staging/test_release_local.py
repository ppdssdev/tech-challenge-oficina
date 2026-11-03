import os
import pty
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / 'scripts/staging/release-local.sh'

class ReleaseTests(unittest.TestCase):
    def run_case(self, mode='--execute', account='797781632267', region='us-east-2', mutable='IMMUTABLE', online='Online', dirty='', ambient='us-east-2', terminal=False, credentials='valid', publish=False, changed_account=False, digest_mismatch=False):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            mock = '''#!/usr/bin/env python3
import os,sys,json,datetime
from pathlib import Path
name=Path(sys.argv[0]).name
a=sys.argv[1:]
with open(os.environ['CALLS'],'a') as f: f.write(name+' '+' '.join(a)+'\\n')
if name=='git':
 if '--show-toplevel' in a: print(os.environ['ROOT'])
 elif 'HEAD' in a: print('a'*40)
 elif 'archive' in a:
  import tarfile
  with tarfile.open(fileobj=sys.stdout.buffer,mode='w|'): pass
 elif 'status' in a: print(os.environ['DIRTY'],end='')
elif name=='aws':
 s=' '.join(a)
 if 'configure get region' in s: print(os.environ['REGION'])
 elif 'export-credentials' in s:
  state=os.environ['CREDENTIALS']
  if state=='missing': sys.exit(1)
  expiration=datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(seconds={'valid':900,'expired':-1,'short':30}[state])
  print(json.dumps({'SessionToken':'mock-only','Expiration':expiration.isoformat()}))
 elif 'get-caller-identity' in s:
  marker=Path(os.environ['ROOT'])/'identity'
  print('000000000000' if marker.exists() and os.environ['CHANGED']=='1' else os.environ['ACCOUNT'])
  marker.touch()
 elif 'get-login-password' in s: print('mock')
 elif 'describe-images' in s: print('sha256:'+('b' if os.environ['MISMATCH']=='1' else 'a')*64)
 elif 'repositoryUri' in s: print('797781632267.dkr.ecr.us-east-2.amazonaws.com/oficina-staging-api')
 elif 'imageTagMutability' in s: print(os.environ['MUTABLE'])
 elif 'list-images' in s: print('')
 elif 'describe-instance-information' in s: print(os.environ['ONLINE'])
 else: sys.exit(99)
elif name=='docker':
 if a[0]=='login': sys.stdin.read()
 elif a[0]=='push': print('digest: sha256:'+'a'*64)
 elif a[0] not in ('info','build'): sys.exit(99)
'''
            (p/'scripts/staging').mkdir(parents=True)
            (p/'scripts/staging/render.sh').write_text('exit 0\n')
            for name in ('git', 'aws', 'docker', 'kubectl'):
                file = p / name
                file.write_text(mock)
                file.chmod(0o755)
            env = dict(os.environ, PATH=tmp+':'+os.environ['PATH'], ROOT=tmp, CALLS=str(p/'calls'), CHANGED=str(int(changed_account)), MISMATCH=str(int(digest_mismatch)), CREDENTIALS=credentials, ACCOUNT=account, REGION=region, MUTABLE=mutable, ONLINE=online, DIRTY=dirty, AWS_REGION=ambient, AWS_DEFAULT_REGION=ambient)
            args = ['bash', str(SCRIPT)] + ([] if mode is None else [mode])
            if terminal:
                master, slave = pty.openpty()
                try:
                    os.write(master, b'PUBLICAR\n' if publish else b'CANCELAR\n')
                    result = subprocess.run(args, stdin=slave, env=env, text=True, capture_output=True, timeout=10)
                finally:
                    os.close(master)
                    os.close(slave)
            else:
                result = subprocess.run(args, env=env, text=True, capture_output=True, timeout=10)
            calls = (p/'calls').read_text() if (p/'calls').exists() else ''
            self.assertEqual(result.returncode, 0 if mode in (None, '--dry-run') or (publish and not changed_account and not digest_mismatch) else 1)
            for forbidden in (('send-command', 'put-', 'apply') if publish else ('send-command', 'push', 'login', 'build', 'put-', 'apply')):
                self.assertNotIn(forbidden, calls)
            for call in calls.splitlines():
                if call.startswith('aws '):
                    self.assertTrue(call.startswith('aws --profile oficina-staging --region us-east-2 '))
            return result.stdout+result.stderr, calls

    def test_mock_publication_revalidates_and_checks_digest(self):
        output, calls = self.run_case(terminal=True, publish=True)
        self.assertIn('Imagem publicada', output)
        self.assertEqual(calls.count('get-caller-identity'), 2)
        self.assertEqual(calls.count('export-credentials'), 2)
        self.assertLess(calls.index('docker build'), calls.rindex('get-caller-identity'))
        self.assertLess(calls.rindex('get-caller-identity'), calls.index('docker push'))

    def test_identity_change_blocks_push(self):
        output, calls = self.run_case(terminal=True, publish=True, changed_account=True)
        self.assertIn('Conta AWS divergente', output)
        self.assertNotIn('docker push', calls)
        self.assertNotIn('get-login-password', calls)

    def test_digest_mismatch(self):
        output, _ = self.run_case(terminal=True, publish=True, digest_mismatch=True)
        self.assertIn('Digest do ECR diverge', output)
        self.assertNotIn('Imagem publicada', output)

    def test_fifteen_minute_credentials(self):
        output, calls = self.run_case(credentials='valid')
        self.assertIn('terminal interativo', output)
        self.assertIn('get-caller-identity', calls)

    def test_unusable_session(self):
        for credentials in ('expired', 'missing', 'short'):
            with self.subTest(credentials=credentials):
                output, calls = self.run_case(credentials=credentials)
                self.assertIn('aws login --profile oficina-staging', output)
                self.assertNotIn('docker ', calls)

    def test_offline_plan(self):
        output, calls = self.run_case('--dry-run')
        self.assertIn('git-'+'a'*40, output)
        self.assertNotIn('aws ', calls)
        self.assertNotIn('docker ', calls)

    def test_default_is_offline(self):
        _, calls = self.run_case(None)
        self.assertNotIn('aws ', calls)
        self.assertNotIn('docker ', calls)

    def test_declined_confirmation(self):
        output, _ = self.run_case(terminal=True)
        self.assertIn('Operação cancelada', output)

    def test_execute_requires_terminal(self):
        output, _ = self.run_case()
        self.assertIn('terminal interativo', output)

    def test_guards(self):
        for kwargs, message in [({'account':'000000000000'}, 'Conta'), ({'region':'us-east-1'}, 'Região'), ({'ambient':'us-east-1'}, 'Região'), ({'mutable':'MUTABLE'}, 'imutáveis'), ({'online':'Offline'}, 'SSM'), ({'dirty':'?? file'}, 'limpo')]:
            with self.subTest(kwargs=kwargs):
                output, _ = self.run_case(**kwargs)
                self.assertIn(message, output)

    def test_unknown_option(self):
        output, calls = self.run_case('--yes')
        self.assertIn('Uso:', output)
        self.assertEqual('', calls)

if __name__ == '__main__':
    unittest.main()
