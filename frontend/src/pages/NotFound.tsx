import { Link } from 'react-router-dom'

export function NotFound() {
  return (
    <div className="text-center py-16">
      <h1 className="text-2xl font-bold text-gray-100">Aquí no hay nada</h1>
      <p className="mt-2 text-gray-400">Esta página no existe o ya no tienes acceso.</p>
      <Link to="/" className="inline-block mt-6 text-indigo-400 hover:text-indigo-300">← Volver al inicio</Link>
    </div>
  )
}
