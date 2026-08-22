# Relationship contract

The graph keeps a language-native edge name and a small language-neutral
behavior contract. These are deliberately separate.

Each relationship has four contract fields:

- `relationshipType`: the exact persisted edge name owned by a parser, for
  example `GO_SATISFIES` or `TS_IMPLEMENTS`.
- `relationshipKind`: the Engine behavior category, for example `CALL`,
  `CONFORMS`, or `REFINES`.
- `fromNodeType` and `toNodeType`: the labels of the relationship endpoints.

The Engine owns only shared edge names (`CALLS`, containment, endpoint binding,
and endpoint matching). A language adapter owns every language-specific edge
name. Adding a new language edge therefore does not require editing a central
Engine enum or its storage adapters.

## Current language vocabulary

| Language | Persisted relationship types |
| --- | --- |
| Java | `JAVA_EXTENDS`, `JAVA_IMPLEMENTS`, `JAVA_OVERRIDES` |
| Go | `GO_EMBEDS`, `GO_SATISFIES`, `GO_METHOD_SATISFIES` |
| JavaScript | `JS_EXTENDS`, `JS_IMPLEMENTS`, `JS_OVERRIDES` |
| TypeScript | `TS_EXTENDS`, `TS_IMPLEMENTS`, `TS_OVERRIDES` |
| Python | `PYTHON_INHERITS`, `PYTHON_CONFORMS`, `PYTHON_OVERRIDES` |
| PHP | `PHP_EXTENDS`, `PHP_IMPLEMENTS`, `PHP_OVERRIDES` |
| Kotlin | `KOTLIN_INHERITS`, `KOTLIN_IMPLEMENTS`, `KOTLIN_OVERRIDES` |

The names intentionally preserve language meaning. For example, Go's implicit
method-set satisfaction is not stored as Java `IMPLEMENTS`.

## Engine dependency rule

Engine workflows must branch on `relationshipKind`, never on a list of
language-specific `relationshipType` values. Exact type names are only used by
queries and features that explicitly ask for a native language relationship.

The GraphDelta validator rejects any dynamic relationship that omits its kind
or endpoint labels. Storage adapters persist the raw type and the behavior
contract, and construct database edges using the supplied endpoint labels.

## Adding a relationship

1. Define the native name in the language parser or adapter.
2. Emit the native name, neutral kind, and endpoint node types.
3. Add a source fixture containing the positive relationship and a confusing
   negative case.
4. Assert both parser output and persisted graph read-back.
5. Do not add the native name to Engine switch statements.
