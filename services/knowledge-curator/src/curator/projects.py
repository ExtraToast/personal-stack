"""Closed project vocabulary.

Mirror of [curator.topics.TopicVocabulary] for the
`project:<slug>` scope shape. Replaces the previous free-form
`project:<github-repo-name>` behaviour, which let the classifier
hallucinate non-existent repos.

Schema (yaml fallback only — DB is the source of truth):

    projects:
      - slug: personal-stack             # required, kebab-case
        aliases: [personal-stack-2, ...] # optional, normalised to slug on match
        github_org: ExtraToast           # optional, recorded for back-links
        description: ...                 # optional

Loader rejects duplicate slugs and duplicate aliases at parse-time
so a typo surfaces at apply-time, not on the next capture.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import psycopg
import yaml


@dataclass(frozen=True, slots=True)
class Project:
    slug: str
    aliases: tuple[str, ...] = ()
    github_org: str = ""
    description: str = ""


class ProjectVocabulary:
    """A closed set of valid project slugs plus their aliases.

    `slug_for(value)` normalises an LLM-emitted project string into
    its canonical slug, returning `None` when the value does not
    match any project or alias.
    """

    def __init__(self, projects: list[Project]) -> None:
        slugs: set[str] = set()
        alias_to_slug: dict[str, str] = {}
        ordered: list[Project] = []
        for project in projects:
            if project.slug in slugs:
                raise ValueError(f"Duplicate project slug: {project.slug}")
            slugs.add(project.slug)
            alias_to_slug[project.slug.lower()] = project.slug
            for alias in project.aliases:
                key = alias.lower()
                if key in alias_to_slug:
                    existing = alias_to_slug[key]
                    if existing != project.slug:
                        raise ValueError(
                            f"Duplicate project alias '{alias}' (maps to "
                            f"both {existing} and {project.slug})",
                        )
                alias_to_slug[key] = project.slug
            ordered.append(project)
        self._projects = tuple(ordered)
        self._alias_to_slug = alias_to_slug

    @classmethod
    def from_yaml(cls, path: Path) -> ProjectVocabulary:
        data: Any = yaml.safe_load(path.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or "projects" not in data:
            raise ValueError(f"{path}: expected mapping with a 'projects' key")
        raw = data["projects"]
        if not isinstance(raw, list):
            raise ValueError(f"{path}: 'projects' must be a list")
        parsed = [cls._parse_project(entry) for entry in raw]
        return cls(parsed)

    @classmethod
    def from_db(cls, conn: psycopg.Connection[Any]) -> ProjectVocabulary:
        """Load the active project vocabulary from `kb_projects` /
        `kb_project_aliases`.

        Each curator pass is a fresh process (CronJob), so loading
        once at boot is enough — no caching layer. Empty table
        returns an empty vocabulary so the caller can fall back to
        a YAML loader or accept that all `project:<...>` emissions
        route to needs-review.
        """

        with conn.cursor() as cur:
            cur.execute(
                "SELECT slug, github_org, description FROM kb_projects "
                "WHERE is_active = TRUE ORDER BY slug",
            )
            rows = cur.fetchall()
            if not rows:
                return cls([])
            slugs = [str(row[0]) for row in rows]
            cur.execute(
                "SELECT slug, alias_lower FROM kb_project_aliases WHERE slug = ANY(%s)",
                (slugs,),
            )
            alias_rows = cur.fetchall()
        aliases_by_slug: dict[str, list[str]] = {slug: [] for slug in slugs}
        for slug, alias in alias_rows:
            if slug in aliases_by_slug and alias and alias != slug:
                aliases_by_slug[slug].append(str(alias))
        parsed = [
            Project(
                slug=str(row[0]),
                aliases=tuple(aliases_by_slug.get(str(row[0]), ())),
                github_org=str(row[1] or ""),
                description=str(row[2] or ""),
            )
            for row in rows
        ]
        return cls(parsed)

    @staticmethod
    def _parse_project(entry: Any) -> Project:
        if not isinstance(entry, dict) or "slug" not in entry:
            raise ValueError(f"Project entry missing 'slug': {entry!r}")
        slug = entry["slug"]
        if not isinstance(slug, str) or not slug:
            raise ValueError(f"Project 'slug' must be a non-empty string: {entry!r}")
        aliases_raw = entry.get("aliases", []) or []
        if not isinstance(aliases_raw, list) or not all(isinstance(a, str) for a in aliases_raw):
            raise ValueError(f"Project 'aliases' must be a list of strings: {entry!r}")
        github_org = entry.get("github_org", "") or ""
        description = entry.get("description", "") or ""
        return Project(
            slug=slug,
            aliases=tuple(aliases_raw),
            github_org=github_org,
            description=description,
        )

    @property
    def projects(self) -> tuple[Project, ...]:
        return self._projects

    @property
    def slugs(self) -> tuple[str, ...]:
        return tuple(p.slug for p in self._projects)

    def slug_for(self, value: str) -> str | None:
        """Return the canonical slug for `value`, or None if unknown.

        Match is case-insensitive against slugs + aliases. The
        classifier emits things like `esa-blueshell/website` or
        `personal-stack-2` — both alias to a real repo via the
        seed migration.
        """

        return self._alias_to_slug.get(value.lower())
