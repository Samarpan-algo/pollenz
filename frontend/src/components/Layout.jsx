import { Link, NavLink, Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/AuthContext.jsx'

export function RequireAuth() {
  const { token } = useAuth()
  const location = useLocation()
  if (!token) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

export function Layout() {
  const { user, signOut } = useAuth()
  const link = ({ isActive }) => (isActive ? 'font-semibold text-accent' : 'text-slate-600 hover:text-slate-900')
  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <nav className="mx-auto flex max-w-3xl items-center gap-4 px-4 py-3 text-sm">
          <Link to="/" className="text-lg font-bold text-accent">
            Pollenz
          </Link>
          <NavLink to="/" end className={link}>
            Communities
          </NavLink>
          <NavLink to="/profile" className={link}>
            Profile
          </NavLink>
          <span className="ml-auto hidden truncate text-slate-500 sm:inline">{user?.name}</span>
          <button type="button" onClick={signOut} className="ml-auto text-slate-600 hover:text-slate-900 sm:ml-0">
            Log out
          </button>
        </nav>
      </header>
      <main className="mx-auto max-w-3xl space-y-6 px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
