# Code Graph

Code Graph is a Java code graph engine. It parses source code into a common graph model, applies incremental updates, and writes the result through replaceable storage adapters.

Code Graph 是一个 Java 代码图谱引擎。它把源码解析成统一图模型，处理增量更新，并通过可替换的存储适配器写入图数据。

## What It Does

- Parses Java source with Eclipse JDT.
- Builds packages, code units, functions, call relationships, inheritance relationships, implementation relationships, override relationships, and endpoints.
- Applies file-level add, update, and delete changes into an existing graph.
- Keeps storage replaceable through repository interfaces.
- Supports memory, Neo4j, Memgraph, and Apache AGE storage modules.
- Exposes a Spring Boot starter and a runnable app for local verification.
- Allows external parsers for other languages through the parser SPI or process protocol.

## Modules

| Module | Purpose |
| --- | --- |
| `code-graph-model` | Common graph model, endpoint model, delta model, and ID helpers. |
| `code-graph-spi` | Parser extension interfaces and ServiceLoader registry. |
| `code-graph-parser-java-jdt` | Java parser based on Eclipse JDT, including Java endpoint extraction through SER rules. |
| `code-graph-parser-process` | Adapter for external language parsers running as local processes. |
| `code-graph-engine` | Domain logic for graph merge, incremental update, placeholder handling, and cascade changes. |
| `code-graph-storage-memory` | In-memory repository implementation for tests and local demos. |
| `code-graph-storage-neo4j` | Neo4j repository implementation. |
| `code-graph-storage-memgraph` | Memgraph storage module. |
| `code-graph-storage-apache-age` | Apache AGE storage module for PostgreSQL graph extension. |
| `code-graph-spring-boot-starter` | Spring Boot integration layer. It wires the engine, parser registry, and repositories. |
| `code-graph-app` | Runnable demo app with REST APIs. |

## Quick Start

Requirements:

- JDK 21
- Maven 3.9+

Build and test:

```bash
mvn test
```

Run the demo app with memory storage:

```bash
mvn spring-boot:run -pl code-graph-app
```

Health check:

```bash
curl http://localhost:8084/api/code-graph/health-check
```

Parse one Java file:

```bash
curl -X POST http://localhost:8084/api/code-graph/files/nodes \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "my-project",
    "absoluteFilePath": "/absolute/path/to/my-project/src/main/java/com/example/UserController.java",
    "projectFilePath": "src/main/java/com/example/UserController.java",
    "gitRepoUrl": "https://github.com/example/my-project.git",
    "gitBranch": "main",
    "sourcepathEntries": [
      "/absolute/path/to/my-project/src/main/java"
    ],
    "classpathEntries": [
      "/absolute/path/to/my-project/target/classes",
      "/absolute/path/to/my-project/target/dependency/spring-web-6.1.0.jar"
    ]
  }'
```

`absoluteFilePath` is used to read the file from disk.
`projectFilePath` is the path stored in graph nodes and should be relative to the project root.
`sourcepathEntries` are source directories.
`classpathEntries` are compiled class directories or dependency jars used by JDT type binding.

Java parsing can still read syntax when classpath is incomplete, but type-accurate relationships need the project classes and dependency jars.

## SER Rules

Endpoint extraction rules can be passed by the caller instead of being hard-coded in the engine. Use `serRuleSources` for new integrations. A single SER source string may contain endpoint extraction rules and trace rules together.

Example request field:

```json
{
  "serRuleSources": [
    "rule spring mvc inbound\nfrom method\nwhen annotation @GetMapping on method\nlet path = from annotation on method @GetMapping take attr(value)\nbuild {\n  kind: \"http\"\n  direction: \"inbound\"\n  method: \"GET\"\n  path: path\n}\n\ntrace spring value\nfrom field\nwhen annotation @Value on field\nlet rawValue = from annotation on field @Value take attr(value)\nbuild {\n  namespace: \"config\"\n  key: rawValue | normalize placeholderKey\n  default: rawValue | normalize placeholderDefault\n}"
  ]
}
```

The parser maps built fields into endpoint properties. The graph engine itself does not decide which endpoint kinds are valid; it stores what the parser emits through the common model.

## Storage

The app defaults to memory storage:

```yaml
code-graph:
  storage:
    type: memory
```

Use another adapter by changing `code-graph.storage.type` and providing the database connection properties required by that module.

Supported values in this repository:

- `memory`
- `neo4j`
- `memgraph`
- `apache-age`

## Parser Extension

For a JVM parser, implement `CodeGraphParser` and expose it through Java `ServiceLoader`.

For a non-JVM parser, use `code-graph-parser-process`: the external process receives a parse request and returns `GraphDelta` data. The engine only consumes the common delta model, so other languages do not need to depend on JDT.

## Current Boundary

This repository is the engine layer. It does not include the UI, wiki generation, RAG workflows, or product-specific orchestration. Those can be built on top of this engine by calling the starter or app API.

## License

MIT
