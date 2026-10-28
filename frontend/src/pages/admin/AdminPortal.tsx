import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { KeyRound, LogIn, LogOut, Store, Trash2, User, X } from 'lucide-react'
import { errorMessage } from '../../api/client'
import {
  useAdminCampaigns,
  useAdminLogin,
  useAdminLogout,
  useAdminStatus,
  useCloseSession,
  useEnterAsGm,
} from '../../api/hooks'
import type { AdminCampaign, AdminInventory, AdminSession } from '../../api/types'
import { EmptyState, ErrorBox, Spinner, SuccessBox } from '../../components/ui/Feedback'
import { Money } from '../../components/ui/Money'
import { card, dangerButton, input, label, primaryButton, secondaryButton, smallButton } from '../../components/ui/styles'
import { DeleteCampaignModal, ResetPassphraseModal } from './AdminModals'

const when = (iso: string) => new Date(iso).toLocaleString('es', { dateStyle: 'short', timeStyle: 'short' })

/** Portal de todo el servidor: existe solo si el servidor tiene ADMIN_KEY, y pide esa clave. */
export function AdminPortal() {
  const { data: status, isLoading, error } = useAdminStatus()
  if (isLoading) return <Spinner />
  if (error) return <ErrorBox>{errorMessage(error)}</ErrorBox>
  if (status === 'off') {
    return (
      <EmptyState title="El portal de administración está apagado">
        Para encenderlo, define <code className="text-gray-300">ADMIN_KEY</code> (16 caracteres o más) en el
        .env del servidor y reinicia la app.
      </EmptyState>
    )
  }
  return status === 'in' ? <AdminDashboard /> : <AdminLogin />
}

function AdminLogin() {
  const login = useAdminLogin()
  const [key, setKey] = useState('')

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    login.mutate(key)
  }

  return (
    <form onSubmit={submit} className={`${card} max-w-md mx-auto p-6 space-y-4`}>
      <h1 className="text-xl font-semibold text-gray-100">Portal de administración</h1>
      <p className="text-gray-400">Ve y administra todas las campañas del servidor.</p>
      {login.error && <ErrorBox>{errorMessage(login.error)}</ErrorBox>}
      <div>
        <label htmlFor="admin-key" className={label}>Clave de administración</label>
        <input id="admin-key" type="password" value={key} onChange={(e) => setKey(e.target.value)}
          autoComplete="current-password" className={input} />
      </div>
      <button type="submit" disabled={login.isPending || !key} className={`${primaryButton} w-full`}>
        {login.isPending ? 'Entrando…' : 'Entrar'}
      </button>
    </form>
  )
}

function AdminDashboard() {
  const { data: campaigns = [], isLoading, error } = useAdminCampaigns()
  const logout = useAdminLogout()
  const all = campaigns.flatMap((c) => c.inventories)
  const shops = all.filter((i) => i.kind === 'SHOP').length
  const sessions = campaigns.reduce((n, c) => n + c.sessions.length, 0)

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-100">Administración</h1>
          <p className="text-gray-400">
            {campaigns.length} {campaigns.length === 1 ? 'campaña' : 'campañas'} · {shops} tiendas ·{' '}
            {all.length - shops} personajes · {sessions} sesiones abiertas
          </p>
        </div>
        <button type="button" onClick={() => logout.mutate()} className={`${secondaryButton} ${smallButton}`}>
          <LogOut className="w-4 h-4" /> Salir del portal
        </button>
      </div>
      {error && <ErrorBox>{errorMessage(error)}</ErrorBox>}
      {isLoading ? <Spinner /> : campaigns.length === 0 ? (
        <EmptyState title="Todavía no hay campañas">Se crean desde la pantalla de inicio.</EmptyState>
      ) : (
        campaigns.map((c) => <CampaignCard key={c.id} campaign={c} />)
      )}
    </div>
  )
}

function CampaignCard({ campaign }: { campaign: AdminCampaign }) {
  const navigate = useNavigate()
  const enter = useEnterAsGm()
  const [modal, setModal] = useState<'passphrase' | 'delete' | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const shops = campaign.inventories.filter((i) => i.kind === 'SHOP')
  const characters = campaign.inventories.filter((i) => i.kind === 'CHARACTER')
  const items = campaign.inventories.reduce((n, i) => n + i.items, 0)

  const enterAsGm = async () => {
    try {
      await enter.mutateAsync(campaign.id)
      navigate('/gm')
    } catch {
      // el error se muestra abajo
    }
  }

  return (
    <section className={`${card} p-4 sm:p-6 space-y-4`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-xl font-semibold text-gray-100 truncate">{campaign.name}</h2>
          <p className="text-sm text-gray-400">
            <span className="font-mono">{campaign.slug}</span> · creada {when(campaign.createdAt)} ·{' '}
            {campaign.lastActivityAt ? `último movimiento ${when(campaign.lastActivityAt)}` : 'sin movimientos'}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={enterAsGm} disabled={enter.isPending} className={`${primaryButton} ${smallButton}`}>
            <LogIn className="w-4 h-4" /> Entrar como GM
          </button>
          <button type="button" onClick={() => setModal('passphrase')} className={`${secondaryButton} ${smallButton}`}>
            <KeyRound className="w-4 h-4" /> Contraseña
          </button>
          <button type="button" onClick={() => setModal('delete')} className={`${dangerButton} ${smallButton}`}>
            <Trash2 className="w-4 h-4" /> Borrar
          </button>
        </div>
      </div>

      {enter.error && <ErrorBox>{errorMessage(enter.error)}</ErrorBox>}
      {notice && <SuccessBox>{notice}</SuccessBox>}

      <dl className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        <Stat label="Tiendas" value={shops.length} />
        <Stat label="Personajes" value={characters.length} />
        <Stat label="Objetos" value={items} />
        <Stat label="Solicitudes abiertas" value={campaign.openTrades} />
      </dl>

      <div className="grid lg:grid-cols-2 gap-4">
        <div>
          <h3 className="mb-2 text-sm font-semibold uppercase tracking-wider text-gray-400">Tiendas y personajes</h3>
          {campaign.inventories.length === 0 ? (
            <p className="text-gray-500">Vacía.</p>
          ) : (
            <ul className="max-h-80 overflow-y-auto space-y-1">
              {[...shops, ...characters].map((inv) => <InventoryRow key={inv.id} inventory={inv} />)}
            </ul>
          )}
        </div>
        <SessionList sessions={campaign.sessions} />
      </div>

      {modal === 'passphrase' && (
        <ResetPassphraseModal
          campaign={campaign}
          onDone={() => setNotice('Contraseña cambiada. Las sesiones de GM de esta campaña se cerraron.')}
          onClose={() => setModal(null)}
        />
      )}
      {modal === 'delete' && <DeleteCampaignModal campaign={campaign} onClose={() => setModal(null)} />}
    </section>
  )
}

function Stat({ label: text, value }: { label: string; value: number }) {
  return (
    <div className="p-3 rounded-lg bg-gray-900/50 border border-gray-700">
      <dt className="text-xs text-gray-400">{text}</dt>
      <dd className="text-xl font-semibold text-gray-100">{value.toLocaleString('es')}</dd>
    </div>
  )
}

function InventoryRow({ inventory: inv }: { inventory: AdminInventory }) {
  const isShop = inv.kind === 'SHOP'
  const Icon = isShop ? Store : User
  const detail = isShop ? (inv.visibleToPlayers ? 'abierta' : 'cerrada') : inv.playerName || 'sin jugador asignado'
  return (
    <li className="flex items-center justify-between gap-3 px-3 py-2 rounded-lg bg-gray-900/50 border border-gray-700">
      <div className="flex items-center gap-2 min-w-0">
        <Icon className="w-4 h-4 flex-shrink-0 text-gray-400" />
        <div className="min-w-0">
          <p className="text-gray-100 truncate">{inv.name}</p>
          <p className="text-xs text-gray-400 truncate">
            {detail} · {inv.items} {inv.items === 1 ? 'objeto' : 'objetos'}
          </p>
        </div>
      </div>
      <Money cp={inv.totalCp} size="sm" />
    </li>
  )
}

function SessionList({ sessions }: { sessions: AdminSession[] }) {
  const close = useCloseSession()

  const closeSession = (s: AdminSession) => {
    const who = s.role === 'GM' ? 'este GM' : s.characterName ?? 'este jugador'
    if (window.confirm(`¿Cerrar la sesión de ${who}? Ese navegador vuelve a la pantalla de inicio.`)) close.mutate(s.id)
  }

  return (
    <div>
      <h3 className="mb-2 text-sm font-semibold uppercase tracking-wider text-gray-400">Sesiones abiertas</h3>
      {close.error && <div className="mb-2"><ErrorBox>{errorMessage(close.error)}</ErrorBox></div>}
      {sessions.length === 0 ? (
        <p className="text-gray-500">Nadie conectado.</p>
      ) : (
        <ul className="max-h-80 overflow-y-auto space-y-1">
          {sessions.map((s) => (
            <li key={s.id} className="flex items-center justify-between gap-3 px-3 py-2 rounded-lg bg-gray-900/50 border border-gray-700">
              <div className="min-w-0">
                <p className="text-gray-100 truncate">
                  <span className={`mr-2 px-1.5 py-0.5 rounded text-xs font-semibold ${
                    s.role === 'GM' ? 'bg-indigo-900/60 text-indigo-300' : 'bg-gray-700 text-gray-300'}`}>
                    {s.role === 'GM' ? 'GM' : 'Jugador'}
                  </span>
                  {s.characterName}
                </p>
                <p className="text-xs text-gray-400">desde {when(s.createdAt)} · vence {when(s.expiresAt)}</p>
              </div>
              <button type="button" onClick={() => closeSession(s)} disabled={close.isPending}
                aria-label="Cerrar sesión" title="Cerrar sesión"
                className="p-1.5 text-gray-400 hover:text-red-300 rounded-lg hover:bg-gray-700 disabled:opacity-50">
                <X className="w-4 h-4" />
              </button>
            </li>
          ))}
        </ul>
      )}
      {sessions.some((s) => s.role === 'PLAYER') && (
        <p className="mt-2 text-xs text-gray-500">
          Un jugador sacado así puede volver con su QR. Para que no pueda, genera un QR nuevo desde el personaje.
        </p>
      )}
    </div>
  )
}
