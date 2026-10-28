import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { useDirectTrade, useInventories } from '../../api/hooks'
import type { Inventory, Item } from '../../api/types'
import { ErrorBox } from '../../components/ui/Feedback'
import { Modal } from '../../components/ui/Modal'
import { Money } from '../../components/ui/Money'
import { MoneyInput } from '../../components/ui/MoneyInput'
import { input, label, primaryButton, secondaryButton } from '../../components/ui/styles'
import { formatMoney, moneyValue, moneyValueToCp } from '../../lib/coins'

/** El GM registra una venta acordada en la mesa: descuenta stock y cobra al personaje en el acto. */
export function DirectTradeModal({ shop, item, onClose }: { shop: Inventory; item: Item; onClose: () => void }) {
  const { data: characters = [] } = useInventories('CHARACTER')
  const trade = useDirectTrade(shop.id)
  const [characterId, setCharacterId] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [price, setPrice] = useState(() => moneyValue(item.priceCp, item.priceDenomination))
  const [error, setError] = useState<string | null>(null)
  const qty = Math.max(1, parseInt(quantity, 10) || 1)
  const unit = moneyValueToCp(price)
  const priced = unit !== null && !Number.isNaN(unit)
  const buyer = characters.find((c) => c.id === characterId)

  const submit = async () => {
    if (!characterId) return setError('Elige a quién le vendes')
    if (!priced) return setError('Indica el precio')
    setError(null)
    try {
      await trade.mutateAsync({ kind: 'BUY', characterId, itemId: item.id, quantity: qty, unitPriceCp: unit })
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <Modal
      title={`Vender ${item.name}`}
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose} className={secondaryButton}>Cancelar</button>
          <button type="button" onClick={submit} disabled={trade.isPending} className={primaryButton}>
            {trade.isPending ? 'Registrando…' : 'Registrar venta'}
          </button>
        </>
      }
    >
      {error && <ErrorBox>{error}</ErrorBox>}
      <div>
        <label htmlFor="direct-character" className={label}>Comprador</label>
        <select id="direct-character" value={characterId} onChange={(e) => setCharacterId(e.target.value)} className={input}>
          <option value="">Elige un personaje…</option>
          {characters.map((c) => (
            <option key={c.id} value={c.id}>{c.name} ({formatMoney(c.purse?.totalCp ?? 0)})</option>
          ))}
        </select>
      </div>
      <div className="grid grid-cols-[6rem_1fr] gap-4">
        <div>
          <label htmlFor="direct-qty" className={label}>Cantidad</label>
          <input id="direct-qty" type="number" inputMode="numeric" min={1} value={quantity}
            onChange={(e) => setQuantity(e.target.value)} className={input} />
        </div>
        <div>
          <label htmlFor="direct-price" className={label}>Precio c/u</label>
          <MoneyInput id="direct-price" value={price} onChange={setPrice} />
        </div>
      </div>
      {priced && (
        <div className="flex flex-wrap items-center gap-2 text-gray-300">
          Total: <Money cp={unit * qty} denomination={price.denomination} />
          {buyer && (
            <span>
              · a {buyer.name} le quedarían <Money cp={(buyer.purse?.totalCp ?? 0) - unit * qty} size="sm" />
            </span>
          )}
        </div>
      )}
    </Modal>
  )
}
