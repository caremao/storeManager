import { Printer } from 'lucide-react'
import { useInventories } from '../../api/hooks'
import type { Inventory } from '../../api/types'
import { EmptyState, Spinner } from '../../components/ui/Feedback'
import { QrImage } from '../../components/ui/QrImage'
import { secondaryButton, smallButton } from '../../components/ui/styles'

function QrCard({ url, title, subtitle }: { url: string | null; title: string; subtitle?: string | null }) {
  return (
    <div className="flex flex-col items-center gap-3 p-6 rounded-xl bg-gray-800 border border-gray-700 break-inside-avoid">
      {url && <QrImage value={url} size={240} alt={`QR de ${title}`} />}
      <p className="text-xl font-semibold text-gray-100">{title}</p>
      {subtitle && <p className="text-gray-400 -mt-2">{subtitle}</p>}
    </div>
  )
}

const grid = 'grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6'

/** Los QR de los personajes y de las vitrinas de las tiendas, para mostrar en la mesa o imprimir. */
export function GmTable() {
  const { data: characters = [], isLoading } = useInventories('CHARACTER')
  const { data: shops = [], isLoading: loadingShops } = useInventories('SHOP')
  if (isLoading || loadingShops) return <Spinner />
  if (characters.length === 0 && shops.length === 0) {
    return <EmptyState title="Todavía no hay personajes ni tiendas">Créalos desde el panel y sus QR aparecen aquí.</EmptyState>
  }
  const shopNote = (shop: Inventory) => (shop.visibleToPlayers ? 'Vitrina' : 'Vitrina · cerrada')
  return (
    <div className="space-y-6">
      <div className="no-print flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-100">Mesa</h1>
          <p className="text-gray-400">Cada jugador escanea el QR de su personaje una vez; su teléfono queda con la sesión.</p>
        </div>
        <button type="button" onClick={() => window.print()} className={`${secondaryButton} ${smallButton}`}>
          <Printer className="w-4 h-4" /> Imprimir
        </button>
      </div>
      {characters.length > 0 && (
        <div className={grid}>
          {characters.map((c) => <QrCard key={c.id} url={c.joinUrl} title={c.name} subtitle={c.playerName} />)}
        </div>
      )}
      {shops.length > 0 && (
        <section className="space-y-4">
          <div>
            <h2 className="text-xl font-semibold text-gray-100">Tiendas</h2>
            <p className="no-print text-gray-400">
              Un jugador de la campaña que lo escanea va directo al mostrador. Cualquier otro ve la vitrina:
              lo que hay a la venta, sin sesión. Lo oculto no aparece.
            </p>
          </div>
          <div className={grid}>
            {shops.map((s) => <QrCard key={s.id} url={s.storefrontUrl} title={s.name} subtitle={shopNote(s)} />)}
          </div>
        </section>
      )}
    </div>
  )
}
