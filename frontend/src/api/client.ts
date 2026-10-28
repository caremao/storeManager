import axios, { AxiosError } from 'axios'

// Rutas relativas: en dev las reenvía Vite y en producción la SPA y la API comparten origen.
// La sesión viaja en una cookie HttpOnly, así que aquí no hay credenciales.
export const api = axios.create({
  baseURL: '',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

/** Mensaje para mostrar: el `detail` del ProblemDetail del backend, ya en español. */
export function errorMessage(error: unknown, fallback = 'Algo salió mal; vuelve a intentarlo.'): string {
  if (error instanceof AxiosError) {
    const detail = (error.response?.data as { detail?: string } | undefined)?.detail
    if (detail) return detail
    if (!error.response) return 'No hay conexión con el servidor'
  }
  return fallback
}

export function isStatus(error: unknown, status: number): boolean {
  return error instanceof AxiosError && error.response?.status === status
}
