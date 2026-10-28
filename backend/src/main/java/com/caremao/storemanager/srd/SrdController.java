package com.caremao.storemanager.srd;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.item.ItemRarity;
import com.caremao.storemanager.item.ItemType;

@RestController
public class SrdController {

    public record SrdItemDto(String index, String name, String source, ItemType type, String category,
            ItemRarity rarity, Double weight, Long valueCp, String description, Map<String, Object> properties) {

        static SrdItemDto of(SrdItem i) {
            return new SrdItemDto(i.getSrdIndex(), i.getName(), i.getSource(), i.getType(), i.getCategory(),
                    i.getRarity(), i.getWeight(), i.getValueCp(), i.getDescription(), i.getProperties());
        }
    }

    private final SrdItemRepository srd;

    public SrdController(SrdItemRepository srd) {
        this.srd = srd;
    }

    /** Búsqueda por nombre (en inglés, como el SRD); pide al menos 2 letras. */
    @GetMapping("/api/srd/items")
    public List<SrdItemDto> search(Actor actor, @RequestParam("q") String query) {
        String q = query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        return srd.findTop20ByNameContainingIgnoreCaseOrderByNameAsc(q).stream().map(SrdItemDto::of).toList();
    }
}
