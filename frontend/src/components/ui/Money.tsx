import { moneyParts, type Denomination } from '../../lib/coins'

interface MoneyProps {
  cp: number | null | undefined
  denomination?: Denomination | null
  /** Texto si no hay precio. */
  empty?: string
  size?: 'sm' | 'md'
}

/** Un monto como chips de moneda, con los colores de la bolsa: [12 po] [5 pp]. */
export function Money({ cp, denomination, empty = 'sin precio', size = 'md' }: MoneyProps) {
  if (cp === null || cp === undefined) return <span className="text-gray-500 italic">{empty}</span>
  const text = size === 'sm' ? 'text-xs' : 'text-sm'
  return (
    <span className="inline-flex flex-wrap items-baseline gap-1 align-baseline">
      {moneyParts(cp, denomination).map(({ coin, amount }) => (
        <span key={coin.key} className={`inline-flex items-baseline gap-0.5 rounded px-1.5 py-0.5 font-semibold ${text} ${coin.chip}`}>
          {amount.toLocaleString('es')}
          <span className="text-[0.75em] font-medium opacity-80">{coin.abbr}</span>
        </span>
      ))}
    </span>
  )
}
