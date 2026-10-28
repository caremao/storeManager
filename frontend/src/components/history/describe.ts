import type { HistoryEntry } from '../../api/types'
import { formatMoney, type Denomination } from '../../lib/coins'

const FIELD_LABELS: Record<string, string> = {
  name: 'nombre',
  type: 'tipo',
  category: 'categoría',
  rarity: 'rareza',
  description: 'descripción',
  notes: 'notas',
  quantity: 'cantidad',
  unlimitedStock: 'stock ilimitado',
  weight: 'peso',
  valueCp: 'valor',
  perceivedValueCp: 'valor estimado',
  valueRevealed: 'conoce el valor',
  negotiable: 'negociable',
  copper: 'pc',
  silver: 'pp',
  gold: 'po',
  platinum: 'ppt',
}

type Changes = Record<string, { old: unknown; new: unknown }>

/** Campos que no vale la pena listar: la moneda ya va con el monto. */
const SILENT = new Set(['priceDenomination', 'perceivedDenomination'])

function show(field: string, value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (field.endsWith('ValueCp') || field === 'valueCp') return formatMoney(Number(value))
  if (typeof value === 'boolean') return value ? 'sí' : 'no'
  const text = String(value)
  return text.length > 30 ? `${text.slice(0, 30)}…` : text
}

function describeChanges(changes: Changes | undefined): string | null {
  if (!changes) return null
  return Object.entries(changes)
    .filter(([field]) => !SILENT.has(field))
    .map(([field, c]) =>
      field === 'description' || field === 'notes'
        ? FIELD_LABELS[field]
        : `${FIELD_LABELS[field] ?? field}: ${show(field, c.old)} → ${show(field, c.new)}`,
    )
    .join(', ')
}

/** Texto principal y detalle de una entrada del historial. */
export function describe(entry: HistoryEntry): { text: string; detail: string | null } {
  const d = entry.details ?? {}
  const where = entry.inventoryName ?? 'un inventario borrado'
  switch (entry.action) {
    case 'ITEM_ADDED':
      return { text: `${entry.actor} agregó ${d.quantity ?? 1}× ${entry.entityName} a ${where}`, detail: null }
    case 'ITEM_UPDATED':
      return { text: `${entry.actor} editó ${entry.entityName} en ${where}`, detail: describeChanges(d.changes as Changes) }
    case 'ITEM_REMOVED':
      return { text: `${entry.actor} quitó ${entry.entityName} de ${where}`, detail: null }
    case 'CURRENCY_UPDATED':
      return {
        text: `${entry.actor} cambió las monedas de ${where}`,
        detail: [describeChanges(d.changes as Changes), d.note ? `«${d.note}»` : null].filter(Boolean).join(' · '),
      }
    case 'TRADE_EXECUTED': {
      const who = (d.characterName as string) ?? 'Alguien'
      const what = `${d.quantity}× ${entry.entityName}`
      const denomination = d.priceDenomination as Denomination | null
      const price = formatMoney(Number(d.totalCp), denomination)
      const haggled = d.listUnitPriceCp != null && d.listUnitPriceCp !== d.unitPriceCp
      return {
        text: d.kind === 'SELL'
          ? `${who} vendió ${what} a ${where} por ${price}`
          : `${who} compró ${what} en ${where} por ${price}`,
        detail: [
          haggled ? `regateado (lista ${formatMoney(Number(d.listUnitPriceCp), denomination)} c/u)` : null,
          entry.actor === 'GM' ? 'aprobado por el GM' : null,
        ].filter(Boolean).join(' · ') || null,
      }
    }
  }
}
