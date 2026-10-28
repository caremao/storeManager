import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, HandCoins, Pencil, RefreshCw, Trash2 } from 'lucide-react'
import { isStatus } from '../../api/client'
import {
  useChangeCurrency,
  useConvertCurrency,
  useDeleteInventory,
  useInventory,
  useRegenerateToken,
  useUpdateInventory,
} from '../../api/hooks'
import type { Inventory, Item, UpdateInventoryInput } from '../../api/types'
import { ConvertModal } from '../../components/currency/ConvertModal'
import { CurrencyModal } from '../../components/currency/CurrencyModal'
import { PurseWidget } from '../../components/currency/PurseWidget'
import { HistoryPanel } from '../../components/history/HistoryPanel'
import { ItemsManager } from '../../components/items/ItemsManager'
import { Spinner } from '../../components/ui/Feedback'
import { QrImage } from '../../components/ui/QrImage'
import { card, dangerButton, secondaryButton, smallButton, successButton } from '../../components/ui/styles'
import { NotFound } from '../NotFound'
import { DirectTradeModal } from './DirectTradeModal'
import { InventoryFormModal } from './InventoryFormModal'

/** Vista del GM de una tienda o un personaje. */
export function GmInventory() {
  const { id } = useParams<{ id: string }>()
  const { data: inventory, isLoading, error } = useInventory(id)

  if (isLoading) return <Spinner />
  if (!inventory || isStatus(error, 404)) return <NotFound />
  return <InventoryView key={inventory.id} inventory={inventory} />
}

function InventoryView({ inventory }: { inventory: Inventory }) {
  const navigate = useNavigate()
  const update = useUpdateInventory(inventory.id)
  const remove = useDeleteInventory()
  const currency = useChangeCurrency(inventory.id)
  const convert = useConvertCurrency(inventory.id)
  const [converting, setConverting] = useState(false)
  const [editing, setEditing] = useState(false)
  const [coins, setCoins] = useState<'add' | 'spend' | null>(null)
  const [selling, setSelling] = useState<Item | null>(null)
  const isShop = inventory.kind === 'SHOP'

  const handleDelete = async () => {
    const what = isShop ? 'la tienda' : 'el personaje'
    if (!window.confirm(`¿Borrar ${what} ${inventory.name} con todo su inventario? El historial se conserva.`)) return
    await remove.mutateAsync(inventory.id)
    navigate('/gm', { replace: true })
  }

  return (
    <div className="space-y-6">
      <Link to="/gm" className="inline-flex items-center gap-1 text-indigo-400 hover:text-indigo-300">
        <ArrowLeft className="w-4 h-4" /> Panel
      </Link>

      <section className={`${card} p-4 sm:p-6`}>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-sm uppercase tracking-wider text-gray-400">{isShop ? 'Tienda' : 'Personaje'}</p>
            <h1 className="text-3xl font-bold text-gray-100">{inventory.name}</h1>
            {inventory.playerName && <p className="text-gray-400">Jugador: {inventory.playerName}</p>}
            {inventory.description && <p className="mt-2 text-gray-300 whitespace-pre-wrap">{inventory.description}</p>}
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={() => setEditing(true)} className={`${secondaryButton} ${smallButton}`}>
              <Pencil className="w-4 h-4" /> Editar
            </button>
            <button type="button" onClick={handleDelete} className={`${dangerButton} ${smallButton}`}>
              <Trash2 className="w-4 h-4" /> Borrar
            </button>
          </div>
        </div>
        {isShop && <ShopSwitches shop={inventory} onChange={(body) => update.mutateAsync(body)} />}
      </section>

      <div className={`grid gap-6 ${isShop ? '' : 'lg:grid-cols-[1fr_auto]'}`}>
        <PurseWidget
          title={isShop ? 'Caja' : 'Bolsa'}
          purse={inventory.purse}
          unlimited={isShop && !!inventory.unlimitedFunds}
          onAdd={() => setCoins('add')}
          onSpend={() => setCoins('spend')}
          onConvert={() => setConverting(true)}
        />
        {!isShop && <CharacterQr character={inventory} />}
      </div>

      <ItemsManager
        inventory={inventory}
        title={isShop ? 'Stock' : 'Inventario'}
        editable
        priceOf={isShop ? (item) => item.priceCp : undefined}
        emptyText={isShop ? 'La tienda está vacía: agrega su stock' : 'Este personaje no lleva nada'}
        detailActions={isShop ? (item, close) => (
          <button type="button" onClick={() => { close(); setSelling(item) }} className={`${successButton} w-full`}>
            <HandCoins className="w-4 h-4" /> Vender a un personaje
          </button>
        ) : undefined}
      />

      <HistoryPanel inventoryId={inventory.id} />

      {editing && (
        <InventoryFormModal
          kind={inventory.kind}
          inventory={inventory}
          onSubmit={(body) => update.mutateAsync(body as UpdateInventoryInput)}
          onClose={() => setEditing(false)}
        />
      )}
      {coins && <CurrencyModal mode={coins} onSubmit={(c) => currency.mutateAsync(c)} onClose={() => setCoins(null)} />}
      {converting && inventory.purse && (
        <ConvertModal purse={inventory.purse} onSubmit={(b) => convert.mutateAsync(b)} onClose={() => setConverting(false)} />
      )}
      {selling && <DirectTradeModal shop={inventory} item={selling} onClose={() => setSelling(null)} />}
    </div>
  )
}

function ShopSwitches({ shop, onChange }: { shop: Inventory; onChange: (body: UpdateInventoryInput) => Promise<unknown> }) {
  const chip = (active: boolean) =>
    `px-3 py-1.5 rounded-full text-sm border transition-colors ${
      active ? 'bg-indigo-900/50 border-indigo-500 text-indigo-200' : 'border-gray-600 text-gray-400 hover:text-gray-200'
    }`
  return (
    <div className="flex flex-wrap gap-2 mt-4">
      <button type="button" className={chip(!!shop.visibleToPlayers)}
        onClick={() => onChange({ visibleToPlayers: !shop.visibleToPlayers })}>
        {shop.visibleToPlayers ? 'Abierta a los jugadores' : 'Cerrada'}
      </button>
      <button type="button" className={chip(!!shop.requiresApproval)}
        onClick={() => onChange({ requiresApproval: !shop.requiresApproval })}>
        {shop.requiresApproval ? 'Con aprobación del GM' : 'Compra directa'}
      </button>
      <span className="px-3 py-1.5 text-sm text-gray-400">
        Vende al {shop.sellMarkupPct}% · compra al {shop.buybackPct}%
      </span>
    </div>
  )
}

function CharacterQr({ character }: { character: Inventory }) {
  const regenerate = useRegenerateToken()
  const handleRegenerate = () => {
    if (window.confirm('El QR actual dejará de funcionar y el teléfono que lo usa tendrá que escanear el nuevo. ¿Seguir?')) {
      regenerate.mutate(character.id)
    }
  }
  if (!character.joinUrl) return null
  return (
    <div className={`${card} p-4 flex flex-col items-center gap-3`}>
      <QrImage value={character.joinUrl} size={180} alt={`QR de ${character.name}`} />
      <button type="button" onClick={handleRegenerate} disabled={regenerate.isPending}
        className={`${secondaryButton} ${smallButton}`}>
        <RefreshCw className="w-4 h-4" /> Nuevo QR
      </button>
    </div>
  )
}
