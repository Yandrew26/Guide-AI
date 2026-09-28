# Guide AI

Frontend for the multi-agent backend: ask questions of your own books, plan trips, check your Google Workspace, and see which agent answered.

## Run

`../run-local.sh` starts the backend and this app together (see the root README). To run only the UI against an already-running gateway:

```bash
npm install
npm run dev
```

Vite proxies `/system/**` to the gateway on `http://localhost:8080`. Point it elsewhere with `GATEWAY=http://host:port npm run dev`.

```bash
npm test        # SSE parsing checks (node's built-in runner)
npm run build   # production bundle in dist/
```

## What maps to what

| Screen | Backend |
| --- | --- |
| Ask (streaming answers, route + books in the margin) | `GET /graph/create-chat`, `GET /graph/rag/stream` |
| Conversation list, date filter, paging | `GET /history/pages`, `GET /history/getByConversationId` |
| Library shelf, author lookups | `GET /test/books`, `/test/books/byAuthor`, `/test/books/sameAuthor` |
| Add PDFs, search inside books | `POST /upload/pdf`, `GET /upload/search` |
| Readers, sync to graph | `GET /user/list`, `POST /user/addUser`, `GET /user/syncUsers` |

The auth service and `multi-agent-gateway-openapi` (:8100) are for external API clients signing requests with `authID` + `authorization`; the UI goes through the internal gateway and doesn't use them.

For a production build served from another origin, add CORS for that origin on the gateway.
