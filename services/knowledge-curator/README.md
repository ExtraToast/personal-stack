# knowledge-curator

Periodic agent that classifies inbox captures, promotes them to
their final folder, and writes the resulting `kb_relations` edges.
Driven by an in-cluster Ollama (`qwen2.5:14b-instruct` for
classification, `nomic-embed-text` for nearest-neighbour evidence)
with strict JSON-schema-constrained output.

See `docs/private/knowledge-vault-redesign.md` for the broader
plan; this README focuses on operating the curator service itself.

## What the curator does on each pass

1. Pull-rebase the shared vault clone (`/var/lib/knowledge-vault`,
   the same PVC the ingest-worker writes into).
2. Walk `_inbox/<YYYY-MM-DD>/*.md` (skipping `_inbox/_needs-review/`).
3. For each file:
   - Embed the body via Ollama `nomic-embed-text` (768d).
   - Pull the top-K most similar existing notes via the
     knowledge-api MCP `recall` tool.
   - Call Ollama `qwen2.5:14b-instruct-q4_K_M` with a strict JSON
     schema (`response_format` carrying GBNF-grammar constraints).
   - Validate the proposed `topic:<slug>` against the closed
     vocabulary in `topics.yaml`; validate `supersedes` / `see_also`
     ids against `kb_notes`.
   - Either `git mv` the file to `topics/<topic-slug>/<type>/<slug>.md`
     (or `projects/<repo>/<type>/<slug>.md` / `agents/<name>/<type>/<slug>.md`)
     and rewrite the frontmatter, OR move to
     `_inbox/_needs-review/<filename>` with the reason in the
     commit message.
   - UPDATE `kb_notes.scope/vault_path/vault_commit/confidence`
     and INSERT `kb_relations` rows for supersedes / see_also.

## When notes land in `_inbox/_needs-review/`

The curator deliberately abdicates rather than guessing wrong:

| Reason                      | Trigger                                                                |
| --------------------------- | ---------------------------------------------------------------------- |
| `missing-id-in-frontmatter` | Inbox file's frontmatter has no `id` field                             |
| `classify-failed:*`         | Ollama returned invalid JSON twice in a row                            |
| `model-flagged:<reason>`    | Classifier emitted a `needs_review_reason`                             |
| `low-confidence:0.XX<floor` | Classifier confidence below `CLASSIFY_CONFIDENCE_FLOOR` (default 0.55) |
| `unknown-topic-slug:<slug>` | LLM proposed a `topic:` not in `topics.yaml`                           |
| `relation-target-missing`   | `supersedes` / `see_also` references a non-existent id                 |

Human review fixes the frontmatter by hand in Obsidian; the next
pass skips files already under `_needs-review/` so corrections
need only flow once.

## Configuration

Environment variables (all optional; defaults wired in for the
in-cluster topology):

- `OLLAMA_BASE_URL` — OpenAI-compatible base URL. Default
  `http://ollama.knowledge-system.svc.cluster.local:11434/v1`.
- `OLLAMA_CHAT_MODEL` — chat model. Default
  `qwen2.5:14b-instruct-q4_K_M`. Fallback `qwen2.5:7b-instruct` on
  smaller VRAM.
- `OLLAMA_EMBEDDING_MODEL` — default `nomic-embed-text`.
- `KNOWLEDGE_API_BASE_URL` — default points at the in-cluster
  service.
- `KNOWLEDGE_API_BEARER_TOKEN` — bearer token for the MCP recall
  path. Reads `curator` field from
  `secret/data/knowledge-system/mcp-bearer`.
- `CLASSIFY_CONFIDENCE_FLOOR` — threshold below which classifications
  route to needs-review. Default 0.55.
- `CLASSIFY_TOP_K_NEIGHBOURS` — recall neighbours per pass. Default 5.
- `TOPICS_YAML_PATH` — closed vocabulary. ConfigMap-mounted at
  `/etc/curator/topics.yaml` in production.

## Adding a topic

Topics live in
`platform/cluster/flux/apps/knowledge/knowledge-curator/topics-configmap.yaml`.
The ConfigMap rolls forward via Flux on commit; the next curator
pass picks up the new slug. Aliases live in the same entry so an
LLM emitting `Kotlin` or `kt` still normalises to `kotlin`.

Closed vocabulary is deliberate — adding `Kubernetes` and `K8s` as
separate folders would fragment the vault. If a real new topic
shows up, edit the YAML and ship a PR.

## Local development

```bash
cd services/knowledge-curator
uv sync --frozen
uv run pytest --cov    # unit tests; coverage gate at 80%
uv run ruff check .    # lint
uv run mypy            # strict-mode type checks
```

Integration tests are not yet wired in. The classify + recall HTTP
paths are exercised via respx-mocked unit tests; the vault git path
runs against a real bare-repo fixture; the postgres store is
unit-tested as a Protocol-implementing in-memory double.

## Why not LightRAG?

LightRAG's graph-RAG ingestion lives in PR 3. The curator here is
the lighter-weight stand-in: a single LLM call per inbox file,
hand-validated against a closed vocabulary, with the relations layer
populated incrementally as the curator promotes notes. The two are
not redundant — LightRAG runs over the _promoted_ corpus to give
`recall` an entity-anchored retrieval mode; the curator runs over
the _inbox_ to decide where each new note lands.
