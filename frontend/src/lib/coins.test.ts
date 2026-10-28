import { describe, expect, it } from 'vitest'
import {
  applyPct,
  formatMoney,
  fromCp,
  moneyValue,
  moneyValueToCp,
  naturalDenomination,
  parseAmount,
  previewConversion,
  toCp,
} from './coins'

describe('formatMoney', () => {
  it('muestra el monto en la moneda del precio cuando es exacto', () => {
    expect(formatMoney(30, 'SILVER')).toBe('3 pp')
    expect(formatMoney(2000, 'PLATINUM')).toBe('2 ppt')
    expect(formatMoney(5000)).toBe('50 po')
  })

  it('desglosa hacia abajo cuando no es exacto', () => {
    expect(formatMoney(33, 'SILVER')).toBe('3 pp 3 pc')
    expect(formatMoney(1250, 'GOLD')).toBe('12 po 5 pp')
    expect(formatMoney(1250, 'PLATINUM')).toBe('1 ppt 2 po 5 pp')
    expect(formatMoney(5, 'GOLD')).toBe('5 pc')
  })

  it('sin precio y cero', () => {
    expect(formatMoney(null)).toBe('sin precio')
    expect(formatMoney(0, 'SILVER')).toBe('0 pp')
  })

  it('el platino solo aparece si es la moneda del precio', () => {
    expect(formatMoney(12_000)).toBe(`${(120).toLocaleString('es')} po`)
  })
})

describe('montos escritos', () => {
  it('acepta coma o punto decimal', () => {
    expect(parseAmount('2,5')).toBe(2.5)
    expect(parseAmount('2.5')).toBe(2.5)
    expect(parseAmount(' 7 ')).toBe(7)
  })

  it('vacío es null y lo inválido es NaN', () => {
    expect(parseAmount('')).toBeNull()
    expect(parseAmount('-3')).toBeNaN()
    expect(parseAmount('3 po')).toBeNaN()
  })

  it('convierte a cobre y vuelve', () => {
    expect(toCp(2.5, 'GOLD')).toBe(250)
    expect(toCp(3, 'SILVER')).toBe(30)
    expect(toCp(1, 'PLATINUM')).toBe(1000)
    expect(fromCp(250, 'GOLD')).toBe('2,5')
    expect(fromCp(null, 'GOLD')).toBe('')
  })

  it('un campo de precio va y vuelve sin perder la moneda', () => {
    expect(moneyValue(30, 'SILVER')).toEqual({ amount: '3', denomination: 'SILVER' })
    expect(moneyValue(null, null)).toEqual({ amount: '', denomination: 'GOLD' })
    expect(moneyValueToCp({ amount: '1,5', denomination: 'PLATINUM' })).toBe(1500)
    expect(moneyValueToCp({ amount: '', denomination: 'GOLD' })).toBeNull()
    expect(moneyValueToCp({ amount: 'diez', denomination: 'GOLD' })).toBeNaN()
  })

  it('elige la moneda natural de un precio del SRD', () => {
    expect(naturalDenomination(1500)).toBe('GOLD')
    expect(naturalDenomination(20)).toBe('SILVER')
    expect(naturalDenomination(5)).toBe('COPPER')
  })
})

describe('cálculos que espejan al backend', () => {
  it('aplica porcentajes con el mismo redondeo que Pricing.apply', () => {
    expect(applyPct(15, 50)).toBe(8)
    expect(applyPct(1500, 50)).toBe(750)
    expect(applyPct(null, 50)).toBeNull()
  })

  it('anticipa una conversión como Purse.convert', () => {
    expect(previewConversion(250, 'COPPER', 'GOLD')).toEqual({ received: 2, used: 200 })
    expect(previewConversion(3, 'GOLD', 'SILVER')).toEqual({ received: 30, used: 3 })
  })
})
