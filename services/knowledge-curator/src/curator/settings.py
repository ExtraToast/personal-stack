"""Settings loaded from environment variables.

Mirrors the knowledge-ingest-worker's `Settings` shape so the two
services have parallel ops surface area. Same env names where the
underlying value is the same (DB_*, VAULT_CLONE_*, VAULT_SSH_KEY_PATH);
new names where the curator has its own knobs (OLLAMA_*, KNOWLEDGE_API_*).
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class Settings:
    # Logging + telemetry
    log_level: str
    service_version: str

    # Vault git clone (shared PVC with knowledge-ingest-worker)
    vault_clone_url: str
    vault_clone_dir: Path
    vault_branch: str
    vault_ssh_key_path: str
    curator_author_name: str
    curator_author_email: str

    # Postgres (knowledge_db)
    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str

    # Ollama — OpenAI-compatible endpoint
    ollama_base_url: str
    ollama_chat_model: str
    ollama_embedding_model: str
    ollama_request_timeout_seconds: float

    # knowledge-api recall (for nearest-neighbour evidence). The
    # curator authenticates with its own bearer token reserved for
    # internal callers.
    knowledge_api_base_url: str
    knowledge_api_bearer_token: str

    # Curator behaviour
    classify_top_k_neighbours: int
    classify_confidence_floor: float

    # Topic vocabulary file (mounted from a ConfigMap in production).
    topics_yaml_path: Path

    @classmethod
    def from_env(cls, env: dict[str, str] | None = None) -> Settings:
        e = os.environ if env is None else env
        return cls(
            log_level=e.get("LOG_LEVEL", "INFO"),
            service_version=e.get("SERVICE_VERSION", "unknown"),
            vault_clone_url=e.get(
                "VAULT_CLONE_URL", "git@github.com:ExtraToast/knowledge-vault.git"
            ),
            vault_clone_dir=Path(e.get("VAULT_CLONE_DIR", "/var/lib/knowledge-vault")),
            vault_branch=e.get("VAULT_BRANCH", "main"),
            vault_ssh_key_path=e.get("VAULT_SSH_KEY_PATH", "/vault/secrets/id_ed25519"),
            curator_author_name=e.get("CURATOR_AUTHOR_NAME", "knowledge-curator"),
            curator_author_email=e.get("CURATOR_AUTHOR_EMAIL", "curator@knowledge.local"),
            db_host=e.get("DB_HOST", "postgres.data-system.svc.cluster.local"),
            db_port=int(e.get("DB_PORT", "5432")),
            db_name=e.get("DB_NAME", "knowledge_db"),
            db_user=e.get("DB_USER", "kb_user"),
            db_password=e.get("DB_PASSWORD", "kb_password"),
            # Ollama lives in-cluster. The OpenAI-compatible endpoint
            # is /v1/ — LightRAG and the curator share it.
            ollama_base_url=e.get(
                "OLLAMA_BASE_URL",
                "http://ollama.knowledge-system.svc.cluster.local:11434/v1",
            ),
            # qwen2.5:14b-instruct is the research-blessed first
            # choice for JSON-constrained classification at <16 GB
            # VRAM. Fall back to 7b on smaller boxes — change the env
            # var, not the code.
            ollama_chat_model=e.get("OLLAMA_CHAT_MODEL", "qwen2.5:14b-instruct-q4_K_M"),
            ollama_embedding_model=e.get("OLLAMA_EMBEDDING_MODEL", "nomic-embed-text"),
            ollama_request_timeout_seconds=float(e.get("OLLAMA_REQUEST_TIMEOUT_SECONDS", "120")),
            knowledge_api_base_url=e.get(
                "KNOWLEDGE_API_BASE_URL",
                "http://knowledge-api.knowledge-system.svc.cluster.local:8080",
            ),
            knowledge_api_bearer_token=e.get("KNOWLEDGE_API_BEARER_TOKEN", ""),
            classify_top_k_neighbours=int(e.get("CLASSIFY_TOP_K_NEIGHBOURS", "5")),
            classify_confidence_floor=float(e.get("CLASSIFY_CONFIDENCE_FLOOR", "0.55")),
            topics_yaml_path=Path(e.get("TOPICS_YAML_PATH", "/etc/curator/topics.yaml")),
        )
