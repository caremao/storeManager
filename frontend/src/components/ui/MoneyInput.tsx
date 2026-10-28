import { COINS, type Denomination, type MoneyValue } from '../../lib/coins'
import { input } from './styles'

interface MoneyInputProps {
  id: string
  value: MoneyValue
  onChange: (value: MoneyValue) => void
  disabled?: boolean
  compact?: boolean
}

/** Cantidad y moneda: "3" + "pp". Admite decimales con coma o punto. */
export function MoneyInput({ id, value, onChange, disabled, compact }: MoneyInputProps) {
  const padding = compact ? 'py-1.5' : ''
  return (
    <div className="flex gap-1">
      <input
        id={id}
        type="text"
        inputMode="decimal"
        autoComplete="off"
        value={value.amount}
        disabled={disabled}
        onChange={(e) => onChange({ ...value, amount: e.target.value })}
        className={`${input} ${padding} min-w-0`}
      />
      <select
        aria-label="Moneda"
        value={value.denomination}
        disabled={disabled}
        onChange={(e) => onChange({ ...value, denomination: e.target.value as Denomination })}
        className={`${input} ${padding} w-auto shrink-0`}
      >
        {COINS.map((coin) => (
          <option key={coin.key} value={coin.key}>{coin.abbr}</option>
        ))}
      </select>
    </div>
  )
}
