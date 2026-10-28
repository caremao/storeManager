import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Eye, EyeOff, Plus, ShieldCheck, User, Zap } from 'lucide-react'
import { errorMessage } from '../../api/client'
import { useCreateInventory, useInventories, useMe, useTrades, useUpdateCampaign } from '../../api/hooks'
import type { CreateInventoryInput, Inventory, InventoryKind } from '../../api/types'
import { HistoryPanel } from '../../components/history/HistoryPanel'
import { TradeList } from '../../components/trades/TradeList'
import { ErrorBox, Spinner } from '../../components/ui/Feedback'
import { card, primaryButton, smallButton } from '../../components/ui/styles'
import { formatCp } from '../../lib/coins'
import { InventoryFormModal } from './InventoryFormModal'

export function GmDashboard() {
  const { data: inventories = [], isLoading } = useInventories()
  const { data: pending = [] } = useTrades('PENDING')
  const create = useCreateInventory()
  const [creating, setCreating] = useState<InventoryKind | null>(null)
  const shops = inventories.filter((i) => i.kind === 'SHOP')
  const characters = inventories.filter((i) => i.kind === 'CHARACTER')

  if (isLoading) return <Spinner />

  return (
    <div className="space-y-6">
      <section className={`${card} p-4 sm:p-6`}>
        <h2 className="flex items-center gap-2 text-xl font-semibold text-gray-100 mb-4">
          Solicitudes pendientes
          {pending.length > 0 && (
            <span className="px-2 py-0.5 text-sm rounded-full bg-yellow-900/50 text-yellow-300">{pending.length}</span>
          )}
        </h2>
        <TradeList trades={pending} mode="gm" emptyText="Nadie está esperando en el mostrador." />
      </section>

      <div className="grid lg:grid-cols-2 gap-6">
        <InventorySection title="Tiendas" kind="SHOP" items={shops} onCreate={() => setCreating('SHOP')} />
        <InventorySection title="Personajes" kind="CHARACTER" items={characters} onCreate={() => setCreating('CHARACTER')} />
      </div>

      <TableRules />

      <HistoryPanel title="Últimos movimientos" />

      {creating && (
        <InventoryFormModal
          kind={creating}
          onSubmit={(body) => create.mutateAsync({ ...(body as CreateInventoryInput), kind: creating })}
          onClose={() => setCreating(null)}
        />
      )}
    </div>
  )
}

function InventorySection({ title, kind, items, onCreate }: {
  title: string
  kind: InventoryKind
  items: Inventory[]
  onCreate: () => void
}) {
  return (
    <section className={`${card} p-4 sm:p-6`}>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-semibold text-gray-100">{title}</h2>
        <button type="button" onClick={onCreate} className={`${primaryButton} ${smallButton}`}>
          <Plus className="w-4 h-4" />
          {kind === 'SHOP' ? 'Nueva tienda' : 'Nuevo personaje'}
        </button>
      </div>
      {items.length === 0 ? (
        <p className="text-gray-400">{kind === 'SHOP' ? 'Todavía no hay tiendas.' : 'Todavía no hay personajes.'}</p>
      ) : (
        <ul className="space-y-2">
          {items.map((inv) => (
            <li key={inv.id}>
              <Link to={`/gm/inventario/${inv.id}`}
                className="flex items-center justify-between gap-3 p-3 rounded-lg bg-gray-900/50 border border-gray-700 hover:border-indigo-500 transition-colors">
                <div className="min-w-0">
                  <p className="font-medium text-gray-100 truncate">{inv.name}</p>
                  <p className="text-sm text-gray-400 truncate">
                    {kind === 'CHARACTER' ? inv.playerName || 'sin jugador asignado' : inv.description || ' '}
                  </p>
                </div>
                {kind === 'SHOP' ? <ShopBadges shop={inv} /> : (
                  <span className="flex items-center gap-1 text-sm text-yellow-400 whitespace-nowrap">
                    <User className="w-4 h-4 text-gray-400" />
                    {formatCp(inv.purse?.totalCp ?? 0)}
                  </span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function TableRules() {
  const { data: me } = useMe()
  const update = useUpdateCampaign()
  if (!me) return null
  const allowed = me.campaign.playersCanAddCoins
  return (
    <section className={`${card} p-4 sm:p-6`}>
      <h2 className="text-xl font-semibold text-gray-100 mb-3">Reglas de la mesa</h2>
      {update.error && <div className="mb-3"><ErrorBox>{errorMessage(update.error)}</ErrorBox></div>}
      <label className="flex items-start gap-3 text-gray-300">
        <input
          type="checkbox"
          checked={allowed}
          disabled={update.isPending}
          onChange={(e) => update.mutate({ playersCanAddCoins: e.target.checked })}
          className="mt-1 w-4 h-4 accent-indigo-500"
        />
        <span>
          Los jugadores pueden sumarse monedas
          <span className="block text-sm text-gray-500">
            Si lo apagas, gastan desde su bolsa pero las monedas nuevas (botín, pagos) se las das tú.
            Las ventas a una tienda siempre las apruebas tú.
          </span>
        </span>
      </label>
    </section>
  )
}

function ShopBadges({ shop }: { shop: Inventory }) {
  return (
    <span className="flex items-center gap-2 text-gray-400">
      {shop.visibleToPlayers
        ? <span title="Abierta"><Eye className="w-4 h-4 text-green-400" /></span>
        : <span title="Cerrada"><EyeOff className="w-4 h-4" /></span>}
      {shop.requiresApproval
        ? <span title="Requiere aprobación"><ShieldCheck className="w-4 h-4" /></span>
        : <span title="Compra directa"><Zap className="w-4 h-4 text-yellow-400" /></span>}
    </span>
  )
}
