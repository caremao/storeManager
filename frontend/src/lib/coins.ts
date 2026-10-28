// Monedas con las abreviaturas del manual en español: ppt (platino), po (oro), pp (plata), pc (cobre).
// Internamente todo es cobre: 10 pc = 1 pp, 10 pp = 1 po, 10 po = 1 ppt.

export type Denomination = 'PLATINUM' | 'GOLD' | 'SILVER' | 'COPPER'

export interface CoinInfo {
  key: Denomination
  cp: number
  abbr: string
  name: string
  /** Clases del chip: mismos colores que la bolsa. */
  chip: string
}

export const COINS: CoinInfo[] = [
  { key: 'PLATINUM', cp: 1000, abbr: 'ppt', name: 'platino', chip: 'bg-slate-700/60 text-slate-200' },
  { key: 'GOLD', cp: 100, abbr: 'po', name: 'oro', chip: 'bg-yellow-900/40 text-yellow-300' },
  { key: 'SILVER', cp: 10, abbr: 'pp', name: 'plata', chip: 'bg-gray-600/50 text-gray-200' },
  { key: 'COPPER', cp: 1, abbr: 'pc', name: 'cobre', chip: 'bg-amber-900/40 text-amber-300' },
]

export const COIN: Record<Denomination, CoinInfo> = Object.fromEntries(COINS.map((c) => [c.key, c])) as Record<
  Denomination,
  CoinInfo
>

export interface MoneyPart {
  coin: CoinInfo
  amount: number
}

/**
 * Desglose de un monto para mostrarlo en su moneda: si es exacto, una sola parte ("3 pp");
 * si no, desde esa moneda hacia abajo ("3 pp 3 pc"). Sin moneda indicada se usa oro, y el
 * platino solo aparece si es la moneda del precio.
 */
export function moneyParts(cp: number, denomination: Denomination | null | undefined = 'GOLD'): MoneyPart[] {
  const preferred = COIN[denomination ?? 'GOLD']
  if (cp % preferred.cp === 0) return [{ coin: preferred, amount: cp / preferred.cp }]
  const parts: MoneyPart[] = []
  let rest = cp
  for (const coin of COINS) {
    if (coin.cp > preferred.cp) continue
    const amount = Math.floor(rest / coin.cp)
    rest -= amount * coin.cp
    if (amount > 0) parts.push({ coin, amount })
  }
  return parts
}

/** Versión en texto (historial, mensajes, opciones de un select): 1250 → "12 po 5 pp". */
export function formatMoney(cp: number | null | undefined, denomination?: Denomination | null): string {
  if (cp === null || cp === undefined) return 'sin precio'
  return moneyParts(cp, denomination)
    .map((p) => `${p.amount.toLocaleString('es')} ${p.coin.abbr}`)
    .join(' ')
}

/** "2,5" o "2.5" → 2.5; vacío → null; texto inválido o negativo → NaN. */
export function parseAmount(text: string): number | null {
  const trimmed = text.trim()
  if (trimmed === '') return null
  if (!/^\d+([.,]\d+)?$/.test(trimmed)) return Number.NaN
  return Number(trimmed.replace(',', '.'))
}

/** Monto escrito en una moneda → cobre, redondeado al cobre. */
export function toCp(amount: number, denomination: Denomination): number {
  return Math.round(amount * COIN[denomination].cp)
}

/** Cobre → monto en esa moneda, como texto para un campo ("2,5"). */
export function fromCp(cp: number | null | undefined, denomination: Denomination): string {
  if (cp === null || cp === undefined) return ''
  return String(cp / COIN[denomination].cp).replace('.', ',')
}

/** Lo que guarda un campo de precio: el texto tal cual se escribe, más la moneda elegida. */
export interface MoneyValue {
  amount: string
  denomination: Denomination
}

export function moneyValue(cp: number | null | undefined, denomination: Denomination | null | undefined): MoneyValue {
  const d = denomination ?? 'GOLD'
  return { amount: fromCp(cp, d), denomination: d }
}

/** null si está vacío; NaN si el texto no es un número válido. */
export function moneyValueToCp(value: MoneyValue): number | null {
  const amount = parseAmount(value.amount)
  if (amount === null) return null
  if (Number.isNaN(amount)) return Number.NaN
  return toCp(amount, value.denomination)
}

/** La moneda más grande en que un monto es exacto (sin platino): sirve para precios del SRD. */
export function naturalDenomination(cp: number | null | undefined): Denomination {
  if (!cp || cp % 100 === 0) return 'GOLD'
  return cp % 10 === 0 ? 'SILVER' : 'COPPER'
}

/** Mismo redondeo que Pricing.apply del backend. */
export function applyPct(cp: number | null | undefined, pct: number | null | undefined): number | null {
  if (cp === null || cp === undefined || pct === null || pct === undefined) return null
  return Math.floor((cp * pct + 50) / 100)
}

/** Lo que haría Purse.convert: cuánto se recibe y cuánto se usa de la moneda de origen. */
export function previewConversion(amount: number, from: Denomination, to: Denomination) {
  const received = Math.floor((amount * COIN[from].cp) / COIN[to].cp)
  const used = (received * COIN[to].cp) / COIN[from].cp
  return { received, used }
}
