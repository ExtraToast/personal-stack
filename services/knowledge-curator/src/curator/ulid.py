"""Tiny ULID generator.

The curator writes audit rows into `kb_audit` and needs a
chronologically-sortable, collision-resistant id. ULID is the
right shape: 48-bit millisecond timestamp + 80-bit randomness,
Crockford base32-encoded into 26 chars. `ORDER BY id DESC`
doubles as `ORDER BY at DESC` because the timestamp prefix
dominates the lex order.

Adding `python-ulid` as a dependency for ~30 lines of code felt
heavy; this implementation is the canonical spec, no external
deps. The python-ulid library is a drop-in replacement if a
future caller needs monotonic guarantees within a millisecond.
"""

from __future__ import annotations

import os
import time

_CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"


def generate() -> str:
    """Generate a 26-char Crockford-base32 ULID for the current time."""

    timestamp_ms = int(time.time() * 1000)
    randomness = int.from_bytes(os.urandom(10), "big")
    return _encode(timestamp_ms, 10) + _encode(randomness, 16)


def _encode(value: int, length: int) -> str:
    out = ["0"] * length
    for i in range(length - 1, -1, -1):
        out[i] = _CROCKFORD[value & 0x1F]
        value >>= 5
    return "".join(out)
