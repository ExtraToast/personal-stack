# Data Model: Scheduled KB Curation Passes

These are operational concepts, not persisted schemas (the KB schema is owned by knowledge-api).

## Curation pass

| Field | Tier-1 (daily) | Tier-2 (weekly) |
| --- | --- | --- |
| cadence | `13 4 * * *` (daily 04:13 UTC) | `0 5 * * 1` (Mon 05:00 UTC) |
| model | `haiku` | `sonnet` |
| max turns | 24 | ~60 |
| max notes inspected | 25 | 40 |
| deadline (`activeDeadlineSeconds`) | 600 | 1800 |
| read tools | `list_recent`, `recall` | + `find_conflicts`, `relations`, `get_note` |
| write surface | none (dry-run) / additive metadata (apply) | knowledge-vault branch + PR only |
| apply posture | `KB_CURATOR_APPLY` (0 dry-run / 1 additive) | always propose-via-PR |

## Triage classification (Tier-1)

One of: `KEEP` (durable; propose scope + ≤3 tags) · `DISCARD` (low-signal; `_discard-candidate` tag) · `DUP` (`_dup-candidate:<ulid>` tag) · `UNSURE` (`_needs-review` + reason). All write actions are additive metadata; none deletes.

## Candidate tags (reversible markers — the max autonomous lossy action)

`_discard-candidate` · `_dup-candidate:<ulid>` · `_archive-candidate` · `_needs-review`. A human or a Tier-2 PR confirms them; removing the tag reverses the proposal.

## Idempotency stamp

`curated:<tier>:<yyyy-mm-dd>` applied to every processed note. A pass skips any note already stamped for the current cycle (NOOP), so re-runs are safe.

## Consolidation PR (Tier-2)

- Branch: `curator/weekly-<yyyy-mm-dd>` on `ExtraToast/knowledge-vault`.
- Contents: staged merges (survivor + losers `supersedes:` survivor), tag/slug renames (observed in-use only), rollup canonical notes, `_archive-candidate` confidence-decay edits, and a `_needs-review` conflict digest.
- Invariants: exactly one branch/PR per run (or none if empty); never auto-merged; no direct KB deletion.
