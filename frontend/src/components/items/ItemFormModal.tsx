import { useEffect, useRef, useState } from 'react'
import { Loader2, Search } from 'lucide-react'
import { errorMessage } from '../../api/client'
import { useSrdSearch } from '../../api/hooks'
import type { Item, ItemInput, ItemRarity, ItemType, SrdItem } from '../../api/types'
import { useDebounce } from '../../hooks/useDebounce'
import { cpToGp, formatCp, gpToCp } from '../../lib/coins'
import { ErrorBox } from '../ui/Feedback'
import { Modal } from '../ui/Modal'
import { input, label, primaryButton, secondaryButton } from '../ui/styles'
import { ITEM_RARITIES, ITEM_TYPES, RARITY_LABELS, TYPE_LABELS } from './utils'

interface ItemFormModalProps {
  /** Si viene, el formulario edita ese item; si no, crea uno nuevo. */
  item?: Item
  /** Tiendas: permite stock 0 (agotado) y stock ilimitado. */
  forShop: boolean
  notesLabel?: string
  onSubmit: (input: ItemInput) => Promise<unknown>
  onClose: () => void
}

interface FormState {
  name: string
  type: ItemType
  rarity: ItemRarity
  category: string
  description: string
  notes: string
  quantity: string
  unlimitedStock: boolean
  weight: string
  valueGp: string
  srdIndex: string | null
  properties: Record<string, unknown> | null
}

function fromItem(item?: Item): FormState {
  return {
    name: item?.name ?? '',
    type: item?.type ?? 'EQUIPMENT',
    rarity: item?.rarity ?? 'COMMON',
    category: item?.category ?? '',
    description: item?.description ?? '',
    notes: item?.notes ?? '',
    quantity: String(item?.quantity ?? 1),
    unlimitedStock: item?.unlimitedStock ?? false,
    weight: item?.weight === null || item?.weight === undefined ? '' : String(item.weight),
    valueGp: String(cpToGp(item?.valueCp)),
    srdIndex: item?.srdIndex ?? null,
    properties: item?.properties ?? null,
  }
}

/**
 * Limpia la descripción del SRD: quita la línea "Tipo, rareza" y convierte las tablas markdown
 * en texto legible (misma idea que cleanSrdDescription del proyecto original).
 */
function cleanSrdDescription(description: string | null): string {
  if (!description) return ''
  return description
    .split(/\n+/)
    .filter((line) => {
      const t = line.trim()
      if (/^(Potion|Weapon|Armor|Wondrous item|Ring|Rod|Staff|Wand|Scroll|Ammunition)[^.]*,\s*\w+/i.test(t) && t.length < 80) return false
      if (/^\|[-|:\s]+\|$/.test(t)) return false
      if (/\(table\)\s*$/i.test(t)) return false
      return true
    })
    .map((line) =>
      line.startsWith('|') && line.endsWith('|')
        ? line.split('|').filter(Boolean).map((s) => s.trim()).join(' — ')
        : line,
    )
    .join('\n')
    .trim()
}

export function ItemFormModal({ item, forShop, notesLabel = 'Notas', onSubmit, onClose }: ItemFormModalProps) {
  const [form, setForm] = useState<FormState>(() => fromItem(item))
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [search, setSearch] = useState('')
  const [showResults, setShowResults] = useState(false)
  const searchRef = useRef<HTMLDivElement>(null)
  const query = useDebounce(search, 300)
  const { data: results = [], isFetching } = useSrdSearch(query)

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) setShowResults(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((f) => ({ ...f, [key]: value }))

  const pickSrd = (srd: SrdItem) => {
    setForm((f) => ({
      ...f,
      name: srd.name,
      type: srd.type,
      rarity: srd.rarity ?? 'COMMON',
      category: srd.category ?? '',
      description: cleanSrdDescription(srd.description),
      weight: srd.weight === null ? '' : String(srd.weight),
      valueGp: srd.valueCp === null ? f.valueGp : String(cpToGp(srd.valueCp)),
      srdIndex: srd.index,
      properties: srd.properties,
    }))
    setSearch('')
    setShowResults(false)
  }

  const handleSubmit = async (e?: React.FormEvent) => {
    e?.preventDefault()
    setError(null)
    const quantity = parseInt(form.quantity, 10)
    if (!form.name.trim()) return setError('El nombre es obligatorio')
    if (Number.isNaN(quantity) || quantity < (forShop ? 0 : 1)) {
      return setError(forShop ? 'La cantidad no puede ser negativa' : 'La cantidad mínima es 1')
    }
    const value = form.valueGp.trim() === '' ? null : gpToCp(parseFloat(form.valueGp.replace(',', '.')))
    const weight = form.weight.trim() === '' ? null : parseFloat(form.weight.replace(',', '.'))
    setSaving(true)
    try {
      await onSubmit({
        name: form.name.trim(),
        type: form.type,
        rarity: form.rarity,
        category: form.category.trim() || null,
        description: form.description.trim() || null,
        notes: form.notes.trim() || null,
        quantity,
        unlimitedStock: forShop && form.unlimitedStock,
        weight: weight !== null && !Number.isNaN(weight) ? weight : null,
        valueCp: value !== null && !Number.isNaN(value) ? value : null,
        srdIndex: form.srdIndex,
        properties: form.properties,
      })
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const srdSearch = (
    <div className="p-4 border-b border-gray-700" ref={searchRef}>
      <label className={label} htmlFor="srd-search">
        Buscar en el SRD (opcional, en inglés)
      </label>
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
        <input
          id="srd-search"
          type="text"
          value={search}
          autoComplete="off"
          onChange={(e) => {
            setSearch(e.target.value)
            setShowResults(true)
          }}
          onFocus={() => setShowResults(true)}
          placeholder="longsword, potion of healing, rope…"
          className={`${input} pl-10 pr-10`}
        />
        {isFetching && (
          <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400 animate-spin" />
        )}
      </div>
      {showResults && results.length > 0 && (
        <div className="mt-1 bg-gray-700 border border-gray-600 rounded-lg shadow-lg max-h-60 overflow-y-auto">
          {results.map((srd) => (
            <button
              key={srd.index}
              type="button"
              onClick={() => pickSrd(srd)}
              className="w-full text-left px-4 py-2 hover:bg-gray-600 border-b border-gray-600 last:border-b-0"
            >
              <div className="flex items-center gap-2">
                <span className="font-medium text-gray-100">{srd.name}</span>
                {srd.source === 'MAGIC_ITEM' && (
                  <span className="text-xs px-1.5 py-0.5 bg-purple-900/50 text-purple-300 rounded">Mágico</span>
                )}
              </div>
              <div className="text-sm text-gray-400">
                {srd.category}
                {` · ${srd.rarity ? RARITY_LABELS[srd.rarity] : 'rareza variable'}`}
                {srd.valueCp !== null && ` · ${formatCp(srd.valueCp)}`}
              </div>
            </button>
          ))}
        </div>
      )}
      {showResults && !isFetching && query.trim().length >= 2 && results.length === 0 && (
        <div className="mt-1 bg-gray-700 border border-gray-600 rounded-lg px-4 py-3 text-sm text-gray-400">
          Sin resultados
        </div>
      )}
    </div>
  )

  return (
    <Modal
      title={item ? 'Editar item' : 'Agregar item'}
      onClose={onClose}
      header={srdSearch}
      footer={
        <>
          <button type="button" onClick={onClose} className={secondaryButton}>
            Cancelar
          </button>
          <button type="button" onClick={() => handleSubmit()} disabled={saving} className={primaryButton}>
            {saving ? 'Guardando…' : item ? 'Guardar' : 'Agregar'}
          </button>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <ErrorBox>{error}</ErrorBox>}
        <div>
          <label htmlFor="item-name" className={label}>
            Nombre <span className="text-red-500">*</span>
          </label>
          <input
            id="item-name"
            value={form.name}
            onChange={(e) => set('name', e.target.value)}
            autoComplete="off"
            className={input}
          />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="item-type" className={label}>Tipo</label>
            <select id="item-type" value={form.type} onChange={(e) => set('type', e.target.value as ItemType)} className={input}>
              {ITEM_TYPES.map((t) => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="item-rarity" className={label}>Rareza</label>
            <select id="item-rarity" value={form.rarity} onChange={(e) => set('rarity', e.target.value as ItemRarity)} className={input}>
              {ITEM_RARITIES.map((r) => <option key={r} value={r}>{RARITY_LABELS[r]}</option>)}
            </select>
          </div>
        </div>
        <div>
          <label htmlFor="item-category" className={label}>Categoría</label>
          <input id="item-category" value={form.category} onChange={(e) => set('category', e.target.value)}
            placeholder="Arma, Armadura, Equipo de aventurero…" className={input} />
        </div>
        <div className="grid grid-cols-3 gap-4">
          <div>
            <label htmlFor="item-quantity" className={label}>Cantidad</label>
            <input id="item-quantity" type="number" inputMode="numeric" min={forShop ? 0 : 1} value={form.quantity}
              disabled={form.unlimitedStock} onChange={(e) => set('quantity', e.target.value)} className={input} />
          </div>
          <div>
            <label htmlFor="item-value" className={label}>Valor (po)</label>
            <input id="item-value" type="number" inputMode="decimal" min={0} step="0.01" value={form.valueGp}
              onChange={(e) => set('valueGp', e.target.value)} className={input} />
          </div>
          <div>
            <label htmlFor="item-weight" className={label}>Peso (lb)</label>
            <input id="item-weight" type="number" inputMode="decimal" min={0} step="0.1" value={form.weight}
              onChange={(e) => set('weight', e.target.value)} className={input} />
          </div>
        </div>
        {forShop && (
          <label className="flex items-center gap-2 text-gray-300">
            <input type="checkbox" checked={form.unlimitedStock} onChange={(e) => set('unlimitedStock', e.target.checked)}
              className="w-4 h-4 accent-indigo-500" />
            Stock ilimitado (no se agota al vender)
          </label>
        )}
        <div>
          <label htmlFor="item-description" className={label}>Descripción</label>
          <textarea id="item-description" rows={4} value={form.description}
            onChange={(e) => set('description', e.target.value)} className={`${input} resize-y`} />
        </div>
        <div>
          <label htmlFor="item-notes" className={label}>{notesLabel}</label>
          <textarea id="item-notes" rows={2} value={form.notes}
            onChange={(e) => set('notes', e.target.value)} className={`${input} resize-y`} />
        </div>
      </form>
    </Modal>
  )
}
