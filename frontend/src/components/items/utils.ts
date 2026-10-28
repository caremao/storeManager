import type { Item, ItemRarity, ItemType } from '../../api/types'

// Colores por rareza tomados del proyecto original; etiquetas en español.
export const RARITY_COLORS: Record<ItemRarity, { bg: string; border: string; text: string; badge: string }> = {
  COMMON: { bg: 'bg-gray-800', border: 'border-gray-500', text: 'text-gray-400', badge: 'bg-gray-700 text-gray-300' },
  UNCOMMON: { bg: 'bg-green-900/30', border: 'border-green-500', text: 'text-green-400', badge: 'bg-green-900/40 text-green-300' },
  RARE: { bg: 'bg-blue-900/30', border: 'border-blue-500', text: 'text-blue-400', badge: 'bg-blue-900/40 text-blue-300' },
  VERY_RARE: { bg: 'bg-purple-900/30', border: 'border-purple-500', text: 'text-purple-400', badge: 'bg-purple-900/40 text-purple-300' },
  LEGENDARY: { bg: 'bg-orange-900/30', border: 'border-orange-500', text: 'text-orange-400', badge: 'bg-orange-900/40 text-orange-300' },
  ARTIFACT: { bg: 'bg-red-900/30', border: 'border-red-500', text: 'text-red-400', badge: 'bg-red-900/40 text-red-300' },
}

export const TYPE_LABELS: Record<ItemType, string> = {
  EQUIPMENT: 'Equipo',
  POTION: 'Poción',
  SCROLL: 'Pergamino',
  CONSUMABLE: 'Consumible',
  MISC: 'Varios',
}

export const RARITY_LABELS: Record<ItemRarity, string> = {
  COMMON: 'Común',
  UNCOMMON: 'Poco común',
  RARE: 'Raro',
  VERY_RARE: 'Muy raro',
  LEGENDARY: 'Legendario',
  ARTIFACT: 'Artefacto',
}

export const ITEM_TYPES: ItemType[] = ['EQUIPMENT', 'POTION', 'SCROLL', 'CONSUMABLE', 'MISC']
export const ITEM_RARITIES: ItemRarity[] = ['COMMON', 'UNCOMMON', 'RARE', 'VERY_RARE', 'LEGENDARY', 'ARTIFACT']

/** Propiedades que trae el catálogo SRD (ver tools/convert_srd.py), en el orden en que se muestran. */
export const PROPERTY_LABELS: Record<string, string> = {
  weaponCategory: 'Tipo de arma',
  damage: 'Daño',
  twoHandedDamage: 'Daño a dos manos',
  properties: 'Propiedades',
  range: 'Alcance',
  throwRange: 'Alcance arrojadiza',
  armorCategory: 'Tipo de armadura',
  armorClass: 'CA',
  strMinimum: 'Fuerza mínima',
  stealthDisadvantage: 'Desventaja en Sigilo',
  speed: 'Velocidad',
  capacity: 'Capacidad',
  contents: 'Contenido',
}

export function formatProperty(value: unknown): string {
  if (Array.isArray(value)) return value.join(', ')
  if (typeof value === 'boolean') return value ? 'Sí' : 'No'
  return String(value)
}

/** Resumen corto para la tarjeta: daño o CA si los hay. */
export function keyStat(item: Item): string | null {
  const p = item.properties
  if (!p) return null
  if (p.damage) return `Daño ${formatProperty(p.damage)}`
  if (p.armorClass) return `CA ${formatProperty(p.armorClass)}`
  return null
}

export function totalWeight(items: Item[]): number {
  return items.reduce((sum, item) => sum + (item.weight ?? 0) * item.quantity, 0)
}
