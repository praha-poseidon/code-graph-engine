# Code Graph deployment

The distributable uses one application image plus isolated MySQL, Neo4j and Qdrant services.
This keeps database upgrades, backups and health checks independent while retaining one-command startup.

## Start

```bash
cp .env.example .env
# Replace every password and CODEGRAPH_MASTER_KEY in .env.
docker compose build app
docker compose up -d
docker compose ps
```

Open `http://localhost:8084/workbench`.

Parser CLIs are mounted from `CODEGRAPH_PARSER_HOME` into `/opt/codegraph/parsers`.

If you want the application image itself to carry one complete language package,
set `CODEGRAPH_TOOL_BUNDLE_URL` before building. It must point to a
`codegraph-tools-<language>-linux-x64.tar.gz` release asset. The bundle is
installed under `/opt/codegraph/tool-bundle` and contains the parser CLI,
static-extract CLI, Skill, `start.sh`, and `install.sh`. Configure the parser
command to use `/opt/codegraph/tool-bundle/bin/parser-<language>` (JavaScript
and TypeScript use `parser-js`). The regular parser volume remains available for
local overrides.
The image does not claim that every language CLI is installed: keep
`CODEGRAPH_PARSER_PROCESS_LANGUAGES` empty until the matching language package has been unpacked.
Then enable only that language and point its command at the mounted executable, for example:

```dotenv
CODEGRAPH_PARSER_PROCESS_LANGUAGES=java
CODEGRAPH_PARSER_JAVA_COMMAND=/opt/codegraph/parsers/bin/parser-java --stdio-stream
```

The application image can be published to GitHub Container Registry with the
`Publish workbench image` workflow. Run it manually with an image tag, or push a
`v*` tag. This publishes only the application layer; MySQL, Neo4j, Qdrant and
the other infrastructure remain the compose services.

A Go parser using `--stdio-stream` is kept alive for the lifetime of one analysis task, while files
are still applied sequentially. The same task-scoped process boundary is used for other streaming
parsers; one-shot parsers are started and released per request.

## Data

- MySQL stores repository configuration and asynchronous analysis tasks.
- Neo4j stores graph nodes and relationships.
- Qdrant is provisioned for vector features; the current graph workbench does not write vectors yet.
- Repository clones are temporary and removed after each task.

## CentOS 8.2 compatibility

The application image currently uses `centos:8.2.2004` because the distribution target requires it.
CentOS Linux 8 reached end of life on 2021-12-31, so this base no longer receives security updates.
Move the runtime stage to a supported Enterprise Linux base before exposing the service to untrusted
networks.
