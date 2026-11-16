import { clearSession, getToken } from './session.js'

const BASE_URL = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

/**
 * One fetch wrapper. Options: method, body (JSON-encoded), query (object), auth (default true).
 * Returns parsed JSON, or null for empty responses (204).
 */
export async function request(path, { method = 'GET', body, query, auth = true } = {}) {
  let url = BASE_URL + path
  if (query) {
    const qs = new URLSearchParams(Object.entries(query).filter(([, v]) => v != null && v !== ''))
    const str = qs.toString()
    if (str) url += `?${str}`
  }
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = auth ? getToken() : null
  if (token) headers.Authorization = `Bearer ${token}`

  let res
  try {
    res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Check your connection.')
  }

  const text = await res.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = null
    }
  }

  if (!res.ok) {
    // Only an authenticated request getting 401 means the session died. A failed login is just an error.
    if (res.status === 401 && token) {
      clearSession()
      window.location.assign('/login')
    }
    throw new ApiError(res.status, data?.error || 'UNKNOWN', data?.message || `Request failed (${res.status})`)
  }
  return data
}
