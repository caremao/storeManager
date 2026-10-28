import type { ComponentType, ReactNode } from 'react'
import { FlaskConical, Package, ScrollText, Sparkles, Sword } from 'lucide-react'
import type { Item, ItemType } from '../../api/types'
import { Money } from '../ui/Money'
import { RARITY_COLORS, RARITY_LABELS, TYPE_LABELS, keyStat } from './utils'

const TYPE_ICONS: Record<ItemType, ComponentType<{ className?: string }>> = {
  EQUIPMENT: Sword,
  POTION: FlaskConical,
  SCROLL: ScrollText,
  CONSUMABLE: Sparkles,
  MISC: Package,
}

export function ItemIcon({ item, className }: { item: Item; className?: string }) {
  const Icon = TYPE_ICONS[item.type]
  return <Icon className={`${className ?? 'w-6 h-6'} ${RARITY_COLORS[item.rarity].text}`} />
}

interface ItemCardProps {
  item: Item
  onClick?: () => void
  /** Precio a mostrar (p. ej. lo que cobra la tienda); se omite si es undefined. */
  price?: number | null
  /** Tiendas: muestra "Agotado" o "∞" en vez de la cantidad. */
  showStock?: boolean
  /** Acciones bajo la tarjeta (fuera del área clickeable, para no anidar botones). */
  footer?: ReactNode
}

export function ItemCard({ item, onClick, price, showStock, footer }: ItemCardProps) {
  const rarity = RARITY_COLORS[item.rarity]
  const soldOut = showStock && !item.unlimitedStock && item.quantity === 0
  const stat = keyStat(item)

  return (
    <div className={`rounded-lg border-2 ${rarity.border} ${rarity.bg} ${soldOut ? 'opacity-60' : ''}`}>
      <button
        type="button"
        onClick={onClick}
        className="w-full text-left p-4 rounded-lg hover:bg-white/5 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500"
      >
        <div className="flex items-start gap-3">
          <div className="flex-shrink-0 w-12 h-12 rounded bg-gray-700 border border-gray-600 flex items-center justify-center">
            <ItemIcon item={item} />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <h3 className="font-semibold text-gray-100 truncate">{item.name}</h3>
              {showStock ? (
                <span className="flex-shrink-0 px-2 py-0.5 text-xs font-medium bg-indigo-900/40 text-indigo-300 rounded-full">
                  {item.unlimitedStock ? '∞' : soldOut ? 'Agotado' : `quedan ${item.quantity}`}
                </span>
              ) : (
                item.quantity > 1 && (
                  <span className="flex-shrink-0 px-2 py-0.5 text-xs font-medium bg-indigo-900/40 text-indigo-300 rounded-full">
                    ×{item.quantity}
                  </span>
                )
              )}
            </div>
            <div className="flex flex-wrap items-center gap-2 mt-1">
              <span className={`text-xs px-2 py-0.5 rounded ${rarity.badge}`}>{RARITY_LABELS[item.rarity]}</span>
              <span className="text-xs text-gray-400">{TYPE_LABELS[item.type]}</span>
              {stat && <span className="text-xs text-gray-400">· {stat}</span>}
              {item.negotiable && (
                <span className="text-xs px-1.5 py-0.5 rounded bg-indigo-900/50 text-indigo-300">Negociable</span>
              )}
              {!item.valueRevealed && (
                <span className="text-xs px-1.5 py-0.5 rounded bg-gray-700 text-gray-300">
                  {item.perceivedValueCp === null ? 'Valor desconocido' : 'Valor estimado'}
                </span>
              )}
            </div>
            {price !== undefined && (
              <p className="mt-2"><Money cp={price} denomination={item.priceDenomination} /></p>
            )}
          </div>
        </div>
      </button>
      {footer && <div className="px-4 pb-3">{footer}</div>}
    </div>
  )
}
