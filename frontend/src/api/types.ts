// Espejo de los DTO del backend (records en *Dtos.java). Los campos `| null` siempre vienen en el JSON.

export type Role = 'GM' | 'PLAYER'
export type InventoryKind = 'SHOP' | 'CHARACTER' | 'PARTY'
export type ItemType = 'EQUIPMENT' | 'POTION' | 'SCROLL' | 'CONSUMABLE' | 'MISC'
export type ItemRarity = 'COMMON' | 'UNCOMMON' | 'RARE' | 'VERY_RARE' | 'LEGENDARY' | 'ARTIFACT'
export type TradeKind = 'BUY' | 'SELL'
export type TradeStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type HistoryAction = 'ITEM_ADDED' | 'ITEM_UPDATED' | 'ITEM_REMOVED' | 'CURRENCY_UPDATED' | 'TRADE_EXECUTED'

export interface Campaign {
  id: string
  slug: string
  name: string
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
  /** Solo el GM la recibe. */
  joinUrl: string | null
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
  valueCp: number | null
  /** Lo que cobra la tienda (valor × recargo); null fuera de tiendas o sin precio. */
  priceCp: number | null
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
  unitPriceCp: number | null
  totalCp: number | null
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
