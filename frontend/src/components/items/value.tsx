import type { ReactNode } from 'react'
import type { Item } from '../../api/types'
import { Money } from '../ui/Money'

export interface ValueLine {
  label: string
  value: ReactNode
}

/**
 * El valor de un objeto de personaje: el real si el dueño lo conoce, o lo que cree que vale. El GM
 * ve las dos cosas. Lo usan la tarjeta de la lista y el detalle.
 */
export function characterValues(item: Item, role: 'gm' | 'player'): ValueLine[] {
  const real = (empty: string) => <Money cp={item.valueCp} denomination={item.priceDenomination} empty={empty} />
  const perceived = <Money cp={item.perceivedValueCp} denomination={item.perceivedDenomination} empty="desconocido" />
  if (role === 'gm') {
    return [
      { label: 'Valor real', value: real('sin fijar') },
      { label: 'El jugador cree', value: item.valueRevealed ? <span className="text-gray-300">lo conoce</span> : perceived },
    ]
  }
  return item.valueRevealed
    ? [{ label: 'Valor', value: real('sin precio') }]
    : [{ label: 'Valor estimado', value: perceived }]
}
