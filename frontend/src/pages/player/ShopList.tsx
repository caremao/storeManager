import { Link } from 'react-router-dom'
import { Store } from 'lucide-react'
import { useInventories } from '../../api/hooks'
import { EmptyState, Spinner } from '../../components/ui/Feedback'

export function ShopList() {
  const { data: shops = [], isLoading } = useInventories('SHOP')
  if (isLoading) return <Spinner />
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-gray-100">Tiendas abiertas</h1>
      {shops.length === 0 ? (
        <EmptyState title="Ninguna tienda abierta">Cuando el GM abra una, aparece aquí sola.</EmptyState>
      ) : (
        <ul className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {shops.map((shop) => (
            <li key={shop.id}>
              <Link to={`/pj/tiendas/${shop.id}`}
                className="flex items-start gap-3 p-4 h-full rounded-lg bg-gray-800 border border-gray-700 hover:border-indigo-500 transition-colors">
                <Store className="w-8 h-8 flex-shrink-0 text-indigo-400" />
                <div className="min-w-0">
                  <p className="text-lg font-semibold text-gray-100">{shop.name}</p>
                  {shop.description && <p className="text-sm text-gray-400 line-clamp-2">{shop.description}</p>}
                  <p className="mt-1 text-xs text-gray-500">
                    {shop.requiresApproval ? 'El GM aprueba cada compra' : 'Compra directa'}
                  </p>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
