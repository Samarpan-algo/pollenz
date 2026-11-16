import { useCallback, useEffect, useState } from 'react'

/** Loads data on mount and whenever `deps` change. Returns { data, error, loading, reload }. */
export function useLoad(fetcher, deps) {
  const [state, setState] = useState({ data: null, error: null, loading: true })

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(fetcher, deps)

  useEffect(() => {
    let cancelled = false
    setState((s) => ({ ...s, loading: true }))
    load().then(
      (data) => !cancelled && setState({ data, error: null, loading: false }),
      (error) => !cancelled && setState({ data: null, error, loading: false }),
    )
    return () => {
      cancelled = true
    }
  }, [load])

  const reload = useCallback(
    () =>
      load().then(
        (data) => setState({ data, error: null, loading: false }),
        (error) => setState((s) => ({ ...s, error, loading: false })),
      ),
    [load],
  )

  return { ...state, reload }
}
