import { useRef, useState } from 'react'
import { Download, FileUp } from 'lucide-react'
import { errorMessage } from '../../api/client'
import { useImportItems } from '../../api/hooks'
import type { ImportResult } from '../../api/types'
import { readCsv } from '../../lib/csv'
import { ErrorBox, SuccessBox } from '../ui/Feedback'
import { Modal } from '../ui/Modal'
import { Money } from '../ui/Money'
import { primaryButton, secondaryButton, smallButton } from '../ui/styles'

/** El mismo tope que el servidor (app.max-request-bytes). */
const MAX_BYTES = 256 * 1024

/** "Descargar plantilla" e "Importar CSV" para el encabezado de items del GM. */
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
            ) : (
              <>
                <button type="button" onClick={chooseAnother} className={secondaryButton}>Elegir otro archivo</button>
                <button type="button" onClick={confirm}
                  disabled={!preview || errors.length > 0 || rows.length === 0 || importer.isPending}
                  className={primaryButton}>
                  {importer.isPending ? 'Importando…' : `Importar ${rows.length} ${rows.length === 1 ? 'fila' : 'filas'}`}
                </button>
              </>
            )
          }
        >
          {error && <ErrorBox>{error}</ErrorBox>}
          {done !== null && <SuccessBox>Listo: se importaron {done} {done === 1 ? 'fila' : 'filas'}.</SuccessBox>}
          {!preview && !error && <p className="text-gray-400">Revisando el archivo…</p>}
          {done === null && errors.length > 0 && (
            <div className="space-y-2">
              <ErrorBox>
                {errors.length === 1 ? 'Hay un error' : `Hay ${errors.length} errores`}: no se importa nada hasta
                corregir el archivo.
              </ErrorBox>
              <ul className="space-y-1 text-sm">
                {errors.map((e, i) => (
                  <li key={i} className="text-red-300">
                    <span className="text-gray-400">Fila {e.row}:</span> {e.message}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {done === null && preview && errors.length === 0 && rows.length === 0 && (
            <p className="text-gray-400">El archivo no trae filas con datos.</p>
          )}
          {done === null && rows.length > 0 && (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-400 border-b border-gray-700">
                  <th className="py-1 pr-2 font-normal">Fila</th>
                  <th className="py-1 pr-2 font-normal">Nombre</th>
                  <th className="py-1 pr-2 font-normal text-right">Cant.</th>
                  <th className="py-1 font-normal text-right">Valor</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.row} className="border-b border-gray-700/50">
                    <td className="py-1 pr-2 text-gray-400">{r.row}</td>
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
          )}
        </Modal>
      )}
    </div>
  )
}
