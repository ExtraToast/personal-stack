# knowledge-curator

Periodic agent that classifies inbox captures, promotes them to
their final folder, and writes the resulting `kb_relations` edges.
Driven by Ollama chat models (`qwen3:32b` on the heavy endpoint when
available, otherwise `qwen3:8b`) and `qwen3-embedding:0.6b` for
nearest-neighbour evidence, with strict JSON-schema-constrained output.

See `docs/private/knowledge-vault-redesign.md` for the broader
plan; this README focuses on operating the curator service itself.

## What the curator does on each pass

1. Pull-rebase the shared vault clone (`/var/lib/knowledge-vault`,
   the same PVC the ingest-worker writes into).
2. Walk `_inbox/<YYYY-MM-DD>/*.md` (skipping review/discard archives).
3. For each file:
   - Embed the body via Ollama `qwen3-embedding:0.6b` (1024d).
   - Pull the top-K most similar existing notes via the
     knowledge-api MCP `recall` tool.
   - Call Ollama chat with a strict JSON schema (`response_format`).
   - Validate the proposed `topic:<slug>` against the closed
     vocabulary in `topics.yaml`; validate `supersedes` / `see_also`
     ids against `kb_notes`.
   - Either `git mv` the file to `topics/<topic-slug>/<type>/<slug>.md`
     (or `projects/<repo>/<type>/<slug>.md` / `agents/<name>/<type>/<slug>.md`)
     and rewrite the frontmatter, discard low-value captures to
     `_inbox/_discarded/<filename>`, or rarely move to
     `_inbox/_needs-review/<filename>` with the reason and attempt
     count in frontmatter.
   - UPDATE `kb_notes.scope/vault_path/vault_commit/confidence`
     and INSERT `kb_relations` rows for supersedes / see_also, or
     DELETE the `kb_notes` row and relations when the capture is
     discarded.

## When notes land in `_inbox/_needs-review/`

The curator is biased toward rewrite-and-promote, and the classifier
can now emit `action=discard` for empty, noisy, duplicate, test, or
otherwise low-value captures. Discarded files are archived under
`_inbox/_discarded/` and their KB row plus relations are deleted.

Needs-review is the rare fallback when neither promotion nor discard
is defensible, or when validation/transport failures prevent a
decision:

| Reason                      | Trigger                                                                |
| --------------------------- | ---------------------------------------------------------------------- |
| `missing-id-in-frontmatter` | Inbox file's frontmatter has no `id` field                             |
| `classify-failed:*`         | Ollama returned invalid JSON twice in a row                            |
| `model-flagged:<reason>`    | Classifier emitted `needs_review_reason` with `action=promote`         |
| `low-confidence:0.XX<floor` | Classifier confidence below `CLASSIFY_CONFIDENCE_FLOOR`                |
| `unknown-topic-slug:<slug>` | LLM proposed a `topic:` not in `topics.yaml`                           |

Each review move writes `review_attempts: <n>` into frontmatter. After
`CURATOR_MAX_REVIEW_ATTEMPTS` failed attempts (default 3), the next
failure is auto-discarded to `_inbox/_discarded/` instead of being
re-classified forever.

## Configuration

Environment variables (all optional; defaults wired in for the
in-cluster topology):

- `OLLAMA_BASE_URL` — OpenAI-compatible base URL. Default
  `http://ollama.knowledge-system.svc.cluster.local:11434/v1`.
- `OLLAMA_CHAT_MODEL` — chat model. Default `qwen3:8b`; production
  uses `OLLAMA_HEAVY_CHAT_MODEL=qwen3:32b` when the heavy endpoint
  probes healthy.
- `OLLAMA_EMBEDDING_MODEL` — default `qwen3-embedding:0.6b`.
- `KNOWLEDGE_API_BASE_URL` — default points at the in-cluster
  service.
- `KNOWLEDGE_API_BEARER_TOKEN` — bearer token for the MCP recall
  path. Reads `curator` field from
  `secret/data/knowledge-system/mcp-bearer`.
- `CLASSIFY_CONFIDENCE_FLOOR` — threshold below which classifications
  route to needs-review. Code default 0.55; production CronJob passes
  0.35 so the band above promotes decisively.
- `CLASSIFY_TOP_K_NEIGHBOURS` — recall neighbours per pass. Default 5.
- `CURATOR_MAX_REVIEW_ATTEMPTS` — review failures before auto-discard.
  Default 3.
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
