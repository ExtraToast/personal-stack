"""Periodic maintenance passes that run alongside the inbox curator.

Each module under this package is a standalone entrypoint
(`python -m curator.maintenance.<name>`) wired as its own k8s CronJob.
They share the curator's existing collaborators — `PostgresCuratorStore`,
`CuratorVault`, `OllamaClassifier`-style chat clients, the heavy/light
endpoint resolver — but each module owns a single narrow concern so
the failure modes stay local.

Round 1 modules:

- ``title_quality``: re-titles notes whose title hits a configurable
  regex set (default: framing-word prefixes like "How to" / "On " /
  "Introduction to"). Reuses the heavy chat endpoint via
  `ollama_router.resolve_chat`; falls back to the in-cluster CPU
  Ollama when the rx7900xtx node is offline.
"""
