import { useState, type ReactNode } from 'react'
import { AlertTriangle, Pencil, Trash2, X } from 'lucide-react'
import type { Item } from '../../api/types'
import { Money } from '../ui/Money'
import { dangerButton, dangerSolidButton, primaryButton, secondaryButton } from '../ui/styles'
import { ItemIcon } from './ItemCard'
import { PROPERTY_LABELS, RARITY_COLORS, RARITY_LABELS, TYPE_LABELS, formatProperty } from './utils'

interface ItemDetailProps {
  item: Item
  onClose: () => void
  onEdit?: () => void
  onDelete?: () => Promise<unknown>
  /** Acciones extra en el pie (p. ej. "Vender a…" o "Pedir"). */
  actions?: ReactNode
  notesLabel?: string
  role: 'gm' | 'player'
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-sm text-gray-400">{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}

/**
 * Qué valor se muestra: en una tienda, el precio (y el valor base para el GM); en un personaje, el
 * real si el dueño lo conoce, o lo que cree que vale. El GM ve las dos cosas.
 */
function ValueRows({ item, role }: { item: Item; role: 'gm' | 'player' }) {
  const perceived = <Money cp={item.perceivedValueCp} denomination={item.perceivedDenomination} empty="desconocido" />
  if (item.priceCp !== null) {
    return (
      <>
        <Row label="Precio en tienda"><Money cp={item.priceCp} denomination={item.priceDenomination} /></Row>
        {role === 'gm' && <Row label="Valor base"><Money cp={item.valueCp} denomination={item.priceDenomination} /></Row>}
      </>
    )
  }
  if (role === 'gm') {
    return (
      <>
        <Row label="Valor real"><Money cp={item.valueCp} denomination={item.priceDenomination} empty="sin fijar" /></Row>
        <Row label="El jugador cree">{item.valueRevealed ? <span className="text-gray-300">lo conoce</span> : perceived}</Row>
      </>
    )
  }
  return item.valueRevealed
    ? <Row label="Valor"><Money cp={item.valueCp} denomination={item.priceDenomination} empty="sin precio" /></Row>
    : <Row label="Valor estimado">{perceived}</Row>
}

/** Panel lateral con el detalle del item (adaptado del proyecto original). */
export function ItemDetail({ item, onClose, onEdit, onDelete, actions, notesLabel = 'Notas', role }: ItemDetailProps) {
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const rarity = RARITY_COLORS[item.rarity]
  const properties = Object.entries(item.properties ?? {}).filter(([key]) => key in PROPERTY_LABELS)

  const handleDelete = async () => {
    if (!onDelete) return
    setDeleting(true)
    try {
      await onDelete()
      onClose()
    } finally {
      setDeleting(false)
    }
  }

  return (
    <>
      <div className="fixed inset-0 z-40 bg-black/50" onClick={onClose} />
      <div className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-gray-800 shadow-xl flex flex-col">
        <div className={`flex items-start justify-between p-4 border-b border-gray-700 ${rarity.bg}`}>
          <div className="flex items-center gap-3">
            <div className={`w-14 h-14 rounded-lg bg-gray-700 border-2 ${rarity.border} flex items-center justify-center`}>
              <ItemIcon item={item} className="w-8 h-8" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-gray-100">{item.name}</h2>
              <div className="flex items-center gap-2 mt-1">
                <span className={`px-2 py-0.5 text-xs font-medium rounded ${rarity.badge}`}>
                  {RARITY_LABELS[item.rarity]}
                </span>
                <span className="px-2 py-0.5 text-xs font-medium bg-indigo-900/40 text-indigo-300 rounded">
                  {item.unlimitedStock ? 'stock ilimitado' : `×${item.quantity}`}
                </span>
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="p-1 text-gray-400 hover:text-gray-200 rounded-lg hover:bg-gray-700/50"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-6">
          <dl className="grid grid-cols-2 gap-3">
            <div>
              <dt className="text-sm text-gray-400">Tipo</dt>
              <dd className="font-medium text-gray-100">{TYPE_LABELS[item.type]}</dd>
            </div>
            {item.category && (
              <div>
                <dt className="text-sm text-gray-400">Categoría</dt>
                <dd className="font-medium text-gray-100">{item.category}</dd>
              </div>
            )}
            {item.weight !== null && (
              <div>
                <dt className="text-sm text-gray-400">Peso</dt>
                <dd className="font-medium text-gray-100">{item.weight} lb</dd>
              </div>
            )}
            <ValueRows item={item} role={role} />
          </dl>
          {item.negotiable && (
            <p className="text-sm text-indigo-300">Negociable: se puede ofrecer otro precio.</p>
          )}

          {properties.length > 0 && (
            <dl className="space-y-1">
              {properties.map(([key, value]) => (
                <div key={key} className="flex justify-between gap-4 py-1.5 border-b border-gray-700">
                  <dt className="text-gray-400">{PROPERTY_LABELS[key]}</dt>
                  <dd className="font-medium text-gray-100 text-right">{formatProperty(value)}</dd>
                </div>
              ))}
            </dl>
          )}

          {item.description && <p className="text-gray-300 whitespace-pre-wrap">{item.description}</p>}

          {item.notes && (
            <section>
              <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-2">{notesLabel}</h3>
              <p className="text-yellow-200 whitespace-pre-wrap bg-yellow-900/20 p-3 rounded-lg border border-yellow-700">
                {item.notes}
              </p>
            </section>
          )}
        </div>

        {(onEdit || onDelete || actions) && (
          <div className="flex flex-wrap gap-3 p-4 border-t border-gray-700 bg-gray-900">
            {actions}
            {onEdit && (
              <button type="button" onClick={onEdit} className={`${primaryButton} flex-1`}>
                <Pencil className="w-4 h-4" />
                Editar
              </button>
            )}
            {onDelete && (
              <button type="button" onClick={() => setConfirming(true)} className={dangerButton}>
                <Trash2 className="w-4 h-4" />
                Quitar
              </button>
            )}
          </div>
        )}
      </div>

      {confirming && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
          <div className="fixed inset-0 bg-black/50" onClick={() => setConfirming(false)} />
          <div className="relative bg-gray-800 rounded-lg shadow-xl max-w-sm w-full p-6">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-full bg-red-900/30 flex items-center justify-center">
                <AlertTriangle className="w-5 h-5 text-red-400" />
              </div>
              <h3 className="text-lg font-semibold text-gray-100">¿Quitar {item.name}?</h3>
            </div>
            <p className="text-gray-300 mb-6">Queda registrado en el historial, pero el item desaparece del inventario.</p>
            <div className="flex gap-3">
              <button type="button" onClick={() => setConfirming(false)} className={`${secondaryButton} flex-1`}>
                Cancelar
              </button>
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleting}
                className={`${dangerSolidButton} flex-1`}
              >
                {deleting ? 'Quitando…' : 'Quitar'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
