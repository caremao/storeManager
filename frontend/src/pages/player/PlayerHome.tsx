import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Store } from 'lucide-react'
import { useChangeCurrency, useConvertCurrency, useInventory, useItems, useMe, useTrades } from '../../api/hooks'
import type { Inventory } from '../../api/types'
import { ConvertModal } from '../../components/currency/ConvertModal'
import { CurrencyModal } from '../../components/currency/CurrencyModal'
import { PurseWidget } from '../../components/currency/PurseWidget'
import { HistoryPanel } from '../../components/history/HistoryPanel'
import { ItemsManager } from '../../components/items/ItemsManager'
import { totalWeight } from '../../components/items/utils'
import { TradeList } from '../../components/trades/TradeList'
import { Spinner } from '../../components/ui/Feedback'
import { card, primaryButton } from '../../components/ui/styles'

/** Lo que ve el jugador al escanear su QR: su bolsa, su inventario y sus pedidos. */
export function PlayerHome() {
  const { data: me } = useMe()
  const { data: character, isLoading } = useInventory(me?.characterId ?? undefined)
  if (isLoading || !character) return <Spinner />
  return <CharacterSheet key={character.id} character={character} />
}

function CharacterSheet({ character }: { character: Inventory }) {
  const { data: me } = useMe()
  const canAddCoins = me?.campaign.playersCanAddCoins ?? false
  const currency = useChangeCurrency(character.id)
  const convert = useConvertCurrency(character.id)
  const [converting, setConverting] = useState(false)
  const { data: items = [] } = useItems(character.id)
  const { data: trades = [] } = useTrades()
  const [coins, setCoins] = useState<'add' | 'spend' | null>(null)
  const countered = trades.filter((t) => t.status === 'COUNTERED')
  const pending = trades.filter((t) => t.status === 'PENDING')
  const recent = trades.filter((t) => t.status !== 'PENDING' && t.status !== 'COUNTERED').slice(0, 5)

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-bold text-gray-100">{character.name}</h1>
        <Link to="/pj/tiendas" className={primaryButton}>
          <Store className="w-5 h-5" /> Ir a las tiendas
        </Link>
      </div>

      <PurseWidget
        title="Tu bolsa"
        purse={character.purse}
        onAdd={canAddCoins ? () => setCoins('add') : undefined}
        onSpend={() => setCoins('spend')}
        onConvert={() => setConverting(true)}
      />

      {countered.length > 0 && (
        <section className={`${card} p-4 sm:p-6 border-indigo-500`}>
          <h2 className="text-xl font-semibold text-indigo-200 mb-4">El GM te hizo una contraoferta</h2>
          <TradeList trades={countered} mode="player" />
        </section>
      )}

      {pending.length > 0 && (
        <section className={`${card} p-4 sm:p-6`}>
          <h2 className="text-xl font-semibold text-gray-100 mb-4">Esperando al GM</h2>
          <TradeList trades={pending} mode="player" />
        </section>
      )}

      <ItemsManager
        inventory={character}
        title="Tu inventario"
        editable
        emptyText="Tu mochila está vacía"
        headerExtra={<span className="text-sm text-gray-400">Peso total: {totalWeight(items).toLocaleString('es')} lb</span>}
      />

      {recent.length > 0 && (
        <section className={`${card} p-4 sm:p-6`}>
          <h2 className="text-xl font-semibold text-gray-100 mb-4">Últimas compras y ventas</h2>
          <TradeList trades={recent} mode="player" />
        </section>
      )}

      <HistoryPanel inventoryId={character.id} title="Tu historial" />

      {coins && <CurrencyModal mode={coins} onSubmit={(c) => currency.mutateAsync(c)} onClose={() => setCoins(null)} />}
      {converting && character.purse && (
        <ConvertModal purse={character.purse} onSubmit={(b) => convert.mutateAsync(b)} onClose={() => setConverting(false)} />
      )}
    </div>
  )
}
