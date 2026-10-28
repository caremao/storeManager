import { useState } from 'react'
import { Check, Handshake, X } from 'lucide-react'
import { errorMessage } from '../../api/client'
import { useAcceptTrade, useApproveTrade, useCancelTrade, useCounterTrade, useRejectTrade } from '../../api/hooks'
import type { Trade, TradeStatus } from '../../api/types'
import { moneyValue, moneyValueToCp } from '../../lib/coins'
import { ErrorBox } from '../ui/Feedback'
import { Money } from '../ui/Money'
import { MoneyInput } from '../ui/MoneyInput'
import { primaryButton, secondaryButton, smallButton, successButton } from '../ui/styles'

const STATUS: Record<TradeStatus, { label: string; className: string }> = {
  PENDING: { label: 'Pendiente', className: 'bg-yellow-900/40 text-yellow-300' },
  COUNTERED: { label: 'Contraoferta', className: 'bg-indigo-900/50 text-indigo-300' },
  APPROVED: { label: 'Hecho', className: 'bg-green-900/40 text-green-300' },
  REJECTED: { label: 'Rechazado', className: 'bg-red-900/40 text-red-300' },
  CANCELLED: { label: 'Cancelado', className: 'bg-gray-700 text-gray-300' },
}

interface TradeListProps {
  trades: Trade[]
  /** GM: aprobar, contraofertar o rechazar. Jugador: aceptar una contraoferta, ofertar o cancelar. */
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
          // Se vuelve a montar con cada contraoferta, así el campo de precio arranca en el precio nuevo.
          <TradeRow key={`${trade.id}-${trade.status}-${trade.unitPriceCp}`} trade={trade} mode={mode} onError={setError} />
        ))}
      </ul>
    </div>
  )
}

function TradeRow({ trade, mode, onError }: { trade: Trade; mode: 'gm' | 'player'; onError: (e: string | null) => void }) {
  const approve = useApproveTrade()
  const accept = useAcceptTrade()
  const counter = useCounterTrade()
  const reject = useRejectTrade()
  const cancel = useCancelTrade()
  const [price, setPrice] = useState(() => moneyValue(trade.unitPriceCp, trade.priceDenomination))
  const busy = approve.isPending || accept.isPending || counter.isPending || reject.isPending || cancel.isPending
  const status = STATUS[trade.status]
  const verb = trade.kind === 'BUY' ? 'compra' : 'vende'
  const preposition = trade.kind === 'BUY' ? 'en' : 'a'
  const myTurn = (mode === 'gm' && trade.status === 'PENDING') || (mode === 'player' && trade.status === 'COUNTERED')
  const open = trade.status === 'PENDING' || trade.status === 'COUNTERED'
  const offered = trade.lastOfferBy !== null && trade.unitPriceCp !== trade.listUnitPriceCp

  const run = async (action: () => Promise<unknown>) => {
    onError(null)
    try {
      await action()
    } catch (err) {
      onError(errorMessage(err))
    }
  }

  const sendCounter = () => {
    const cp = moneyValueToCp(price)
    if (cp === null || Number.isNaN(cp)) {
      onError('Indica un precio (por ejemplo 7,5)')
      return
    }
    return run(() => counter.mutateAsync({ id: trade.id, unitPriceCp: cp }))
  }

  const acceptCurrent = () =>
    run(() => (mode === 'gm' ? approve.mutateAsync(trade.id) : accept.mutateAsync(trade.id)))

  return (
    <li className="p-3 rounded-lg bg-gray-900/50 border border-gray-700">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-gray-100">
          <span className="font-semibold">{trade.characterName ?? '¿?'}</span> {verb}{' '}
          <span className="font-semibold">{trade.quantity}× {trade.itemName}</span> {preposition} {trade.shopName ?? 'la tienda'}
        </p>
        <span className={`text-xs px-2 py-0.5 rounded ${status.className}`}>{status.label}</span>
      </div>

      <div className="mt-1 space-y-0.5 text-sm text-gray-400">
        {offered && (
          <p className="flex flex-wrap items-center gap-x-2">
            Precio de lista:
            <Money cp={trade.listUnitPriceCp} denomination={trade.priceDenomination} size="sm" empty="sin precio" />
          </p>
        )}
        <p className="flex flex-wrap items-center gap-x-2">
          {trade.unitPriceCp === null ? (
            <span>Sin precio: el GM propone uno</span>
          ) : (
            <>
              {offered ? (trade.lastOfferBy === 'GM' ? 'Contraoferta del GM:' : 'Oferta del jugador:') : 'Precio:'}
              <Money cp={trade.unitPriceCp} denomination={trade.priceDenomination} size="sm" /> c/u · total
              <Money cp={trade.totalCp} denomination={trade.priceDenomination} size="sm" />
            </>
          )}
          {trade.note && <span>· «{trade.note}»</span>}
        </p>
        {open && !myTurn && (
          <p className="italic">{mode === 'gm' ? 'Esperando la respuesta del jugador…' : 'Esperando al GM…'}</p>
        )}
      </div>

      {open && myTurn && (
        <div className="flex flex-wrap items-end gap-2 mt-3">
          {trade.unitPriceCp !== null && (
            <button type="button" disabled={busy} onClick={acceptCurrent} className={`${successButton} ${smallButton}`}>
              <Check className="w-4 h-4" />
              {mode === 'gm' ? 'Aprobar' : 'Aceptar'}
            </button>
          )}
          <div className="w-44">
            <label className="block text-xs text-gray-400 mb-1" htmlFor={`price-${trade.id}`}>
              {mode === 'gm' ? 'Contraofertar c/u' : 'Ofrecer c/u'}
            </label>
            <MoneyInput id={`price-${trade.id}`} value={price} onChange={setPrice} compact />
          </div>
          <button type="button" disabled={busy} onClick={sendCounter} className={`${primaryButton} ${smallButton}`}>
            <Handshake className="w-4 h-4" />
            {mode === 'gm' ? 'Contraofertar' : 'Ofrecer'}
          </button>
        </div>
      )}
      {open && (
        <div className="mt-2">
          {mode === 'gm' ? (
            <button type="button" disabled={busy} onClick={() => run(() => reject.mutateAsync(trade.id))}
              className={`${secondaryButton} ${smallButton}`}>
              <X className="w-4 h-4" /> Rechazar
            </button>
          ) : (
            <button type="button" disabled={busy} onClick={() => run(() => cancel.mutateAsync(trade.id))}
              className={`${secondaryButton} ${smallButton}`}>
              Cancelar pedido
            </button>
          )}
        </div>
      )}
    </li>
  )
}
