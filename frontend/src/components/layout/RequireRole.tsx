import { Navigate, Outlet } from 'react-router-dom'
import { useMe } from '../../api/hooks'
import type { Role } from '../../api/types'
import { Spinner } from '../ui/Feedback'

/** Deja pasar solo al rol indicado; al resto lo manda a su propia pantalla. */
export function RequireRole({ role }: { role: Role }) {
  const { data: me, isLoading } = useMe()
  if (isLoading) return <Spinner />
  if (!me) return <Navigate to="/" replace />
  if (me.role !== role) return <Navigate to={me.role === 'GM' ? '/gm' : '/pj'} replace />
  return <Outlet />
}
