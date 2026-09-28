# Guide AI — multi-agent system

A Spring AI multi-agent assistant. A classifier routes each message to one of four agents, and the answer streams back over SSE:

| Agent | What it does |
| --- | --- |
| Library | Semantic search over your uploaded PDFs (Elasticsearch) plus recommendations from a reading graph (Neo4j) |
| Travel | Trip planning with live weather, maps, time zones, and rail tools (DashScope MCP servers) |
| Workspace | Gmail, Calendar, Docs via the `workspace-mcp` server |
| Conversation | Everything else |

```
multi-agent-frontend/         React UI (Vite) → :5173
multi-agent-gateway/          internal gateway → :8080  (/system/** → services, used by the UI)
multi-agent-gateway-openapi/  signed external API → :8100 (authID + authorization headers, Redis rate limit)
multi-agent-service/
  multi-agent-service-search  agent graph, chat history, readers → :8081
  multi-agent-service-upload  PDF ingest, passage search → :8082
  multi-agent-service-auth    API signature check → :8084
```

## Run locally

Needs JDK 17+, Maven, Node 20+, Docker, a [DashScope](https://bailian.console.aliyun.com/) API key, MySQL 8 and Redis (native or via compose), and [`uv`](https://docs.astral.sh/uv/) for the Workspace agent.

```bash
cp .env.example .env                                         # then fill it in
mysql -u root -p < db/schema.sql                             # or: docker compose --profile db up -d
docker compose up -d                                         # Elasticsearch + Neo4j
./run-local.sh                                               # all services + UI, logs in ./logs
```

Open http://localhost:5173, add yourself as a reader, add a PDF in Library, and ask away.

Every service reads the repo-root `.env` on startup, so IntelliJ run configs and `mvn -pl <module> spring-boot:run` work the same way. Real environment variables override it.

## Security notes

- Secrets live only in `.env` (gitignored). Nothing in the repo contains credentials; see `.env.example` for the list.
- The services bind to `127.0.0.1` and have no user authentication: "readers" are profiles, not accounts. Anyone who can reach port 8080 can read every conversation and upload files. Keep it local, or put real auth in front before setting `SERVER_ADDRESS=0.0.0.0`.
- The open API gateway (:8100) signs requests as `md5("authId=<id>&secretKey=<secret>")`. That signature never changes, so treat it as a static API key: send it only over HTTPS and rotate it by changing `secret_key` in `rag_auth_base`.
- The Workspace agent can read and send your email. Its OAuth tokens are stored by `workspace-mcp` on your machine, not in this repo.
