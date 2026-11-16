import { request } from './client.js'

export const listCommunities = () => request('/communities')
export const getCommunity = (id) => request(`/communities/${id}`)
export const createCommunity = (name) => request('/communities', { method: 'POST', body: { name } })
export const joinCommunity = (inviteCode) => request('/communities/join', { method: 'POST', body: { inviteCode } })
