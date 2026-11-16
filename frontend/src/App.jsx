import { Navigate, Route, Routes } from 'react-router'
import { Layout, RequireAuth } from './components/Layout.jsx'
import { useAuth } from './auth/AuthContext.jsx'
import { LoginPage, RegisterPage } from './pages/AuthPages.jsx'
import CommunitiesPage from './pages/CommunitiesPage.jsx'
import CommunityPage from './pages/CommunityPage.jsx'
import PoolPage from './pages/PoolPage.jsx'
import ProfilePage from './pages/ProfilePage.jsx'

function GuestOnly({ children }) {
  const { token } = useAuth()
  return token ? <Navigate to="/" replace /> : children
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route index element={<CommunitiesPage />} />
          <Route path="communities/:id" element={<CommunityPage />} />
          <Route path="pools/:id" element={<PoolPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
