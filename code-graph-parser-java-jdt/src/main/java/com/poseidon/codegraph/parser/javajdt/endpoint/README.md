# Endpoint Parsing

Graph-engine no longer owns a YAML endpoint rule engine. Endpoint extraction now uses the shared Java static extract modules:

1. `java-static-extract-core` parses `.ser` files into rule models.
2. `java-static-extract-jdt` executes those rules on JDT AST nodes.
3. `code-graph-core` maps extracted endpoint labels and fields to graph endpoint classes.

## Runtime Flow

```text
EndpointParsingService
  -> SerRuleLoader
  -> DefaultJdtStaticExtractEngine
  -> StaticExtractResult
  -> StaticExtractEndpointMapper
  -> CodeEndpoint / HttpEndpoint / MqEndpoint / RedisEndpoint / DbEndpoint
```

## Rule Files

Built-in rules live in this module:

```text
code-graph-parser-java-jdt/src/main/resources/static-extract/
```

`rules/index.txt` and `traces/index.txt` list enabled files.

`rules/index.txt` 和 `traces/index.txt` 控制哪些内置规则启用。

Current built-ins:

当前内置规则：

- Spring MVC HTTP inbound endpoints.
- Spring MVC HTTP 入站端点。
- RestTemplate HTTP outbound endpoints.
- RestTemplate HTTP 出站端点。
- Spring config trace for `@Value` and `Environment.getProperty`.
- Spring 配置追踪，支持 `@Value` 和 `Environment.getProperty`。

Built-ins are enabled by default. External SER strings are appended to built-ins unless `ParseRequest.options.includeBuiltinRules` is set to `false`.

内置规则默认启用。外部传入的 SER 字符串默认会追加到内置规则后面；如果 `ParseRequest.options.includeBuiltinRules` 设置为 `false`，则只使用外部规则。

External SER strings may contain multiple `rule` and `trace` blocks in the same text. The parser separates blocks before execution, so callers can pass one generated SER document through `ParseRequest.ruleSources` or `CreateFileNodesRequest.serRuleSources`.

外部 SER 字符串可以在同一段文本里同时包含多个 `rule` 和 `trace` 块。解析器会先按块拆开再执行，所以调用方可以把模型生成的一整份 SER 文档直接放进 `ParseRequest.ruleSources` 或 `CreateFileNodesRequest.serRuleSources`。

A Spring MVC inbound rule looks like:

Spring MVC 入站规则示例：

```ser
rule "Spring MVC HTTP Inbound"
endpoint HTTP inbound

find method with annotation @*Mapping

let basePath =
  from annotation on class @RequestMapping take attr(value)
  from annotation on class @RequestMapping take attr(path)
  default ""

let methodPath =
  from annotation on method @*Mapping take attr(value)
  from annotation on method @*Mapping take attr(path)
  default ""

let httpMethod =
  from annotation on method @*Mapping take name
  map {
    GetMapping: GET
    PostMapping: POST
  }

build {
  httpMethod: httpMethod
  path: concat(basePath, methodPath) | normalize slash | normalize pathVariable
}
```

## Graph Mapping

Static extract does not validate endpoint business types. It emits:

- `endpoint.type`, for example `HTTP`
- `endpoint.direction`, for example `inbound`
- `build` fields, for example `httpMethod` and `path`

`StaticExtractEndpointMapper` is the graph boundary. It chooses the graph endpoint class from `endpoint.type`, then applies `build` fields by matching graph model setter names.
