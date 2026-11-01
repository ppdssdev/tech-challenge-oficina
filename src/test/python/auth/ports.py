from dataclasses import dataclass
from typing import Protocol
from uuid import UUID


@dataclass(frozen=True)
class Identity:
    customer_id: UUID
    active: bool


class IdentityLookup(Protocol):
    def find(self, cpf: str) -> list[Identity]: ...


class TokenSigner(Protocol):
    def sign(self, customer_id: UUID) -> str: ...


class PrivateKeyProvider(Protocol):
    # Future Secrets Manager adapter implements this port, with bounded caching.
    def load(self) -> bytes: ...
