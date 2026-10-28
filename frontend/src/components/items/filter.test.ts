import { describe, expect, it } from 'vitest'
import { categoriesOf, fold, matches, type Filterable } from './filter'

const item = (name: string, category: string | null, type: Filterable['type'] = 'MISC'): Filterable => ({
  name,
  type,
  category,
})

describe('categoriesOf', () => {
  it('junta las que solo cambian en mayúsculas, tildes o espacios, y las ordena', () => {
    const categories = categoriesOf([
      item('Arco', 'armas'),
      item('Daga', 'Armas'),
      item('Hacha', ' armas '),
      item('Poción de curación', 'Pociones'),
      item('Antídoto', 'Pócimas'),
      item('Cuerda', null),
      item('Vela', '  '),
    ])
    expect(categories).toEqual([
      { key: 'armas', label: 'armas', count: 3 },
      { key: 'pocimas', label: 'Pócimas', count: 1 },
      { key: 'pociones', label: 'Pociones', count: 1 },
    ])
    expect(categoriesOf([item('Arco', 'armas'), item('Daga', 'Armas')])[0].label).toBe('Armas')
  })
})

describe('matches', () => {
  const potion = item('Poción de curación', 'Pociones', 'POTION')

  it('busca en el nombre y en la categoría, sin tildes ni mayúsculas', () => {
    expect(matches(potion, 'CURACION', 'ALL', null)).toBe(true)
    expect(matches(potion, 'pocio', 'ALL', null)).toBe(true)
    expect(matches(item('Antídoto', 'Pociones'), 'pociones', 'ALL', null)).toBe(true)
    expect(matches(potion, 'espada', 'ALL', null)).toBe(false)
    expect(matches(potion, '   ', 'ALL', null)).toBe(true)
  })

  it('suma el tipo y la categoría elegidos', () => {
    expect(matches(potion, '', 'POTION', fold('pociones'))).toBe(true)
    expect(matches(potion, '', 'SCROLL', null)).toBe(false)
    expect(matches(potion, '', 'ALL', 'armas')).toBe(false)
    expect(matches(item('Cuerda', null), '', 'ALL', 'armas')).toBe(false)
  })
})
