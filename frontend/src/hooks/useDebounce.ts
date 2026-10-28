import { useEffect, useState } from 'react'

/** Retrasa un valor (p. ej. el texto de una búsqueda) para no pedir en cada tecla. */
export function useDebounce<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState<T>(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])

  return debounced
}
