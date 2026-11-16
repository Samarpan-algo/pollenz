import { Client } from '@stomp/stompjs'
import { getToken } from './session.js'

/** `ws(s)://<host>/ws` unless VITE_WS_URL overrides it. */
export function buildWsUrl(envUrl, loc) {
  if (envUrl) return envUrl
  return `${loc.protocol === 'https:' ? 'wss:' : 'ws:'}//${loc.host}/ws`
}

export const poolTopic = (poolId) => `/topic/pools/${poolId}`
export const chatDestination = (poolId) => `/app/pools/${poolId}/chat`
export const ERRORS_QUEUE = '/user/queue/errors'

/**
 * Opens a STOMP connection (auto-reconnect) and subscribes to one pool's topic and the user's error queue.
 * Callbacks: onConnect(), onDisconnect(), onEvent({ type, payload }), onError({ error, message }).
 * Returns { send(destination, body), close() }.
 */
export function openPoolSocket(poolId, { onConnect, onDisconnect, onEvent, onError }) {
  const client = new Client({
    brokerURL: buildWsUrl(import.meta.env.VITE_WS_URL, window.location),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    // The token is read on every (re)connect so a re-login is picked up.
    beforeConnect: () => {
      client.connectHeaders = { Authorization: `Bearer ${getToken() ?? ''}` }
    },
    onConnect: () => {
      client.subscribe(poolTopic(poolId), (frame) => {
        try {
          onEvent(JSON.parse(frame.body))
        } catch {
          /* ignore malformed frames */
        }
      })
      client.subscribe(ERRORS_QUEUE, (frame) => {
        try {
          onError(JSON.parse(frame.body))
        } catch {
          onError({ message: 'Message not sent' })
        }
      })
      onConnect()
    },
    onWebSocketClose: () => onDisconnect(),
    onStompError: () => onDisconnect(),
  })
  client.activate()
  return {
    send: (destination, body) => {
      if (!client.connected) return false
      client.publish({ destination, body: JSON.stringify(body), headers: { 'content-type': 'application/json' } })
      return true
    },
    close: () => client.deactivate(),
  }
}
