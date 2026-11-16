import { useCallback, useState } from 'react'

/**
 * Runs an async action with busy + error state. On a 409 the server message is shown and
 * `onConflict` (usually a refetch) is called.
 */
export function useAction(onConflict) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const run = useCallback(
    async (fn) => {
      setBusy(true)
      setError(null)
      try {
        await fn()
        return true
      } catch (e) {
        setError(e.message || 'Something went wrong')
        if (e.status === 409 && onConflict) onConflict()
        return false
      } finally {
        setBusy(false)
      }
    },
    [onConflict],
  )

  return { busy, error, run, setError }
}
