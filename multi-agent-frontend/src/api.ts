// Everything goes through the gateway: /system/** -> search service, /system/upload/** -> upload service.
const BASE = '/system'

export type Reader = { userId: string; userName: string; email?: string; mobile?: string }

export type HistoryRow = {
  id: number
  appId: string | null
  userId: string
  userName: string
  conversationId: string
  traceId: string
  content: string
  type: 'USER' | 'ASSISTANT'
  createdDate: string // "yyyy-MM-dd HH:mm:ss"
}

export type ConversationSummary = {
  userId: string
  userName: string
  conversationId: string
  content: string
  createdDate: string
}

export type Page<T> = { data: T[]; pageNo: number; pageSize: number; total: number; pages: number }

export type Book = { title: string; authors: string[] }

export type Passage = {
  id: string
  text: string
  score: number | null
  metadata: { book_title?: string; page_number?: number; total_pages?: number; authors?: string[] }
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(BASE + path, init)
  } catch {
    throw new ApiError(0, 'The gateway is unreachable. Start multi-agent-gateway on port 8080.')
  }
  if (!res.ok) throw new ApiError(res.status, await describeFailure(res))
  const text = await res.text()
  const type = res.headers.get('content-type') ?? ''
  return (type.includes('json') && text ? JSON.parse(text) : text) as T
}

// Spring's default error bodies ("Internal Server Error") add nothing, so map by status.
async function describeFailure(res: Response) {
  if (res.status >= 502) return 'A backend service is not responding. Check that the services behind the gateway are running.'
  if (res.status >= 500) return `The service hit an error (${res.status}). Its log has the details.`
  if (res.status === 404) return 'Endpoint not found. Check that the gateway routes /system to the services.'
  // 400/422 from our controllers carry a plain-text reason meant for people
  const text = res.headers.get('content-type')?.startsWith('text/plain') ? (await res.text()).trim() : ''
  return text && text.length < 200 ? text : `Request failed (${res.status}).`
}

const qs = (params: Record<string, string | number | undefined>) =>
  new URLSearchParams(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== '') as [string, string][],
  ).toString()

export const api = {
  readers: () => request<Reader[]>('/user/list'),
  addReader: (reader: Reader) =>
    request<string>('/user/addUser', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(reader),
    }),
  syncReaders: () => request<string>('/user/syncUsers'),

  // The backend answers with a sentence; the id is everything after the last ": ".
  startConversation: async (userId: string) =>
    (await request<string>(`/graph/create-chat?${qs({ userId })}`)).split(': ').pop()!.trim(),
  conversation: (conversationId: string) =>
    request<HistoryRow[]>(`/history/getByConversationId?${qs({ conversationId })}`),
  conversations: (userId: string, pageNum: number, pageSize: number, dateStart?: string, dateEnd?: string) =>
    request<Page<ConversationSummary>>(`/history/pages?${qs({ userId, pageNum, pageSize, dateStart, dateEnd })}`),

  books: () => request<Book[]>('/test/books'),
  booksByAuthor: (name: string) => request<string[]>(`/test/books/byAuthor?${qs({ name })}`),
  booksBySameAuthor: (bookTitle: string) => request<string[]>(`/test/books/sameAuthor?${qs({ bookTitle })}`),
  searchPassages: (query: string, topK: number) => request<Passage[]>(`/upload/search?${qs({ query, topK })}`),
  uploadPdf: (file: File) => {
    const body = new FormData()
    body.append('file', file)
    return request<string>('/upload/pdf', { method: 'POST', body })
  },
}

/* ---------- Streaming answers ---------- */

export type StreamEvent =
  | { kind: 'route'; route: string }
  | { kind: 'found'; titles: string[] }
  | { kind: 'related'; titles: string[] }
  | { kind: 'text'; text: string }

// Graph nodes emit `data:{"<node>_stream": "<chunk>"}`; the classifier emits `data:{"route": "..."}`.
export function toStreamEvent(payload: Record<string, unknown>): StreamEvent | null {
  if (typeof payload.route === 'string') return { kind: 'route', route: payload.route.trim() }
  if (typeof payload.rag_chat_stream === 'string') return { kind: 'text', text: payload.rag_chat_stream }
  if (typeof payload.vector_search_stream === 'string')
    return { kind: 'found', titles: parseTitleList(payload.vector_search_stream) }
  if (typeof payload.neo4j_search_stream === 'string')
    return { kind: 'related', titles: parseTitleList(payload.neo4j_search_stream) }
  return null // prompt_template_stream and anything new: not shown
}

// Java List.toString(): "[A, B]". ponytail: titles containing ", " split wrongly; send JSON from the node if that bites.
export function parseTitleList(raw: string): string[] {
  const inner = raw.trim().replace(/^\[|\]$/g, '')
  return [...new Set(inner.split(', ').map((t) => t.trim()).filter(Boolean))]
}

// Splits an SSE buffer into complete `data:` payloads plus the unfinished remainder.
export function parseSSE(buffer: string): { data: string[]; rest: string } {
  const blocks = buffer.replace(/\r\n/g, '\n').split('\n\n')
  const rest = blocks.pop() ?? ''
  const data = blocks
    .map((block) =>
      block
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).replace(/^ /, ''))
        .join('\n'),
    )
    .filter(Boolean)
  return { data, rest }
}

export async function streamAnswer(
  message: string,
  conversationId: string,
  onEvent: (event: StreamEvent) => void,
  signal: AbortSignal,
) {
  let res: Response
  try {
    res = await fetch(`${BASE}/graph/rag/stream?${qs({ message, conversationId })}`, {
      headers: { Accept: 'text/event-stream' },
      signal,
    })
  } catch (e) {
    if (signal.aborted) throw e
    throw new ApiError(0, 'The gateway is unreachable. Start multi-agent-gateway on port 8080.')
  }
  if (!res.ok || !res.body) throw new ApiError(res.status, await describeFailure(res))

  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader()
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    const parsed = parseSSE(buffer + value)
    buffer = parsed.rest
    for (const data of parsed.data) {
      try {
        const event = toStreamEvent(JSON.parse(data))
        if (event) onEvent(event)
      } catch {
        /* a malformed frame is skipped rather than killing the answer */
      }
    }
  }
}
