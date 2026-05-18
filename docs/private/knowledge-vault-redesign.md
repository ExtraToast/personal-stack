# knowledge-vault — restructure (3-PR plan)

Final design after two parallel research passes on PKM best practice
and LightRAG/Ollama specifics. Implementation rolls out as three
stacked PRs, each containing many focused commits.

---

## Goals (user-stated)

1. General info on a language / framework → topic folder.
2. Project-specific lessons → per-project folder.
3. No generic `notes/` unless humans put stuff there.
4. Use `_inbox/` for raw captures; a secondary classifier agent
   processes, improves titles, dedups, and promotes.
5. Better filenames — readable, not ULIDs.
6. Use **local AI** for classification (Ollama in-cluster).
7. Use **LightRAG** for retrieval / graph.

## Non-goals (this redesign)

- Replacing forward-auth or bearer-bypass.
- Touching the existing Vault Agent inject pattern.
- Splitting `kb_notes` into multiple tables.
- Building a UI; Obsidian + MCP stay the only surfaces.

---

## Layout (replaces SCHEMA.md's draft)

```
<vault root>/
  topics/                            general, cross-project knowledge
    <topic-slug>/                    one folder per language / framework / tool
      <kebab-title>.md
  projects/                          repo-scoped knowledge
    <github-repo-name>/              one folder per github repo
      <kebab-title>.md
  agents/                            assistant guidance
    _shared/                         every agent sees these
      <kebab-title>.md
    <agent-name>/                    one folder per assistant slug
      <kebab-title>.md
  _inbox/                            untriaged captures, worker writes here ONLY
    YYYY-MM-DD/
      HHMMSS-<slug>--<id8>.md
    _needs-review/                   classifier flagged for human resolution
      <slug>--<id8>.md
  _index/                            auto-generated, never hand-edited
    recent.md
    conflicts.md
    topics/<topic>.md                MoC per topic, generated from frontmatter
```

Notes:

- **No `notes/`**, no `personal/`, no `work/`, no `public/` directories.
  Per-folder access boundaries replace `scope`-as-folder. `scope` stays
  in frontmatter for retrieval filters; folder is the canonical home.
- A note's "primary" location is one folder. Cross-references happen
  via `[[wikilinks]]` and `see_also` frontmatter — not by duplicating
  files.
- ULID stays in frontmatter `id`. Filename is a kebab-slug of the title,
  truncated to 60 chars at a word boundary, collision-suffixed with
  `-2`, `-3`. Obsidian auto-updates wikilinks on rename.
- `_inbox/YYYY-MM-DD/HHMMSS-...` timestamps the capture so triaging is
  obvious. The `--<id8>` suffix is the first 8 chars of the ULID, giving
  uniqueness even if titles collide before curator processes them.

### Why this matches the research

The PKM research converged on a hybrid: **PARA-style folder hierarchy
for actionable scope (topics + projects) + Zettelkasten-style typed
relations on top (supersedes, see_also, contradicts, refines)**.
Closed tag vocabulary, 4–6 typed relations, validated targets. Atomic
notes ≈ 200–600 words. Slug filenames + ULID in frontmatter. Auto-
generated indexes where mechanical; hand-curated MoCs only when
narrative justifies it. The proposed layout honors each of these.

---

## Capture → inbox → curator pipeline

```
            ┌────────────┐
            │ MCP client │ (Claude Code / Codex / curl)
            └─────┬──────┘
                  │ tools/call capture_lesson
                  ▼
            ┌────────────┐
            │knowledge-  │ ULID mint, INSERT kb_notes (scope=_inbox)
            │api         │ publish knowledge.lesson to RabbitMQ
            └─────┬──────┘
                  │
                  ▼
            ┌──────────────┐    git commit + push
            │ingest-worker │ ── writes _inbox/YYYY-MM-DD/HHMMSS-slug--id8.md
            └─────┬────────┘    UPDATE kb_notes.vault_path / vault_commit
                  │
                  │   (every 5 min CronJob)
                  ▼
            ┌──────────────┐
            │  curator     │  for each _inbox/*.md:
            │              │    1. embed body (Ollama nomic-embed-text)
            │              │    2. recall top-K similar
            │              │    3. LLM classify (Ollama qwen2.5:14b,
            │              │       response_format=<json-schema>)
            │              │    4. validate enums + supersedes targets
            │              │    5a. valid: git mv to topics/<t>/<slug>.md
            │              │        update kb_notes, INSERT kb_relations
            │              │        commit `curator(<scope>): promote <slug>`
            │              │    5b. invalid: git mv to _inbox/_needs-review
            │              │        commit `curator: review <reason>`
            │              │    6. regenerate _index/{recent,conflicts}.md
            │              │       and _index/topics/<t>.md for affected
            │              │       topics
            └──────────────┘
```

### Curator implementation choices (from research)

- **Model**: `qwen2.5:14b-instruct-q4_K_M` first, `qwen2.5:7b-instruct`
  fallback if VRAM is contested by the embedding model. Llama 3.1 8B
  as second fallback (English-only). Avoid thinking-mode models for
  JSON output.
- **Structured output**: OpenAI-compatible `/v1/chat/completions` with
  `response_format = {"type":"json_schema","json_schema":...}`. Ollama
  compiles to GBNF and hard-constrains at sampling time. Embed the same
  schema text in the system prompt as belt-and-suspenders.
- **Prompt shape**: terse role + enumerated enum values + 2–4 few-shot
  examples + the candidate note + K nearest neighbours in XML-tagged
  blocks. `temperature=0`, finite `max_tokens` ≈ 1.5× expected JSON.
- **Validation**: Pydantic decode + enum check + supersedes-target
  existence check. Single retry with the validation error appended to
  the prompt. On second failure, route to `_needs-review/`.
- **Closed vocabularies** (enforced at validate-time):
  - `type`: `lesson | decision | note | fact`
  - `scope`: `personal | work | public | project:<repo> | agent:<name>`
  - `topic`: closed list seeded from initial topics (`python`, `kotlin`,
    `kubernetes`, `vault`, `mcp`, `git`, `obsidian`, `rabbitmq`,
    `postgres`, …). New topics require an explicit human-or-LLM-promoted
    add to `_index/_topics.yaml`. Prevents tag drift.

---

## LightRAG integration

Source: HKUDS/LightRAG, current `lightrag-hku` v1.4.16. Backed by
Postgres + pgvector + Apache AGE — same Postgres knowledge-api already
uses. The recommended deploy is the LightRAG REST server, not the
embedded Python lib.

### Roles

- Worker publishes new captures to LightRAG via the REST server's
  `insert` endpoint at the same time it writes the inbox file.
- Curator publishes promoted notes (re-insert with the curated body +
  metadata) so LightRAG re-extracts entities/relations.
- knowledge-api `recall` becomes a hybrid: query LightRAG's `mix` mode
  (entity + community + vector) first; fall back to Postgres FTS only
  if the LightRAG endpoint is unreachable. The existing `RecallRepository`
  becomes the fallback path.

### Caveats from research worth carrying forward

- AGE has known perf cliffs over ~10⁵ nodes; we expect ~10⁴ at peak.
  Pin LightRAG to a version known to migrate cleanly (the v1.4.9 → next
  migration broke a 17h downtime case). Watch issue #2255.
- LightRAG maintainers state "minimum 32B parameter LLM for extraction".
  With 14B local, the graph will be noisier. Mitigate by enabling
  `ENABLE_LLM_CACHE_FOR_EXTRACT=true` and gleaning loops.
- Ollama v0.13.0–0.13.2 broke embeddings. Pin elsewhere.
- If extraction quality is poor against local 14B, fall back to `naive`
  (pure pgvector) mode — at ~10⁴ short notes a clean vector index often
  beats a noisy graph anyway.

---

## Knowledge graph

Closed relation set (per PKM research; more is noise):

| predicate      | direction     | meaning                                                         |
| -------------- | ------------- | --------------------------------------------------------------- |
| `supersedes`   | newer → older | newer note retires older; older's confidence×0.2                |
| `refines`      | newer → older | newer narrows the older's claim; both keep full confidence      |
| `contradicts`  | a ↔ b         | symmetric; routes to `_index/conflicts.md` for human resolution |
| `see_also`     | a ↔ b         | symmetric; surfaced via `knowledge.relations`                   |
| `derived_from` | a → source    | source can be another note id or a `url:`                       |

Writes:

- `supersedes` / `refines` / `contradicts` — curator-only, validated.
- `see_also` — curator from top-K embedding neighbours over a threshold.
- `derived_from` — capture-side (caller passes parent_id) and curator
  (detects `[[wikilink-to-existing]]` in body).

New MCP tool `knowledge.relations(id, depth=1)` walks the graph. The
existing `find_conflicts` becomes a degenerate query.

---

## Migration of existing 19 captures

One-shot k8s `Job` that runs the curator pipeline against every row in
`kb_notes` whose `vault_path` starts with `notes/`, then `rm -rf notes/`.
Rate-limited to 1 req/sec to Ollama. Failed classifications land in
`_inbox/_needs-review/` for human review. Job logs the mapping to
`docs/private/knowledge-vault-migration-2026-05-18.md` for audit.

---

## Three PRs — what lands in each

### PR 1 — restructure (worker → inbox + migration only)

- New `SCHEMA.md` reflecting the topics/projects/agents layout.
- Worker: change `VaultGitWriter._relative_path` to write
  `_inbox/<YYYY-MM-DD>/<HHMMSS>-<slug>--<id8>.md`. Commit msg becomes
  `worker(inbox): <type> <slug>`.
- Worker: title-derived kebab slug instead of ULID filename.
- Migration Job manifest that moves the existing 19 `notes/` files into
  `_inbox/2026-05-18/` so the curator (next PR) can re-process them.
  No classification in this PR — just the move.
- knowledge-api `CaptureMcpTools.kt`: drop the `project:<repo>` example
  in favour of describing the topic/project split.
- knowledge-api `RecallRepository`: scope-default = caller's scope ∪
  topics-visible-to-everyone. Existing scope-as-string stays.
- Worker tests updated for the new path scheme.
- Dotfiles (~/.claude/hooks/, ~/.claude/skills/) refreshed to reflect
  the new conventions.
- CLAUDE.md (this repo) gets a short pointer to this redesign doc.

### PR 2 — knowledge-curator service

- New `services/knowledge-curator/` Python service.
- pyproject deps: `httpx`, `pika`, `psycopg[binary,pool]`, `pgvector`,
  `pydantic`, `gitpython`, `structlog`, `opentelemetry-distro`,
  testcontainers for tests.
- CronJob manifest, every 5 min; shares PVC with worker.
- Polls `_inbox/`, runs the classify-validate-promote loop above.
- Tests: real Postgres + git via Testcontainers; Ollama mocked via a
  WireMock-style HTTP fake serving JSON schema responses.
- knowledge-api: new `knowledge.relations(id, depth)` MCP tool.
- knowledge-api: `kb_relations` writer endpoint (POST `/internal/relations`)
  the curator uses; bearer-protected.
- Initial topic seed list committed under
  `platform/cluster/flux/apps/knowledge/knowledge-curator/topics.yaml`.

### PR 3 — LightRAG + indexes + closed-vocab enforcement

- LightRAG REST server Deployment (`platform/cluster/flux/apps/knowledge/lightrag/`).
- Worker + curator forward inserts to LightRAG.
- knowledge-api `RecallRepository` becomes a fallback; primary path
  delegates to LightRAG `mix` mode.
- Curator regenerates `_index/recent.md`, `_index/conflicts.md`,
  `_index/topics/<topic>.md` on each pass.
- knowledge-api enforces the closed topic vocabulary at capture time:
  unknown `topic` value → reject with `INVALID_PARAMS` and surface the
  allowed-list error message.
- CLAUDE.md adds a short ops section: AGE perf caveats, Ollama version
  pin, LightRAG version pin, how to add a new topic.

---

## Open questions (now decided)

1. ~~Schedule vs filesystem event for curator~~ → **5-min CronJob**.
   Filesystem watch on a shared PVC is fragile; latency tolerance is
   minutes, not seconds.
2. ~~Curator's LLM creds~~ → **in-cluster Ollama, no external creds
   needed**. Aligns with "use local AI".
3. ~~Supersedes confidence threshold~~ → applied auto if curator
   confidence ≥ 0.75 AND a single closest neighbour beats the next by
   ≥0.15. Below either, queue to `_index/conflicts.md`.
4. ~~LiveSync vs `git mv` race~~ → curator pulls + rebases right before
   `git mv` and pushes immediately. LiveSync's CouchDB→git path runs
   every 5 min via the sidecar; conflict probability is low. Accept that
   conflicts may appear in `_index/conflicts.md` and resolve manually.
5. ~~Backfill velocity~~ → 19 notes is trivial; rate-limit anyway
   to 1 req/sec so the curator doesn't burst.
6. ~~`agent:_shared` scope semantics~~ → visible to every session's
   agent by default; the SessionStart hook surfaces top notes under
   that scope alongside project-scoped recent.

---

## Sources

Research delegated to two background agents. Headline conclusions:

**PKM**: PARA-style folders + Zettelkasten relations; atomic ~200–600
words; slug filename + ULID frontmatter; closed tag vocab; 4–6 typed
relations; inbox processed within 48h; auto-indexes for mechanical
groupings only.
[Matuschak](https://notes.andymatuschak.org/Evergreen_notes_should_be_atomic),
[zettelkasten.de](https://zettelkasten.de/atomicity/guide/),
[Forte](https://fortelabs.com/blog/a-complete-guide-to-tagging-for-personal-knowledge-management/),
[Enterprise Knowledge taxonomy→ontology](https://enterprise-knowledge.com/from-taxonomy-to-ontology/).

**LightRAG/Ollama**: `lightrag-hku` v1.4.16 active but rough; REST
server > Core lib; Postgres+pgvector+AGE works; AGE perf risk at scale;
`qwen2.5:14b-instruct-q4_K_M` first choice; Ollama `format=<schema>`
is GBNF-grammar-constrained (hard); pin Ollama away from 0.13.0–0.13.2;
embed nomic-embed-text 768d.
[HKUDS/LightRAG](https://github.com/HKUDS/LightRAG),
[Ollama structured outputs](https://ollama.com/blog/structured-outputs),
[LightRAG embedding bug #2495](https://github.com/HKUDS/LightRAG/issues/2495),
[AGE migration pain #2255](https://github.com/HKUDS/LightRAG/issues/2255).
