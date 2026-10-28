package com.caremao.storemanager.storefront;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.common.ApiException;
import com.caremao.storemanager.inventory.Denomination;
import com.caremao.storemanager.inventory.Inventory;
import com.caremao.storemanager.inventory.InventoryRepository;
import com.caremao.storemanager.item.ItemRarity;
import com.caremao.storemanager.item.ItemRepository;
import com.caremao.storemanager.item.ItemType;
import com.caremao.storemanager.item.Pricing;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Vitrina pública de una tienda: lo que abre su QR. Sin sesión y de solo lectura; muestra los
 * productos visibles con precio y stock, nunca la caja, las notas, el valor base ni los ocultos.
 */
@RestController
public class StorefrontController {

    public record StorefrontItem(UUID id, String name, ItemType type, String category, ItemRarity rarity,
            String description, int quantity, boolean unlimitedStock, Double weight, Long priceCp,
            Denomination priceDenomination, boolean negotiable, Map<String, Object> properties) {
    }

    /** {@code shopId} solo viene si quien mira es un jugador de esa campaña, para ir a pedir. */
    public record Storefront(String name, String description, boolean requiresApproval, List<StorefrontItem> items,
            UUID shopId) {
    }

    private final InventoryRepository inventories;
    private final ItemRepository items;

    public StorefrontController(InventoryRepository inventories, ItemRepository items) {
        this.inventories = inventories;
        this.items = items;
    }

    @GetMapping("/api/storefront/{token}")
    public Storefront storefront(@PathVariable String token, HttpServletRequest request) {
        Inventory shop = inventories.findByPublicToken(token)
                .filter(Inventory::isShop)
                .filter(Inventory::isVisibleToPlayers)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Esta tienda está cerrada o el QR ya no sirve"));
        Actor viewer = (Actor) request.getAttribute(Actor.REQUEST_ATTRIBUTE);
        boolean playerHere = viewer != null && !viewer.isGm() && viewer.campaignId().equals(shop.getCampaignId());
        List<StorefrontItem> stock = items.findByInventoryIdOrderByNameAsc(shop.getId()).stream()
                .filter(item -> !item.isHidden())
                .map(item -> new StorefrontItem(item.getId(), item.getName(), item.getType(), item.getCategory(),
                        item.getRarity(), item.getDescription(), item.getQuantity(), item.isUnlimitedStock(),
                        item.getWeight(), Pricing.apply(item.getValueCp(), shop.getSellMarkupPct()),
                        item.getPriceDenomination(), item.isNegotiable(), item.getProperties()))
                .toList();
        return new Storefront(shop.getName(), shop.getDescription(), shop.isRequiresApproval(), stock,
                playerHere ? shop.getId() : null);
    }
}
