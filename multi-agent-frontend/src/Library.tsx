import { useCallback, useEffect, useRef, useState, type DragEvent, type FormEvent } from 'react'
import { Check, FileUp, LoaderCircle, MessageSquareText, Search, X } from 'lucide-react'
import { api, type ApiError, type Book, type Passage } from './api'
import { Notice, Spine, bookHref, go } from './ui'

type Upload = { name: string; state: 'working' | 'added' | 'exists' | 'failed'; message?: string }

export function Library({ selected }: { selected: string | null }) {
  const [books, setBooks] = useState<Book[] | null>(null)
  const [booksError, setBooksError] = useState<string | null>(null)
  const [uploads, setUploads] = useState<Upload[]>([])
  const [dragging, setDragging] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  const loadBooks = useCallback(() => {
    setBooksError(null)
    api.books().then(setBooks, (e: ApiError) => setBooksError(e.message))
  }, [])
  useEffect(loadBooks, [loadBooks])

  // One at a time: each PDF is parsed, embedded, and graphed on the server, which is slow and memory-hungry.
  async function uploadFiles(files: File[]) {
    const pdfs = files.filter((f) => f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf'))
    const rejected = files.filter((f) => !pdfs.includes(f))
    if (rejected.length)
      setUploads((u) => [...rejected.map((f) => ({ name: f.name, state: 'failed' as const, message: 'Not a PDF. Only PDF files can be added.' })), ...u])
    for (const file of pdfs) {
      setUploads((u) => [{ name: file.name, state: 'working' }, ...u.filter((x) => x.name !== file.name)])
      const set = (patch: Partial<Upload>) => setUploads((u) => u.map((x) => (x.name === file.name ? { ...x, ...patch } : x)))
      try {
        const reply = await api.uploadPdf(file)
        set(reply.includes('already exists') ? { state: 'exists', message: 'Already on the shelf.' } : { state: 'added', message: 'Added to the shelf.' })
        loadBooks()
      } catch (e) {
        set({ state: 'failed', message: (e as ApiError).message })
      }
    }
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setDragging(false)
    uploadFiles([...e.dataTransfer.files])
  }

  const selectedBook = selected ? books?.find((b) => b.title === selected) ?? { title: selected, authors: [] } : null

  return (
    <div
      className="page library"
      data-dragging={dragging}
      onDragOver={(e) => {
        if (e.dataTransfer.types.includes('Files')) {
          e.preventDefault()
          setDragging(true)
        }
      }}
      onDragLeave={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node)) setDragging(false)
      }}
      onDrop={onDrop}
    >
      <header className="page-head">
        <div>
          <h1>Library</h1>
          <p className="lede">
            {books ? `${books.length} ${books.length === 1 ? 'book' : 'books'} the library agent can read from.` : 'The books the library agent can read from.'}
          </p>
        </div>
        <button className="button primary" onClick={() => fileInput.current?.click()}>
          <FileUp size={18} aria-hidden /> Add PDFs
        </button>
        <input
          ref={fileInput}
          type="file"
          accept="application/pdf,.pdf"
          multiple
          hidden
          onChange={(e) => {
            uploadFiles([...(e.target.files ?? [])])
            e.target.value = ''
          }}
        />
      </header>

      {uploads.length > 0 && (
        <ul className="uploads" aria-live="polite">
          {uploads.map((u) => (
            <li key={u.name} data-state={u.state}>
              {u.state === 'working' ? (
                <LoaderCircle className="spin" size={16} aria-hidden />
              ) : u.state === 'failed' ? (
                <X size={16} aria-hidden />
              ) : (
                <Check size={16} aria-hidden />
              )}
              <span className="upload-name">{u.name}</span>
              <span className="upload-message">
                {u.state === 'working' ? 'Reading, indexing, and finding authors. Long books take a few minutes.' : u.message}
              </span>
            </li>
          ))}
        </ul>
      )}

      {selectedBook && <BookDetail book={selectedBook} />}

      <PassageSearch />

      <section className="shelf-section" aria-labelledby="shelf-heading">
        <h2 id="shelf-heading">On the shelf</h2>
        {booksError && (
          <Notice action={<button className="button" onClick={loadBooks}>Try again</button>}>
            <strong>Couldn't load the shelf.</strong> {booksError}
          </Notice>
        )}
        {!books && !booksError && <div className="shelf shelf-loading" aria-label="Loading books" />}
        {books?.length === 0 && (
          <div className="shelf-empty">
            <p>The shelf is empty. Add a PDF and the library agent can answer from it and recommend related books.</p>
            <button className="button" onClick={() => fileInput.current?.click()}>
              <FileUp size={18} aria-hidden /> Add your first PDF
            </button>
          </div>
        )}
        {books && books.length > 0 && (
          <ul className="shelf">
            {books.map((b) => (
              <li key={b.title} aria-current={b.title === selected || undefined}>
                <Spine title={b.title} authors={b.authors} />
              </li>
            ))}
          </ul>
        )}
        <p className="drop-hint">You can also drop PDFs anywhere on this page.</p>
      </section>

      <div className="drop-overlay" aria-hidden>
        <p>Drop PDFs to add them to the shelf</p>
      </div>
    </div>
  )
}

function BookDetail({ book }: { book: Book }) {
  const [sameAuthor, setSameAuthor] = useState<string[] | null>(null)
  const [author, setAuthor] = useState<string | null>(null)
  const [byAuthor, setByAuthor] = useState<string[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setSameAuthor(null)
    setAuthor(null)
    setError(null)
    api.booksBySameAuthor(book.title).then(
      (titles) => setSameAuthor([...new Set(titles)].filter((t) => t !== book.title)),
      (e: ApiError) => setError(e.message),
    )
  }, [book.title])

  useEffect(() => {
    if (!author) return
    setByAuthor(null)
    api.booksByAuthor(author).then(setByAuthor, (e: ApiError) => setError(e.message))
  }, [author])

  return (
    <section className="book-detail" aria-labelledby="book-title">
      <div className="book-detail-head">
        <div className="detail-spine">
          <Spine title={book.title} authors={book.authors} />
        </div>
        <div>
          <h2 id="book-title">{book.title}</h2>
          {book.authors.length > 0 ? (
            <p className="byline">
              by{' '}
              {book.authors.map((a, i) => (
                <span key={a}>
                  {i > 0 && (i === book.authors.length - 1 ? ' and ' : ', ')}
                  <button className="link-button" aria-pressed={author === a} onClick={() => setAuthor(author === a ? null : a)}>
                    {a}
                  </button>
                </span>
              ))}
            </p>
          ) : (
            <p className="byline muted">No author was found when this PDF was read.</p>
          )}
          <div className="book-actions">
            <button className="button primary" onClick={() => go(`/?${new URLSearchParams({ ask: `What is "${book.title}" about?` })}`)}>
              <MessageSquareText size={18} aria-hidden /> Ask about this book
            </button>
            <a className="button quiet" href="#/library">
              Close
            </a>
          </div>
        </div>
      </div>

      {error && <Notice>{error}</Notice>}

      {author && (
        <div className="related">
          <h3>Books by {author}</h3>
          {byAuthor === null ? <p className="muted">Looking…</p> : <TitleList titles={byAuthor} />}
        </div>
      )}
      {!author && sameAuthor && sameAuthor.length > 0 && (
        <div className="related">
          <h3>Also by the same author</h3>
          <TitleList titles={sameAuthor} />
        </div>
      )}
    </section>
  )
}

const TitleList = ({ titles }: { titles: string[] }) =>
  titles.length ? (
    <ul className="title-list">
      {titles.map((t) => (
        <li key={t}>
          <Spine title={t} size="sm" />
        </li>
      ))}
    </ul>
  ) : (
    <p className="muted">No other books on the shelf.</p>
  )

function PassageSearch() {
  const [query, setQuery] = useState('')
  const [topK, setTopK] = useState(5)
  const [results, setResults] = useState<Passage[] | null>(null)
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [searched, setSearched] = useState('')

  const search = async (e: FormEvent) => {
    e.preventDefault()
    const q = query.trim()
    if (!q) return
    setSearching(true)
    setError(null)
    try {
      setResults(await api.searchPassages(q, topK))
      setSearched(q)
    } catch (err) {
      setError((err as ApiError).message)
    } finally {
      setSearching(false)
    }
  }

  return (
    <section className="passage-search" aria-labelledby="search-heading">
      <h2 id="search-heading">Search inside your books</h2>
      <form className="search-form" onSubmit={search} role="search">
        <label htmlFor="passage-query" className="visually-hidden">
          Search passages
        </label>
        <Search size={18} aria-hidden className="search-icon" />
        <input
          id="passage-query"
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="A theme, idea, or phrase"
        />
        <label htmlFor="passage-count" className="visually-hidden">
          Number of passages
        </label>
        <select id="passage-count" value={topK} onChange={(e) => setTopK(Number(e.target.value))}>
          <option value={5}>5 passages</option>
          <option value={10}>10 passages</option>
          <option value={20}>20 passages</option>
        </select>
        <button className="button" type="submit" disabled={!query.trim() || searching}>
          {searching ? 'Searching…' : 'Search'}
        </button>
      </form>
      <p className="field-hint">Short queries are expanded by the model before searching, so they take a moment longer.</p>

      {error && <Notice>{error}</Notice>}
      {results && (
        <div className="results">
          <div className="results-head">
            <p>
              {results.length ? `${results.length} passages for “${searched}”` : `No passages matched “${searched}”. Try different words.`}
            </p>
            <button className="link-button" onClick={() => setResults(null)}>
              Clear results
            </button>
          </div>
          <ol className="passages">
            {results.map((p) => (
              <li key={p.id}>
                <div className="passage-meta">
                  {p.metadata.book_title && (
                    <a className="passage-book" href={bookHref(p.metadata.book_title)}>
                      {p.metadata.book_title}
                    </a>
                  )}
                  {p.metadata.page_number != null && <span>Page {p.metadata.page_number}</span>}
                  {p.score != null && <span>{Math.round(p.score * 100)}% match</span>}
                </div>
                <p className="passage-text">{p.text}</p>
              </li>
            ))}
          </ol>
        </div>
      )}
    </section>
  )
}
