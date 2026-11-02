"""Test helper: use the real sibling signer, with an ephemeral key supplied on stdin.

Never writes into the sibling repository. Output is consumed in-memory by the Java test.
"""
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True
source = Path(sys.argv[1]).resolve()
sys.path.insert(0, str(source))
from auth.jwt import RS256Signer  # noqa: E402

request = json.load(sys.stdin)


class EphemeralProvider:
    def load(self):
        return request["private_pem"].encode("ascii")


signer = RS256Signer(EphemeralProvider(), request["issuer"], request["audience"], request["kid"])
sys.stdout.write(signer.sign(request["customer_id"]))
