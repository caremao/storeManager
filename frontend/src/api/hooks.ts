import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { api, isStatus } from './client'
import type {
  Campaign,
  CreateInventoryInput,
  CurrencyChange,
  Denomination,
  HistoryPage,
  Inventory,
  InventoryKind,
  Item,
  ItemInput,
  Me,
  ServerConfig,
  SrdItem,
  Trade,
  TradeKind,
  TradeStatus,
  UpdateInventoryInput,
} from './types'

export const keys = {
  me: ['me'] as const,
  config: ['config'] as const,
  inventories: (kind?: InventoryKind) => ['inventories', kind ?? 'all'] as const,
  inventory: (id: string) => ['inventory', id] as const,
  items: (inventoryId: string) => ['items', inventoryId] as const,
  trades: (status?: TradeStatus) => ['trades', status ?? 'all'] as const,
  history: (inventoryId?: string, size = 20) => ['history', inventoryId ?? 'all', size] as const,
  srd: (query: string) => ['srd', query] as const,
}

/** Tras un cambio propio: lo mismo que haría el evento SSE, pero sin esperarlo. */
export function invalidateInventory(qc: QueryClient, ...ids: string[]) {
  for (const id of ids) {
    qc.invalidateQueries({ queryKey: ['inventory', id] })
    qc.invalidateQueries({ queryKey: ['items', id] })
  }
  qc.invalidateQueries({ queryKey: ['inventories'] })
  qc.invalidateQueries({ queryKey: ['history'] })
}

// --- Sesión ---

export function useMe() {
  return useQuery({
    queryKey: keys.me,
    queryFn: async (): Promise<Me | null> => {
      try {
        return (await api.get<Me>('/api/me')).data
      } catch (error) {
        if (isStatus(error, 401)) return null
        throw error
      }
    },
    staleTime: Infinity,
  })
}

export function useServerConfig() {
  return useQuery({
    queryKey: keys.config,
    queryFn: async () => (await api.get<ServerConfig>('/api/config')).data,
    staleTime: Infinity,
  })
}

function useSessionMutation<V>(request: (vars: V) => Promise<Me>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (me) => {
      qc.clear()
      qc.setQueryData(keys.me, me)
    },
  })
}

export function useCreateCampaign() {
  return useSessionMutation(async (body: { name: string; passphrase: string; creationKey?: string }) =>
    (await api.post<Me>('/api/campaigns', body)).data)
}

export function useGmLogin() {
  return useSessionMutation(async ({ slug, passphrase }: { slug: string; passphrase: string }) =>
    (await api.post<Me>(`/api/campaigns/${encodeURIComponent(slug)}/gm-login`, { passphrase })).data)
}

export function useJoin() {
  return useSessionMutation(async (token: string) => (await api.post<Me>('/api/join', { token })).data)
}

export function useUpdateCampaign() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (body: { playersCanAddCoins?: boolean }) =>
      (await api.patch<Campaign>('/api/campaign', body)).data,
    onSuccess: (campaign) => {
      qc.setQueryData<Me | null>(keys.me, (me) => (me ? { ...me, campaign } : me))
    },
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      await api.post('/api/logout')
    },
    onSettled: () => {
      qc.clear()
      qc.setQueryData(keys.me, null)
    },
  })
}

// --- Inventarios (tiendas y personajes) ---

export function useInventories(kind?: InventoryKind) {
  return useQuery({
    queryKey: keys.inventories(kind),
    queryFn: async () => (await api.get<Inventory[]>('/api/inventories', { params: { kind } })).data,
  })
}

export function useInventory(id: string | undefined) {
  return useQuery({
    queryKey: keys.inventory(id ?? ''),
    queryFn: async () => (await api.get<Inventory>(`/api/inventories/${id}`)).data,
    enabled: !!id,
    retry: (count, error) => !isStatus(error, 404) && count < 2,
  })
}

export function useCreateInventory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (body: CreateInventoryInput) => (await api.post<Inventory>('/api/inventories', body)).data,
    onSuccess: (inv) => invalidateInventory(qc, inv.id),
  })
}

export function useUpdateInventory(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (body: UpdateInventoryInput) =>
      (await api.patch<Inventory>(`/api/inventories/${id}`, body)).data,
    onSuccess: (inv) => {
      qc.setQueryData(keys.inventory(id), inv)
      invalidateInventory(qc, id)
    },
  })
}

export function useDeleteInventory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/api/inventories/${id}`)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inventories'] })
      qc.invalidateQueries({ queryKey: ['trades'] })
    },
  })
}

export function useChangeCurrency(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (body: CurrencyChange) =>
      (await api.post<Inventory>(`/api/inventories/${id}/currency`, body)).data,
    onSuccess: (inv) => {
      qc.setQueryData(keys.inventory(id), inv)
      invalidateInventory(qc, id)
    },
  })
}

export function useConvertCurrency(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (body: { from: Denomination; to: Denomination; amount: number }) =>
      (await api.post<Inventory>(`/api/inventories/${id}/currency/convert`, body)).data,
    onSuccess: (inv) => {
      qc.setQueryData(keys.inventory(id), inv)
      invalidateInventory(qc, id)
    },
  })
}

export function useRegenerateToken() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => (await api.post<Inventory>(`/api/inventories/${id}/token`)).data,
    onSuccess: (inv) => {
      qc.setQueryData(keys.inventory(inv.id), inv)
      qc.invalidateQueries({ queryKey: ['inventories'] })
    },
  })
}

// --- Items ---

export function useItems(inventoryId: string | undefined) {
  return useQuery({
    queryKey: keys.items(inventoryId ?? ''),
    queryFn: async () => (await api.get<Item[]>(`/api/inventories/${inventoryId}/items`)).data,
    enabled: !!inventoryId,
    retry: (count, error) => !isStatus(error, 404) && count < 2,
  })
}

export function useSaveItem(inventoryId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, input }: { id?: string; input: ItemInput }) => {
      const url = `/api/inventories/${inventoryId}/items`
      return (id ? await api.put<Item>(`${url}/${id}`, input) : await api.post<Item>(url, input)).data
    },
    onSuccess: () => invalidateInventory(qc, inventoryId),
  })
}

export function useRevealItem(inventoryId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (itemId: string) =>
      (await api.post<Item>(`/api/inventories/${inventoryId}/items/${itemId}/reveal`)).data,
    onSuccess: () => invalidateInventory(qc, inventoryId),
  })
}

export function useDeleteItem(inventoryId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (itemId: string) => {
      await api.delete(`/api/inventories/${inventoryId}/items/${itemId}`)
    },
    onSuccess: () => invalidateInventory(qc, inventoryId),
  })
}

export function useSrdSearch(query: string) {
  return useQuery({
    queryKey: keys.srd(query),
    queryFn: async () => (await api.get<SrdItem[]>('/api/srd/items', { params: { q: query } })).data,
    enabled: query.trim().length >= 2,
    staleTime: Infinity,
  })
}

// --- Trades ---

export function useTrades(status?: TradeStatus) {
  return useQuery({
    queryKey: keys.trades(status),
    queryFn: async () => (await api.get<Trade[]>('/api/trades', { params: { status } })).data,
  })
}

function useTradeMutation<V>(request: (vars: V) => Promise<Trade>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (trade) => {
      qc.invalidateQueries({ queryKey: ['trades'] })
      invalidateInventory(qc, trade.shopId, trade.characterId)
    },
  })
}

export function useRequestTrade(shopId: string) {
  return useTradeMutation(
    async (body: { kind: TradeKind; itemId: string; quantity: number; offerUnitPriceCp?: number; note?: string }) =>
      (await api.post<Trade>(`/api/shops/${shopId}/trades`, body)).data,
  )
}

export function useDirectTrade(shopId: string) {
  return useTradeMutation(
    async (body: { kind: TradeKind; characterId: string; itemId: string; quantity: number; unitPriceCp?: number }) =>
      (await api.post<Trade>(`/api/shops/${shopId}/direct-trades`, body)).data,
  )
}

/** El GM acepta el precio que está sobre la mesa. */
export function useApproveTrade() {
  return useTradeMutation(async (id: string) => (await api.post<Trade>(`/api/trades/${id}/approve`)).data)
}

/** El jugador acepta la contraoferta del GM. */
export function useAcceptTrade() {
  return useTradeMutation(async (id: string) => (await api.post<Trade>(`/api/trades/${id}/accept`)).data)
}

/** Contraoferta del GM o nueva oferta del jugador (según de quién sea el turno). */
export function useCounterTrade() {
  return useTradeMutation(async ({ id, unitPriceCp }: { id: string; unitPriceCp: number }) =>
    (await api.post<Trade>(`/api/trades/${id}/counter`, { unitPriceCp })).data)
}

export function useRejectTrade() {
  return useTradeMutation(async (id: string) => (await api.post<Trade>(`/api/trades/${id}/reject`, {})).data)
}

export function useCancelTrade() {
  return useTradeMutation(async (id: string) => (await api.post<Trade>(`/api/trades/${id}/cancel`)).data)
}

// --- Historial ---

export function useHistory(inventoryId?: string, size = 20) {
  return useQuery({
    queryKey: keys.history(inventoryId, size),
    queryFn: async () =>
      (await api.get<HistoryPage>('/api/history', { params: { inventoryId, size } })).data,
  })
}
