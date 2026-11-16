import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { clearSession, loadSession, saveSession } from '../api/session.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [session, setSession] = useState(loadSession)

  const signIn = useCallback(({ token, user }) => {
    saveSession(token, user)
    setSession({ token, user })
  }, [])

  const updateUser = useCallback((user) => {
    setSession((s) => {
      saveSession(s.token, user)
      return { ...s, user }
    })
  }, [])

  const signOut = useCallback(() => {
    clearSession()
    setSession({ token: null, user: null })
  }, [])

  const value = useMemo(
    () => ({ token: session.token, user: session.user, signIn, signOut, updateUser }),
    [session, signIn, signOut, updateUser],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
