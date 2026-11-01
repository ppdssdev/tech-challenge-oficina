import json, sys, time, uuid
import jwt
from auth.jwt import RS256Signer
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization

class Provider:
    def __init__(self, key): self.key = key
    def load(self):
        return self.key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())

keys = [rsa.generate_private_key(public_exponent=65537, key_size=2048) for _ in range(2)]
kids = [str(uuid.uuid4()) for _ in keys]
issuer, audience = 'https://issuer.test', 'oficina-api-test'
signers = [RS256Signer(Provider(k), issuer, audience, kid) for k, kid in zip(keys, kids)]
token = signers[0].sign(sys.argv[1])
claims = jwt.decode(token, options={'verify_signature': False})
def encode(c, kid=kids[0], key=keys[0]):
    return jwt.encode(c, key, algorithm='RS256', headers={'kid': kid})
tokens = {'valid': token, 'rotated': signers[1].sign(sys.argv[1]), 'other': signers[0].sign(uuid.uuid4())}
for name, changes in {'expired': {'iat': int(time.time())-2000, 'nbf': int(time.time())-2000, 'exp': int(time.time())-1100}, 'issuer': {'iss': 'https://evil.test'}, 'audience': {'aud': 'wrong'}, 'subject': {'sub': '1-1-1-1-1'}, 'role': {'role': 'ADMIN'}, 'future': {'nbf': int(time.time())+300}, 'iat': {'iat': int(time.time())+300}}.items():
    tokens[name] = encode(dict(claims, **changes))
for claim in ['sub', 'iss', 'aud', 'role', 'iat', 'nbf', 'exp']:
    c = dict(claims); del c[claim]; tokens['missing_'+claim] = encode(c)
tokens['unknown'] = encode(claims, 'unknown')
tokens['no_kid'] = jwt.encode(claims, keys[0], algorithm='RS256')
tokens['none'] = jwt.encode(claims, '', algorithm='none', headers={'kid': kids[0]})
tokens['hmac'] = jwt.encode(claims, 'test-secret-with-at-least-32-bytes-123456789', algorithm='HS256', headers={'kid': kids[0]})
parts = token.split('.'); parts[2] = ('A' if parts[2][0] != 'A' else 'B') + parts[2][1:]; tokens['tampered'] = '.'.join(parts)
jwks = []
for key, kid in zip(keys, kids):
    jwk = json.loads(jwt.algorithms.RSAAlgorithm.to_jwk(key.public_key())); jwk.update(kid=kid, alg='RS256', use='sig'); jwks.append(jwk)
print(json.dumps(dict(tokens=tokens, old={'keys': jwks[:1]}, both={'keys': jwks}, new={'keys': jwks[1:]})))
