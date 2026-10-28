import { Minus, Plus } from 'lucide-react'
import type { Purse } from '../../api/types'
import { formatCp } from '../../lib/coins'
import { card, dangerSolidButton, smallButton, successButton } from '../ui/styles'

interface PurseWidgetProps {
  title: string
  purse: Purse | null | undefined
  unlimited?: boolean
  onAdd?: () => void
  onSpend?: () => void
}

const COINS = [
  { key: 'platinum', label: 'PPT', className: 'bg-slate-700/50 text-slate-300' },
  { key: 'gold', label: 'PO', className: 'bg-yellow-900/30 text-yellow-400' },
  { key: 'silver', label: 'PP', className: 'bg-gray-700/50 text-gray-300' },
  { key: 'copper', label: 'PC', className: 'bg-amber-900/30 text-amber-400' },
] as const

/** Bolsa o caja (adaptado del TreasuryWidget del proyecto original). */
export function PurseWidget({ title, purse, unlimited, onAdd, onSpend }: PurseWidgetProps) {
  return (
    <div className={`${card} p-4 sm:p-6`}>
      <div className="flex items-center justify-between gap-4 mb-4">
        <h2 className="text-xl font-semibold text-gray-100">{title}</h2>
        {(onAdd || onSpend) && (
          <div className="flex gap-2">
            {onAdd && (
              <button type="button" onClick={onAdd} className={`${successButton} ${smallButton}`}>
                <Plus className="w-4 h-4" />
                Sumar
              </button>
            )}
            {onSpend && (
              <button type="button" onClick={onSpend} className={`${dangerSolidButton} ${smallButton}`}>
                <Minus className="w-4 h-4" />
                Gastar
              </button>
            )}
          </div>
        )}
      </div>
      {unlimited ? (
        <p className="text-gray-400">Fondos ilimitados: la tienda siempre puede pagar.</p>
      ) : (
        <>
          <div className="grid grid-cols-4 gap-2 sm:gap-4 mb-3">
            {COINS.map((coin) => (
              <div key={coin.key} className={`rounded-lg p-3 text-center ${coin.className}`}>
                <p className="text-2xl font-bold">{purse?.[coin.key] ?? 0}</p>
                <p className="text-xs font-medium opacity-80">{coin.label}</p>
              </div>
            ))}
          </div>
          <p className="text-center text-gray-400 text-sm">
            Total: <span className="font-semibold text-gray-300">{formatCp(purse?.totalCp ?? 0)}</span>
          </p>
        </>
      )}
    </div>
  )
}
