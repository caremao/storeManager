// Monedas con las abreviaturas del manual en español: ppt (platino), po (oro), pp (plata), pc (cobre).
// Internamente todo es cobre: 10 pc = 1 pp, 10 pp = 1 po, 10 po = 1 ppt.

/** 1250 → "12 po 5 pp". */
export function formatCp(cp: number | null | undefined): string {
  if (cp === null || cp === undefined) return 'sin precio'
  const gp = Math.floor(cp / 100)
  const sp = Math.floor((cp % 100) / 10)
  const rest = cp % 10
  const parts: string[] = []
  if (gp > 0) parts.push(`${gp.toLocaleString('es')} po`)
  if (sp > 0) parts.push(`${sp} pp`)
  if (rest > 0 || parts.length === 0) parts.push(`${rest} pc`)
  return parts.join(' ')
}

/** Precio escrito en oro (admite decimales, p. ej. 0,5) → cobre. */
export function gpToCp(gp: number): number {
  return Math.round(gp * 100)
}

export function cpToGp(cp: number | null | undefined): number | '' {
  return cp === null || cp === undefined ? '' : cp / 100
}

/** Mismo redondeo que Pricing.apply del backend. */
export function applyPct(cp: number | null | undefined, pct: number | null | undefined): number | null {
  if (cp === null || cp === undefined || pct === null || pct === undefined) return null
  return Math.floor((cp * pct + 50) / 100)
}
