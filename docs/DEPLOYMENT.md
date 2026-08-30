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
Only languages listed in `CODEGRAPH_PARSER_PROCESS_LANGUAGES` can be analyzed. A Go parser using
`--stdio-stream` is kept alive for the lifetime of one analysis task, while files are still applied
sequentially.

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
