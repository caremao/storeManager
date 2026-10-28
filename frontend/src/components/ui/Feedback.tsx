import type { ReactNode } from 'react'

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="text-center py-12">
      <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-indigo-600 border-t-transparent" />
      {label && <p className="mt-4 text-gray-400">{label}</p>}
    </div>
  )
}

export function ErrorBox({ children }: { children: ReactNode }) {
  return <div className="p-3 bg-red-900/30 border border-red-500 text-red-300 rounded-lg">{children}</div>
}

export function SuccessBox({ children }: { children: ReactNode }) {
  return <div className="p-3 bg-green-900/30 border border-green-600 text-green-300 rounded-lg">{children}</div>
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="text-center py-10 bg-gray-800 rounded-lg border-2 border-dashed border-gray-600">
      <h3 className="text-lg font-medium text-gray-100">{title}</h3>
      {children && <div className="mt-2 text-gray-400">{children}</div>}
    </div>
  )
}
