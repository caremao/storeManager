import { useState, type ReactNode } from 'react'
import { useDeleteItem, useItems, useSaveItem } from '../../api/hooks'
import type { Inventory, Item } from '../../api/types'
import { ErrorBox } from '../ui/Feedback'
import { card } from '../ui/styles'
import { ItemDetail } from './ItemDetail'
import { ItemFormModal } from './ItemFormModal'
import { ItemsList } from './ItemsList'

interface ItemsManagerProps {
  inventory: Inventory
  title: string
  /** Puede agregar, editar y quitar (GM en todo, jugador en lo suyo). */
  editable: boolean
  priceOf?: (item: Item) => number | null
  /** Acciones extra en el detalle; reciben un callback para cerrarlo. */
  detailActions?: (item: Item, close: () => void) => ReactNode
  renderFooter?: (item: Item) => ReactNode
  emptyText?: string
  headerExtra?: ReactNode
}

/** Lista de items de un inventario con su detalle y su formulario de alta o edición. */
export function ItemsManager({
  inventory,
  title,
  editable,
  priceOf,
  detailActions,
  renderFooter,
  emptyText,
  headerExtra,
}: ItemsManagerProps) {
  const { data: items = [], isLoading, error } = useItems(inventory.id)
  const save = useSaveItem(inventory.id)
  const remove = useDeleteItem(inventory.id)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [form, setForm] = useState<{ item?: Item } | null>(null)
  const forShop = inventory.kind === 'SHOP'
  const notesLabel = forShop ? 'Notas del GM (los jugadores no las ven)' : 'Notas'
  // El seleccionado se toma de la lista fresca, así el detalle se actualiza con SSE.
  const selected = items.find((i) => i.id === selectedId) ?? null

  return (
    <section className={`${card} p-4 sm:p-6`}>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 className="text-xl font-semibold text-gray-100">{title}</h2>
        {headerExtra}
      </div>
      {error && <ErrorBox>No se pudieron cargar los items.</ErrorBox>}
      <ItemsList
        items={items}
        isLoading={isLoading}
        onItemClick={(item) => setSelectedId(item.id)}
        onAddClick={editable ? () => setForm({}) : undefined}
        priceOf={priceOf}
        showStock={forShop}
        renderFooter={renderFooter}
        emptyText={emptyText}
      />

      {selected && !form && (
        <ItemDetail
          item={selected}
          notesLabel={notesLabel}
          onClose={() => setSelectedId(null)}
          onEdit={editable ? () => setForm({ item: selected }) : undefined}
          onDelete={editable ? () => remove.mutateAsync(selected.id) : undefined}
          actions={detailActions?.(selected, () => setSelectedId(null))}
        />
      )}
      {form && (
        <ItemFormModal
          item={form.item}
          forShop={forShop}
          notesLabel={notesLabel}
          onSubmit={(input) => save.mutateAsync({ id: form.item?.id, input })}
          onClose={() => setForm(null)}
        />
      )}
    </section>
  )
}
