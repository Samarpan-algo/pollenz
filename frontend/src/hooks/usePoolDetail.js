import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext.jsx'
import { getPool } from '../api/pools.js'
import { chatDestination, openPoolSocket } from '../api/realtime.js'

/** Non-members cannot subscribe, so they poll slowly to keep the Join view fresh. */
export const POLL_INTERVAL_MS = 15000

/**
 * Keeps a pool detail fresh. Pool members hold a STOMP subscription to /topic/pools/{id}: every
 * non-chat event refetches the detail, and so does every (re)connect. Non-members poll every 15 s.
 * Returns { pool, error, loading, refresh, realtime } where realtime = { connected, sendChat,
 * addChatListener }. Chat listeners get CHAT_MESSAGE events ({ type, payload }) and
 * { type: 'CHAT_ERROR', payload: { message } } when the server drops a message.
 */
export function usePoolDetail(id) {
  const { user } = useAuth()
  const [pool, setPool] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [connected, setConnected] = useState(false)
  const seq = useRef(0)
  const socket = useRef(null)
  const chatListeners = useRef(new Set())

  const refresh = useCallback(async () => {
    const mine = ++seq.current
    try {
      const data = await getPool(id)
      if (mine !== seq.current) return // a newer request already won
      setPool(data)
      setError(null)
    } catch (e) {
      if (mine !== seq.current) return
      setError(e)
    } finally {
      if (mine === seq.current) setLoading(false)
    }
  }, [id])

  const isMember = !!pool && pool.members.some((m) => m.userId === user?.id)

  useEffect(() => {
    refresh()
  }, [refresh])

  useEffect(() => {
    if (isMember) return undefined
    const tick = () => {
      if (document.visibilityState === 'visible') refresh()
    }
    const timer = setInterval(tick, POLL_INTERVAL_MS)
    document.addEventListener('visibilitychange', tick)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', tick)
    }
  }, [isMember, refresh])

  useEffect(() => {
    if (!isMember) return undefined
    const emit = (event) => chatListeners.current.forEach((fn) => fn(event))
    const conn = openPoolSocket(id, {
      onConnect: () => {
        setConnected(true)
        refresh() // anything missed while the socket was down
      },
      onDisconnect: () => setConnected(false),
      onEvent: (event) => {
        if (event.type === 'CHAT_MESSAGE') emit(event)
        else refresh()
      },
      onError: (err) => emit({ type: 'CHAT_ERROR', payload: err }),
    })
    socket.current = conn
    return () => {
      socket.current = null
      conn.close()
      setConnected(false)
    }
  }, [id, isMember, refresh])

  const sendChat = useCallback((body) => !!socket.current && socket.current.send(chatDestination(id), { body }), [id])

  const addChatListener = useCallback((fn) => {
    chatListeners.current.add(fn)
    return () => chatListeners.current.delete(fn)
  }, [])

  return { pool, error, loading, refresh, realtime: { connected, sendChat, addChatListener } }
}
