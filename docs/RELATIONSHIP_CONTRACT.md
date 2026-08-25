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

Go does not have inheritance or overriding. `SATISFIES_METHOD` connects a
concrete method to an interface method it satisfies; `SHADOWS` records an outer
method hiding a promoted method from an embedded type.

## Adding a relationship

1. Define the source-language name in its parser or adapter.
2. Emit the name, language, and endpoint node types.
3. Add a source fixture with exact positive and confusing negative cases.
4. Assert parser output and persisted graph read-back.
5. Do not add the language relationship to Engine branching logic.
