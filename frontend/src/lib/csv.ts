/**
 * Excel en español guarda "CSV (delimitado por comas)" en Windows-1252, no en UTF-8: si al leerlo
 * como UTF-8 aparecen caracteres inválidos, se vuelve a leer en esa codificación.
 */
export async function readCsv(file: Blob): Promise<string> {
  const bytes = await file.arrayBuffer()
  const utf8 = new TextDecoder('utf-8').decode(bytes)
  return utf8.includes('�') ? new TextDecoder('windows-1252').decode(bytes) : utf8
}
