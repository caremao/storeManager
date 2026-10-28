import { useState } from 'react'
import { Minus, Plus } from 'lucide-react'
import { primaryButton, smallButton } from '../ui/styles'

interface QuantityActionProps {
  /** Máximo elegible; undefined = sin límite (stock ilimitado). */
  max?: number
  actionLabel: string
  /** Si viene, el botón queda deshabilitado y se muestra este motivo. */
  blockedReason?: string | null
  busy?: boolean
  onConfirm: (quantity: number) => Promise<unknown>
  /** Texto a la derecha según la cantidad elegida (p. ej. el total). */
  summary?: (quantity: number) => string
}

/** Selector de cantidad con su botón: "Pedir", "Comprar", "Vender"… */
export function QuantityAction({ max, actionLabel, blockedReason, busy, onConfirm, summary }: QuantityActionProps) {
  const [quantity, setQuantity] = useState(1)
  const limit = max ?? 999
  const clamp = (n: number) => Math.min(Math.max(1, n), Math.max(1, limit))
  const step = 'p-1.5 rounded-md bg-gray-700 text-gray-200 hover:bg-gray-600 disabled:opacity-40'

  return (
    <div className="space-y-1">
      <div className="flex items-center gap-2">
        <button type="button" aria-label="Menos" className={step} disabled={quantity <= 1}
          onClick={() => setQuantity((q) => clamp(q - 1))}>
          <Minus className="w-4 h-4" />
        </button>
        <span className="w-8 text-center font-semibold text-gray-100">{quantity}</span>
        <button type="button" aria-label="Más" className={step} disabled={quantity >= limit}
          onClick={() => setQuantity((q) => clamp(q + 1))}>
          <Plus className="w-4 h-4" />
        </button>
        <button type="button" disabled={!!blockedReason || busy || limit < 1}
          onClick={() => onConfirm(quantity).then(() => setQuantity(1), () => undefined)}
          className={`${primaryButton} ${smallButton} ml-auto`}>
          {actionLabel}
        </button>
      </div>
      {blockedReason ? (
        <p className="text-xs text-red-300">{blockedReason}</p>
      ) : (
        summary && <p className="text-xs text-gray-400 text-right">{summary(quantity)}</p>
      )}
    </div>
  )
}
