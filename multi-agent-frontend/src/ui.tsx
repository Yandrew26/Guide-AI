import { useEffect, useState, type CSSProperties, type ReactNode } from 'react'
import { CircleAlert } from 'lucide-react'

/* ---------- Agent routes (classifier categories in ParallelGraphConfig) ---------- */

const ROUTES: Record<string, { label: string; tone: string }> = {
  'BOOK LOOKUP': { label: 'Library agent', tone: 'book' },
  TRAVEL: { label: 'Travel agent', tone: 'travel' },
  'GOOGLE WORKSPACE': { label: 'Workspace agent', tone: 'work' },
  'CASUAL CHAT': { label: 'Conversation', tone: 'chat' },
}

export const routeInfo = (route?: string | null) =>
  (route && ROUTES[route]) || { label: route || 'Unrouted', tone: 'chat' }

export function RouteMark({ route }: { route?: string | null }) {
  const info = routeInfo(route)
  return (
    <span className="route-mark" data-tone={info.tone}>
      {info.label}
    </span>
  )
}

/* ---------- Hash routing: #/chat/<id>, #/library?book=<title>, #/readers ---------- */

export function useHash() {
  const [hash, setHash] = useState(() => location.hash.slice(1) || '/')
  useEffect(() => {
    const onChange = () => setHash(location.hash.slice(1) || '/')
    addEventListener('hashchange', onChange)
    return () => removeEventListener('hashchange', onChange)
  }, [])
  const [path, query = ''] = hash.split('?')
  return { segments: path.split('/').filter(Boolean).map(decodeURIComponent), params: new URLSearchParams(query) }
}

export const go = (path: string) => {
  location.hash = path
}

export const bookHref = (title: string) => `#/library?${new URLSearchParams({ book: title })}`

/* ---------- Book spines ---------- */

function hash(text: string) {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 16777619)
  return h >>> 0
}

// Deterministic cloth colour and height per title, so a book looks the same everywhere.
export function Spine({ title, authors, size = 'md' }: { title: string; authors?: string[]; size?: 'sm' | 'md' }) {
  const h = hash(title)
  const style = {
    '--spine-hue': h % 360,
    '--spine-height': `${86 + (h >> 9) % 15}%`,
  } as CSSProperties
  const label = authors?.length ? `${title}, by ${authors.join(', ')}` : title
  return (
    <a className="spine" data-size={size} data-cloth={(h >> 5) & 1 ? 'dark' : 'pale'} style={style} href={bookHref(title)} title={label} aria-label={label}>
      <span className="spine-title">{title}</span>
    </a>
  )
}

/* ---------- Small shared pieces ---------- */

export function Notice({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="notice" role="alert">
      <CircleAlert size={18} aria-hidden />
      <div>{children}</div>
      {action}
    </div>
  )
}

// Server timestamps are "yyyy-MM-dd HH:mm:ss" in GMT+8 (see @JsonFormat on the DTOs).
export const serverDate = (s: string) => new Date(s.replace(' ', 'T') + '+08:00')

export function dayLabel(date: Date) {
  const start = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const days = Math.round((start(new Date()) - start(date)) / 86_400_000)
  if (days === 0) return 'Today'
  if (days === 1) return 'Yesterday'
  return date.toLocaleDateString(undefined, {
    weekday: days < 7 ? 'long' : undefined,
    day: 'numeric',
    month: 'short',
    year: date.getFullYear() === new Date().getFullYear() ? undefined : 'numeric',
  })
}

export const timeLabel = (date: Date) => date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
