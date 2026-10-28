import { describe as group, expect, it } from 'vitest'
import type { HistoryEntry } from '../../api/types'
import { describe } from './describe'

const entry = (over: Partial<HistoryEntry>): HistoryEntry => ({
  id: '1',
  inventoryId: 'inv',
  inventoryName: 'Gharra',
  relatedInventoryId: null,
  actor: 'GM',
  action: 'ITEM_UPDATED',
  entityName: 'Ídolo',
  details: null,
  createdAt: '2026-10-28T00:00:00Z',
  ...over,
})

group('historial', () => {
  it('nombra los campos en español y formatea montos', () => {
    const { text, detail } = describe(entry({
      details: {
        changes: {
          perceivedValueCp: { old: null, new: 50000 },
          valueRevealed: { old: false, new: true },
          perceivedDenomination: { old: null, new: 'GOLD' },
        },
      },
    }))
    expect(text).toBe('GM editó Ídolo en Gharra')
    expect(detail).toBe('valor estimado: — → 500 po, conoce el valor: no → sí')
  })

  it('marca las compras regateadas con el precio de lista', () => {
    const { text, detail } = describe(entry({
      action: 'TRADE_EXECUTED',
      inventoryName: 'Zoco',
      actor: 'Gharra',
      entityName: 'Alfombra',
      details: { kind: 'BUY', quantity: 1, unitPriceCp: 8000, listUnitPriceCp: 10000, totalCp: 8000,
        priceDenomination: 'GOLD', characterName: 'Gharra' },
    }))
    expect(text).toBe('Gharra compró 1× Alfombra en Zoco por 80 po')
    expect(detail).toBe('regateado (lista 100 po c/u)')
  })
})
