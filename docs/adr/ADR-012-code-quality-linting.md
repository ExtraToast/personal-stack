# ADR-012: Code Quality & Linting

## Status

Accepted

## Date

2026-03-25

## Context

Strict code quality enforcement is a core requirement. Both TypeScript/Vue and Kotlin codebases must have rigorous,
automated quality gates that prevent low-quality code from reaching the main branch.

## Decision

### TypeScript / Vue

#### TypeScript Strict Mode

```json
{
  "strict": true,
  "noUncheckedIndexedAccess": true,
  "exactOptionalPropertyTypes": true
}
```

Maximum type safety — catches the most bugs at compile time.

#### ESLint

- **@antfu/eslint-config** as the base (Anthony Fu's opinionated preset)
- Custom overrides for project-specific strict rules on top
- Vue-specific rules enforced

#### Key Rules (Error Level)

| Rule                                     | Level         | Rationale                                     |
|------------------------------------------|---------------|-----------------------------------------------|
| no-explicit-any                          | error         | Forces proper typing                          |
| no-type-assertion (as)                   | warn          | Prefer type guards, but sometimes unavoidable |
| explicit-function-return-type (exported) | error         | Public API must be explicitly typed           |
| vue/require-emit-declaration             | error         | Explicit component contracts                  |
| vue/require-prop-types                   | error         | Props must be typed                           |
| vue/max-template-expression-complexity   | error (max 3) | Move complex expressions to computed          |

#### Formatting

- **Prettier** for all formatting (TS, Vue, CSS, JSON, Markdown)
- Prettier runs before ESLint (no conflict)

#### Zero Warnings Policy

- `pnpm lint` runs with `--max-warnings 0`
- Any ESLint warning is a CI failure

### Kotlin

#### Static Analysis

- **detekt** — code smell detection, complexity analysis
- **ktlint** — formatting enforcement

#### Strictness Thresholds

| Rule                                      | Threshold                       |
|-------------------------------------------|---------------------------------|
| Max function length                       | 30 lines                        |
| Max file length                           | 300 lines                       |
| Cyclomatic complexity                     | max 10                          |
| `!!` (non-null assertion)                 | **forbidden** (error)           |
| `var` (mutable variable)                  | **forbidden** (error) — use val |
| Explicit return types on public functions | **required**                    |

#### Constructor Injection

- Field injection (@Autowired on fields) banned
- Constructor injection only
- Enforced by detekt rule + ArchUnit test

### Shared Configuration

- **.editorconfig** at repository root — consistent indentation/encoding across all IDEs
- Applies to both Kotlin and TypeScript files

### Pre-Commit Hooks

- **Husky + lint-staged** for TypeScript/Vue (only lints staged files)
- **Gradle Git hook** for Kotlin (runs detekt + ktlint on staged files)
- Catches issues before they reach CI

## Consequences

- Very strict rules will cause friction initially — but prevent technical debt accumulation
- `var` being an error (stricter than recommended "warn") means occasional refactoring needed for inherently mutable
  state — use `lateinit` or wrapper types
- @antfu/eslint-config is opinionated — some rules may conflict with team preferences; override as needed
- Zero warnings policy means every warning must be addressed or explicitly disabled with a comment
- Pre-commit hooks add ~2-5 seconds to each commit
