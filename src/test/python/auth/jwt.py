import re
import time
from pathlib import Path
from uuid import UUID

import jwt
from cryptography.hazmat.primitives.serialization import load_pem_private_key
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKey

from auth.ports import PrivateKeyProvider


class FilePrivateKeyProvider:
    def __init__(self, path):
        self.path = path

    def load(self):
        return Path(self.path).read_bytes()


class RS256Signer:
    def __init__(self, provider: PrivateKeyProvider, issuer, audience, kid, clock=time.time):
        if not all(isinstance(v, str) and v.strip() for v in (issuer, audience, kid)):
            raise ValueError('Missing JWT configuration')
        if not re.fullmatch(r'[A-Za-z0-9._-]{1,128}', kid):
            raise ValueError('Invalid kid')
        self.key = load_pem_private_key(provider.load(), password=None)
        if not isinstance(self.key, RSAPrivateKey) or self.key.key_size < 2048:
            raise ValueError('RSA key of at least 2048 bits required')
        self.issuer, self.audience, self.kid, self.clock = issuer, audience, kid, clock

    def sign(self, customer_id):
        subject = str(UUID(str(customer_id)))
        now = int(self.clock())
        return jwt.encode(
            dict(iss=self.issuer, aud=self.audience, sub=subject, role='CUSTOMER',
                 iat=now, nbf=now, exp=now + 900),
            self.key, algorithm='RS256', headers={'kid': self.kid},
        )
