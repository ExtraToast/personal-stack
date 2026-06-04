# Knowledge-system evolution plan

Status as of 2026-06-04: this plan is partially superseded. Keep it as
historical context for the Postgres-only migration split, embedding/model
tradeoffs, and the original retrieval roadmap, but verify current source
before using any PR-stack item as future work.

Already shipped since this document was written:

- `knowledge.recall` supports `fast`, `hybrid`, and `deep` modes; hybrid
  is no longer a future PR.
- The knowledge MCP exposes canonical dot-named tools including
  `knowledge.suggest_topic`, `knowledge.find_duplicates`,
  `knowledge.list_tag_candidates`, `knowledge.rename_tag`,
  `knowledge.merge_tags`, `knowledge.relations`, and
  `knowledge.list_audit`.
- Recall usage stats, embeddings, topic/tag hygiene helpers, and admin
  discovery surfaces exist in current `knowledge-api` and curator code.
- Agent-memory work has moved toward `platform/agents/kit/manifest.yaml`
  plus renderer-backed Claude/Codex hook and skill parity.

Still useful from this document:

- The jOOQ `DDLDatabase` constraint and `db/migration-pg` split.
- The distinction between source-grounded `knowledge-api` recall and
  LightRAG graph retrieval.
- The safety stance that recall degradation must be non-fatal and admin
  mutation tools must remain admin-token gated.

Stacked PRs that take the recall side from single-leg FTS to the hybrid
HyDE+vector+graph+rerank shape the prior design proposals (see
`_inbox/2026-05-19/075925-knowledge-system-retrieval-architecture…`
in the vault) already specified, plus tag hygiene and a coherent Qwen3
stack. Each PR is shippable and revertable alone.

## Background — what is already built

Verified by reading `services/knowledge-api/`, `services/knowledge-curator/`,
and `services/knowledge-ingest-worker/`:

- Capture, curator, audit table, title normaliser, relation resolver,
  topic + project vocabulary, discovery tools
  (`list_topics/tags/scopes/sources`, `topic_stats`, `list_inbox`),
  Reflexion-style digest tool, install.sh hook bundle — **all shipped**.
- `RecallRepository.recall()` is single-leg Postgres FTS only.
- `LightRagClient.publish()` is publish-only. No reader consumes
  LightRAG's retrieval surface yet.
- `OllamaEmbedder` exists in the curator, used at _promote_ time to fetch
  neighbour notes for the classifier — never persisted to `kb_notes`.

## Critical constraint discovered while planning

The V1 schema comment explicitly states that DDL must stay inside the
SQL subset jOOQ's `DDLDatabase` code generator can interpret on top of
H2: no `TEXT[]`, no `JSONB`, no `USING GIN`. `DDLDatabase` doesn't know
`vector(N)` either.

The whole migration tree is fed to `DDLDatabase` via
`jooq-codegen-conventions.gradle.kts`:

```kotlin
migrationLocations = listOf("filesystem:src/main/resources/db/migration")
```

So a naïve `ALTER TABLE kb_notes ADD COLUMN embedding vector(1024)` in
`V9__embeddings.sql` would break `compileKotlin`.

**Resolution:** introduce a Postgres-only migration location
`src/main/resources/db/migration-pg/`. Flyway loads it via
`spring.flyway.locations: classpath:db/migration,classpath:db/migration-pg`;
the jOOQ codegen task only ever sees `db/migration/`. The pgvector
column is not in the generated jOOQ metamodel — which is fine because
recall queries are hand-written SQL anyway, just like the FTS leg in
`RecallRepository.kt:58`.

`integrationTest` (Testcontainers Postgres) loads both locations; `test`
unit tests that hit an in-memory H2 (if any) load only the default.

## PR stack

Each `[PR-N]` is one branch off the previous. Bracketed tag in commit
message: `kb: <description> [PR-N]`. No phase numbers in subjects —
matches the existing repo convention (per the feedback memory).

### [PR-1] Embedding foundation — this branch

Goal: every recall now runs FTS + vector legs and fuses with reciprocal
rank fusion. Existing rows stay NULL-embedding until PR-2 backfills;
the vector leg degrades gracefully (returns empty) for un-embedded
rows.

- New dir `services/knowledge-api/src/main/resources/db/migration-pg/`
  with `V9__embeddings.sql` that:
  - `CREATE EXTENSION IF NOT EXISTS vector;`
  - `ALTER TABLE kb_notes ADD COLUMN embedding vector(1024);`
  - `CREATE INDEX kb_notes_embedding_hnsw_idx ON kb_notes USING hnsw (embedding vector_cosine_ops);`
  - `ALTER TABLE kb_notes ADD COLUMN embedding_model VARCHAR(64);`
  - `ALTER TABLE kb_notes ADD COLUMN embedded_at TIMESTAMP;`
- `application.yml` flyway locations include the new dir.
- New port `QueryEmbedder` in `knowledge/recall/`. `OllamaQueryEmbedder`
  implementation posts to Ollama `/v1/embeddings` (same shape the curator
  uses).
- `EmbeddingRepository.recallVector(queryEmbedding: FloatArray, scope: String?, limit: Int)`
  runs `ORDER BY embedding <=> $1::vector LIMIT $2` with the same scope
  filter semantics as `RecallRepository.recall`.
- `RecallService.recallHybrid(query, scope, limit, mode)` runs both
  legs, RRF-fuses (`1 / (k + rank_i)`, `k = 60` per the literature
  default), returns top-N.
- `recall` MCP tool grows a `mode` parameter:
  - `fast` — FTS only (current behaviour). Default in `application.yml`
    so the upgrade is opt-in until PR-2 lands and the index is warm.
  - `hybrid` — FTS + vector, RRF-fused.
  - `deep` — placeholder, currently aliases to `hybrid`. Wired
    properly in PR-4.
- OTel spans on `RecallService.recall*`:
  `recall.mode`, `recall.fts_hits`, `recall.vector_hits`,
  `recall.fused_hits`, `recall.embedder_ms`, `recall.latency_ms`,
  `recall.degraded` (list of legs that errored/timed out).
- Tests:
  - Unit: RRF fusion math (synthetic ranked lists).
  - Integration: pgvector index works under Testcontainers (`pgvector/pgvector:pg16`
    image) — insert two rows with known embeddings, query with a third,
    confirm ordering. Verifies the Flyway location wiring + the SQL.

### [PR-2] Curator embed-on-promote + Qwen3-Embedding-0.6B

Goal: every promoted note gets an embedding written to `kb_notes`.
Backfill existing rows in a one-shot Job.

- Ollama deployment pulls `qwen3-embedding:0.6b` alongside the existing
  models. Pin Ollama outside `0.13.0-0.13.2` (per CLAUDE.md).
- `services/knowledge-curator/src/curator/embed.py`:
  - `OllamaEmbedder.embed()` already exists; bump model parameter to
    `qwen3-embedding:0.6b` via `KB_EMBED_MODEL` env. Matryoshka
    truncation kept full-dim at store time; query-time truncation lands
    in PR-4 only if telemetry shows latency matters.
- `services/knowledge-curator/src/curator/promote.py`:
  - After successful classification + DB row write, call
    `OllamaEmbedder.embed(title + "\n\n" + body)` and UPDATE
    `kb_notes SET embedding = ?, embedding_model = ?, embedded_at = NOW()`.
  - Failure is non-fatal — log + audit `action=embed_skipped`. The
    next reclassify pass re-attempts.
- New CronJob `kb-embedding-backfill` (Helm chart sibling of
  `kb-renormalise-titles`) runs nightly: SELECT 100 rows ordered by
  `embedded_at IS NULL DESC, captured_at`, embed, UPDATE. Stops when
  all rows have current-model embeddings.
- `kb_audit` row per backfill batch with counts.

### [PR-3] Telemetry baseline + Grafana panel

Goal: visibility on every recall before any further layers land. This
is small but moves first because every later PR is otherwise impossible
to evaluate.

- (Most of the OTel work already shipped in PR-1; this PR just adds the
  Grafana panel.)
- Grafana dashboard JSON in `platform/cluster/flux/apps/observability/grafana-dashboards/knowledge-system.json`:
  recall p50/p95/p99, fts/vector hit ratio, degraded-legs count,
  per-mode rate.

### [PR-4] LightRAG read-side + listwise rerank

Goal: third RRF leg + reorder by Qwen3-Reranker-0.6B.

- New port `GraphRetriever` in `knowledge/recall/`. `LightRagRetriever`
  implementation posts to
  `lightrag.knowledge-system.svc.cluster.local:9621/query` with
  `{query, mode: "mix", top_k, only_need_context: true}`.
- Circuit-breaker + 5s timeout (LightRAG slowness must not block
  read path; on fault, log + add to `recall.degraded` and drop the leg
  from the RRF input).
- New port `Reranker` + `OllamaListwiseReranker` adapter. One Ollama
  call with `qwen3-reranker:0.6b`, prompt asks for IDs in best-to-worst
  order, validates the output is a permutation of the input IDs.
- `mode=deep` wires all three retrieval legs + the reranker. p50
  budget: ~5s on a warm Ollama (vs ~50ms `fast`, ~200ms `hybrid`).
- New `recall.rerank_used` + `recall.graph_used` attributes on the
  existing span.

### [PR-5] Discovery completion — `suggest_topic` + `find_duplicates`

Both designed but deliberately deferred until embeddings exist.

- `knowledge.suggest_topic(text)` → `{slug, confidence, alternates: [{slug, score}]}`.
  Implementation: embed the text, find top-K topic centroids (mean of
  topic's notes' embeddings, cached), classifier-only fallback if no
  centroid yet.
- `knowledge.find_duplicates(query_or_id, threshold=0.85)` →
  `[{id, score, title}]`. Implementation: embed the query/note,
  `embedding <=> ? < (1 - threshold)`.
- Both are READ-ONLY MCP tools, no auth bump.

### [PR-6] Tag hygiene — `merge_tags`, `rename_tag`, periodic re-evaluation

This addresses the user's explicit ask: tags need merging, relevance,
periodic re-evaluation. The curator quality plan in the KB designed
`rename_tag` as admin-only; this PR adds `merge_tags` and the periodic
pass.

- Admin MCP tools (already gated by admin bearer):
  - `knowledge.merge_tags(from: list<string>, into: string)` — bulk
    UPDATE `kb_note_tags`, audit row per merge with the source tag
    list, idempotent (re-running with the same `into` is a no-op).
  - `knowledge.rename_tag(from, to)` — already designed, finally
    implemented. Same audit shape.
  - `knowledge.list_tag_candidates(min_count=2)` →
    `[{cluster: [tag], score}]`. Embeds each tag once, clusters by
    cosine ≥ 0.85, returns clusters with their member tags and the
    suggested canonical (highest-count member).
- New curator pass `tag_hygiene` (weekly CronJob
  `kb-tag-hygiene`):
  1. Pull all tags + counts via `list_tag_candidates`.
  2. For each cluster above the threshold, emit an
     `audit.action=suggest_merge_tags` row with the cluster + suggested
     canonical. **Never auto-merge** — same posture as topic
     `suggest_merge` in the curator quality plan: a human runs the
     `merge_tags` call after review.
  3. Also flag tags with count = 1 older than 6 months as
     `audit.action=suggest_drop_tag` candidates.
- `knowledge.list_audit(action=suggest_merge_tags)` makes the queue
  visible.
- Tag-level reclassification finishes the stub in
  `services/knowledge-curator/src/curator/reclassify.py:30`: after a
  `rename_tag` or `merge_tags`, the watermark moves the same way it
  already does for topics, and the next reclassify pass re-runs the
  tag classifier on rows whose `tag_classified_at < the rename audit
row's `at`. Same confidence floor + budget logic as topic
  reclassification.

### [PR-7] Curator model bump — Qwen2.5-14B → Qwen3-14B

- Ollama deployment swaps `qwen2.5:14b-instruct` for `qwen3:14b`.
- Curator config: drop Ollama's JSON-schema grammar overlay, use
  Qwen3's native structured-output mode. `temperature: 0.1`.
- Single-PR rollback risk: pin the previous model behind
  `KB_CLASSIFIER_MODEL` env so a config flip reverts without a deploy.
- Validation: run the curator against a fixed eval-set of 50 captured
  inboxes and compare `topic`, `tags`, `confidence` distributions.
  Acceptance: ≥ same precision on topic, ≥ same JSON-validity rate.

### [PR-8] A-MEM link evolution + Cognee usage decay

Both are 2025-2026 papers (A-MEM NeurIPS 2025 arXiv:2502.12110;
Cognee `memify`). Apply as new curator passes only after PR-1-2 land
embeddings, and PR-5 lands `find_duplicates`.

- A-MEM link evolution:
  - On promote: embed → find top-K nearest neighbours → ask the
    classifier to propose `see_also` edges OR rewrite the contextual
    tags of one of the neighbours.
  - Budget: 1 LLM call per promote, 200 token max output.
  - Audit row per edge added or contextual-attribute rewrite.
- Cognee usage decay:
  - Add `kb_notes.recall_count INT DEFAULT 0, last_recalled_at TIMESTAMP`.
  - Bump on every recall hit (async, non-transactional).
  - Reranker uses recall_count as a tie-break feature.
  - Stale-note detection (already partially designed) reuses this:
    notes with `recall_count = 0` and `captured_at < 6 months` are
    candidates for review.

## Things kept out of scope

- **HyDE + multi-query expansion** (`mode=expand`): diminishing returns
  at ≤ 10³ notes. Revisit once corpus crosses that threshold.
- **Contextual Retrieval** (Anthropic ingestion-time enrichment):
  embedding cost not yet justified at this scale.
- **RAPTOR / hierarchical summarisation**: same reasoning.
- **Late-interaction (ColBERT v2 / MUVERA)**: simpler stack saturates
  at our corpus size.
- **Self-RAG / CoVe**: requires fine-tuning, off-table for Ollama.
- **MCP sampling**: every server tool stays a normal tool-call. Don't
  open the sampling attack surface for a homelab solo use case.

## Tool surface watch

Current tool count under the STRAP threshold (~96 = "context rot"
zone). Tally after PR-6:

- capture: capture_lesson, capture_decision, ingest_note
- read: recall, list_recent, get_note, find_conflicts, relations,
  digest_transcript
- discovery: list_topics, list_tags, list_scopes, list_sources,
  topic_stats, list_inbox, suggest_topic, find_duplicates,
  list_tag_candidates
- admin: add_topic, update_topic, merge_topics, rename_tag,
  merge_tags, list_audit
- ≈ 23 tools. Comfortable. If the count grows past ~30, consolidate
  the `list_*` discovery family into a single `knowledge.discover(kind, ...)`.

## Where this plan diverges from the prior proposals

1. Prior plan picked `nomic-embed-text` 768-dim; this plan picks
   Qwen3-Embedding-0.6B 1024-dim. Reason: better MTEB, Matryoshka-native,
   instruction-aware. Same Ollama overhead.
2. Prior plan kept listwise rerank "via the chat model" (qwen2.5:7b).
   This plan introduces a dedicated `qwen3-reranker:0.6b`. 1 GB extra
   VRAM, larger precision gain.
3. Prior plan didn't address tag hygiene. This plan adds PR-6 in
   response to direct user feedback (2026-05-20).
4. Prior plan didn't enumerate the DDLDatabase / pg-only migration
   split. Documented above; will write a separate decision capture
   after PR-1 lands.

## Implementation order rationale

- PR-1 first: foundation everything else depends on.
- PR-2 next: without embedded rows, PR-1's hybrid mode is no better
  than FTS.
- PR-3 between PR-2 and PR-4: don't add complexity without
  visibility.
- PR-4 only after PR-2 has been in prod long enough for the index to
  warm.
- PR-5 + PR-6 can swap order; PR-6 addresses user-visible drift
  directly.
- PR-7 is independent of all of the above; can land anywhere.
- PR-8 last, depends on PR-2 (embeddings) + PR-5 (duplicate finder).
