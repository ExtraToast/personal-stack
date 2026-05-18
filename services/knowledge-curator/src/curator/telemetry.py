"""structlog + OpenTelemetry wiring shared with the worker.

Same JSON shape as `services/knowledge-ingest-worker/src/knowledge_worker/telemetry.py`
so Loki queries can fan across both services without per-source
parsers.
"""

from __future__ import annotations

import logging
import sys

import structlog


def configure(*, level: str = "INFO", service_version: str = "unknown") -> None:
    log_level = getattr(logging, level.upper(), logging.INFO)
    logging.basicConfig(level=log_level, stream=sys.stdout, format="%(message)s")

    processors: list[structlog.types.Processor] = [
        structlog.contextvars.merge_contextvars,
        structlog.processors.add_log_level,
        structlog.processors.TimeStamper(fmt="iso", utc=True),
        _add_service_version(service_version),
        structlog.processors.dict_tracebacks,
        structlog.processors.JSONRenderer(),
    ]
    structlog.configure(
        processors=processors,
        wrapper_class=structlog.make_filtering_bound_logger(log_level),
        cache_logger_on_first_use=True,
    )


def _add_service_version(service_version: str) -> structlog.types.Processor:
    def _processor(
        _logger: structlog.types.WrappedLogger,
        _method_name: str,
        event_dict: structlog.types.EventDict,
    ) -> structlog.types.EventDict:
        event_dict.setdefault("service.version", service_version)
        event_dict.setdefault("service", "knowledge-curator")
        return event_dict

    return _processor
