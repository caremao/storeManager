import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { errorMessage, isStatus } from '../../api/client'
import { useInventory, useItems, useMe, useRequestTrade, useTrades } from '../../api/hooks'
import type { Inventory, Item, TradeKind } from '../../api/types'
import { ItemDetail } from '../../components/items/ItemDetail'
import { ItemsList } from '../../components/items/ItemsList'
import { QuantityAction } from '../../components/trades/QuantityAction'
import { TradeList } from '../../components/trades/TradeList'
import { EmptyState, ErrorBox, Spinner, SuccessBox } from '../../components/ui/Feedback'
import { Money } from '../../components/ui/Money'
import { card } from '../../components/ui/styles'
import { applyPct, formatMoney } from '../../lib/coins'

/** El mostrador: el stock de la tienda con sus precios y lo que el jugador le puede vender. */
export function ShopCounter() {
  const { id } = useParams<{ id: string }>()
  const { data: me } = useMe()
  const { data: shop, isLoading, error } = useInventory(id)
  const { data: character } = useInventory(me?.characterId ?? undefined)

  if (isLoading) return <Spinner />
  if (!shop || isStatus(error, 404)) {
    return (
      <EmptyState title="Esta tienda está cerrada">
        <Link to="/pj/tiendas" className="text-indigo-400 hover:text-indigo-300">Ver las tiendas abiertas</Link>
      </EmptyState>
    )
  }
  return <Counter key={shop.id} shop={shop} character={character} />
}

function Counter({ shop, character }: { shop: Inventory; character: Inventory | undefined }) {
  const { data: stock = [], isLoading } = useItems(shop.id)
  const { data: myItems = [] } = useItems(character?.id)
  const { data: trades = [] } = useTrades()
  const request = useRequestTrade(shop.id)
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null)
  const [detail, setDetail] = useState<Item | null>(null)
  const noticeTimer = useRef<number | undefined>(undefined)
  const wallet = character?.purse?.totalCp ?? 0
  const pending = trades.filter((t) => t.shopId === shop.id && (t.status === 'PENDING' || t.status === 'COUNTERED'))
  const sellable = myItems.filter((i) => i.quantity > 0)
  // Lo que ofrecería la tienda: solo se puede anticipar si el jugador conoce el valor real.
  const buybackOf = (item: Item) => (item.valueRevealed ? applyPct(item.valueCp, shop.buybackPct) : null)

  const show = (ok: boolean, text: string) => {
    window.clearTimeout(noticeTimer.current)
    setNotice({ ok, text })
    noticeTimer.current = window.setTimeout(() => setNotice(null), 6000)
  }

  const trade = async (kind: TradeKind, item: Item, quantity: number, offerUnitPriceCp?: number) => {
    try {
      const result = await request.mutateAsync({ kind, itemId: item.id, quantity, offerUnitPriceCp })
      const what = `${quantity}× ${item.name}`
      if (result.status === 'APPROVED') {
        show(true, kind === 'BUY' ? `¡Listo! Compraste ${what}.` : `¡Listo! Vendiste ${what}.`)
      } else if (offerUnitPriceCp !== undefined) {
        show(true, `Oferta enviada al GM: ${what} a ${formatMoney(offerUnitPriceCp, item.priceDenomination)} c/u.`)
      } else {
        show(true, `Pedido enviado al GM: ${kind === 'BUY' ? 'comprar' : 'vender'} ${what}.`)
      }
    } catch (err) {
      show(false, errorMessage(err))
      throw err
    }
  }

  const buyBlocked = (item: Item): string | null => {
    if (!item.unlimitedStock && item.quantity === 0) return 'Agotado'
    if (item.priceCp !== null && item.priceCp > wallet) return 'No te alcanza'
    return null
  }

  const direct = (priceKnown: boolean) => !shop.requiresApproval && priceKnown

  return (
    <div className="space-y-6">
      <Link to="/pj/tiendas" className="inline-flex items-center gap-1 text-indigo-400 hover:text-indigo-300">
        <ArrowLeft className="w-4 h-4" /> Tiendas
      </Link>

      <section className={`${card} p-4 sm:p-6`}>
        <h1 className="text-3xl font-bold text-gray-100">{shop.name}</h1>
        {shop.description && <p className="mt-2 text-gray-300 whitespace-pre-wrap">{shop.description}</p>}
        <p className="mt-3 text-gray-400">
          Tu bolsa: <Money cp={wallet} />
          {shop.requiresApproval && ' · el GM aprueba cada compra'}
        </p>
      </section>

      {notice && (notice.ok ? <SuccessBox>{notice.text}</SuccessBox> : <ErrorBox>{notice.text}</ErrorBox>)}

      {pending.length > 0 && (
        <section className={`${card} p-4 sm:p-6`}>
          <h2 className="text-xl font-semibold text-gray-100 mb-4">Tus pedidos en esta tienda</h2>
          <TradeList trades={pending} mode="player" />
        </section>
      )}

      <section className={`${card} p-4 sm:p-6`}>
        <h2 className="text-xl font-semibold text-gray-100 mb-4">Mostrador</h2>
        <ItemsList
          items={stock}
          isLoading={isLoading}
          showStock
          priceOf={(item) => item.priceCp}
          onItemClick={setDetail}
          emptyText="La tienda no tiene nada a la venta"
          renderFooter={(item) => (
            <QuantityAction
              max={item.unlimitedStock ? undefined : item.quantity}
              actionLabel={direct(item.priceCp !== null) ? 'Comprar' : 'Pedir'}
              blockedReason={buyBlocked(item)}
              busy={request.isPending}
              onConfirm={(q) => trade('BUY', item, q)}
              summary={(q) => (item.priceCp === null
                ? 'el GM fija el precio'
                : `total ${formatMoney(item.priceCp * q, item.priceDenomination)}`)}
              offer={item.negotiable ? {
                denomination: item.priceDenomination,
                startCp: item.priceCp,
                onOffer: (q, cp) => trade('BUY', item, q, cp),
              } : undefined}
            />
          )}
        />
      </section>

      <section className={`${card} p-4 sm:p-6`}>
        <h2 className="text-xl font-semibold text-gray-100 mb-1">Vender a esta tienda</h2>
        <p className="text-sm text-gray-400 mb-4">
          Paga el {shop.buybackPct}% del valor de cada cosa. El GM aprueba cada venta.
        </p>
        <ItemsList
          items={sellable}
          priceOf={buybackOf}
          onItemClick={setDetail}
          emptyText="No tienes nada para vender"
          renderFooter={(item) => {
            const buyback = buybackOf(item)
            return (
              <QuantityAction
                max={item.quantity}
                actionLabel="Ofrecer a la tienda"
                busy={request.isPending}
                onConfirm={(q) => trade('SELL', item, q)}
                summary={(q) => (buyback === null
                  ? 'la tienda te hará una oferta'
                  : `recibes ${formatMoney(buyback * q, item.priceDenomination)}`)}
                offer={{
                  denomination: item.valueRevealed ? item.priceDenomination : item.perceivedDenomination,
                  startCp: buyback ?? item.perceivedValueCp,
                  onOffer: (q, cp) => trade('SELL', item, q, cp),
                }}
              />
            )
          }}
        />
      </section>

      {detail && <ItemDetail item={detail} role="player" onClose={() => setDetail(null)} />}
    </div>
  )
}
