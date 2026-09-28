import { useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { ArrowUp, RotateCcw, Square } from 'lucide-react'
import { api, streamAnswer, type ApiError, type HistoryRow, type Reader, type StreamEvent } from './api'
import { Notice, RouteMark, Spine, serverDate, timeLabel } from './ui'

type Turn = {
  id: string
  question: string
  answer: string
  route?: string
  found: string[]
  related: string[]
  status: 'streaming' | 'done' | 'stopped' | 'error'
  error?: string
  at: Date
}

const STARTERS = [
  { route: 'BOOK LOOKUP', text: 'Which books in my library talk about building good habits?' },
  { route: 'TRAVEL', text: 'Plan three unhurried days in Hangzhou next month, with the weather for each day.' },
  { route: 'GOOGLE WORKSPACE', text: 'What’s on my Google Calendar this week?' },
  { route: 'CASUAL CHAT', text: 'What can you help me with?' },
]

// History rows come as USER/ASSISTANT pairs sharing a traceId.
function toTurns(rows: HistoryRow[]): Turn[] {
  const byTrace = new Map<string, Turn>()
  for (const row of rows) {
    const turn = byTrace.get(row.traceId) ?? {
      id: row.traceId,
      question: '',
      answer: '',
      route: row.appId ?? undefined,
      found: [],
      related: [],
      status: 'done' as const,
      at: serverDate(row.createdDate),
    }
    if (row.type === 'USER') turn.question = row.content
    else turn.answer = row.content
    byTrace.set(row.traceId, turn)
  }
  return [...byTrace.values()].map((t) =>
    t.answer ? t : { ...t, status: 'error', error: 'No answer was saved for this question.' },
  )
}

export function Chat({
  reader,
  conversationId: initialId,
  initialDraft,
  onCreated,
  onTurnDone,
}: {
  reader: Reader
  conversationId?: string
  initialDraft: string
  onCreated: (id: string) => void
  onTurnDone: () => void
}) {
  const [conversationId, setConversationId] = useState(initialId)
  // Only a conversation opened from history is loaded; one started here already has its turns in state.
  const [historyId] = useState(initialId)
  const [turns, setTurns] = useState<Turn[]>([])
  const [loading, setLoading] = useState(Boolean(initialId))
  const [loadError, setLoadError] = useState<string | null>(null)
  const [draft, setDraft] = useState(initialDraft)
  const [announcement, setAnnouncement] = useState('')
  const abortRef = useRef<AbortController | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const pinnedToBottom = useRef(true)
  const streaming = turns.some((t) => t.status === 'streaming')

  const loadHistory = () => {
    if (!historyId) return
    setLoading(true)
    setLoadError(null)
    api
      .conversation(historyId)
      .then((rows) => setTurns(toTurns(rows)))
      .catch((e: ApiError) => setLoadError(e.message))
      .finally(() => setLoading(false))
  }
  useEffect(loadHistory, [historyId])
  useEffect(() => () => abortRef.current?.abort(), [])
  // Focus for keyboard users; on phones it would just throw up the keyboard.
  useEffect(() => {
    if (matchMedia('(pointer: fine)').matches) inputRef.current?.focus()
  }, [])

  // Follow the answer as it streams, unless the reader scrolled up to read something else.
  useLayoutEffect(() => {
    const el = scrollRef.current
    if (el && pinnedToBottom.current) el.scrollTop = el.scrollHeight
  }, [turns])

  const update = (id: string, patch: (t: Turn) => Partial<Turn>) =>
    setTurns((all) => all.map((t) => (t.id === id ? { ...t, ...patch(t) } : t)))

  async function ask(question: string) {
    question = question.trim()
    if (!question || streaming) return
    const id = crypto.randomUUID()
    setDraft('')
    pinnedToBottom.current = true
    setTurns((all) => [...all, { id, question, answer: '', found: [], related: [], status: 'streaming', at: new Date() }])

    const controller = new AbortController()
    abortRef.current = controller
    try {
      let cid = conversationId
      if (!cid) {
        cid = await api.startConversation(reader.userId)
        setConversationId(cid)
        onCreated(cid)
      }
      let answered = false
      await streamAnswer(
        question,
        cid,
        (event: StreamEvent) => {
          if (event.kind === 'route') update(id, () => ({ route: event.route }))
          if (event.kind === 'found') update(id, () => ({ found: event.titles }))
          if (event.kind === 'related') update(id, (t) => ({ related: event.titles.filter((x) => !t.found.includes(x)) }))
          if (event.kind === 'text') {
            answered = true
            update(id, (t) => ({ answer: t.answer + event.text }))
          }
        },
        controller.signal,
      )
      update(id, () =>
        answered
          ? { status: 'done' }
          : { status: 'error', error: 'The agent finished without writing an answer. The search service log will say why.' },
      )
      setAnnouncement(answered ? 'Answer complete.' : 'No answer was written.')
    } catch (e) {
      if (controller.signal.aborted) update(id, () => ({ status: 'stopped' }))
      else update(id, () => ({ status: 'error', error: (e as ApiError).message }))
    } finally {
      abortRef.current = null
      onTurnDone()
    }
  }

  const retry = (turn: Turn) => {
    setTurns((all) => all.filter((t) => t.id !== turn.id))
    ask(turn.question)
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    ask(draft)
  }
  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      ask(draft)
    }
  }

  return (
    <div className="chat">
      <div
        className="chat-scroll"
        ref={scrollRef}
        onScroll={(e) => {
          const el = e.currentTarget
          pinnedToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
        }}
      >
        <div className="chat-column">
          {loading && (
            <div className="turn-skeleton" aria-label="Loading conversation">
              <span /> <span /> <span />
            </div>
          )}
          {loadError && (
            <Notice action={<button className="button" onClick={loadHistory}>Try again</button>}>
              <strong>Couldn't open this conversation.</strong> {loadError}
            </Notice>
          )}

          {!loading && !loadError && turns.length === 0 && (
            <section className="empty-chat" aria-labelledby="empty-heading">
              <h1 id="empty-heading">What would you like to know, {reader.userName}?</h1>
              <p className="lede">
                Each question goes to one of four agents. The margin shows which one answered and which of your books it used.
              </p>
              <ul className="starters">
                {STARTERS.map((s) => (
                  <li key={s.route}>
                    <button className="starter" onClick={() => ask(s.text)}>
                      <RouteMark route={s.route} />
                      <span>{s.text}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {turns.map((turn) => (
            <TurnView key={turn.id} turn={turn} onRetry={() => retry(turn)} />
          ))}
        </div>
      </div>

      <form className="composer" onSubmit={submit}>
        <div className="composer-box">
          <label htmlFor="question" className="visually-hidden">
            Your question
          </label>
          <textarea
            id="question"
            ref={inputRef}
            rows={1}
            value={draft}
            placeholder={turns.length ? 'Ask a follow-up' : 'Ask about your books, a trip, or your week'}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={onKeyDown}
          />
          {streaming ? (
            <button type="button" className="send stop" onClick={() => abortRef.current?.abort()} aria-label="Stop answering">
              <Square size={16} fill="currentColor" aria-hidden />
            </button>
          ) : (
            <button type="submit" className="send" disabled={!draft.trim()} aria-label="Ask">
              <ArrowUp size={18} aria-hidden />
            </button>
          )}
        </div>
        <p className="composer-hint">Enter sends. Shift+Enter adds a line.</p>
      </form>

      <p className="visually-hidden" aria-live="polite">
        {announcement}
      </p>
    </div>
  )
}

function TurnView({ turn, onRetry }: { turn: Turn; onRetry: () => void }) {
  const isStreaming = turn.status === 'streaming'
  return (
    <article className="turn" aria-busy={isStreaming}>
      <p className="question">{turn.question}</p>

      <aside className="margin" aria-label="How this was answered">
        {turn.route ? <RouteMark route={turn.route} /> : isStreaming ? <span className="routing">Choosing an agent…</span> : null}
        {turn.found.length > 0 && (
          <div className="margin-group">
            <h3>Found in your library</h3>
            <ul>
              {turn.found.map((t) => (
                <li key={t}>
                  <Spine title={t} size="sm" />
                </li>
              ))}
            </ul>
          </div>
        )}
        {turn.related.length > 0 && (
          <div className="margin-group">
            <h3>Related on your shelf</h3>
            <ul>
              {turn.related.map((t) => (
                <li key={t}>
                  <Spine title={t} size="sm" />
                </li>
              ))}
            </ul>
          </div>
        )}
        <time dateTime={turn.at.toISOString()}>{timeLabel(turn.at)}</time>
      </aside>

      <div className="answer">
        {turn.answer ? (
          <div className="prose">
            <Markdown
              remarkPlugins={[remarkGfm]}
              components={{ a: (props) => <a {...props} target="_blank" rel="noreferrer" /> }}
            >
              {turn.answer}
            </Markdown>
            {isStreaming && <span className="caret" aria-hidden />}
          </div>
        ) : (
          isStreaming && (
            <div className="answer-skeleton" aria-label="Writing an answer">
              <span /> <span /> <span />
            </div>
          )
        )}
        {turn.status === 'stopped' && <p className="turn-note">Stopped before the answer finished.</p>}
        {turn.status === 'error' && (
          <Notice
            action={
              <button className="button" onClick={onRetry}>
                <RotateCcw size={16} aria-hidden /> Ask again
              </button>
            }
          >
            {turn.error}
          </Notice>
        )}
      </div>
    </article>
  )
}
