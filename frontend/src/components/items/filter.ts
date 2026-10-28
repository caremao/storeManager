import { useMemo, useState } from 'react'
import type { ItemType } from '../../api/types'
import { useDebounce } from '../../hooks/useDebounce'
import { ITEM_TYPES } from './utils'

/** Lo mínimo para filtrar: sirve para los items y para los productos de la vitrina. */
export interface Filterable {
  name: string
  type: ItemType
  category: string | null
}

export type TypeTab = 'ALL' | ItemType

export interface CategoryOption {
  /** La categoría sin mayúsculas ni tildes: "Pociones" y "pociones " son la misma. */
  key: string
  /** La grafía más usada; si empatan, la que empieza con mayúscula ("Armas" antes que "armas"). */
  label: string
  count: number
}

/** Para comparar sin mayúsculas ni tildes: "pocion" encuentra "Poción". */
export function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').trim().toLowerCase()
}

/** Las categorías que hay, una por grafía, en orden alfabético. */
export function categoriesOf(items: Filterable[]): CategoryOption[] {
  const spellings = new Map<string, Map<string, number>>()
  for (const item of items) {
    const label = item.category?.trim()
    if (!label) continue
    const key = fold(label)
    const counts = spellings.get(key) ?? new Map<string, number>()
    counts.set(label, (counts.get(label) ?? 0) + 1)
    spellings.set(key, counts)
  }
  const capitalized = (s: string) => (s[0] !== s[0].toLowerCase() ? 1 : 0)
  return [...spellings.entries()]
    .map(([key, counts]) => {
      const [label] = [...counts.entries()].sort(
        ([a, na], [b, nb]) => nb - na || capitalized(b) - capitalized(a),
      )[0]
      return { key, label, count: [...counts.values()].reduce((sum, n) => sum + n, 0) }
    })
    .sort((a, b) => a.label.localeCompare(b.label, 'es'))
}

/** El texto busca en el nombre y en la categoría; el tipo y la categoría elegidos se suman. */
export function matches(item: Filterable, query: string, type: TypeTab, category: string | null): boolean {
  if (type !== 'ALL' && item.type !== type) return false
  const itemCategory = item.category ? fold(item.category) : ''
  if (category !== null && itemCategory !== category) return false
  const q = fold(query)
  return !q || fold(item.name).includes(q) || itemCategory.includes(q)
}

/** Búsqueda, pestañas por tipo y categorías de una lista; filtra en el cliente. */
export function useItemFilter<T extends Filterable>(items: T[]) {
  const [search, setSearch] = useState('')
  const [typeTab, setType] = useState<TypeTab>('ALL')
  const [categoryKey, setCategory] = useState<string | null>(null)
  const query = useDebounce(search, 200)

  const typeCounts = useMemo(() => {
    const result: Record<string, number> = { ALL: items.length }
    for (const item of items) result[item.type] = (result[item.type] ?? 0) + 1
    return result
  }, [items])
  const categories = useMemo(() => categoriesOf(items), [items])

  // Si se vende lo último de un tipo o una categoría, su botón desaparece y el filtro también.
  const type = typeCounts[typeTab] ? typeTab : 'ALL'
  const category = categories.some((c) => c.key === categoryKey) ? categoryKey : null

  const visible = useMemo(
    () => items.filter((item) => matches(item, query, type, category)),
    [items, query, type, category],
  )

  const types: TypeTab[] = ['ALL', ...ITEM_TYPES.filter((t) => typeCounts[t])]
  // Solo se ofrecen las categorías si elegir una deja afuera algo.
  const showCategories = categories.length > 1 || (categories.length === 1 && categories[0].count < items.length)

  return {
    search,
    setSearch,
    type,
    setType,
    types: types.length > 2 ? types : [],
    typeCounts,
    category,
    setCategory,
    categories: showCategories ? categories : [],
    visible,
    filtered: query.trim() !== '' || type !== 'ALL' || category !== null,
    clear: () => {
      setSearch('')
      setType('ALL')
      setCategory(null)
    },
  }
}

export type ItemFilter = ReturnType<typeof useItemFilter>
