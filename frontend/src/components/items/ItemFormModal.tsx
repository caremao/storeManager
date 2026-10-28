import { useEffect, useRef, useState } from 'react'
import { Loader2, Search } from 'lucide-react'
import { errorMessage } from '../../api/client'
import { useSrdSearch } from '../../api/hooks'
import type { Item, ItemInput, ItemRarity, ItemType, SrdItem } from '../../api/types'
import { useDebounce } from '../../hooks/useDebounce'
import {
  formatMoney,
  moneyValue,
  moneyValueToCp,
  naturalDenomination,
  type MoneyValue,
} from '../../lib/coins'
import { ErrorBox } from '../ui/Feedback'
import { Modal } from '../ui/Modal'
import { MoneyInput } from '../ui/MoneyInput'
import { input, label, primaryButton, secondaryButton } from '../ui/styles'
import { ITEM_RARITIES, ITEM_TYPES, RARITY_LABELS, TYPE_LABELS } from './utils'

interface ItemFormModalProps {
  /** Si viene, el formulario edita ese item; si no, crea uno nuevo. */
  item?: Item
  /** Tiendas: permite stock 0 (agotado), stock ilimitado y productos negociables. */
  forShop: boolean
  /** El GM fija el valor real; el jugador solo anota cuánto cree que vale. */
  role: 'gm' | 'player'
  notesLabel?: string
  /** Las categorías que ya se usan en el inventario, como sugerencias para no escribirlas distinto. */
  categories?: string[]
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
  value: MoneyValue
  perceived: MoneyValue
  valueRevealed: boolean
  negotiable: boolean
  hidden: boolean
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
    value: moneyValue(item?.valueCp, item?.priceDenomination),
    perceived: moneyValue(item?.perceivedValueCp, item?.perceivedDenomination),
    valueRevealed: item?.valueRevealed ?? true,
    negotiable: item?.negotiable ?? false,
    hidden: item?.hidden ?? false,
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

export function ItemFormModal({
  item,
  forShop,
  role,
  notesLabel = 'Notas',
  categories = [],
  onSubmit,
  onClose,
}: ItemFormModalProps) {
  const gm = role === 'gm'
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
      // El precio del SRD es el valor real para el GM y una estimación para el jugador.
      ...(srd.valueCp === null
        ? {}
        : gm
          ? { value: moneyValue(srd.valueCp, naturalDenomination(srd.valueCp)) }
          : { perceived: moneyValue(srd.valueCp, naturalDenomination(srd.valueCp)) }),
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
    const value = moneyValueToCp(form.value)
    const perceived = moneyValueToCp(form.perceived)
    if (Number.isNaN(value) || Number.isNaN(perceived)) {
      return setError('El valor tiene que ser un número (por ejemplo 2,5)')
    }
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
        // Lo que no le toca al jugador se reenvía tal como estaba (el servidor lo ignora igual).
        valueCp: gm ? value : (item?.valueCp ?? null),
        priceDenomination: gm ? form.value.denomination : (item?.priceDenomination ?? null),
        perceivedValueCp: perceived,
        perceivedDenomination: form.perceived.denomination,
        valueRevealed: gm ? form.valueRevealed : (item?.valueRevealed ?? false),
        negotiable: gm && forShop && form.negotiable,
        hidden: gm && forShop && form.hidden,
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
                {srd.valueCp !== null && ` · ${formatMoney(srd.valueCp, naturalDenomination(srd.valueCp))}`}
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
            list="item-categories" maxLength={100} autoComplete="off" placeholder="Armas, Pociones, Herramientas…"
            className={input} />
          <datalist id="item-categories">
            {categories.map((c) => <option key={c} value={c} />)}
          </datalist>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="item-quantity" className={label}>Cantidad</label>
            <input id="item-quantity" type="number" inputMode="numeric" min={forShop ? 0 : 1} value={form.quantity}
              disabled={form.unlimitedStock} onChange={(e) => set('quantity', e.target.value)} className={input} />
          </div>
          <div>
            <label htmlFor="item-weight" className={label}>Peso (lb)</label>
            <input id="item-weight" type="number" inputMode="decimal" min={0} step="0.1" value={form.weight}
              onChange={(e) => set('weight', e.target.value)} className={input} />
          </div>
        </div>
        {gm ? (
          <>
            <div>
              <label htmlFor="item-value" className={label}>{forShop ? 'Valor' : 'Valor real'}</label>
              <MoneyInput id="item-value" value={form.value} onChange={(v) => set('value', v)} />
            </div>
            {!forShop && (
              <>
                <label className="flex items-center gap-2 text-gray-300">
                  <input type="checkbox" checked={form.valueRevealed}
                    onChange={(e) => set('valueRevealed', e.target.checked)} className="w-4 h-4 accent-indigo-500" />
                  El jugador conoce el valor real
                </label>
                {!form.valueRevealed && (
                  <div>
                    <label htmlFor="item-perceived" className={label}>
                      Lo que cree el jugador que vale (vacío = desconocido)
                    </label>
                    <MoneyInput id="item-perceived" value={form.perceived} onChange={(v) => set('perceived', v)} />
                  </div>
                )}
              </>
            )}
          </>
        ) : (
          <div>
            <label htmlFor="item-perceived" className={label}>Cuánto crees que vale (opcional)</label>
            <MoneyInput id="item-perceived" value={form.perceived} onChange={(v) => set('perceived', v)} />
          </div>
        )}
        {forShop && (
          <>
            <label className="flex items-center gap-2 text-gray-300">
              <input type="checkbox" checked={form.unlimitedStock}
                onChange={(e) => set('unlimitedStock', e.target.checked)} className="w-4 h-4 accent-indigo-500" />
              Stock ilimitado (no se agota al vender)
            </label>
            <label className="flex items-center gap-2 text-gray-300">
              <input type="checkbox" checked={form.negotiable}
                onChange={(e) => set('negotiable', e.target.checked)} className="w-4 h-4 accent-indigo-500" />
              Negociable: el jugador puede ofrecer otro precio
            </label>
            <label className="flex items-center gap-2 text-gray-300">
              <input type="checkbox" checked={form.hidden}
                onChange={(e) => set('hidden', e.target.checked)} className="w-4 h-4 accent-indigo-500" />
              Oculto: los jugadores no lo ven hasta que lo muestres
            </label>
          </>
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
