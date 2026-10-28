import { Printer } from 'lucide-react'
import { useInventories } from '../../api/hooks'
import { EmptyState, Spinner } from '../../components/ui/Feedback'
import { QrImage } from '../../components/ui/QrImage'
import { secondaryButton, smallButton } from '../../components/ui/styles'

/** Los QR de todos los personajes, para mostrar en la mesa o imprimir. */
export function GmTable() {
  const { data: characters = [], isLoading } = useInventories('CHARACTER')
  if (isLoading) return <Spinner />
  if (characters.length === 0) {
    return <EmptyState title="Todavía no hay personajes">Créalos desde el panel y sus QR aparecen aquí.</EmptyState>
  }
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
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {characters.map((c) => (
          <div key={c.id} className="flex flex-col items-center gap-3 p-6 rounded-xl bg-gray-800 border border-gray-700 break-inside-avoid">
            {c.joinUrl && <QrImage value={c.joinUrl} size={240} alt={`QR de ${c.name}`} />}
            <p className="text-xl font-semibold text-gray-100">{c.name}</p>
            {c.playerName && <p className="text-gray-400 -mt-2">{c.playerName}</p>}
          </div>
        ))}
      </div>
    </div>
  )
}
