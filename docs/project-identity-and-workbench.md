# Project identity and workbench

## Identity contract

`repository_config.id` remains the relational primary key for tasks. The
`repository_identity` table assigns each canonical repository a deterministic,
fixed-length 26-character Base32 `projectId` derived from SHA-256. Updating
transport, credentials, display path, or branch does not change it. The database
unique constraint remains the collision guard.

The duplicate-registration key is full SHA-256 of canonical Git host + full group
path + repository name. HTTPS and SSH default transports normalize to the same
key. Non-default ports and case-sensitive server paths remain distinct; GitHub
paths are lowercased. Credential-bearing HTTP URLs are rejected. Credentials never
participate in keys or node details. This is single-platform identity isolation,
not a multi-tenant authorization implementation.

Graph scope is `project:<UUID>:branch:<SHA-256(branch)>`. It is independent of task
ID and clone directory. Empty branch represents the configured default-branch
slot; explicit `main` is a separate slot. The worker passes this opaque scope to
all language parsers through the existing projectName contract. Core library
callers must likewise pass a stable scope, not a display name.

Registered REST file operations can pass `repositoryId` and optionally
`gitBranch`; the server resolves the same scope for POST, PUT and DELETE
`/api/code-graph/files/nodes`. `/api/config/projects` returns `projectId`,
`canonicalRepository` and `graphScope` for integrations. Direct GraphDelta import
and project-scoped queries use `graphScope` as their existing `projectName`.

## New identity only

Historical short-name identities are not supported. There is no startup backfill,
legacy identity field, display-name fallback, or migration notice. Registrations
must have an identity created with the current registration flow. Missing identity
rows are rejected, never silently mapped to a display name. Removing compatibility
code does not delete existing database records or task history. Existing legacy
registrations are outside this identity contract and are not migrated.

The development application currently uses an in-memory graph backend. SQL task
history survives restarts; graph data requires an export before a development
restart. Production graph persistence is a separate backend configuration.

## UI

Task center is a task-keyed master/detail layout with independent scrolling, stable
selection during polling, per-task events and explicit stopped progress. Repository
is a filter and metadata, not the primary title. Clicking the task navigation opens
all repositories; jumping from a repository applies that repository filter.

Graph view now returns a `properties` map of the stored graph business DTO. Node
details show common fields and a collapsible, copyable JSON/property list, preserving
false, zero, null, arrays and language-specific DTO fields. This endpoint is still
the existing memory-backed workbench API, not a new Neo4j/AGE graph-view adapter.
Repository authentication records are not included. Properties absent from the
parser/storage DTO cannot be reconstructed by the UI.

## Verification scope

Tests cover canonical aliases, same-name repositories in different groups,
transactional duplicate rollback, stable IDs after edits, rejection of missing identities,
registered file-operation scopes, and stored-node property readback. The real Java
CLI fixture exercises independent project/branch scopes, repeat application and
deletion isolation through Engine memory storage. Other language CLIs and production
graph database tests remain separately gated by their runtime/environment variables.
