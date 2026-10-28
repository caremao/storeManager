import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { useDeleteCampaign, useResetPassphrase } from '../../api/hooks'
import type { AdminCampaign } from '../../api/types'
import { ErrorBox } from '../../components/ui/Feedback'
import { Modal } from '../../components/ui/Modal'
import { dangerSolidButton, input, label, primaryButton, secondaryButton } from '../../components/ui/styles'

/** Contraseña nueva para el GM (p. ej. si olvidó la suya). Cierra sus sesiones abiertas. */
export function ResetPassphraseModal({ campaign, onDone, onClose }: {
  campaign: AdminCampaign
  onDone: () => void
  onClose: () => void
}) {
  const reset = useResetPassphrase()
  const [passphrase, setPassphrase] = useState('')
  const [confirm, setConfirm] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)

  const submit = async (e?: React.FormEvent) => {
    e?.preventDefault()
    setLocalError(null)
    if (passphrase.length < 8) return setLocalError('La contraseña debe tener al menos 8 caracteres')
    if (passphrase !== confirm) return setLocalError('Las contraseñas no coinciden')
    try {
      await reset.mutateAsync({ campaignId: campaign.id, passphrase })
      onDone()
      onClose()
    } catch {
      // el error se muestra arriba
    }
  }

  const error = localError ?? (reset.error ? errorMessage(reset.error) : null)

  return (
    <Modal
      title={`Contraseña de ${campaign.name}`}
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose} className={secondaryButton}>Cancelar</button>
          <button type="button" onClick={() => submit()} disabled={reset.isPending} className={primaryButton}>
            {reset.isPending ? 'Guardando…' : 'Cambiar contraseña'}
          </button>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4">
        {error && <ErrorBox>{error}</ErrorBox>}
        <p className="text-gray-300">
          La contraseña actual deja de servir y quien esté dentro como GM tiene que volver a entrar. Los jugadores
          siguen conectados.
        </p>
        <div>
          <label htmlFor="reset-pass" className={label}>Contraseña nueva del GM</label>
          <input id="reset-pass" type="password" value={passphrase} onChange={(e) => setPassphrase(e.target.value)}
            autoComplete="new-password" className={input} />
        </div>
        <div>
          <label htmlFor="reset-confirm" className={label}>Repite la contraseña</label>
          <input id="reset-confirm" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password" className={input} />
        </div>
        {/* Enter en un campo envía el formulario */}
        <button type="submit" hidden />
      </form>
    </Modal>
  )
}

/** Borrar no tiene vuelta atrás: se confirma escribiendo el nombre de la campaña. */
export function DeleteCampaignModal({ campaign, onClose }: { campaign: AdminCampaign; onClose: () => void }) {
  const remove = useDeleteCampaign()
  const [typed, setTyped] = useState('')
  const shops = campaign.inventories.filter((i) => i.kind === 'SHOP').length
  const characters = campaign.inventories.length - shops
  const matches = typed.trim() === campaign.name.trim()

  const submit = async () => {
    try {
      await remove.mutateAsync(campaign.id)
      onClose()
    } catch {
      // el error se muestra arriba
    }
  }

  return (
    <Modal
      title={`Borrar ${campaign.name}`}
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose} className={secondaryButton}>Cancelar</button>
          <button type="button" onClick={submit} disabled={!matches || remove.isPending} className={dangerSolidButton}>
            {remove.isPending ? 'Borrando…' : 'Borrar para siempre'}
          </button>
        </>
      }
    >
      {remove.error && <ErrorBox>{errorMessage(remove.error)}</ErrorBox>}
      <p className="text-gray-300">
        Se borra <span className="font-mono text-gray-100">{campaign.slug}</span> con{' '}
        {shops} {shops === 1 ? 'tienda' : 'tiendas'}, {characters} {characters === 1 ? 'personaje' : 'personajes'},
        sus objetos y monedas, las solicitudes y todo el historial.
        {campaign.sessions.length > 0 && ` Se cierran las ${campaign.sessions.length} sesiones abiertas.`} Los QR dejan
        de funcionar. No se puede deshacer.
      </p>
      <div>
        <label htmlFor="delete-confirm" className={label}>
          Escribe <span className="font-semibold text-gray-100">{campaign.name}</span> para confirmar
        </label>
        <input id="delete-confirm" value={typed} onChange={(e) => setTyped(e.target.value)}
          autoComplete="off" className={input} />
      </div>
    </Modal>
  )
}
