# Quick Start

This guide verifies the engine with the built-in app and memory storage.

## 1. Build

```bash
mvn test
```

## 2. Start The App

```bash
mvn spring-boot:run -pl code-graph-app
```

The app listens on port `8084`.

```bash
curl http://localhost:8084/api/code-graph/health-check
```

## 3. Parse A Java File

Send one file at a time:

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
      "/absolute/path/to/my-project/target/dependency"
    ]
  }'
```

Fields:

- `projectName`: logical project name used for graph isolation.
- `absoluteFilePath`: real local file path used for reading source code.
- `projectFilePath`: stable path relative to the project root, stored in graph data.
- `gitRepoUrl`: repository URL stored as metadata.
- `gitBranch`: branch name stored as metadata.
- `sourcepathEntries`: source directories, usually `src/main/java`.
- `classpathEntries`: compiled class directories and dependency jar directories/files for JDT type binding.

If classpath is missing, syntax-level parsing may still work, but type-based relationships can be incomplete.

## 4. Update Or Delete

Update one file:

```bash
curl -X PUT http://localhost:8084/api/code-graph/files/nodes \
  -H 'Content-Type: application/json' \
  -d '{ "...": "same request body as create" }'
```

Delete one file:

```bash
curl -X DELETE http://localhost:8084/api/code-graph/files/nodes \
  -H 'Content-Type: application/json' \
  -d '{
    "projectName": "my-project",
    "projectFilePath": "src/main/java/com/example/UserController.java"
  }'
```

## 5. Pass SER Rules

Use `serRuleSources` when endpoint or trace rules come from a database, config service, file, or agent-generated output.

```json
{
  "serRuleSources": [
    "rule spring mvc inbound\nfrom method\nwhen annotation @GetMapping on method\nlet path = from annotation on method @GetMapping take attr(value)\nbuild {\n  kind: \"http\"\n  direction: \"inbound\"\n  method: \"GET\"\n  path: path\n}"
  ]
}
```

`endpointRuleSources` and `traceRuleSources` are still accepted, but new integrations should prefer `serRuleSources`.
