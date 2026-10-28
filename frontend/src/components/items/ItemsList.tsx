import type { ReactNode } from 'react'
import { PackageOpen } from 'lucide-react'
import type { Item } from '../../api/types'
import { ItemCard } from './ItemCard'
import { ItemFilters } from './ItemFilters'
import { useItemFilter } from './filter'
import type { ValueLine } from './value'

interface ItemsListProps {
  items: Item[]
  isLoading?: boolean
  onItemClick?: (item: Item) => void
  /** Sin esto no se muestra el botón de agregar. */
  onAddClick?: () => void
  priceOf?: (item: Item) => number | null
  valuesOf?: (item: Item) => ValueLine[]
  showStock?: boolean
  renderFooter?: (item: Item) => ReactNode
  emptyText?: string
}

/** Lista con búsqueda, pestañas por tipo y categorías; filtra en el cliente (adaptado del proyecto original). */
export function ItemsList({
  items,
  isLoading,
  onItemClick,
  onAddClick,
  priceOf,
  valuesOf,
  showStock,
  renderFooter,
  emptyText = 'Todavía no hay nada aquí',
}: ItemsListProps) {
  const filter = useItemFilter(items)

  return (
    <div className="space-y-4">
      <ItemFilters filter={filter} onAddClick={onAddClick} />

      {isLoading ? (
        <div className="text-center py-8 text-gray-400">Cargando…</div>
      ) : filter.visible.length === 0 ? (
        <NothingHere filtered={filter.filtered} emptyText={emptyText} onClear={filter.clear} />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filter.visible.map((item) => (
            <ItemCard
              key={item.id}
              item={item}
              onClick={onItemClick ? () => onItemClick(item) : undefined}
              price={priceOf ? priceOf(item) : undefined}
              values={valuesOf?.(item)}
              showStock={showStock}
              footer={renderFooter?.(item)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

export function NothingHere({ filtered, emptyText, onClear }: { filtered: boolean; emptyText: string; onClear: () => void }) {
  return (
    <div className="text-center py-10 bg-gray-800 rounded-lg border-2 border-dashed border-gray-600">
      <PackageOpen className="mx-auto w-10 h-10 text-gray-400" />
      <p className="mt-3 text-gray-400">{filtered ? 'Nada coincide con la búsqueda' : emptyText}</p>
      {filtered && (
        <button type="button" onClick={onClear} className="mt-2 text-indigo-400 hover:text-indigo-300">
          Ver todo
        </button>
      )}
    </div>
  )
}
