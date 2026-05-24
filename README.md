# Code Graph

Code Graph is a pluggable code graph engine for turning source code into queryable graph data. It focuses on project-scale static analysis, incremental updates, endpoint extraction, and replaceable graph storage.

Code Graph 是一个可插拔的代码图谱引擎，用来把源码转换成可查询、可存储、可增量更新的图数据。它重点解决项目级静态分析、增量更新、端点提取和图数据库适配问题。

## Why

Most code intelligence products need the same foundation:

- parse source code into stable graph nodes
- connect classes, methods, calls, inheritance, implementation, overrides, and endpoints
- update the graph when files change
- write the result into a graph database
- let different language parsers plug into the same graph engine

Code Graph provides this foundation as an engine instead of a closed application.

很多代码智能产品都需要同一套基础能力：

- 把源码解析成稳定的图节点
- 连接类、方法、调用、继承、实现、重写和端点
- 文件变化后增量更新图谱
- 写入不同图数据库
- 让不同语言的解析器接入同一个图引擎

Code Graph 提供的是这套基础引擎，而不是一个封闭应用。

## What You Can Build With It

- code understanding and dependency analysis
- API endpoint inventory and endpoint matching
- impact analysis for code changes
- architecture visualization
- code search and graph retrieval for agents
- cross-service relationship analysis
- custom static extraction pipelines

## Current Capabilities

- Java parsing based on Eclipse JDT.
- Package, class, interface, enum, annotation, and method nodes.
- Call relationships.
- Inheritance, implementation, and override relationships.
- Endpoint extraction through external SER rules.
- File-level add, update, and delete handling.
- Placeholder merge rules for external or unresolved nodes.
- Common `GraphDelta` model for parser-to-engine integration.
- Parser SPI for JVM parsers.
- Process adapter for non-JVM language parsers.
- Storage adapters for memory, Neo4j, Memgraph, and Apache AGE.
- Spring Boot starter and runnable demo app.

## Repository Structure

| Module | Purpose |
| --- | --- |
| `code-graph-model` | Common graph model, endpoint model, delta model, and ID helpers. |
| `code-graph-spi` | Parser extension interfaces and ServiceLoader registry. |
| `code-graph-parser-java-jdt` | Java parser based on Eclipse JDT, including endpoint extraction through SER rules. |
| `code-graph-parser-process` | Adapter for external parsers running as local processes. |
| `code-graph-engine` | Domain logic for graph merge, incremental update, placeholder handling, and cascade changes. |
| `code-graph-storage-memory` | In-memory repository implementation for tests and local demos. |
| `code-graph-storage-neo4j` | Neo4j repository implementation. |
| `code-graph-storage-memgraph` | Memgraph storage module. |
| `code-graph-storage-apache-age` | Apache AGE storage module for PostgreSQL graph extension. |
| `code-graph-spring-boot-starter` | Spring Boot integration layer. |
| `code-graph-app` | Runnable demo app with REST APIs. |

## Quick Start

Requirements:

- JDK 21
- Maven 3.9+

Clone and test:

```bash
git clone https://github.com/praha-poseidon/code-graph.git
cd code-graph
mvn test
```

Start the demo app with memory storage:

```bash
mvn spring-boot:run -pl code-graph-app
```

Check the service:

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

The app currently returns whether the write succeeded. During local verification, parsing details are visible in logs. A small debug/query API is planned so users can inspect graph data directly after parsing.

当前 App 会返回写入是否成功。做本地验证时，解析出的节点、关系和端点数量可以从日志中看到。memory storage 模式下也提供了轻量调试查询 API，让用户在解析后直接查看图数据。

Inspect parsed data when using memory storage:

使用 memory storage 时查看解析结果：

```bash
curl http://localhost:8084/api/code-graph/debug/projects/my-project/graph
curl http://localhost:8084/api/code-graph/debug/projects/my-project/nodes
curl http://localhost:8084/api/code-graph/debug/projects/my-project/endpoints
curl http://localhost:8084/api/code-graph/debug/projects/my-project/relationships
```

These debug APIs are only registered when the in-memory repository is active. Production graph databases should be queried through their own tooling or a product-level query API.

这些调试 API 只会在内存仓库启用时注册。生产图数据库建议通过数据库自身工具或产品层查询 API 查看。

## Inputs Explained

| Field | Meaning |
| --- | --- |
| `projectName` | Logical project name used for graph isolation. |
| `absoluteFilePath` | Real local file path used to read source code. |
| `projectFilePath` | Stable path relative to the project root, stored in graph data. |
| `gitRepoUrl` | Repository URL stored as metadata. |
| `gitBranch` | Branch name stored as metadata. |
| `sourcepathEntries` | Source directories, usually `src/main/java`. |
| `classpathEntries` | Compiled class directories and dependency jars used by JDT type binding. |

Java parsing can still read syntax when classpath is incomplete, but type-accurate relationships need the project classes and dependency jars.

## Frontend Parser Integration

Code Graph can also consume a frontend parser through `code-graph-parser-process`. The frontend parser runs as a local CLI, receives `ParseRequest` on stdin, returns `GraphDelta` on stdout, and the Java engine writes that delta through the same storage pipeline.

Code Graph 也可以通过 `code-graph-parser-process` 接入前端解析器。前端解析器作为本地 CLI 运行，从标准输入接收 `ParseRequest`，从标准输出返回 `GraphDelta`，Java engine 再通过同一套存储链路写入图谱。

Build the frontend parser:

构建前端解析器：

```bash
cd /path/to/code-graph-parser-js
npm install
npm run build
```

Start the app with the TypeScript process parser enabled:

启动 app，并启用 TypeScript 外部进程解析器：

```bash
mvn spring-boot:run -pl code-graph-app \
  -Dcodegraph.parser.process.languages=typescript \
  -Dcodegraph.parser.process.typescript.command="node '/path/to/code-graph-parser-js/dist/cli.js' --stdio"
```

Then parse a `.tsx` or `.ts` file through the normal app API:

然后通过普通 app API 解析 `.tsx` 或 `.ts` 文件：

```bash
curl -X POST http://localhost:8084/api/code-graph/files/nodes \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "frontend-demo",
    "absoluteFilePath": "/absolute/path/to/react-app/src/pages/UserPage.tsx",
    "projectFilePath": "src/pages/UserPage.tsx",
    "gitRepoUrl": "https://github.com/example/react-app.git",
    "gitBranch": "main",
    "sourcepathEntries": [
      "/absolute/path/to/react-app/src"
    ],
    "classpathEntries": []
  }'
```

The app infers `typescript` from `.ts` and `.tsx`, calls the configured CLI, and stores frontend modules, functions, outbound HTTP endpoints, imports, render relationships, hook usage, and calls.

App 会根据 `.ts` 和 `.tsx` 推断语言为 `typescript`，调用已配置的 CLI，并写入前端模块、函数、出站 HTTP 端点、导入关系、组件渲染关系、Hook 使用关系和调用关系。

## Endpoint Rules

Endpoint extraction is rule-driven. The caller can pass SER rule text directly through `serRuleSources`, so rules may come from a file, database, config service, or agent-generated output.

端点提取是规则驱动的。调用方可以通过 `serRuleSources` 直接传入 SER 规则文本，所以规则可以来自文件、数据库、配置中心，也可以由 Agent 生成。

Example:

```json
{
  "serRuleSources": [
    "rule \"Custom HTTP Inbound\"\nendpoint HTTP inbound\n\nfind method with annotation @RouteGet\n\nlet httpMethod =\n  from literal GET take value\n\nlet path =\n  from annotation on method @RouteGet take attr(value)\n\nbuild {\n  httpMethod: httpMethod\n  path: path | normalize slash | normalize pathVariable\n}\n\ntrace \"Spring Value\"\nfrom field\nwhen annotation @Value on field\n\nlet rawValue =\n  from annotation on field @Value take attr(value)\n\nbuild {\n  namespace: \"config\"\n  key: rawValue | normalize placeholderKey\n  default: rawValue | normalize placeholderDefault\n}"
  ]
}
```

The engine stores what the parser emits through the common model. It does not hard-code which endpoint kinds are valid.

## Storage

The app defaults to memory storage:

```yaml
code-graph:
  storage:
    type: memory
```

Supported storage types:

- `memory`
- `neo4j`
- `memgraph`
- `apache-age`

Use another adapter by changing `code-graph.storage.type` and providing the database connection properties required by that module.

## Extending Parsers

For a JVM parser, implement `CodeGraphParser` and expose it through Java `ServiceLoader`.

For a non-JVM parser, use `code-graph-parser-process`: the external process receives a parse request and returns `GraphDelta` data. The engine consumes the common delta model, so other languages do not need to depend on JDT.

## Project Boundary

This repository is the engine layer. It does not include UI, wiki generation, RAG workflows, or product-specific orchestration. Those can be built on top of this engine through the starter or app API.

## Roadmap

- More ready-to-use SER examples.
- Better documentation for custom parser authors.
- More end-to-end examples with Neo4j and Apache AGE.
- Packaging for easier local installation.

## License

MIT
