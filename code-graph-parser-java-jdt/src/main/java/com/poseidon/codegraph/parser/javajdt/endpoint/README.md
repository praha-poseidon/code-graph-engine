# Endpoint Parsing

Graph-engine no longer owns a YAML endpoint rule engine. Endpoint extraction uses the shared **static-extract-java** modules (latest):

1. `static-extract-java-core` parses SER text into rule models (`AntlrSerRuleParser`).
2. `static-extract-java-jdt` executes those rules on JDT AST nodes (`DefaultJdtStaticExtractEngine`).
3. `StaticExtractEndpointMapper` maps extract results to graph endpoint classes.

## Runtime Flow

```text
ParseRequest.ruleSources (+ optional externalValues)
  -> EndpointParsingService (AntlrSerRuleParser)
  -> DefaultJdtStaticExtractEngine
  -> StaticExtractResult
  -> StaticExtractEndpointMapper
  -> CodeEndpoint / HttpEndpoint / MqEndpoint / RedisEndpoint / DbEndpoint
```

## Rules: caller-supplied only

**The engine has no built-in SER rules** (no classpath `static-extract/` resources, no default Spring/RestTemplate rules). Without caller-supplied SER text, endpoint extraction yields nothing.

Pass SER via:

- `ParseRequest.ruleSources` (process / SPI parse path)
- `EndpointParsingService` constructors / `setRuleSources(...)`
- `CreateFileNodesRequest.serRuleSources` (HTTP app API)

`traceRuleSources` is legacy. Standalone `.trace` documents are not loaded. Put value-trace patches inside the same rule with an embedded `trace { ... }` block (see RestTemplate example below). If a “trace” source actually contains a full `rule ...`, it is still accepted as a rule document.

External config dictionaries are **per call** via `externalValues` / `MapExternalValueResolver` (e.g. namespace `config` for `@Value` placeholder lookup).

## SER shape

Each source string may contain one or more `rule "..." ...` documents. A new document starts at a line beginning with `rule `. Embedded `trace { }` stays with its rule.

Spring MVC inbound:

```ser
rule "Spring MVC HTTP Inbound"
endpoint HTTP inbound

find method
when annotation @*Mapping on method

let basePath =
  from annotation @RequestMapping on class take attr(value)
  from annotation @RequestMapping on class take attr(path)
  fallback ""
let methodPath =
  from annotation @*Mapping on method take attr(value)
  from annotation @*Mapping on method take attr(path)
  fallback ""
let httpMethod =
  from annotation @*Mapping on method take name
  map {
    GetMapping: GET
    PostMapping: POST
    PutMapping: PUT
    DeleteMapping: DELETE
    PatchMapping: PATCH
    RequestMapping: GET
  }

build {
  httpMethod: httpMethod
  path: concat(basePath, methodPath) | normalize slash | normalize pathVariable
}
```

RestTemplate outbound with embedded config trace:

```ser
rule "RestTemplate HTTP Outbound"
endpoint HTTP outbound

find call RestTemplate.[getForObject,getForEntity,postForObject,postForEntity,put,delete]

let rawUrl =
  from argument[0] take value

let httpMethod =
  from method take name
  map {
    getForObject: GET
    getForEntity: GET
    postForObject: POST
    postForEntity: POST
    put: PUT
    delete: DELETE
  }

build {
  httpMethod: httpMethod
  path: rawUrl | normalize extractPath | normalize pathVariable
}

trace {
  from field
  when annotation @Value on field

  let rawValue =
    from annotation @Value on field take attr(value)

  build {
    namespace: "config"
    lookup: rawValue | normalize placeholderLookup
    default: rawValue | normalize placeholderDefault
  }

  from call
  when method Environment.getProperty

  let configLookup =
    from argument[0] take value

  build {
    namespace: "config"
    lookup: configLookup
  }
}
```

Vocabulary and more examples: `static-extract-java` repo (`jdt/vocabulary.md`, `examples/`).

## Graph Mapping

Static extract does not validate endpoint business types. It emits:

- `endpoint.type`, for example `HTTP`
- `endpoint.direction`, for example `inbound`
- `build` fields, for example `httpMethod` and `path`

`StaticExtractEndpointMapper` is the graph boundary. It chooses the graph endpoint class from `endpoint.type`, then applies `build` fields by matching graph model setter names.
