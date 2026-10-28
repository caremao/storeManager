import { useEffect, useState } from 'react'
import QRCode from 'qrcode'

export function QrImage({ value, size = 220, alt }: { value: string; size?: number; alt: string }) {
  const [rendered, setRendered] = useState<{ value: string; src: string } | null>(null)

  useEffect(() => {
    let cancelled = false
    QRCode.toDataURL(value, { width: size, margin: 1, errorCorrectionLevel: 'M' }).then((src) => {
      if (!cancelled) setRendered({ value, src })
    })
    return () => {
      cancelled = true
    }
  }, [value, size])

  if (!rendered || rendered.value !== value) {
    return <div style={{ width: size, height: size }} className="bg-gray-700 rounded animate-pulse" />
  }
  return <img src={rendered.src} width={size} height={size} alt={alt} className="rounded bg-white" />
}
