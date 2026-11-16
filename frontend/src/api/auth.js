import { request } from './client.js'

export const register = ({ name, email, password }) =>
  request('/auth/register', { method: 'POST', body: { name, email, password }, auth: false })

export const login = ({ email, password }) =>
  request('/auth/login', { method: 'POST', body: { email, password }, auth: false })

export const getMe = () => request('/users/me')

/** Pass upiId '' to clear it. */
export const updateMe = ({ name, upiId }) => request('/users/me', { method: 'PATCH', body: { name, upiId } })
