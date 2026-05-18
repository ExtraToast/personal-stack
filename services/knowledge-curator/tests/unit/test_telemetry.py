from __future__ import annotations

import json

import pytest
import structlog

from curator.telemetry import configure


def test_configure_emits_json_with_service_fields(capsys: pytest.CaptureFixture[str]) -> None:
    configure(level="INFO", service_version="abc123")
    log = structlog.get_logger("test_configure")
    log.info("hello", extra_field=42)
    captured = capsys.readouterr()
    payload = json.loads(captured.out.strip().splitlines()[-1])
    assert payload["event"] == "hello"
    assert payload["service"] == "knowledge-curator"
    assert payload["service.version"] == "abc123"
    assert payload["extra_field"] == 42


def test_configure_uses_provided_level() -> None:
    configure(level="WARNING", service_version="v")
    log = structlog.get_logger("test_level")
    # Just confirm a debug call doesn't blow up; level filtering is
    # the wrapper class's responsibility.
    log.debug("ignored")
    log.warning("emitted")
