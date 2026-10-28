import { Plus, Search, Tag } from 'lucide-react'
import { input, primaryButton } from '../ui/styles'
import type { ItemFilter } from './filter'
import { TYPE_LABELS } from './utils'

interface ItemFiltersProps {
  filter: ItemFilter
  /** Sin esto no se muestra el botón de agregar. */
  onAddClick?: () => void
}

/** Buscador por nombre o categoría, pestañas por tipo y botones por categoría (si hay más de una). */
export function ItemFilters({ filter, onAddClick }: ItemFiltersProps) {
  return (
    <div className="space-y-3">
      <div className="flex gap-3">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
          <input
            type="search"
            placeholder="Buscar por nombre o categoría…"
            aria-label="Buscar por nombre o categoría"
            value={filter.search}
            onChange={(e) => filter.setSearch(e.target.value)}
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

      {filter.types.length > 0 && (
        <div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
          <div className="flex gap-1 min-w-max bg-gray-900/50 p-1 rounded-lg">
            {filter.types.map((key) => (
              <button
                key={key}
                type="button"
                onClick={() => filter.setType(key)}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
                  filter.type === key ? 'bg-gray-700 text-gray-100 shadow' : 'text-gray-400 hover:text-gray-200'
                }`}
              >
                {key === 'ALL' ? 'Todo' : TYPE_LABELS[key]}
                <span className="px-1.5 text-xs rounded-full bg-gray-600 text-gray-300">{filter.typeCounts[key]}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {filter.categories.length > 0 && (
        <div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
          <div className="flex items-center gap-2 min-w-max" role="group" aria-label="Categoría">
            <Tag className="w-4 h-4 text-gray-400" aria-hidden />
            {[{ key: null, label: 'Todas' }, ...filter.categories].map((c) => {
              const active = filter.category === c.key
              return (
                <button
                  key={c.key ?? ''}
                  type="button"
                  aria-pressed={active}
                  onClick={() => filter.setCategory(active ? null : c.key)}
                  className={`px-3 py-1 rounded-full border text-sm transition-colors ${
                    active
                      ? 'bg-indigo-600 border-indigo-500 text-white'
                      : 'border-gray-600 text-gray-300 hover:bg-gray-700'
                  }`}
                >
                  {c.label}
                </button>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
