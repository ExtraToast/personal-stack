"""Unit tests for the local ULID generator."""

from __future__ import annotations

import time

from curator.ulid import generate

_CROCKFORD = set("0123456789ABCDEFGHJKMNPQRSTVWXYZ")


def test_generate_returns_26_char_crockford_string() -> None:
    value = generate()
    assert len(value) == 26
    assert set(value) <= _CROCKFORD


def test_generate_is_collision_free_within_a_run() -> None:
    values = {generate() for _ in range(2000)}
    assert len(values) == 2000


def test_generate_lex_sorts_in_chronological_order() -> None:
    first = generate()
    time.sleep(0.005)
    second = generate()
    assert first < second
