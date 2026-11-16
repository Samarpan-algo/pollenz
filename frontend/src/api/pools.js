import { request } from './client.js'

export const listPools = (communityId, status) => request(`/communities/${communityId}/pools`, { query: { status } })
export const createPool = (communityId, body) => request(`/communities/${communityId}/pools`, { method: 'POST', body })
export const getPool = (id) => request(`/pools/${id}`)

export const joinPool = (id) => request(`/pools/${id}/join`, { method: 'POST' })
export const leavePool = (id) => request(`/pools/${id}/leave`, { method: 'DELETE' })

export const addItem = (id, item) => request(`/pools/${id}/items`, { method: 'POST', body: item })
export const updateItem = (id, itemId, item) => request(`/pools/${id}/items/${itemId}`, { method: 'PUT', body: item })
export const deleteItem = (id, itemId) => request(`/pools/${id}/items/${itemId}`, { method: 'DELETE' })

export const lockPool = (id) => request(`/pools/${id}/lock`, { method: 'POST' })
export const cancelPool = (id) => request(`/pools/${id}/cancel`, { method: 'POST' })
export const orderPool = (id, { feesPaise, items }) =>
  request(`/pools/${id}/order`, { method: 'POST', body: { feesPaise, items } })
export const deliverPool = (id) => request(`/pools/${id}/deliver`, { method: 'POST' })
export const markPaid = (id, memberId) => request(`/pools/${id}/members/${memberId}/paid`, { method: 'POST' })

export const getMessages = (id) => request(`/pools/${id}/messages`)
