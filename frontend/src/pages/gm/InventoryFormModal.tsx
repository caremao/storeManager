import { useState } from 'react'
import { errorMessage } from '../../api/client'
import type { CreateInventoryInput, Inventory, InventoryKind, UpdateInventoryInput } from '../../api/types'
import { ErrorBox } from '../../components/ui/Feedback'
import { Modal } from '../../components/ui/Modal'
import { input, label, primaryButton, secondaryButton } from '../../components/ui/styles'

interface InventoryFormModalProps {
  kind: InventoryKind
  /** Si viene, edita; si no, crea. */
  inventory?: Inventory
  onSubmit: (body: CreateInventoryInput | UpdateInventoryInput) => Promise<unknown>
  onClose: () => void
}

/** Alta o edición de una tienda o un personaje, con los ajustes propios de cada uno. */
export function InventoryFormModal({ kind, inventory, onSubmit, onClose }: InventoryFormModalProps) {
  const isShop = kind === 'SHOP'
  const [name, setName] = useState(inventory?.name ?? '')
  const [description, setDescription] = useState(inventory?.description ?? '')
  const [playerName, setPlayerName] = useState(inventory?.playerName ?? '')
  const [visible, setVisible] = useState(inventory?.visibleToPlayers ?? false)
  const [approval, setApproval] = useState(inventory?.requiresApproval ?? true)
  const [unlimitedFunds, setUnlimitedFunds] = useState(inventory?.unlimitedFunds ?? false)
  const [markup, setMarkup] = useState(String(inventory?.sellMarkupPct ?? 100))
  const [buyback, setBuyback] = useState(String(inventory?.buybackPct ?? 50))
  const [gold, setGold] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async (e?: React.FormEvent) => {
    e?.preventDefault()
    if (!name.trim()) return setError('El nombre es obligatorio')
    const shop = isShop
      ? {
          visibleToPlayers: visible,
          requiresApproval: approval,
          unlimitedFunds,
          sellMarkupPct: parseInt(markup, 10) || 0,
          buybackPct: parseInt(buyback, 10) || 0,
        }
      : {}
    const base = {
      name: name.trim(),
      description: description.trim(),
      ...(isShop ? shop : { playerName: playerName.trim() }),
    }
    const initialGold = parseInt(gold, 10) || 0
    const body: CreateInventoryInput | UpdateInventoryInput = inventory
      ? base
      : { ...base, kind, purse: initialGold ? { copper: 0, silver: 0, gold: initialGold, platinum: 0 } : undefined }
    setSaving(true)
    setError(null)
    try {
      await onSubmit(body)
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const noun = isShop ? 'tienda' : 'personaje'

  return (
    <Modal
      title={inventory ? `Editar ${noun}` : isShop ? 'Nueva tienda' : 'Nuevo personaje'}
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose} className={secondaryButton}>Cancelar</button>
          <button type="button" onClick={() => submit()} disabled={saving} className={primaryButton}>
            {saving ? 'Guardando…' : inventory ? 'Guardar' : 'Crear'}
          </button>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4">
        {error && <ErrorBox>{error}</ErrorBox>}
        <div>
          <label htmlFor="inv-name" className={label}>Nombre</label>
          <input id="inv-name" value={name} onChange={(e) => setName(e.target.value)}
            placeholder={isShop ? 'Herrería de Cofferdeep' : 'Gharra'} className={input} />
        </div>
        {!isShop && (
          <div>
            <label htmlFor="inv-player" className={label}>Jugador (opcional)</label>
            <input id="inv-player" value={playerName} onChange={(e) => setPlayerName(e.target.value)} className={input} />
          </div>
        )}
        <div>
          <label htmlFor="inv-desc" className={label}>Descripción</label>
          <textarea id="inv-desc" rows={2} value={description} onChange={(e) => setDescription(e.target.value)}
            className={`${input} resize-y`} />
        </div>
        {!inventory && (
          <div>
            <label htmlFor="inv-gold" className={label}>{isShop ? 'Caja inicial (po)' : 'Oro inicial (po)'}</label>
            <input id="inv-gold" type="number" inputMode="numeric" min={0} value={gold}
              onChange={(e) => setGold(e.target.value)} className={input} />
          </div>
        )}
        {isShop && (
          <>
            <fieldset className="space-y-2">
              <Toggle checked={visible} onChange={setVisible} text="Abierta: los jugadores la ven en el mostrador" />
              <Toggle checked={approval} onChange={setApproval} text="Cada compra o venta la aprueba el GM" />
              <Toggle checked={unlimitedFunds} onChange={setUnlimitedFunds} text="Fondos ilimitados (no lleva caja)" />
            </fieldset>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label htmlFor="inv-markup" className={label}>Vende al (% del valor)</label>
                <input id="inv-markup" type="number" min={0} max={1000} value={markup}
                  onChange={(e) => setMarkup(e.target.value)} className={input} />
              </div>
              <div>
                <label htmlFor="inv-buyback" className={label}>Compra al (% del valor)</label>
                <input id="inv-buyback" type="number" min={0} max={1000} value={buyback}
                  onChange={(e) => setBuyback(e.target.value)} className={input} />
              </div>
            </div>
          </>
        )}
      </form>
    </Modal>
  )
}

function Toggle({ checked, onChange, text }: { checked: boolean; onChange: (v: boolean) => void; text: string }) {
  return (
    <label className="flex items-center gap-2 text-gray-300">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="w-4 h-4 accent-indigo-500" />
      {text}
    </label>
  )
}
