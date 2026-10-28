import { useState } from 'react'
import { errorMessage } from '../../api/client'
import type { CurrencyChange } from '../../api/types'
import { ErrorBox } from '../ui/Feedback'
import { Modal } from '../ui/Modal'
import { input, label, primaryButton, secondaryButton } from '../ui/styles'

interface CurrencyModalProps {
  mode: 'add' | 'spend'
  onSubmit: (change: CurrencyChange) => Promise<unknown>
  onClose: () => void
}

const FIELDS = [
  { key: 'platinum', label: 'Platino (ppt)' },
  { key: 'gold', label: 'Oro (po)' },
  { key: 'silver', label: 'Plata (pp)' },
  { key: 'copper', label: 'Cobre (pc)' },
] as const

type Amounts = Record<(typeof FIELDS)[number]['key'], string>

/** Sumar o gastar monedas. Al gastar, el servidor da cambio si las monedas exactas no alcanzan. */
export function CurrencyModal({ mode, onSubmit, onClose }: CurrencyModalProps) {
  const [amounts, setAmounts] = useState<Amounts>({ platinum: '', gold: '', silver: '', copper: '' })
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const sign = mode === 'add' ? 1 : -1

  const submit = async (e?: React.FormEvent) => {
    e?.preventDefault()
    const n = (v: string) => Math.max(0, parseInt(v, 10) || 0) * sign
    const change: CurrencyChange = {
      platinum: n(amounts.platinum),
      gold: n(amounts.gold),
      silver: n(amounts.silver),
      copper: n(amounts.copper),
      note: note.trim() || undefined,
    }
    if (!change.platinum && !change.gold && !change.silver && !change.copper) {
      return setError('Indica al menos una cantidad')
    }
    setSaving(true)
    setError(null)
    try {
      await onSubmit(change)
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={mode === 'add' ? 'Sumar monedas' : 'Gastar monedas'}
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose} className={secondaryButton}>Cancelar</button>
          <button type="button" onClick={() => submit()} disabled={saving} className={primaryButton}>
            {saving ? 'Guardando…' : mode === 'add' ? 'Sumar' : 'Gastar'}
          </button>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4">
        {error && <ErrorBox>{error}</ErrorBox>}
        <div className="grid grid-cols-2 gap-4">
          {FIELDS.map((field) => (
            <div key={field.key}>
              <label htmlFor={`coin-${field.key}`} className={label}>{field.label}</label>
              <input
                id={`coin-${field.key}`}
                type="number"
                inputMode="numeric"
                min={0}
                value={amounts[field.key]}
                onChange={(e) => setAmounts((a) => ({ ...a, [field.key]: e.target.value }))}
                className={input}
              />
            </div>
          ))}
        </div>
        <div>
          <label htmlFor="coin-note" className={label}>Nota (opcional)</label>
          <input id="coin-note" value={note} onChange={(e) => setNote(e.target.value)}
            placeholder={mode === 'add' ? 'Botín del dragón' : 'Posada y comida'} className={input} />
        </div>
      </form>
    </Modal>
  )
}
