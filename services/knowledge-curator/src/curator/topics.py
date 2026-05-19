"""Closed topic vocabulary loaded from a YAML config map.

A closed list of topic slugs prevents tag-drift (`kotlin`, `Kotlin`,
`kt`) and keeps the on-disk folder structure stable: new topics
require an explicit edit to `topics.yaml` rather than a one-off
LLM hallucination. The curator validates the LLM-classified topic
against this list; unknown values route the note to
`_inbox/_needs-review/` instead of being silently dropped or
auto-promoted.

Schema (yaml):

    topics:
      - slug: kotlin            # required, kebab-case
        aliases: [Kotlin, kt]   # optional, normalised to slug on match
        description: ...        # optional, shown in the classifier prompt

The loader rejects duplicate slugs and duplicate aliases at parse
time so the operator notices typos at apply-time, not at the next
capture.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import psycopg
import yaml


@dataclass(frozen=True, slots=True)
class Topic:
    slug: str
    aliases: tuple[str, ...] = ()
    description: str = ""


class TopicVocabulary:
    """A closed set of valid topic slugs plus their aliases.

    Provides ``slug_for(value)`` which normalises an LLM-emitted topic
    string into its canonical slug, returning ``None`` when the value
    does not match any topic or alias.
    """

    def __init__(self, topics: list[Topic]) -> None:
        slugs: set[str] = set()
        alias_to_slug: dict[str, str] = {}
        ordered: list[Topic] = []
        for topic in topics:
            if topic.slug in slugs:
                raise ValueError(f"Duplicate topic slug: {topic.slug}")
            slugs.add(topic.slug)
            alias_to_slug[topic.slug.lower()] = topic.slug
            for alias in topic.aliases:
                key = alias.lower()
                if key in alias_to_slug:
                    existing = alias_to_slug[key]
                    if existing != topic.slug:
                        raise ValueError(
                            f"Duplicate topic alias '{alias}' (maps to "
                            f"both {existing} and {topic.slug})"
                        )
                alias_to_slug[key] = topic.slug
            ordered.append(topic)
        self._topics = tuple(ordered)
        self._alias_to_slug = alias_to_slug

    @classmethod
    def from_yaml(cls, path: Path) -> TopicVocabulary:
        data: Any = yaml.safe_load(path.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or "topics" not in data:
            raise ValueError(f"{path}: expected mapping with a 'topics' key")
        raw_topics = data["topics"]
        if not isinstance(raw_topics, list):
            raise ValueError(f"{path}: 'topics' must be a list")
        parsed = [cls._parse_topic(entry) for entry in raw_topics]
        return cls(parsed)

    @classmethod
    def from_db(cls, conn: psycopg.Connection[Any]) -> TopicVocabulary:
        """Load the active topic vocabulary from ``kb_topics`` /
        ``kb_topic_aliases``.

        Each curator pass is a fresh process (CronJob), so loading
        once at boot is enough — no caching layer. If the table is
        empty (pre-seed) the caller should fall back to the YAML
        loader; this method just returns an empty vocabulary rather
        than raising, so the fallback is plain control flow.
        """

        with conn.cursor() as cur:
            cur.execute(
                "SELECT slug, description FROM kb_topics WHERE is_active = TRUE ORDER BY slug",
            )
            rows = cur.fetchall()
            if not rows:
                return cls([])
            slugs = [str(row[0]) for row in rows]
            cur.execute(
                "SELECT slug, alias_lower FROM kb_topic_aliases WHERE slug = ANY(%s)",
                (slugs,),
            )
            alias_rows = cur.fetchall()
        aliases_by_slug: dict[str, list[str]] = {slug: [] for slug in slugs}
        for slug, alias in alias_rows:
            if slug in aliases_by_slug and alias and alias != slug:
                aliases_by_slug[slug].append(str(alias))
        parsed = [
            Topic(
                slug=str(row[0]),
                aliases=tuple(aliases_by_slug.get(str(row[0]), ())),
                description=str(row[1] or ""),
            )
            for row in rows
        ]
        return cls(parsed)

    @staticmethod
    def _parse_topic(entry: Any) -> Topic:
        if not isinstance(entry, dict) or "slug" not in entry:
            raise ValueError(f"Topic entry missing 'slug': {entry!r}")
        slug = entry["slug"]
        if not isinstance(slug, str) or not slug:
            raise ValueError(f"Topic 'slug' must be a non-empty string: {entry!r}")
        aliases_raw = entry.get("aliases", []) or []
        if not isinstance(aliases_raw, list) or not all(isinstance(a, str) for a in aliases_raw):
            raise ValueError(f"Topic 'aliases' must be a list of strings: {entry!r}")
        description = entry.get("description", "") or ""
        return Topic(slug=slug, aliases=tuple(aliases_raw), description=description)

    @property
    def topics(self) -> tuple[Topic, ...]:
        return self._topics

    @property
    def slugs(self) -> tuple[str, ...]:
        return tuple(t.slug for t in self._topics)

    def slug_for(self, value: str) -> str | None:
        """Return the canonical slug for `value`, or None if unknown."""

        return self._alias_to_slug.get(value.lower())
