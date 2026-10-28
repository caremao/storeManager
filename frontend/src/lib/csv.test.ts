import { describe, expect, it } from 'vitest'
import { readCsv } from './csv'

describe('readCsv', () => {
  it('lee UTF-8, con o sin BOM', async () => {
    expect(await readCsv(new Blob(['nombre\nPoción\n']))).toBe('nombre\nPoción\n')
    expect(await readCsv(new Blob(['﻿nombre\nAnillo\n']))).toBe('nombre\nAnillo\n')
  })

  it('reconoce el CSV de Excel en Windows-1252', async () => {
    // "Poción" en Windows-1252: la ó es el byte 0xF3, que no es UTF-8 válido.
    const bytes = new Uint8Array([0x50, 0x6f, 0x63, 0x69, 0xf3, 0x6e])
    expect(await readCsv(new Blob([bytes]))).toBe('Poción')
  })
})
