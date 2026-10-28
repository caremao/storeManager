import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { LogOut, Store } from 'lucide-react'
import { useCampaignEvents } from '../../api/events'
import { useLogout, useMe } from '../../api/hooks'

const navClass = ({ isActive }: { isActive: boolean }) =>
  `px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
    isActive ? 'bg-gray-700 text-gray-100' : 'text-gray-400 hover:text-gray-200'
  }`

export function AppLayout() {
  const { data: me } = useMe()
  const logout = useLogout()
  const navigate = useNavigate()
  useCampaignEvents(!!me)

  const handleLogout = async () => {
    await logout.mutateAsync().catch(() => undefined)
    navigate('/', { replace: true })
  }

  return (
    <div className="min-h-screen bg-gray-900 text-gray-100">
      <header className="no-print bg-gray-800 border-b border-gray-700 shadow-md">
        <div className="container mx-auto px-4 py-3 flex flex-wrap items-center justify-between gap-3">
          <Link to="/" className="flex items-center gap-2 text-lg font-bold text-indigo-400 hover:text-indigo-300">
            <Store className="w-6 h-6" />
            <span>{me?.campaign.name ?? 'StoreManager'}</span>
          </Link>
          {me && (
            <nav className="flex items-center gap-1">
              {me.role === 'GM' ? (
                <>
                  <NavLink to="/gm" end className={navClass}>Panel</NavLink>
                  <NavLink to="/gm/mesa" className={navClass}>Mesa</NavLink>
                </>
              ) : (
                <>
                  <NavLink to="/pj" end className={navClass}>{me.characterName}</NavLink>
                  <NavLink to="/pj/tiendas" className={navClass}>Tiendas</NavLink>
                </>
              )}
              <button type="button" onClick={handleLogout} aria-label="Salir" title="Salir"
                className="ml-2 p-2 text-gray-400 hover:text-gray-200 rounded-lg hover:bg-gray-700">
                <LogOut className="w-5 h-5" />
              </button>
            </nav>
          )}
        </div>
      </header>
      <main className="container mx-auto px-4 py-6 pb-16">
        <Outlet />
      </main>
    </div>
  )
}
