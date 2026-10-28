import { useState } from 'react'
import { ArrowRight } from 'lucide-react'
import { errorMessage } from '../../api/client'
import type { Purse } from '../../api/types'
import { COIN, COINS, previewConversion, type Denomination } from '../../lib/coins'
import { ErrorBox } from '../ui/Feedback'
import { Modal } from '../ui/Modal'
import { input, label, primaryButton, secondaryButton } from '../ui/styles'

interface ConvertModalProps {
  purse: Purse
  onSubmit: (body: { from: Denomination; to: Denomination; amount: number }) => Promise<unknown>
  onClose: () => void
}

const HELD: Record<Denomination, keyof Purse> = { PLATINUM: 'platinum', GOLD: 'gold', SILVER: 'silver', COPPER: 'copper' }

/** Cambia monedas de una denominación a otra (p. ej. oro a plata), con vista previa. */
export function ConvertModal({ purse, onSubmit, onClose }: ConvertModalProps) {
  const [from, setFrom] = useState<Denomination>('GOLD')
  const [to, setTo] = useState<Denomination>('SILVER')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const qty = parseInt(amount, 10)
  const held = purse[HELD[from]]
  const valid = Number.isInteger(qty) && qty > 0 && from !== to
  const preview = valid ? previewConversion(qty, from, to) : null

  const submit = async () => {
    if (!valid) return setError(from === to ? 'Elige dos monedas distintas' : 'Indica cuántas monedas convertir')
    setSaving(true)
    setError(null)
    try {
      await onSubmit({ from, to, amount: qty })
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const coinSelect = (id: string, value: Denomination, onChange: (d: Denomination) => void) => (
    <select id={id} value={value} onChange={(e) => onChange(e.target.value as Denomination)} className={input}>
      {COINS.map((c) => <option key={c.key} value={c.key}>{c.name} ({c.abbr})</option>)}
    </select>
  )

  return (
    <Modal
      title="Convertir monedas"
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose} className={secondaryButton}>Cancelar</button>
          <button type="button" onClick={submit} disabled={saving || !preview?.received} className={primaryButton}>
            {saving ? 'Convirtiendo…' : 'Convertir'}
          </button>
        </>
      }
    >
      {error && <ErrorBox>{error}</ErrorBox>}
      <div className="grid grid-cols-[1fr_auto_1fr] items-end gap-2">
        <div>
          <label htmlFor="convert-from" className={label}>De</label>
          {coinSelect('convert-from', from, setFrom)}
        </div>
        <ArrowRight className="mb-3 w-5 h-5 text-gray-400" />
        <div>
          <label htmlFor="convert-to" className={label}>A</label>
          {coinSelect('convert-to', to, setTo)}
        </div>
      </div>
      <div>
        <label htmlFor="convert-amount" className={label}>Cantidad de {COIN[from].name} (tienes {held})</label>
        <input id="convert-amount" type="number" inputMode="numeric" min={1} max={held} value={amount}
          onChange={(e) => setAmount(e.target.value)} className={input} />
      </div>
      {preview && (
        <p className="text-gray-300">
          {preview.received > 0
            ? <>Recibes <strong>{preview.received} {COIN[to].abbr}</strong> y usas {preview.used} {COIN[from].abbr}
                {preview.used < qty && ` (te quedan ${qty - preview.used} ${COIN[from].abbr} sin convertir)`}.</>
            : `No alcanza para 1 ${COIN[to].abbr}.`}
        </p>
      )}
    </Modal>
  )
}
