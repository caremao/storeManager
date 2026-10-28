import { useEffect, useRef } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useJoin } from '../api/hooks'
import { ErrorBox, Spinner } from '../components/ui/Feedback'
import { card } from '../components/ui/styles'

/**
 * Destino del QR: cambia el token por una cookie de sesión y lo saca del historial del navegador.
 * El token llega en el fragmento (`/join#token`), que el navegador nunca envía al servidor; la ruta
 * `/join/:token` queda para los QR impresos antes de ese cambio.
 */
export function Join() {
  const { token: legacyToken } = useParams<{ token: string }>()
  const join = useJoin()
  const { mutateAsync } = join
  const navigate = useNavigate()
  const started = useRef(false)
  const token = window.location.hash.slice(1) || legacyToken

  useEffect(() => {
    // En StrictMode el efecto corre dos veces; el token solo se canjea una.
    if (!token || started.current) return
    started.current = true
    mutateAsync(token)
      .then(() => navigate('/pj', { replace: true }))
      .catch(() => undefined)
  }, [token, mutateAsync, navigate])

  if (!token || join.isError) {
    return (
      <div className={`${card} max-w-md mx-auto p-6 space-y-4`}>
        <ErrorBox>{token ? errorMessage(join.error) : 'Este link no trae el código del personaje.'}</ErrorBox>
        <Link to="/" className="text-indigo-400 hover:text-indigo-300">← Volver al inicio</Link>
      </div>
    )
  }
  return <Spinner label="Entrando a la mesa…" />
}
