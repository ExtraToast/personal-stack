from __future__ import annotations

from pathlib import Path

from curator.settings import Settings


def test_defaults_target_in_cluster_ollama_and_knowledge_api() -> None:
    s = Settings.from_env(env={})
    assert s.ollama_base_url.endswith("/v1")
    assert s.ollama_chat_model.startswith("qwen2.5")
    # Default embedder is now Qwen3-Embedding-0.6B — Matryoshka-native,
    # multilingual, beats nomic on MTEB at ~1 GB Q4. The 1024-dim
    # output matches knowledge-api's V9 `vector(1024)` column.
    assert s.ollama_embedding_model == "qwen3-embedding:0.6b"
    assert s.knowledge_api_base_url.startswith("http://knowledge-api")
    assert s.vault_clone_dir == Path("/var/lib/knowledge-vault")
    assert s.classify_top_k_neighbours == 5
    assert 0.0 <= s.classify_confidence_floor <= 1.0


def test_overrides_apply() -> None:
    s = Settings.from_env(
        env={
            "OLLAMA_BASE_URL": "http://localhost:11434/v1",
            "OLLAMA_CHAT_MODEL": "qwen2.5:7b-instruct",
            "CLASSIFY_TOP_K_NEIGHBOURS": "3",
            "CLASSIFY_CONFIDENCE_FLOOR": "0.7",
            "DB_HOST": "pg",
            "DB_PORT": "5433",
            "DB_NAME": "kb",
            "VAULT_CLONE_DIR": "/tmp/vault",
            "TOPICS_YAML_PATH": "/tmp/topics.yaml",
        }
    )
    assert s.ollama_base_url == "http://localhost:11434/v1"
    assert s.ollama_chat_model == "qwen2.5:7b-instruct"
    assert s.classify_top_k_neighbours == 3
    assert abs(s.classify_confidence_floor - 0.7) < 1e-9
    assert s.db_host == "pg"
    assert s.db_port == 5433
    assert s.vault_clone_dir == Path("/tmp/vault")
    assert s.topics_yaml_path == Path("/tmp/topics.yaml")
