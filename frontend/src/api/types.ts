// Espejo de los DTO del backend (records en *Dtos.java). Los campos `| null` siempre vienen en el JSON.

import type { Denomination } from '../lib/coins'

export type { Denomination }

export type Role = 'GM' | 'PLAYER'
export type InventoryKind = 'SHOP' | 'CHARACTER' | 'PARTY'
export type ItemType = 'EQUIPMENT' | 'POTION' | 'SCROLL' | 'CONSUMABLE' | 'MISC'
export type ItemRarity = 'COMMON' | 'UNCOMMON' | 'RARE' | 'VERY_RARE' | 'LEGENDARY' | 'ARTIFACT'
export type TradeKind = 'BUY' | 'SELL'
/** PENDING: le toca al GM. COUNTERED: el GM contraofertó y le toca al jugador. */
export type TradeStatus = 'PENDING' | 'COUNTERED' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type OfferBy = 'PLAYER' | 'GM'
export type HistoryAction =
  | 'ITEM_ADDED'
  | 'ITEM_UPDATED'
  | 'ITEM_REMOVED'
  | 'CURRENCY_UPDATED'
  | 'TRADE_EXECUTED'
  | 'ITEMS_IMPORTED'

export interface Campaign {
  id: string
  slug: string
  name: string
  /** Regla de mesa: si es false, el jugador gasta sus monedas pero no se las suma. */
  playersCanAddCoins: boolean
}

export interface Me {
  role: Role
  campaign: Campaign
  characterId: string | null
  characterName: string | null
}

export interface ServerConfig {
  campaignCreationKeyRequired: boolean
}

export interface Purse {
  copper: number
  silver: number
  gold: number
  platinum: number
  totalCp: number
}

export interface Inventory {
  id: string
  kind: InventoryKind
  name: string
  description: string | null
  /** null cuando un jugador mira una tienda (la caja es del GM). */
  purse: Purse | null
  visibleToPlayers: boolean | null
  requiresApproval: boolean | null
  unlimitedFunds: boolean | null
  sellMarkupPct: number | null
  buybackPct: number | null
  playerName: string | null
  /** QR del personaje; solo el GM lo recibe. */
  joinUrl: string | null
  /** QR de la vitrina pública de una tienda; solo el GM lo recibe. */
  storefrontUrl: string | null
  updatedAt: string
}

export interface PurseInput {
  copper: number
  silver: number
  gold: number
  platinum: number
}

export interface ShopSettings {
  visibleToPlayers?: boolean
  requiresApproval?: boolean
  unlimitedFunds?: boolean
  sellMarkupPct?: number
  buybackPct?: number
}

export interface CreateInventoryInput extends ShopSettings {
  kind: InventoryKind
  name: string
  description?: string
  playerName?: string
  purse?: PurseInput
}

export interface UpdateInventoryInput extends ShopSettings {
  name?: string
  description?: string
  playerName?: string
}

export interface CurrencyChange extends PurseInput {
  note?: string
}

export interface Item {
  id: string
  inventoryId: string
  name: string
  type: ItemType
  category: string | null
  rarity: ItemRarity
  description: string | null
  notes: string | null
  quantity: number
  unlimitedStock: boolean
  weight: number | null
  /** Valor real. Al jugador solo le llega si `valueRevealed`, y nunca el de una tienda. */
  valueCp: number | null
  /** Lo que cobra la tienda (valor × recargo); null fuera de tiendas o sin precio. */
  priceCp: number | null
  /** Moneda en que se fijó el precio; null = oro. */
  priceDenomination: Denomination | null
  /** Lo que cree el dueño que vale; null = desconocido. */
  perceivedValueCp: number | null
  perceivedDenomination: Denomination | null
  valueRevealed: boolean
  /** En una tienda: acepta ofertas. */
  negotiable: boolean
  /** En una tienda: solo lo ve el GM. */
  hidden: boolean
  srdIndex: string | null
  properties: Record<string, unknown> | null
  updatedAt: string
}

export interface ItemInput {
  name: string
  type: ItemType
  rarity: ItemRarity
  category: string | null
  description: string | null
  notes: string | null
  quantity: number
  unlimitedStock: boolean
  weight: number | null
  valueCp: number | null
  priceDenomination: Denomination | null
  perceivedValueCp: number | null
  perceivedDenomination: Denomination | null
  /** Solo los respeta el servidor si los manda el GM. */
  valueRevealed: boolean
  negotiable: boolean
  hidden: boolean
  srdIndex: string | null
  properties: Record<string, unknown> | null
}

export interface Trade {
  id: string
  shopId: string
  shopName: string | null
  characterId: string
  characterName: string | null
  itemId: string
  itemName: string
  kind: TradeKind
  quantity: number
  /** Precio sobre la mesa. */
  unitPriceCp: number | null
  totalCp: number | null
  /** Precio de la tienda al pedir. */
  listUnitPriceCp: number | null
  /** Quién propuso `unitPriceCp`; null = precio de lista. */
  lastOfferBy: OfferBy | null
  priceDenomination: Denomination | null
  status: TradeStatus
  note: string | null
  createdAt: string
  resolvedAt: string | null
}

export interface HistoryEntry {
  id: string
  inventoryId: string | null
  inventoryName: string | null
  relatedInventoryId: string | null
  actor: string
  action: HistoryAction
  entityName: string | null
  details: Record<string, unknown> | null
  createdAt: string
}

export interface HistoryPage {
  entries: HistoryEntry[]
  total: number
}

export interface StorefrontItem {
  id: string
  name: string
  type: ItemType
  category: string | null
  rarity: ItemRarity
  description: string | null
  quantity: number
  unlimitedStock: boolean
  weight: number | null
  priceCp: number | null
  priceDenomination: Denomination | null
  negotiable: boolean
  properties: Record<string, unknown> | null
}

/** Vitrina pública. `shopId` solo viene si quien mira es jugador de esa campaña. */
export interface Storefront {
  name: string
  description: string | null
  requiresApproval: boolean
  items: StorefrontItem[]
  shopId: string | null
}

export interface ImportResult {
  rows: { row: number; name: string; quantity: number; valueCp: number | null; denomination: Denomination | null; merges: boolean }[]
  errors: { row: number; message: string }[]
  imported: number
}

export interface SrdItem {
  index: string
  name: string
  source: 'EQUIPMENT' | 'MAGIC_ITEM'
  type: ItemType
  category: string | null
  rarity: ItemRarity | null
  weight: number | null
  valueCp: number | null
  description: string | null
  properties: Record<string, unknown> | null
}

// --- Portal admin ---

export interface AdminInventory {
  id: string
  kind: InventoryKind
  name: string
  playerName: string | null
  /** Cantidad de items distintos (no de unidades). */
  items: number
  totalCp: number
  /** null en los personajes. */
  visibleToPlayers: boolean | null
}

export interface AdminSession {
  id: string
  role: Role
  /** null en las sesiones de GM. */
  characterName: string | null
  createdAt: string
  expiresAt: string
}

export interface AdminCampaign {
  id: string
  slug: string
  name: string
  createdAt: string
  /** Último movimiento del historial; null si nunca hubo. */
  lastActivityAt: string | null
  /** Solicitudes pendientes o con contraoferta. */
  openTrades: number
  inventories: AdminInventory[]
  sessions: AdminSession[]
}
