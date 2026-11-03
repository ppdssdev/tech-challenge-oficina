import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / 'scripts/staging/release-local.sh'

class ReleaseTests(unittest.TestCase):
    def run_case(self, mode='--dry-run', account='797781632267', region='us-east-2', mutable='IMMUTABLE', online='Online', dirty='', ambient='us-east-2'):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            mock = '''#!/usr/bin/env python3
import os,sys,json
from pathlib import Path
name=Path(sys.argv[0]).name
a=sys.argv[1:]
with open(os.environ['CALLS'],'a') as f: f.write(name+' '+' '.join(a)+'\\n')
if name=='git':
 if '--show-toplevel' in a: print(os.environ['ROOT'])
 elif 'HEAD' in a: print('a'*40)
 elif 'status' in a: print(os.environ['DIRTY'],end='')
elif name=='aws':
 s=' '.join(a)
 if 'configure get region' in s: print(os.environ['REGION'])
 elif 'export-credentials' in s: print(json.dumps({'SessionToken':'mock-only','Expiration':'2099-01-01T00:00:00Z'}))
 elif 'get-caller-identity' in s: print(os.environ['ACCOUNT'])
 elif 'repositoryUri' in s: print('797781632267.dkr.ecr.us-east-2.amazonaws.com/oficina-staging-api')
 elif 'imageTagMutability' in s: print(os.environ['MUTABLE'])
 elif 'describe-instance-information' in s: print(os.environ['ONLINE'])
 else: sys.exit(99)
elif name=='docker' and a!=['info']: sys.exit(99)
'''
            for name in ('git', 'aws', 'docker'):
                file = p / name
                file.write_text(mock)
                file.chmod(0o755)
            env = dict(os.environ, PATH=tmp+':'+os.environ['PATH'], ROOT=tmp, CALLS=str(p/'calls'), ACCOUNT=account, REGION=region, MUTABLE=mutable, ONLINE=online, DIRTY=dirty, AWS_REGION=ambient, AWS_DEFAULT_REGION=ambient)
            result = subprocess.run(['bash', str(SCRIPT), mode], env=env, text=True, capture_output=True)
            calls = (p/'calls').read_text() if (p/'calls').exists() else ''
            self.assertNotEqual(result.returncode, 0)
            for forbidden in ('send-command', 'push', 'login', 'build', 'put-', 'apply'):
                self.assertNotIn(forbidden, calls)
            return result.stdout+result.stderr, calls

    def test_plan_and_block_execute(self):
        for mode in ('--dry-run', '--execute'):
            output, _ = self.run_case(mode)
            self.assertIn('git-'+'a'*40, output)
            self.assertIn('render.sh ausente', output)

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
