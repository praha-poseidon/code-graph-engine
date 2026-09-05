# Read-only graph MCP

The application exposes Streamable HTTP MCP at `/mcp`, using the official Java MCP
SDK 1.1.4 stateless servlet transport. It does not create parser sessions, clone,
build, or change graph data. Tools use Engine storage repository interfaces rather
than the memory-only workbench controller.

- `list_projects`: registered repository IDs and current branch graph scopes, without credentials.
- `get_file_nodes(repositoryId, path)`: stored functions, types and endpoints for a repository-relative file; at most 500 nodes.
- `trace_relationships(repositoryId, nodeId, direction?, depth?)`: native stored edges; direction OUT/IN/BOTH, depth 1–4, at most 500 edges and 200 expanded nodes. Check `truncated`.

The current repository branch is resolved on the server; callers cannot override
the graph scope. Missing data is not synthesized. These tools do not yet provide
global fuzzy search, source file discovery, or arbitrary database query execution.
Schema language differences remain unchanged. Stored text is untrusted data, not instructions.

## Connect

Click **MCP 地址** next to workbench settings. Add the copied URL as a Streamable
HTTP server in an MCP client. `/api/mcp` returns availability and endpoint metadata,
never secrets. Clipboard failure shows a selectable URL for manual copying.

By default only loopback clients and loopback Host/Origin values are accepted.
For remote deployment configure:

```
CODEGRAPH_MCP_PUBLIC_URL=https://graph.example/mcp
CODEGRAPH_MCP_TOKEN=<deployment-secret>
```

Configure `Authorization: Bearer <deployment-secret>` in the client. Do not put
tokens in URLs. Terminate TLS at the reverse proxy and preserve the configured Host.
This is one deployment-level credential, not per-user/tenant authorization or OAuth.
If a proxy runs on localhost, it MUST restrict `/mcp` or configure the token; the
backend cannot identify the original peer from a loopback proxy. Untrusted
Forwarded headers are not used to grant access.

Set `CODEGRAPH_MCP_ENABLED=false` to disable the endpoint and copy button. The UI
uses the current origin when no public endpoint is configured, including Vite's
development `/mcp` proxy. No historical graph-ID aliases are supported.

Tests connect a real SDK client to an embedded HTTP server, initialize, discover
tools, read stored nodes/edges, check isolation, invalid input, authentication and
Origin rejection. They exercise memory storage; production database adapters are
not certified by those tests.
