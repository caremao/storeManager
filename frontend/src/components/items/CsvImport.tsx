import { useRef, useState } from 'react'
import { Download, FileUp } from 'lucide-react'
import { errorMessage } from '../../api/client'
import { useImportItems } from '../../api/hooks'
import type { CsvPosition, ImportResult } from '../../api/types'
import { readCsv } from '../../lib/csv'
import { ErrorBox, SuccessBox } from '../ui/Feedback'
import { Modal } from '../ui/Modal'
import { Money } from '../ui/Money'
import { primaryButton, secondaryButton, smallButton } from '../ui/styles'

/** El mismo tope que el servidor (app.max-request-bytes). */
const MAX_BYTES = 256 * 1024

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

/** La línea del archivo y, si una celda con saltos de línea las separa, la fila de Excel. */
function Where({ line, row }: CsvPosition) {
  if (!line) return <>—</>
  return (
    <>
      {line}
      {row > 0 && row !== line && <span className="block text-xs text-gray-500">fila {row} en Excel</span>}
    </>
  )
}

/**
 * "Descargar plantilla" e "Importar CSV" para el encabezado de items del GM. Al elegir el archivo
 * se hace el barrido de validación (dry run): o la lista de errores con su línea y columna, o la
 * confirmación de que todo está bien con la vista previa, y recién ahí se puede importar.
 */
export function CsvImport({ inventoryId }: { inventoryId: string }) {
  const fileInput = useRef<HTMLInputElement>(null)
  const importer = useImportItems(inventoryId)
  const [file, setFile] = useState<{ name: string; csv: string } | null>(null)
  const [preview, setPreview] = useState<ImportResult | null>(null)
  const [done, setDone] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const close = () => {
    setFile(null)
    setPreview(null)
    setDone(null)
    setError(null)
    importer.reset()
  }

  const pick = async (picked: File | undefined) => {
    if (!picked) return
    close()
    if (picked.size > MAX_BYTES) {
      setFile({ name: picked.name, csv: '' })
      setError('El archivo pasa de 256 KB: divídelo en partes.')
      return
    }
    const csv = await readCsv(picked)
    setFile({ name: picked.name, csv })
    try {
      setPreview(await importer.mutateAsync({ csv, dryRun: true }))
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const confirm = async () => {
    if (!file) return
    setError(null)
    try {
      const result = await importer.mutateAsync({ csv: file.csv, dryRun: false })
      // Si alguien cambió el stock entre la vista previa y la confirmación, vuelven los errores.
      if (result.errors.length > 0) setPreview(result)
      else setDone(result.imported)
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const chooseAnother = () => {
    if (fileInput.current) fileInput.current.value = ''
    fileInput.current?.click()
  }

  const errors = preview?.errors ?? []
  const rows = preview?.rows ?? []
  const merging = rows.filter((r) => r.merges).length
  // Sin nada que importar, lo que sigue es corregir el archivo y elegirlo de nuevo.
  const blocked = errors.length > 0 || (preview !== null && rows.length === 0) || (preview === null && error !== null)

  return (
    <div className="flex flex-wrap gap-2">
      <a href={`/api/inventories/${inventoryId}/items/template.csv`} download
        className={`${secondaryButton} ${smallButton}`}>
        <Download className="w-4 h-4" /> Descargar plantilla
      </a>
      <button type="button" onClick={chooseAnother} className={`${secondaryButton} ${smallButton}`}>
        <FileUp className="w-4 h-4" /> Importar CSV
      </button>
      <input ref={fileInput} type="file" accept=".csv,text/csv" className="hidden" aria-label="Archivo CSV"
        onChange={(e) => pick(e.target.files?.[0])} />

      {file && (
        <Modal
          title={`Importar ${file.name}`}
          onClose={close}
          footer={
            done !== null ? (
              <button type="button" onClick={close} className={primaryButton}>Cerrar</button>
            ) : blocked ? (
              <>
                <button type="button" onClick={close} className={secondaryButton}>Cerrar</button>
                <button type="button" onClick={chooseAnother} className={primaryButton}>Elegir otro archivo</button>
              </>
            ) : (
              <>
                <button type="button" onClick={chooseAnother} className={secondaryButton}>Elegir otro archivo</button>
                <button type="button" onClick={confirm} disabled={!preview || importer.isPending}
                  className={primaryButton}>
                  {!preview ? 'Revisando…' : importer.isPending ? 'Importando…' : `Importar ${plural(rows.length, 'fila', 'filas')}`}
                </button>
              </>
            )
          }
        >
          {error && <ErrorBox>{error}</ErrorBox>}
          {done !== null && <SuccessBox>Listo: se importaron {plural(done, 'fila', 'filas')}.</SuccessBox>}
          {!preview && !error && <p className="text-gray-400">Revisando el archivo…</p>}

          {done === null && errors.length > 0 && (
            <div className="space-y-2">
              <ErrorBox>
                {errors.length === 1 ? 'La revisión encontró un error' : `La revisión encontró ${errors.length} errores`}.
                Corrige el archivo y vuelve a elegirlo: no se importa nada mientras haya errores.
              </ErrorBox>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-400 border-b border-gray-700">
                    <th className="py-1 pr-2 font-normal">Línea</th>
                    <th className="py-1 pr-2 font-normal">Columna</th>
                    <th className="py-1 font-normal">Error</th>
                  </tr>
                </thead>
                <tbody>
                  {errors.map((e, i) => (
                    <tr key={i} className="border-b border-gray-700/50 align-top">
                      <td className="py-1 pr-2 text-gray-300 whitespace-nowrap"><Where {...e} /></td>
                      <td className="py-1 pr-2 text-gray-300 break-words">{e.column ?? '—'}</td>
                      <td className="py-1 text-red-300">{e.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {done === null && preview && errors.length === 0 && rows.length === 0 && (
            <p className="text-gray-400">El archivo no trae filas con datos.</p>
          )}

          {done === null && errors.length === 0 && rows.length > 0 && (
            <div className="space-y-2">
              <SuccessBox>
                Todo está OK: {plural(rows.length, 'fila revisada', 'filas revisadas')}, sin errores.
                {merging > 0 && ` ${merging === 1 ? 'Una se suma' : `${merging} se suman`} al stock de un item que ya existe.`}
              </SuccessBox>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-400 border-b border-gray-700">
                    <th className="py-1 pr-2 font-normal">Línea</th>
                    <th className="py-1 pr-2 font-normal">Nombre</th>
                    <th className="py-1 pr-2 font-normal text-right">Cant.</th>
                    <th className="py-1 font-normal text-right">Valor</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={r.line} className="border-b border-gray-700/50 align-top">
                      <td className="py-1 pr-2 text-gray-400 whitespace-nowrap"><Where {...r} /></td>
                      <td className="py-1 pr-2 text-gray-100">
                        {r.name}
                        {r.merges && <span className="ml-2 text-xs text-indigo-300">suma al stock</span>}
                        {r.category && !r.merges && <span className="block text-xs text-gray-400">{r.category}</span>}
                      </td>
                      <td className="py-1 pr-2 text-right text-gray-200">{r.quantity}</td>
                      <td className="py-1 text-right"><Money cp={r.valueCp} denomination={r.denomination} empty="—" /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Modal>
      )}
    </div>
  )
}
