import { useCallback, useEffect, useRef, useState } from 'react'
import { getPool } from '../api/pools.js'

export const POLL_INTERVAL_MS = 5000

/**
 * Keeps a pool detail fresh. Currently polls every 5 s while the tab is visible.
 * This is the only place that knows how updates arrive: swap the effect body for a
 * WebSocket subscription later and keep the returned shape.
 */
export function usePoolDetail(id) {
  const [pool, setPool] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const seq = useRef(0)

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

  useEffect(() => {
    refresh()
    const tick = () => {
      if (document.visibilityState === 'visible') refresh()
    }
    const timer = setInterval(tick, POLL_INTERVAL_MS)
    document.addEventListener('visibilitychange', tick)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', tick)
    }
  }, [refresh])

  return { pool, error, loading, refresh }
}
