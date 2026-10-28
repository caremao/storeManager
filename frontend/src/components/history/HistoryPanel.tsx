import { useState } from 'react'
import { ArrowLeftRight, Coins, FileUp, History, Minus, Pencil, Plus } from 'lucide-react'
import { useHistory } from '../../api/hooks'
import type { HistoryAction } from '../../api/types'
import { card, secondaryButton, smallButton } from '../ui/styles'
import { describe } from './describe'

const ICONS: Record<HistoryAction, typeof Plus> = {
  ITEM_ADDED: Plus,
  ITEM_UPDATED: Pencil,
  ITEM_REMOVED: Minus,
  CURRENCY_UPDATED: Coins,
  TRADE_EXECUTED: ArrowLeftRight,
  ITEMS_IMPORTED: FileUp,
}

const PAGE = 15

export function HistoryPanel({ inventoryId, title = 'Historial' }: { inventoryId?: string; title?: string }) {
  const [size, setSize] = useState(PAGE)
  const { data, isLoading } = useHistory(inventoryId, size)

  return (
    <section className={`${card} p-4 sm:p-6`}>
      <h2 className="flex items-center gap-2 text-xl font-semibold text-gray-100 mb-4">
        <History className="w-5 h-5 text-gray-400" />
        {title}
      </h2>
      {isLoading ? (
        <p className="text-gray-400">Cargando…</p>
      ) : !data || data.entries.length === 0 ? (
        <p className="text-gray-400">Sin movimientos todavía.</p>
      ) : (
        <ol className="space-y-3">
          {data.entries.map((entry) => {
            const Icon = ICONS[entry.action]
            const { text, detail } = describe(entry)
            return (
              <li key={entry.id} className="flex gap-3">
                <Icon className="w-4 h-4 mt-1 flex-shrink-0 text-indigo-400" />
                <div className="min-w-0">
                  <p className="text-gray-200">{text}</p>
                  <p className="text-xs text-gray-500">
                    {new Date(entry.createdAt).toLocaleString('es', { dateStyle: 'short', timeStyle: 'short' })}
                    {detail ? ` · ${detail}` : ''}
                  </p>
                </div>
              </li>
            )
          })}
        </ol>
      )}
      {data && data.total > data.entries.length && (
        <button type="button" onClick={() => setSize((s) => s + PAGE)} className={`${secondaryButton} ${smallButton} mt-4`}>
          Ver más
        </button>
      )}
    </section>
  )
}
