import { useState, type ReactNode } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { errorMessage } from '../../api/client'
import { useDeleteItem, useItems, useMe, useRevealItem, useSaveItem } from '../../api/hooks'
import type { Inventory, Item } from '../../api/types'
import { ErrorBox } from '../ui/Feedback'
import { card, secondaryButton } from '../ui/styles'
import { ItemDetail } from './ItemDetail'
import { ItemFormModal } from './ItemFormModal'
import { ItemsList } from './ItemsList'
import { categoriesOf } from './filter'
import { toInput } from './utils'
import { characterValues } from './value'

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
  const { data: me } = useMe()
  const { data: items = [], isLoading, error } = useItems(inventory.id)
  const save = useSaveItem(inventory.id)
  const remove = useDeleteItem(inventory.id)
  const reveal = useRevealItem(inventory.id)
  const toggleHidden = useSaveItem(inventory.id)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [form, setForm] = useState<{ item?: Item } | null>(null)
  const role = me?.role === 'GM' ? 'gm' : 'player'
  const forShop = inventory.kind === 'SHOP'
  const notesLabel = forShop ? 'Notas del GM (los jugadores no las ven)' : 'Notas'
  // El seleccionado se toma de la lista fresca, así el detalle se actualiza con SSE.
  const selected = items.find((i) => i.id === selectedId) ?? null

  const actionsFor = (item: Item, close: () => void) => (
    <>
      {role === 'gm' && !forShop && !item.valueRevealed && (
        <button type="button" disabled={reveal.isPending} onClick={() => reveal.mutate(item.id)}
          className={`${secondaryButton} w-full`}>
          <Eye className="w-4 h-4" /> Revelar valor
        </button>
      )}
      {role === 'gm' && forShop && (
        <button type="button" disabled={toggleHidden.isPending}
          onClick={() => toggleHidden.mutate({ id: item.id, input: { ...toInput(item), hidden: !item.hidden } })}
          className={`${secondaryButton} w-full`}>
          {item.hidden
            ? <><Eye className="w-4 h-4" /> Mostrar a los jugadores</>
            : <><EyeOff className="w-4 h-4" /> Ocultar a los jugadores</>}
        </button>
      )}
      {detailActions?.(item, close)}
    </>
  )

  return (
    <section className={`${card} p-4 sm:p-6`}>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 className="text-xl font-semibold text-gray-100">{title}</h2>
        {headerExtra}
      </div>
      {error && <ErrorBox>No se pudieron cargar los items.</ErrorBox>}
      {(reveal.error ?? toggleHidden.error) && (
        <div className="mb-3"><ErrorBox>{errorMessage(reveal.error ?? toggleHidden.error)}</ErrorBox></div>
      )}
      <ItemsList
        items={items}
        isLoading={isLoading}
        onItemClick={(item) => setSelectedId(item.id)}
        onAddClick={editable ? () => setForm({}) : undefined}
        priceOf={priceOf}
        valuesOf={forShop ? undefined : (item) => characterValues(item, role)}
        showStock={forShop}
        renderFooter={renderFooter}
        emptyText={emptyText}
      />

      {selected && !form && (
        <ItemDetail
          item={selected}
          role={role}
          notesLabel={notesLabel}
          onClose={() => setSelectedId(null)}
          onEdit={editable ? () => setForm({ item: selected }) : undefined}
          onDelete={editable ? () => remove.mutateAsync(selected.id) : undefined}
          actions={actionsFor(selected, () => setSelectedId(null))}
        />
      )}
      {form && (
        <ItemFormModal
          item={form.item}
          forShop={forShop}
          role={role}
          notesLabel={notesLabel}
          categories={categoriesOf(items).map((c) => c.label)}
          onSubmit={(input) => save.mutateAsync({ id: form.item?.id, input })}
          onClose={() => setForm(null)}
        />
      )}
    </section>
  )
}
