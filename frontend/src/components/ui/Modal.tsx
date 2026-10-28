import type { ReactNode } from 'react'
import { X } from 'lucide-react'

interface ModalProps {
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  /** Contenido fijo entre el título y el cuerpo con scroll (p. ej. el buscador del SRD). */
  header?: ReactNode
}

export function Modal({ title, onClose, children, footer, header }: ModalProps) {
  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      <div className="flex min-h-full items-center justify-center p-4">
        <div className="fixed inset-0 bg-black/50" onClick={onClose} />
        <div className="relative w-full max-w-lg bg-gray-800 rounded-lg shadow-xl">
          <div className="flex items-center justify-between p-4 border-b border-gray-700">
            <h2 className="text-xl font-semibold text-gray-100">{title}</h2>
            <button
              type="button"
              onClick={onClose}
              aria-label="Cerrar"
              className="p-1 text-gray-400 hover:text-gray-200 rounded-lg hover:bg-gray-700"
            >
              <X className="w-6 h-6" />
            </button>
          </div>
          {header}
          <div className="p-4 space-y-4 max-h-[60vh] overflow-y-auto">{children}</div>
          {footer && (
            <div className="flex justify-end gap-3 p-4 border-t border-gray-700 bg-gray-900 rounded-b-lg">
              {footer}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
