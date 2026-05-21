# Architecture

Code Graph is split into a small engine core, parser adapters, storage adapters, and an optional Spring Boot runtime.

## Dependency Direction

```text
code-graph-app
  -> code-graph-spring-boot-starter
      -> code-graph-engine
      -> code-graph-spi
      -> storage modules
      -> parser modules through ServiceLoader

code-graph-engine
  -> code-graph-model

code-graph-parser-java-jdt
  -> code-graph-model
  -> code-graph-spi

code-graph-storage-*
  -> code-graph-engine repository interfaces
  -> code-graph-model
```

The engine does not depend on Neo4j, Memgraph, Apache AGE, Spring MVC controllers, UI modules, wiki generation, or RAG logic.

## Core Concepts

`code-graph-model` defines the public graph data:

- `CodePackage`
- `CodeUnit`
- `CodeFunction`
- `CodeRelationship`
- `CodeEndpoint`
- `GraphDelta`
- `ParseRequest`

`GraphDelta` is the handoff format between parsers and the engine. A parser can be Java/JDT, another JVM parser, or an external process.

## Parser Boundary

A parser receives a `ParseRequest` and returns a `GraphDelta`.

The Java parser uses Eclipse JDT. Other languages should not reuse Java internals. They should either:

- implement `CodeGraphParser` directly, or
- run as an external process through `code-graph-parser-process`.

## Engine Boundary

`code-graph-engine` applies graph changes. It owns:

- placeholder versus non-placeholder merge decisions
- insert/update planning
- delete and cascade update behavior
- endpoint matching relationship persistence
- repository-facing domain rules

Repository implementations should stay simple. They execute reads and writes; they should not own graph merge policy.

## Storage Boundary

Storage modules implement the repository interfaces from the engine application layer.

Current adapters:

- memory
- Neo4j
- Memgraph
- Apache AGE

The starter wires one adapter based on `code-graph.storage.type`.

## Runtime Boundary

`code-graph-spring-boot-starter` is the integration layer. It creates the `IncrementalUpdateService`, loads parser implementations through ServiceLoader, and connects the engine to repositories.

`code-graph-app` is only a runnable local entrypoint and demo API. Product systems can depend on the starter directly instead of using the app.
