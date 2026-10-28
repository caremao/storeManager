import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { invalidateInventory } from './hooks'

interface ChangeEvent {
  inventoryIds: string[]
  tradeIds: string[]
  /** Cambiaron las reglas de la mesa (viajan en /api/me). */
  campaign: boolean
}

const RETRY_MS = 5000

/**
 * Escucha /api/events y refresca lo que cambió. El servidor solo avisa qué cambió; los datos
 * se vuelven a pedir con los permisos de cada uno.
 */
export function useCampaignEvents(enabled: boolean) {
  const qc = useQueryClient()

  useEffect(() => {
    if (!enabled) return
    let source: EventSource | null = null
    let retryTimer: number | undefined
    let connectedBefore = false

    const connect = () => {
      source = new EventSource('/api/events')
      source.addEventListener('ready', () => {
        // Al reconectar pudimos perdernos avisos: se refresca todo.
        if (connectedBefore) qc.invalidateQueries()
        connectedBefore = true
      })
      source.addEventListener('change', (event) => {
        const data = JSON.parse((event as MessageEvent<string>).data) as ChangeEvent
        invalidateInventory(qc, ...data.inventoryIds)
        if (data.tradeIds.length > 0) qc.invalidateQueries({ queryKey: ['trades'] })
        if (data.campaign) qc.invalidateQueries({ queryKey: ['me'] })
      })
      source.onerror = () => {
        // EventSource reintenta solo; si se cerró del todo (p. ej. sesión revocada) reintentamos nosotros.
        if (source?.readyState === EventSource.CLOSED) {
          source.close()
          qc.invalidateQueries({ queryKey: ['me'] })
          retryTimer = window.setTimeout(connect, RETRY_MS)
        }
      }
    }

    connect()
    return () => {
      window.clearTimeout(retryTimer)
      source?.close()
    }
  }, [enabled, qc])
}
