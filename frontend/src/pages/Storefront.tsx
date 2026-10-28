import { useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Store } from 'lucide-react'
import { errorMessage, isStatus } from '../api/client'
import { useMe, useStorefront } from '../api/hooks'
import type { StorefrontItem } from '../api/types'
import { ItemCard, type CardItem } from '../components/items/ItemCard'
import { ItemFilters } from '../components/items/ItemFilters'
import { NothingHere } from '../components/items/ItemsList'
import { useItemFilter } from '../components/items/filter'
import { PROPERTY_LABELS, RARITY_LABELS, TYPE_LABELS, formatProperty } from '../components/items/utils'
import { EmptyState, ErrorBox, Spinner } from '../components/ui/Feedback'
import { Modal } from '../components/ui/Modal'
import { Money } from '../components/ui/Money'
import { card } from '../components/ui/styles'

/** La vitrina solo trae lo que se vende: nada oculto ni de valor desconocido. */
const toCard = (item: StorefrontItem): CardItem => ({
  ...item,
  perceivedValueCp: null,
  valueRevealed: true,
  hidden: false,
})

/**
 * Destino del QR de una tienda: el stock a la vista, sin sesión y de solo lectura. El token va en el
 * fragmento (`/vitrina#token`), que el navegador no envía al servidor ni deja en el Referer. Un jugador de
 * la campaña pasa directo al mostrador; con `replace`, "atrás" no lo vuelve a traer acá.
 */
export function Storefront() {
  const token = useLocation().hash.slice(1)
  const { data: me } = useMe()
  const { data: shop, isLoading, error } = useStorefront(token)
  const [detail, setDetail] = useState<StorefrontItem | null>(null)

  if (!token) return <EmptyState title="Este link no trae el código de la tienda" />
  if (isLoading) return <Spinner label="Abriendo la vitrina…" />
  if (isStatus(error, 404)) {
    return <EmptyState title="Esta tienda está cerrada">O el QR ya no sirve: pídele el nuevo al GM.</EmptyState>
  }
  if (!shop) return <ErrorBox>{errorMessage(error)}</ErrorBox>
  if (shop.shopId) return <Navigate to={`/pj/tiendas/${shop.shopId}`} replace />

  return (
    <div className="space-y-6">
      <section className={`${card} p-4 sm:p-6`}>
        <p className="flex items-center gap-1 text-sm uppercase tracking-wider text-gray-400">
          <Store className="w-4 h-4" /> Vitrina
        </p>
        <h1 className="text-3xl font-bold text-gray-100">{shop.name}</h1>
        {shop.description && <p className="mt-2 text-gray-300 whitespace-pre-wrap">{shop.description}</p>}
        <p className="mt-4 text-sm text-gray-400">
          {me?.role === 'GM'
            ? 'Así la ve quien no tiene sesión. Los jugadores de la campaña van directo al mostrador.'
            : 'Para comprar o vender, escanea el QR de tu personaje y vuelve a abrir esta vitrina.'}
        </p>
      </section>

      {shop.items.length === 0 ? (
        <EmptyState title="No hay nada a la vista" />
      ) : (
        <Showcase items={shop.items} onItemClick={setDetail} />
      )}

      {detail && <StorefrontDetail item={detail} onClose={() => setDetail(null)} />}
    </div>
  )
}

function Showcase({ items, onItemClick }: { items: StorefrontItem[]; onItemClick: (item: StorefrontItem) => void }) {
  const filter = useItemFilter(items)
  return (
    <section className="space-y-4">
      <ItemFilters filter={filter} />
      {filter.visible.length === 0 ? (
        <NothingHere filtered={filter.filtered} emptyText="No hay nada a la vista" onClear={filter.clear} />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filter.visible.map((item) => (
            <ItemCard key={item.id} item={toCard(item)} price={item.priceCp} showStock onClick={() => onItemClick(item)} />
          ))}
        </div>
      )}
    </section>
  )
}

function StorefrontDetail({ item, onClose }: { item: StorefrontItem; onClose: () => void }) {
  const properties = Object.entries(item.properties ?? {}).filter(([key]) => key in PROPERTY_LABELS)
  return (
    <Modal title={item.name} onClose={onClose}>
      <dl className="grid grid-cols-2 gap-3">
        <div>
          <dt className="text-sm text-gray-400">Precio</dt>
          <dd><Money cp={item.priceCp} denomination={item.priceDenomination} /></dd>
        </div>
        <div>
          <dt className="text-sm text-gray-400">Quedan</dt>
          <dd className="font-medium text-gray-100">
            {item.unlimitedStock ? 'sin límite' : item.quantity === 0 ? 'agotado' : item.quantity}
          </dd>
        </div>
        <div>
          <dt className="text-sm text-gray-400">Tipo</dt>
          <dd className="font-medium text-gray-100">{TYPE_LABELS[item.type]} · {RARITY_LABELS[item.rarity]}</dd>
        </div>
        {item.category && (
          <div>
            <dt className="text-sm text-gray-400">Categoría</dt>
            <dd className="font-medium text-gray-100">{item.category}</dd>
          </div>
        )}
        {item.weight !== null && (
          <div>
            <dt className="text-sm text-gray-400">Peso</dt>
            <dd className="font-medium text-gray-100">{item.weight} lb</dd>
          </div>
        )}
      </dl>
      {item.negotiable && <p className="text-sm text-indigo-300">Negociable: se puede ofrecer otro precio.</p>}
      {properties.length > 0 && (
        <dl className="space-y-1">
          {properties.map(([key, value]) => (
            <div key={key} className="flex justify-between gap-4 py-1.5 border-b border-gray-700">
              <dt className="text-gray-400">{PROPERTY_LABELS[key]}</dt>
              <dd className="font-medium text-gray-100 text-right">{formatProperty(value)}</dd>
            </div>
          ))}
        </dl>
      )}
      {item.description && <p className="text-gray-300 whitespace-pre-wrap">{item.description}</p>}
    </Modal>
  )
}
