# Relationship contract

Parsers identify source-language relationships and return them in `GraphDelta`.
Engine validates their endpoints and persists them without interpreting
language-specific semantics.

Each relationship carries:

- `relationshipType`: the exact persisted edge name;
- `language`: the source language, so relationship names do not need language prefixes;
- `fromNodeType` and `toNodeType`: the graph labels of both endpoints.

## Shared vocabulary

The cross-language protocol owns only relationships whose meaning is genuinely
shared: `CALLS`, `RENDERS`, package/unit/function containment, endpoint binding,
and `MATCHES`.

Engine cascade processing follows direct `CALLS` only. It does not interpret
inheritance, interface satisfaction, overriding, embedding, or shadowing.

## Language vocabulary

| Language | Persisted relationship types |
| --- | --- |
| Java | `EXTENDS`, `IMPLEMENTS`, `OVERRIDES` |
| Go | `EMBEDS`, `SATISFIES`, `SATISFIES_METHOD`, `SHADOWS` |
| JavaScript | `EXTENDS`, `OVERRIDES` when statically present |
| TypeScript | `EXTENDS`, `IMPLEMENTS`, `OVERRIDES` |
| Python | `INHERITS`, `CONFORMS`, `OVERRIDES` |
| PHP | `EXTENDS`, `IMPLEMENTS`, `OVERRIDES`, `USES_TRAIT` |
| Kotlin | `INHERITS`, `IMPLEMENTS`, `OVERRIDES` |
| Swift | `INHERITS`, `CONFORMS`, `REFINES`, `OVERRIDES`, `WITNESSES` |

Go does not have inheritance or overriding. `SATISFIES_METHOD` connects a
concrete method to an interface method it satisfies; `SHADOWS` records an outer
method hiding a promoted method from an embedded type.

Swift keeps protocol conformance separate from class inheritance. `WITNESSES`
connects a concrete method to the protocol requirement it satisfies, including
requirements inherited through protocol refinement.

## Adding a relationship

1. Define the source-language name in its parser or adapter.
2. Emit the name, language, and endpoint node types.
3. Add a source fixture with exact positive and confusing negative cases.
4. Assert parser output and persisted graph read-back.
5. Do not add the language relationship to Engine branching logic.

## Semantic verification matrix

Parser tests are language contracts, not one shared Java-shaped fixture. Every contract must run
the real CLI, preserve the production one-file request order, apply through Engine, and read the
persisted graph. The common harness may compare nodes and edges, but the source oracle is owned by
the language:

| Language | Required positive cases | Required confusing negative cases |
| --- | --- | --- |
| Java | generic superclass, interface, override, interface/base dispatch | same simple name; unresolved dependency must not become `Object` |
| Go | implicit satisfaction, pointer receiver, embedding, promoted method shadowing, cross-package interface | partial method set; promoted method is not a declared override |
| TypeScript | explicit heritage, import alias/re-export, resolved call, optional/conditional call | same-named imported type; dynamic property dispatch |
| JavaScript | prototype/class inheritance and statically resolved calls | no fabricated interface relation; dynamic property dispatch |
| Python | imported base, `Protocol` conformance when statically justified, override | unrelated duck-typed same-name method |
| PHP | interface extension, class inheritance, trait use, override | private base method; unrelated same-name method |
| Kotlin | interface inheritance, class implementation, override, Java interop | unrelated same-name method; unresolved classpath target |
| Swift | class inheritance, protocol refinement/conformance, witness, override | conformance is not `IMPLEMENTS`; witness is not `OVERRIDES` |

Passing a CLI smoke test or validating `GraphDelta` is only parser-level diagnosis. Completion
requires the source oracle to match an Engine-persisted snapshot; a Neo4j test skipped because the
backend or language runtime is absent remains explicitly unverified.

## Session failure contract

A task-scoped parser session is fail-closed. Timeout, invalid JSON, invalid `GraphDelta`, broken
stdio, interruption, or an unexpected parser exit must make the session unusable and terminate the
parser process tree, including language servers such as `gopls`. Worker cleanup runs in `finally`;
failure of `session.close()` must not prevent clone/cache cleanup.
