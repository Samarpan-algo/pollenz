const BUTTON_STYLES = {
  primary: 'bg-accent text-white hover:bg-accent-dark border-transparent',
  secondary: 'bg-white text-slate-800 hover:bg-slate-100 border-slate-300',
  danger: 'bg-white text-red-700 hover:bg-red-50 border-red-300',
}

export function Button({ variant = 'primary', className = '', type = 'button', ...props }) {
  return (
    <button
      type={type}
      className={`inline-flex items-center justify-center rounded-md border px-4 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50 ${BUTTON_STYLES[variant]} ${className}`}
      {...props}
    />
  )
}

const INPUT_CLASS =
  'block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-base focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent'

export function Input(props) {
  return <input className={INPUT_CLASS} {...props} />
}

export function Select({ children, ...props }) {
  return (
    <select className={INPUT_CLASS} {...props}>
      {children}
    </select>
  )
}

export function Field({ label, hint, children }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-slate-500">{hint}</span>}
    </label>
  )
}

export function ErrorText({ children }) {
  if (!children) return null
  return (
    <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
      {children}
    </p>
  )
}

export function Card({ className = '', ...props }) {
  return <div className={`rounded-lg border border-slate-200 bg-white p-4 ${className}`} {...props} />
}

export function Section({ title, children, action }) {
  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

export function Loading({ children = 'Loading...' }) {
  return <p className="py-8 text-center text-sm text-slate-500">{children}</p>
}

export function Empty({ children }) {
  return <p className="rounded-md border border-dashed border-slate-300 px-3 py-6 text-center text-sm text-slate-500">{children}</p>
}

export function ProgressBar({ current, target }) {
  const pct = target > 0 ? Math.min(100, Math.floor((current * 100) / target)) : 0
  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={pct}
      className="h-2 w-full overflow-hidden rounded-full bg-slate-200"
    >
      <div className="h-full bg-accent" style={{ width: `${pct}%` }} />
    </div>
  )
}

const STATUS_STYLES = {
  OPEN: 'bg-indigo-100 text-indigo-800',
  LOCKED: 'bg-amber-100 text-amber-800',
  ORDERED: 'bg-slate-200 text-slate-800',
  DELIVERED: 'bg-slate-200 text-slate-800',
  SETTLED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
  EXPIRED: 'bg-red-100 text-red-800',
}

export function StatusBadge({ status }) {
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_STYLES[status] ?? 'bg-slate-100'}`}>
      {status}
    </span>
  )
}

export const PLATFORMS = ['BLINKIT', 'INSTAMART', 'ZEPTO', 'BIGBASKET', 'OTHER']
export const POOL_STATUSES = ['OPEN', 'LOCKED', 'ORDERED', 'DELIVERED', 'SETTLED', 'CANCELLED', 'EXPIRED']

export function platformLabel(p) {
  return p ? p.charAt(0) + p.slice(1).toLowerCase() : ''
}
