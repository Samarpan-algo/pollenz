// Token + user persisted in localStorage. Storage can throw (private mode), so every access is guarded.
const KEY = 'pollenz.session'

export function loadSession() {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return { token: null, user: null }
    const s = JSON.parse(raw)
    return s && s.token ? { token: s.token, user: s.user ?? null } : { token: null, user: null }
  } catch {
    return { token: null, user: null }
  }
}

export function saveSession(token, user) {
  try {
    localStorage.setItem(KEY, JSON.stringify({ token, user }))
  } catch {
    /* ignore */
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    /* ignore */
  }
}

export function getToken() {
  return loadSession().token
}
