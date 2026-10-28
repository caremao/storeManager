import { useState } from 'react'
import { Check, X } from 'lucide-react'
import { errorMessage } from '../../api/client'
import { useApproveTrade, useCancelTrade, useRejectTrade } from '../../api/hooks'
import type { Trade, TradeStatus } from '../../api/types'
import { moneyValue, moneyValueToCp } from '../../lib/coins'
import { ErrorBox } from '../ui/Feedback'
import { Money } from '../ui/Money'
import { MoneyInput } from '../ui/MoneyInput'
import { secondaryButton, smallButton, successButton } from '../ui/styles'

const STATUS: Record<TradeStatus, { label: string; className: string }> = {
  PENDING: { label: 'Pendiente', className: 'bg-yellow-900/40 text-yellow-300' },
  APPROVED: { label: 'Hecho', className: 'bg-green-900/40 text-green-300' },
  REJECTED: { label: 'Rechazado', className: 'bg-red-900/40 text-red-300' },
  CANCELLED: { label: 'Cancelado', className: 'bg-gray-700 text-gray-300' },
}

interface TradeListProps {
  trades: Trade[]
  /** GM: aprobar o rechazar. Jugador: cancelar sus pendientes. */
  mode: 'gm' | 'player'
  emptyText?: string
}

export function TradeList({ trades, mode, emptyText = 'No hay solicitudes.' }: TradeListProps) {
  const [error, setError] = useState<string | null>(null)
  if (trades.length === 0) return <p className="text-gray-400">{emptyText}</p>
  return (
    <div className="space-y-3">
      {error && <ErrorBox>{error}</ErrorBox>}
      <ul className="space-y-3">
        {trades.map((trade) => (
          <TradeRow key={trade.id} trade={trade} mode={mode} onError={setError} />
        ))}
      </ul>
    </div>
  )
}

function TradeRow({ trade, mode, onError }: { trade: Trade; mode: 'gm' | 'player'; onError: (e: string | null) => void }) {
  const approve = useApproveTrade()
  const reject = useRejectTrade()
  const cancel = useCancelTrade()
  const [price, setPrice] = useState(() => moneyValue(trade.unitPriceCp, trade.priceDenomination))
  const busy = approve.isPending || reject.isPending || cancel.isPending
  const status = STATUS[trade.status]
  const verb = trade.kind === 'BUY' ? 'compra' : 'vende'
  const preposition = trade.kind === 'BUY' ? 'en' : 'a'

  const run = async (action: () => Promise<unknown>) => {
    onError(null)
    try {
      await action()
    } catch (err) {
      onError(errorMessage(err))
    }
  }

  const approveWithPrice = () => {
    const cp = moneyValueToCp(price)
    if (Number.isNaN(cp)) {
      onError('El precio tiene que ser un número')
      return
    }
    const changed = cp !== null && cp !== trade.unitPriceCp
    return run(() => approve.mutateAsync({ id: trade.id, unitPriceCp: changed ? cp : undefined }))
  }

  return (
    <li className="p-3 rounded-lg bg-gray-900/50 border border-gray-700">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-gray-100">
          <span className="font-semibold">{trade.characterName ?? '¿?'}</span> {verb}{' '}
          <span className="font-semibold">{trade.quantity}× {trade.itemName}</span> {preposition} {trade.shopName ?? 'la tienda'}
        </p>
        <span className={`text-xs px-2 py-0.5 rounded ${status.className}`}>{status.label}</span>
      </div>
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-gray-400 mt-1">
        {trade.unitPriceCp === null ? (
          <span>Sin precio: lo fija el GM</span>
        ) : (
          <>
            <Money cp={trade.unitPriceCp} denomination={trade.priceDenomination} size="sm" /> c/u · total
            <Money cp={trade.totalCp} denomination={trade.priceDenomination} size="sm" />
          </>
        )}
        {trade.note && <span>· «{trade.note}»</span>}
      </div>

      {trade.status === 'PENDING' && mode === 'gm' && (
        <div className="flex flex-wrap items-end gap-2 mt-3">
          <div className="w-44">
            <label className="block text-xs text-gray-400 mb-1" htmlFor={`price-${trade.id}`}>Precio c/u</label>
            <MoneyInput id={`price-${trade.id}`} value={price} onChange={setPrice} compact />
          </div>
          <button type="button" disabled={busy} onClick={approveWithPrice} className={`${successButton} ${smallButton}`}>
            <Check className="w-4 h-4" />
            Aprobar
          </button>
          <button type="button" disabled={busy} onClick={() => run(() => reject.mutateAsync(trade.id))}
            className={`${secondaryButton} ${smallButton}`}>
            <X className="w-4 h-4" />
            Rechazar
          </button>
        </div>
      )}
      {trade.status === 'PENDING' && mode === 'player' && (
        <button type="button" disabled={busy} onClick={() => run(() => cancel.mutateAsync(trade.id))}
          className={`${secondaryButton} ${smallButton} mt-3`}>
          Cancelar pedido
        </button>
      )}
    </li>
  )
}
