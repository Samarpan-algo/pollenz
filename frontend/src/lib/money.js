// All money is integer paise. Never use floats for arithmetic.

/** 1250 -> "₹12.50", 123456 -> "₹1,234.56". */
export function formatPaise(paise) {
  const n = Number(paise)
  if (!Number.isInteger(n)) return '₹0.00'
  const abs = Math.abs(n)
  const rupees = Math.floor(abs / 100)
  const rem = String(abs % 100).padStart(2, '0')
  return `${n < 0 ? '-' : ''}₹${rupees.toLocaleString('en-IN')}.${rem}`
}

/** 1250 -> "12.50" (plain, for inputs and UPI links). */
export function paiseToInput(paise) {
  const abs = Math.abs(paise)
  return `${paise < 0 ? '-' : ''}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}

/**
 * Parses a rupee string like "12", "12.5", "₹1,234.50" into integer paise by string
 * manipulation. Returns null when invalid (negative, >2 decimals, not a number).
 */
export function parseRupees(input) {
  if (typeof input !== 'string') return null
  const s = input.trim().replace(/^₹\s*/, '').replace(/,/g, '')
  const m = /^(\d*)(?:\.(\d{0,2}))?$/.exec(s)
  if (!m || (m[1] === '' && !m[2])) return null
  const rupees = m[1] === '' ? 0 : Number(m[1])
  const frac = (m[2] ?? '').padEnd(2, '0')
  const paise = rupees * 100 + Number(frac)
  return Number.isSafeInteger(paise) ? paise : null
}

/** Backend cap for any amount field. */
export const MAX_PAISE = 1_000_000_000
