import { useState, type FormEvent } from 'react'
import { RefreshCw, UserPlus } from 'lucide-react'
import { api, type ApiError, type Reader } from './api'
import { Notice } from './ui'

export function Readers({
  readers,
  error,
  current,
  onChoose,
  onChanged,
}: {
  readers: Reader[] | null
  error: string | null
  current: Reader | null
  onChoose: (id: string) => void
  onChanged: () => void
}) {
  const [sync, setSync] = useState<{ state: 'idle' | 'working' | 'done' | 'failed'; message?: string }>({ state: 'idle' })

  const runSync = async () => {
    setSync({ state: 'working' })
    try {
      await api.syncReaders()
      setSync({ state: 'done', message: 'Every reader is in the recommendations graph.' })
    } catch (e) {
      setSync({ state: 'failed', message: (e as ApiError).message })
    }
  }

  return (
    <div className="page readers">
      <header className="page-head">
        <div>
          <h1>Readers</h1>
          <p className="lede">Each reader has their own conversations. Books they ask about feed recommendations for everyone else.</p>
        </div>
      </header>

      {error && (
        <Notice action={<button className="button" onClick={onChanged}>Try again</button>}>
          <strong>Couldn't load readers.</strong> {error}
        </Notice>
      )}

      {readers && readers.length > 0 && (
        <div className="table-wrap">
          <table className="reader-table">
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Reader ID</th>
                <th scope="col">Email</th>
                <th scope="col">Phone</th>
                <th scope="col">
                  <span className="visually-hidden">Status</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {readers.map((r) => (
                <tr key={r.userId} aria-current={r.userId === current?.userId || undefined}>
                  <th scope="row">{r.userName}</th>
                  <td className="mono-ish">{r.userId}</td>
                  <td>{r.email || <span className="muted">None</span>}</td>
                  <td>{r.mobile || <span className="muted">None</span>}</td>
                  <td className="row-action">
                    {r.userId === current?.userId ? (
                      <span className="current-badge">Reading now</span>
                    ) : (
                      <button className="button quiet" onClick={() => onChoose(r.userId)}>
                        Read as {r.userName}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {readers?.length === 0 && <p className="muted">No readers yet. Add the first one below.</p>}

      <div className="readers-columns">
        <section aria-labelledby="add-heading">
          <h2 id="add-heading">Add a reader</h2>
          <ReaderForm onAdded={onChanged} />
        </section>

        <section aria-labelledby="sync-heading" className="sync">
          <h2 id="sync-heading">Recommendations graph</h2>
          <p>
            Readers added here are placed in the graph automatically. If readers were added straight to the database,
            sync them so their reading history can shape recommendations.
          </p>
          <button className="button" onClick={runSync} disabled={sync.state === 'working'}>
            <RefreshCw size={16} className={sync.state === 'working' ? 'spin' : undefined} aria-hidden />
            {sync.state === 'working' ? 'Syncing readers…' : 'Sync readers to graph'}
          </button>
          <p className="form-status" role="status" data-state={sync.state}>
            {sync.message}
          </p>
        </section>
      </div>
    </div>
  )
}

const EMPTY: Reader = { userId: '', userName: '', email: '', mobile: '' }

export function ReaderForm({ onAdded }: { onAdded: (reader: Reader) => void }) {
  const [form, setForm] = useState(EMPTY)
  const [status, setStatus] = useState<{ state: 'idle' | 'working' | 'done' | 'failed'; message?: string }>({ state: 'idle' })
  const field = (key: keyof Reader) => ({
    id: `reader-${key}`,
    value: form[key] ?? '',
    onChange: (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value }),
  })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const reader = { ...form, userId: form.userId.trim(), userName: form.userName.trim() }
    setStatus({ state: 'working' })
    try {
      await api.addReader(reader)
      setStatus({ state: 'done', message: `Added ${reader.userName}.` })
      setForm(EMPTY)
      onAdded(reader)
    } catch (err) {
      setStatus({ state: 'failed', message: (err as ApiError).message })
    }
  }

  return (
    <form className="reader-form" onSubmit={submit}>
      <div className="field">
        <label htmlFor="reader-userName">Name</label>
        <input {...field('userName')} required autoComplete="name" />
      </div>
      <div className="field">
        <label htmlFor="reader-userId">Reader ID</label>
        {/* Conversation ids are "<userId>_<session>", so an underscore in the id would break them. */}
        <input
          {...field('userId')}
          required
          pattern="[A-Za-z0-9-]+"
          maxLength={64}
          aria-describedby="reader-userId-hint"
          autoComplete="username"
          spellCheck={false}
        />
        <p className="field-hint" id="reader-userId-hint">
          Letters, numbers, and hyphens.
        </p>
      </div>
      <div className="field">
        <label htmlFor="reader-email">
          Email <span className="optional">optional</span>
        </label>
        <input {...field('email')} type="email" autoComplete="email" />
      </div>
      <div className="field">
        <label htmlFor="reader-mobile">
          Phone <span className="optional">optional</span>
        </label>
        <input {...field('mobile')} type="tel" autoComplete="tel" />
      </div>
      <div className="form-actions">
        <button className="button primary" type="submit" disabled={status.state === 'working'}>
          <UserPlus size={18} aria-hidden /> {status.state === 'working' ? 'Adding reader…' : 'Add reader'}
        </button>
        <p className="form-status" role="status" data-state={status.state}>
          {status.message}
        </p>
      </div>
    </form>
  )
}
