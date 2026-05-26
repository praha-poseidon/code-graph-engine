# Process Parser Adapter

This module lets `code-graph-core` call parsers implemented in other languages.

这个模块让 `code-graph-core` 可以调用 Go、Python、TypeScript 等非 JVM 语言实现的解析器。

The external parser protocol is intentionally small:

外部解析器协议刻意保持很小：

- stdin receives one `ParseRequest` JSON.
- 标准输入接收一个 `ParseRequest` JSON。
- stdout must return one `GraphDelta` JSON.
- 标准输出必须返回一个 `GraphDelta` JSON。
- stderr is treated as diagnostic text when the process exits with a non-zero code.
- 当进程非 0 退出时，标准错误会作为诊断信息返回。

## Configuration

## 配置

Enable one or more process parsers with JVM properties:

通过 JVM 参数启用一个或多个进程解析器：

```bash
-Dcodegraph.parser.process.languages=go,python
-Dcodegraph.parser.process.go.command="/path/to/go-parser --stdio"
-Dcodegraph.parser.process.python.command="python3 /path/to/python_parser.py"
-Dcodegraph.parser.process.timeoutSeconds=60
```

For the frontend React parser:

前端 React 解析器可以这样接入：

```bash
-Dcodegraph.parser.process.languages=typescript
-Dcodegraph.parser.process.typescript.command="node '/path/to/code-graph-parser-js/dist/cli.js' --stdio"
```

The same configuration can be supplied with environment variables:

也可以通过环境变量配置：

```bash
CODEGRAPH_PARSER_PROCESS_LANGUAGES=go,python
CODEGRAPH_PARSER_GO_COMMAND="/path/to/go-parser --stdio"
CODEGRAPH_PARSER_PYTHON_COMMAND="python3 /path/to/python_parser.py"
CODEGRAPH_PARSER_PROCESS_TIMEOUT_SECONDS=60
```

The language name must match `ParseRequest.language` and the engine language inference.

语言名必须和 `ParseRequest.language` 以及 engine 推断出的语言一致。

## Input

## 输入

The process reads a `ParseRequest` from stdin:

进程从标准输入读取 `ParseRequest`：

```json
{
  "projectName": "demo",
  "language": "go",
  "projectRoot": "/repo",
  "sourceFiles": ["/repo/main.go"],
  "sourceRoots": ["/repo"],
  "dependencies": [],
  "gitRepoUrl": "https://github.com/acme/demo.git",
  "gitBranch": "main",
  "changeType": "SOURCE_MODIFIED",
  "ruleSources": [],
  "traceRuleSources": [],
  "externalValues": {},
  "options": {
    "projectFilePath": "main.go"
  }
}
```

## Output

## 输出

The process writes a `GraphDelta` to stdout:

进程向标准输出写出 `GraphDelta`：

```json
{
  "scope": {
    "projectName": "demo",
    "language": "go",
    "gitRepoUrl": "https://github.com/acme/demo.git",
    "gitBranch": "main",
    "projectRoot": "/repo",
    "sourceFiles": ["/repo/main.go"],
    "changeType": "SOURCE_MODIFIED",
    "attributes": {}
  },
  "packages": [],
  "units": [],
  "functions": [],
  "endpoints": [],
  "relationships": [],
  "deletedNodeIds": [],
  "deletedRelationshipIds": [],
  "diagnostics": []
}
```

`GraphDelta` is the storage write protocol. Every node and relationship must have a stable `id`, `language`, `projectFilePath`, and `projectName`. Parser node IDs should be stable inside one project and should not include `projectName`; the Java engine applies project scoping before writing storage. The process adapter will stamp `projectName` from `ParseRequest` when it is missing, but parser implementations should still treat it as part of the contract.

`GraphDelta` 是写入存储的协议。每个节点和关系都必须有稳定的 `id`、`language`、`projectFilePath`、`projectName`。解析器输出的节点 ID 应该只在单个项目内稳定，不要把 `projectName` 拼进 ID；Java engine 会在写入前统一加项目 scope。process adapter 会在缺失时用 `ParseRequest` 里的 `projectName` 补齐，但解析器实现仍然应该把它当成协议字段。

Relationship `id` is required. It should be deterministic for the tuple `(fromNodeId, relationshipType, toNodeId)`, so rerunning the same parser does not create duplicate relationships.

关系的 `id` 是必填字段。它应该由 `(fromNodeId, relationshipType, toNodeId)` 稳定生成，这样同一个解析器重复运行时不会生成重复关系。

Recommended ID prefixes:

推荐的 ID 前缀：

- Package: `pkg:<qualified-package-name>`
- 包：`pkg:<qualified-package-name>`
- Unit: `unit:<qualified-type-name>`
- 类型单元：`unit:<qualified-type-name>`
- Function: `fn:<qualified-function-signature>`
- 函数：`fn:<qualified-function-signature>`
- Endpoint: `endpoint:<direction>:<type>:<stable-match-identity-hash-or-key>`
- 端点：`endpoint:<direction>:<type>:<stable-match-identity-hash-or-key>`
- Relationship: `rel:<stable-hash>`
- 关系：`rel:<stable-hash>`

Endpoint objects are polymorphic. Add `endpointKind` when returning endpoints:

端点对象是多态的。返回端点时需要增加 `endpointKind`：

```json
{
  "endpointKind": "http",
  "id": "demo:GET:/users/{id}",
  "name": "GET /users/{id}",
  "language": "go",
  "projectFilePath": "main.go",
  "endpointType": "HTTP",
  "direction": "inbound",
  "httpMethod": "GET",
  "path": "/users/{id}",
  "matchIdentity": "GET /users/{id}"
}
```

Supported `endpointKind` values are `http`, `mq`, `redis`, and `db`.

当前支持的 `endpointKind` 是 `http`、`mq`、`redis`、`db`。
