import { useId, useState } from 'react'
import { Handshake, Minus, Plus } from 'lucide-react'
import { moneyValue, moneyValueToCp, type Denomination } from '../../lib/coins'
import { MoneyInput } from '../ui/MoneyInput'
import { primaryButton, secondaryButton, smallButton } from '../ui/styles'

interface OfferOption {
  /** Moneda con que arranca el campo de la oferta. */
  denomination: Denomination | null
  /** Precio sugerido para arrancar (el de lista, si hay). */
  startCp: number | null
  onOffer: (quantity: number, unitPriceCp: number) => Promise<unknown>
}

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
  /** Permite proponer otro precio (productos negociables y ventas). */
  offer?: OfferOption
}

/** Selector de cantidad con su botón ("Pedir", "Comprar", "Ofrecer") y, si se puede, una oferta. */
export function QuantityAction({ max, actionLabel, blockedReason, busy, onConfirm, summary, offer }: QuantityActionProps) {
  const [quantity, setQuantity] = useState(1)
  const [offering, setOffering] = useState(false)
  const [price, setPrice] = useState(() => moneyValue(offer?.startCp, offer?.denomination))
  const [offerError, setOfferError] = useState<string | null>(null)
  const offerId = useId()
  const limit = max ?? 999
  const clamp = (n: number) => Math.min(Math.max(1, n), Math.max(1, limit))
  const step = 'p-1.5 rounded-md bg-gray-700 text-gray-200 hover:bg-gray-600 disabled:opacity-40'
  const done = () => {
    setQuantity(1)
    setOffering(false)
  }

  const sendOffer = () => {
    const cp = moneyValueToCp(price)
    if (cp === null || Number.isNaN(cp)) {
      setOfferError('Indica tu precio (por ejemplo 7,5)')
      return
    }
    setOfferError(null)
    offer?.onOffer(quantity, cp).then(done, () => undefined)
  }

  return (
    <div className="space-y-2">
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
          onClick={() => onConfirm(quantity).then(done, () => undefined)}
          className={`${primaryButton} ${smallButton} ml-auto`}>
          {actionLabel}
        </button>
      </div>
      {blockedReason ? (
        <p className="text-xs text-red-300">{blockedReason}</p>
      ) : (
        summary && <p className="text-xs text-gray-400 text-right">{summary(quantity)}</p>
      )}
      {offer && limit >= 1 && (
        offering ? (
          <div className="flex flex-wrap items-end gap-2">
            <div className="flex-1 min-w-40">
              <MoneyInput id={offerId} value={price} onChange={setPrice} compact />
            </div>
            <button type="button" disabled={busy} onClick={sendOffer} className={`${primaryButton} ${smallButton}`}>
              <Handshake className="w-4 h-4" /> Enviar oferta c/u
            </button>
            {offerError && <p className="w-full text-xs text-red-300">{offerError}</p>}
          </div>
        ) : (
          <button type="button" onClick={() => setOffering(true)} className={`${secondaryButton} ${smallButton} w-full`}>
            <Handshake className="w-4 h-4" /> Ofrecer otro precio
          </button>
        )
      )}
    </div>
  )
}
