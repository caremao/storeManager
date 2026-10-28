import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { QrCode } from 'lucide-react'
import { errorMessage } from '../api/client'
import { useCreateCampaign, useGmLogin, useMe, useServerConfig } from '../api/hooks'
import { ErrorBox, Spinner } from '../components/ui/Feedback'
import { card, input, label, primaryButton, secondaryButton } from '../components/ui/styles'

export function Home() {
  const { data: me, isLoading } = useMe()
  if (isLoading) return <Spinner />
  if (me) return <Navigate to={me.role === 'GM' ? '/gm' : '/pj'} replace />

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="text-center py-4">
        <h1 className="text-3xl font-bold text-gray-100">Tiendas e inventarios para tu mesa</h1>
        <p className="mt-2 text-gray-400">El GM arma las tiendas; los jugadores compran desde su teléfono.</p>
      </div>
      <div className={`${card} p-4 flex items-center gap-3 text-gray-300`}>
        <QrCode className="w-8 h-8 flex-shrink-0 text-indigo-400" />
        <p>¿Eres jugador? Escanea el QR de tu personaje que te muestra el GM; no necesitas cuenta.</p>
      </div>
      <div className="grid md:grid-cols-2 gap-6">
        <GmLoginForm />
        <CreateCampaignForm />
      </div>
    </div>
  )
}

function GmLoginForm() {
  const login = useGmLogin()
  const navigate = useNavigate()
  const [slug, setSlug] = useState('')
  const [passphrase, setPassphrase] = useState('')

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      await login.mutateAsync({ slug: slug.trim().toLowerCase(), passphrase })
      navigate('/gm', { replace: true })
    } catch {
      // el error se muestra abajo
    }
  }

  return (
    <form onSubmit={submit} className={`${card} p-6 space-y-4`}>
      <h2 className="text-xl font-semibold text-gray-100">Entrar como GM</h2>
      {login.error && <ErrorBox>{errorMessage(login.error)}</ErrorBox>}
      <div>
        <label htmlFor="login-slug" className={label}>Campaña</label>
        <input id="login-slug" value={slug} onChange={(e) => setSlug(e.target.value)}
          autoCapitalize="none" autoComplete="username" className={input} />
      </div>
      <div>
        <label htmlFor="login-pass" className={label}>Contraseña del GM</label>
        <input id="login-pass" type="password" value={passphrase} onChange={(e) => setPassphrase(e.target.value)}
          autoComplete="current-password" className={input} />
      </div>
      <button type="submit" disabled={login.isPending || !slug || !passphrase} className={`${primaryButton} w-full`}>
        {login.isPending ? 'Entrando…' : 'Entrar'}
      </button>
    </form>
  )
}

/** Igual que CampaignDtos.NAME_PATTERN: el nombre es también lo que el GM escribe para entrar. */
const CAMPAIGN_NAME = /^[A-Za-z0-9]+$/
const MAX_CAMPAIGN_NAME = 48

function CreateCampaignForm() {
  const create = useCreateCampaign()
  const { data: config } = useServerConfig()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [passphrase, setPassphrase] = useState('')
  const [confirm, setConfirm] = useState('')
  const [creationKey, setCreationKey] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)
  const nameOk = CAMPAIGN_NAME.test(name)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLocalError(null)
    if (!nameOk) return setLocalError('El nombre solo puede tener letras sin tilde y números, sin espacios')
    if (passphrase.length < 8) return setLocalError('La contraseña debe tener al menos 8 caracteres')
    if (passphrase !== confirm) return setLocalError('Las contraseñas no coinciden')
    try {
      await create.mutateAsync({ name, passphrase, creationKey: creationKey || undefined })
      navigate('/gm', { replace: true })
    } catch {
      // el error se muestra abajo
    }
  }

  const error = localError ?? (create.error ? errorMessage(create.error) : null)

  return (
    <form onSubmit={submit} className={`${card} p-6 space-y-4`}>
      <h2 className="text-xl font-semibold text-gray-100">Crear campaña</h2>
      {error && <ErrorBox>{error}</ErrorBox>}
      <div>
        <label htmlFor="new-name" className={label}>Nombre</label>
        <input id="new-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={MAX_CAMPAIGN_NAME}
          autoCapitalize="none" autoComplete="off" spellCheck={false} placeholder="Gemflower"
          aria-invalid={name !== '' && !nameOk} aria-describedby="new-name-hint" className={input} />
        <p id="new-name-hint" className={`mt-1 text-sm ${name !== '' && !nameOk ? 'text-red-400' : 'text-gray-400'}`}>
          Una sola palabra: letras sin tilde y números, sin espacios. Es lo que escribirás en «Campaña» para entrar.
        </p>
      </div>
      <div>
        <label htmlFor="new-pass" className={label}>Contraseña del GM</label>
        <input id="new-pass" type="password" value={passphrase} onChange={(e) => setPassphrase(e.target.value)}
          autoComplete="new-password" className={input} />
      </div>
      <div>
        <label htmlFor="new-confirm" className={label}>Repite la contraseña</label>
        <input id="new-confirm" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)}
          autoComplete="new-password" className={input} />
      </div>
      {config?.campaignCreationKeyRequired && (
        <div>
          <label htmlFor="new-key" className={label}>Clave de creación del servidor</label>
          <input id="new-key" type="password" value={creationKey} onChange={(e) => setCreationKey(e.target.value)} className={input} />
        </div>
      )}
      <button type="submit" disabled={create.isPending || !nameOk} className={`${secondaryButton} w-full`}>
        {create.isPending ? 'Creando…' : 'Crear y entrar'}
      </button>
    </form>
  )
}
