#!/usr/bin/env python3
"""Create only the new observability Secret; password never enters argv/stdout."""
import base64
import getpass
import json
import os
import subprocess
import sys
import warnings

kubectl = os.environ.get('KUBECTL', 'kubectl')
# Do not fall back to echoed input in a noninteractive SSM Run Command.
warnings.simplefilter('error', getpass.GetPassWarning)
try:
    password = getpass.getpass('Senha única do cofre (mínimo 20 caracteres): ')
    confirmation = getpass.getpass('Confirme a senha: ')
except (getpass.GetPassWarning, EOFError):
    sys.exit('Use sessão SSM interativa com terminal; senha não foi lida.')
if (len(password) < 20 or password != confirmation
        or password.lower() in {'admin' * 4, 'password' * 3}
        or len(set(password)) < 8):
    sys.exit('Senha fraca ou confirmação diferente; nada foi criado.')
secret = {
    'apiVersion': 'v1', 'kind': 'Secret', 'type': 'Opaque',
    'metadata': {'name': 'oficina-observability-grafana-admin', 'namespace': 'oficina-staging'},
    'data': {k: base64.b64encode(v.encode()).decode() for k, v in
             {'username': 'oficina-operator', 'password': password}.items()},
}
# create (not apply): never update an existing Secret or store last-applied credentials.
result = subprocess.run([kubectl, 'create', '-f', '-'], input=json.dumps(secret),
                        text=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
if result.returncode:
    sys.exit('Secret não criado (pode já existir). Verifique acesso/nome; conteúdo não foi exibido.')
print('Secret administrativo criado; senha não exibida nem gravada em arquivo.')
