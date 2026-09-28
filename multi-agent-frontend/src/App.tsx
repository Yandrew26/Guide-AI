import { useCallback, useEffect, useRef, useState } from 'react'
import { Library as LibraryIcon, Menu, MessageSquarePlus, Users, X } from 'lucide-react'
import { api, type ApiError, type ConversationSummary, type Reader } from './api'
import { Chat } from './Chat'
import { Library } from './Library'
import { ReaderForm, Readers } from './Readers'
import { Notice, dayLabel, go, serverDate, useHash } from './ui'

const READER_KEY = 'reading-room.reader'
const storedReader = () => {
  try {
    return localStorage.getItem(READER_KEY)
  } catch {
    return null
  }
}

export default function App() {
  const { segments, params } = useHash()
  const [readers, setReaders] = useState<Reader[] | null>(null)
  const [readersError, setReadersError] = useState<string | null>(null)
  const [readerId, setReaderId] = useState(storedReader)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [historyVersion, setHistoryVersion] = useState(0)
  const [newChatNonce, setNewChatNonce] = useState(0)
  const freshId = useRef<string | null>(null)

  // Arriving at #/ from anywhere means a blank conversation.
  useEffect(() => {
    const onHash = () => {
      if ((location.hash.slice(1) || '/') === '/') {
        freshId.current = null
        setNewChatNonce((n) => n + 1)
      }
    }
    addEventListener('hashchange', onHash)
    return () => removeEventListener('hashchange', onHash)
  }, [])

  const loadReaders = useCallback(() => {
    setReadersError(null)
    api.readers().then(setReaders, (e: ApiError) => setReadersError(e.message))
  }, [])
  useEffect(loadReaders, [loadReaders])

  const reader = readers?.find((r) => r.userId === readerId) ?? null
  const chooseReader = (id: string) => {
    setReaderId(id)
    try {
      localStorage.setItem(READER_KEY, id)
    } catch {
      /* private mode: the choice just won't survive a reload */
    }
  }

  const view = segments[0] ?? 'chat'
  const conversationId = view === 'chat' ? segments[1] : undefined
  // A conversation started on this screen keeps its component (and live stream) when the URL gains its id.
  const chatKey = conversationId && conversationId !== freshId.current ? conversationId : `new-${newChatNonce}`

  const startNewChat = () => {
    freshId.current = null
    setNewChatNonce((n) => n + 1)
    go('/')
  }

  let main
  if (view === 'readers') {
    main = <Readers readers={readers} error={readersError} current={reader} onChoose={chooseReader} onChanged={loadReaders} />
  } else if (!reader) {
    main = <Welcome readers={readers} error={readersError} onRetry={loadReaders} onChoose={chooseReader} onAdded={loadReaders} />
  } else if (view === 'library') {
    main = <Library selected={params.get('book')} />
  } else {
    main = (
      <Chat
        key={chatKey}
        reader={reader}
        conversationId={conversationId}
        initialDraft={params.get('ask') ?? ''}
        onCreated={(id) => {
          freshId.current = id // set before the hash changes so that render keeps this component
          go(`/chat/${encodeURIComponent(id)}`)
        }}
        onTurnDone={() => setHistoryVersion((v) => v + 1)}
      />
    )
  }

  return (
    <div className="shell">
      <header className="topbar">
        <button className="icon-button" onClick={() => setDrawerOpen(true)} aria-label="Open navigation" aria-expanded={drawerOpen}>
          <Menu size={20} />
        </button>
        <a className="wordmark" href="#/">Guide AI</a>
      </header>

      <div className="backdrop" data-open={drawerOpen} onClick={() => setDrawerOpen(false)} aria-hidden />
      <nav
        className="sidebar"
        data-open={drawerOpen}
        aria-label="Main"
        onClick={(e) => (e.target as Element).closest('a, .new-chat') && setDrawerOpen(false)}
        onKeyDown={(e) => e.key === 'Escape' && setDrawerOpen(false)}
      >
        <div className="sidebar-head">
          <a className="wordmark" href="#/">Guide AI</a>
          <button className="icon-button drawer-close" onClick={() => setDrawerOpen(false)} aria-label="Close navigation">
            <X size={20} />
          </button>
        </div>

        <button className="button primary new-chat" onClick={startNewChat} disabled={!reader}>
          <MessageSquarePlus size={18} aria-hidden /> New conversation
        </button>

        <ul className="nav-list">
          <li>
            <a href="#/library" aria-current={view === 'library' ? 'page' : undefined}>
              <LibraryIcon size={18} aria-hidden /> Library
            </a>
          </li>
          <li>
            <a href="#/readers" aria-current={view === 'readers' ? 'page' : undefined}>
              <Users size={18} aria-hidden /> Readers
            </a>
          </li>
        </ul>

        {reader && <ConversationList reader={reader} activeId={conversationId} version={historyVersion} />}

        {readers && readers.length > 0 && (
          <div className="reader-switch">
            <label htmlFor="reader-select">Reading as</label>
            <select id="reader-select" value={reader?.userId ?? ''} onChange={(e) => chooseReader(e.target.value)}>
              {!reader && <option value="">Choose a reader</option>}
              {readers.map((r) => (
                <option key={r.userId} value={r.userId}>
                  {r.userName}
                </option>
              ))}
            </select>
          </div>
        )}
      </nav>

      <main className="main" id="main">
        {main}
      </main>
    </div>
  )
}

/* ---------- Conversation history in the sidebar ---------- */

const PAGE_SIZE = 20

function ConversationList({ reader, activeId, version }: { reader: Reader; activeId?: string; version: number }) {
  const [items, setItems] = useState<ConversationSummary[] | null>(null)
  const [page, setPage] = useState({ pageNo: 1, pages: 1 })
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const request = useRef(0)

  const load = useCallback(
    async (pageNo: number) => {
      const id = ++request.current
      setError(null)
      try {
        const result = await api.conversations(
          reader.userId,
          pageNo,
          PAGE_SIZE,
          from && `${from} 00:00:00`,
          to && `${to} 23:59:59`,
        )
        if (id !== request.current) return
        // The backend matches userId with LIKE, so "7" would also return "17"'s conversations.
        const mine = result.data.filter((c) => c.userId === reader.userId)
        setItems((prev) => (pageNo === 1 ? mine : [...(prev ?? []), ...mine]))
        setPage({ pageNo: result.pageNo, pages: result.pages })
      } catch (e) {
        if (id === request.current) setError((e as ApiError).message)
      }
    },
    [reader.userId, from, to],
  )

  useEffect(() => {
    load(1)
  }, [load, version])

  const groups = new Map<string, ConversationSummary[]>()
  for (const item of items ?? []) {
    const label = dayLabel(serverDate(item.createdDate))
    groups.set(label, [...(groups.get(label) ?? []), item])
  }
  const filtered = Boolean(from || to)

  return (
    <section className="history" aria-labelledby="history-heading">
      <div className="history-head">
        <h2 id="history-heading">Conversations</h2>
      </div>
      <details className="date-filter" open={filtered || undefined}>
        <summary>{filtered ? 'Filtered by date' : 'Filter by date'}</summary>
        <div className="date-fields">
          <label>
            From
            <input type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label>
            To
            <input type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
          </label>
          {filtered && (
            <button className="link-button" onClick={() => (setFrom(''), setTo(''))}>
              Clear dates
            </button>
          )}
        </div>
      </details>

      {error && <p className="history-note error">{error}</p>}
      {!items && !error && (
        <div className="history-skeleton" aria-label="Loading conversations">
          <span /> <span /> <span />
        </div>
      )}
      {items?.length === 0 && (
        <p className="history-note">{filtered ? 'Nothing in this date range.' : 'Your conversations will appear here.'}</p>
      )}

      {[...groups].map(([label, list]) => (
        <div key={label} className="history-group">
          <h3>{label}</h3>
          <ul>
            {list.map((c) => (
              <li key={c.conversationId}>
                <a
                  href={`#/chat/${encodeURIComponent(c.conversationId)}`}
                  aria-current={c.conversationId === activeId ? 'page' : undefined}
                  title={c.content}
                >
                  {c.content}
                </a>
              </li>
            ))}
          </ul>
        </div>
      ))}

      {page.pageNo < page.pages && (
        <button
          className="link-button more"
          disabled={loadingMore}
          onClick={async () => {
            setLoadingMore(true)
            await load(page.pageNo + 1)
            setLoadingMore(false)
          }}
        >
          {loadingMore ? 'Loading…' : 'Show older conversations'}
        </button>
      )}
    </section>
  )
}

/* ---------- First run: nobody chosen yet ---------- */

function Welcome({
  readers,
  error,
  onRetry,
  onChoose,
  onAdded,
}: {
  readers: Reader[] | null
  error: string | null
  onRetry: () => void
  onChoose: (id: string) => void
  onAdded: () => void
}) {
  return (
    <div className="page narrow welcome">
      <h1>Who's reading?</h1>
      <p className="lede">
        Conversations, reading history, and recommendations are kept per reader. Choose one to start, or add yourself.
      </p>
      {error && (
        <Notice action={<button className="button" onClick={onRetry}>Try again</button>}>
          <strong>Couldn't load readers.</strong> {error}
        </Notice>
      )}
      {readers && readers.length > 0 && (
        <ul className="reader-choices">
          {readers.map((r) => (
            <li key={r.userId}>
              <button className="reader-choice" onClick={() => onChoose(r.userId)}>
                <span className="reader-name">{r.userName}</span>
                <span className="reader-id">{r.userId}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {readers && (
        <section className="welcome-add" aria-labelledby="add-first">
          <h2 id="add-first">{readers.length ? 'Someone new' : 'Add the first reader'}</h2>
          <ReaderForm
            onAdded={(r) => {
              onAdded()
              onChoose(r.userId)
            }}
          />
        </section>
      )}
    </div>
  )
}
