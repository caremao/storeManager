import { useMemo, useState, type ReactNode } from 'react'
import { PackageOpen, Plus, Search } from 'lucide-react'
import type { Item, ItemType } from '../../api/types'
import { useDebounce } from '../../hooks/useDebounce'
import { input, primaryButton } from '../ui/styles'
import { ItemCard } from './ItemCard'
import { ITEM_TYPES, TYPE_LABELS } from './utils'

interface ItemsListProps {
  items: Item[]
  isLoading?: boolean
  onItemClick?: (item: Item) => void
  /** Sin esto no se muestra el botón de agregar. */
  onAddClick?: () => void
  priceOf?: (item: Item) => number | null
  showStock?: boolean
  renderFooter?: (item: Item) => ReactNode
  emptyText?: string
}

type Tab = 'ALL' | ItemType

/** Lista con búsqueda y pestañas por tipo; filtra en el cliente (adaptado del proyecto original). */
export function ItemsList({
  items,
  isLoading,
  onItemClick,
  onAddClick,
  priceOf,
  showStock,
  renderFooter,
  emptyText = 'Todavía no hay nada aquí',
}: ItemsListProps) {
  const [tab, setTab] = useState<Tab>('ALL')
  const [search, setSearch] = useState('')
  const query = useDebounce(search, 200).trim().toLowerCase()

  const counts = useMemo(() => {
    const result: Record<string, number> = { ALL: items.length }
    for (const item of items) result[item.type] = (result[item.type] ?? 0) + 1
    return result
  }, [items])

  const visible = useMemo(
    () =>
      items.filter(
        (item) => (tab === 'ALL' || item.type === tab) && (!query || item.name.toLowerCase().includes(query)),
      ),
    [items, tab, query],
  )

  const tabs: Tab[] = ['ALL', ...ITEM_TYPES.filter((t) => counts[t])]

  return (
    <div className="space-y-4">
      <div className="flex gap-3">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
          <input
            type="search"
            placeholder="Buscar…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className={`${input} pl-10`}
          />
        </div>
        {onAddClick && (
          <button type="button" onClick={onAddClick} className={primaryButton}>
            <Plus className="w-5 h-5" />
            <span className="hidden sm:inline">Agregar</span>
          </button>
        )}
      </div>

      {tabs.length > 2 && (
        <div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
          <div className="flex gap-1 min-w-max bg-gray-900/50 p-1 rounded-lg">
            {tabs.map((key) => (
              <button
                key={key}
                type="button"
                onClick={() => setTab(key)}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
                  tab === key ? 'bg-gray-700 text-gray-100 shadow' : 'text-gray-400 hover:text-gray-200'
                }`}
              >
                {key === 'ALL' ? 'Todo' : TYPE_LABELS[key]}
                <span className="px-1.5 text-xs rounded-full bg-gray-600 text-gray-300">{counts[key]}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="text-center py-8 text-gray-400">Cargando…</div>
      ) : visible.length === 0 ? (
        <div className="text-center py-10 bg-gray-800 rounded-lg border-2 border-dashed border-gray-600">
          <PackageOpen className="mx-auto w-10 h-10 text-gray-400" />
          <p className="mt-3 text-gray-400">{query ? 'Nada coincide con la búsqueda' : emptyText}</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {visible.map((item) => (
            <ItemCard
              key={item.id}
              item={item}
              onClick={onItemClick ? () => onItemClick(item) : undefined}
              price={priceOf ? priceOf(item) : undefined}
              showStock={showStock}
              footer={renderFooter?.(item)}
            />
          ))}
        </div>
      )}
    </div>
  )
}
