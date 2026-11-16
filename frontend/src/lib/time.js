/** "2h 15m left", "5m left", or "Closed". */
export function timeLeft(iso, now = Date.now()) {
  const ms = new Date(iso).getTime() - now
  if (Number.isNaN(ms)) return ''
  if (ms <= 0) return 'Closed'
  const mins = Math.ceil(ms / 60000)
  const d = Math.floor(mins / 1440)
  const h = Math.floor((mins % 1440) / 60)
  const m = mins % 60
  if (d > 0) return `${d}d ${h}h left`
  if (h > 0) return `${h}h ${m}m left`
  return `${m}m left`
}

export function formatDateTime(iso) {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}
